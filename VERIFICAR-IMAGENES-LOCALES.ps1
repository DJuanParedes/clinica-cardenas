[CmdletBinding()]
param()
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot

$logDir = Join-Path $PSScriptRoot 'build-logs'
if(-not (Test-Path -LiteralPath $logDir)){ New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$logPath = Join-Path $logDir 'verificacion-imagenes.log'
$statusPath = Join-Path $PSScriptRoot 'IMAGENES-ESTADO.txt'
$currentVersion=([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'VERSION.txt'),[Text.Encoding]::UTF8)).Trim()
$messages = New-Object System.Collections.Generic.List[string]

function Add-Log([string]$text){
  $script:messages.Add($text) | Out-Null
  Write-Host $text
}
function Save-Messages {
  # El log es diagnostico: nunca debe convertir una verificacion correcta en fallo.
  try {
    $tmp = $script:logPath + '.tmp.' + [Guid]::NewGuid().ToString('N')
    [IO.File]::WriteAllLines($tmp, [string[]]$script:messages, (New-Object Text.UTF8Encoding($true)))
    Move-Item -LiteralPath $tmp -Destination $script:logPath -Force
  } catch {
    Write-Warning "No se pudo guardar el log interno de imagenes: $($_.Exception.Message)"
  }
}
function Save-State([string[]]$lines){
  try { $lines | Set-Content -LiteralPath $script:statusPath -Encoding UTF8 }
  catch { Write-Warning "No se pudo guardar IMAGENES-ESTADO.txt: $($_.Exception.Message)" }
}
function Invoke-DockerSafe([string[]]$DockerArgs,[switch]$MergeError){
  # Los objetos ausentes (imagen/contenedor) son estados esperables durante una instalacion limpia.
  # ErrorActionPreference=Continue evita que stderr de docker.exe se convierta en una excepcion PowerShell 5.1.
  $previous = $ErrorActionPreference
  try {
    $ErrorActionPreference='Continue'
    if($MergeError){ $raw=@(& docker @DockerArgs 2>&1) }
    else { $raw=@(& docker @DockerArgs 2>$null) }
    $rc=$LASTEXITCODE
    $output=@($raw | ForEach-Object { [string]$_ })
    return [pscustomobject]@{ ExitCode=$rc; Output=$output }
  } finally {
    $ErrorActionPreference=$previous
  }
}
function Get-ImageInfo([string]$image){
  $result=Invoke-DockerSafe @('image','inspect',$image)
  if($result.ExitCode -ne 0 -or $result.Output.Count -eq 0){ return $null }
  try {
    $parsed = (($result.Output -join "`n") | ConvertFrom-Json -ErrorAction Stop)
    if($parsed -is [System.Array]){ return $parsed[0] }
    return $parsed
  } catch {
    throw "No se pudo interpretar docker image inspect para $image como JSON: $($_.Exception.Message)"
  }
}
function Get-LabelValue($info,[string]$name){
  if($null -eq $info -or $null -eq $info.Config -or $null -eq $info.Config.Labels){ return '' }
  $prop = $info.Config.Labels.PSObject.Properties[$name]
  if($null -eq $prop -or $null -eq $prop.Value){ return '' }
  return ([string]$prop.Value).Trim()
}

$exitCode=1
try {
  $dockerInfo=Invoke-DockerSafe @('info')
  if($dockerInfo.ExitCode -ne 0){ throw 'Docker Desktop no responde.' }
  $compose=Invoke-DockerSafe @('compose','config','--services')
  if($compose.ExitCode -ne 0){ throw 'docker compose config --services fallo.' }
  $services=@($compose.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
  if($services.Count -ne 17){ throw "Compose debe contener MySQL + 16 componentes; detectados: $($services.Count)." }

  # Inventario primero: NO se inspecciona una imagen que sabemos que no existe.
  # Esto hace que 0 imagenes sea INCOMPLETO (normal) y no ERROR_VERIFICADOR.
  $listResult=Invoke-DockerSafe @('image','ls','--format','{{.Repository}}:{{.Tag}}')
  if($listResult.ExitCode -ne 0){ throw 'No se pudo listar las imagenes Docker locales.' }
  $present = @{}
  foreach($line in $listResult.Output){
    $tag=([string]$line).Trim()
    if(-not [string]::IsNullOrWhiteSpace($tag)){ $present[$tag]=$true }
  }

  $bad=@(); $okCount=0
  foreach($svc in $services){
    if($svc -eq 'mysql'){
      if(-not $present.ContainsKey('mysql:8.4')){ $bad+='mysql (mysql:8.4 ausente)' }
      else { $okCount++; Add-Log 'OK mysql:8.4' }
      continue
    }
    $image="clinica-cardenas-local-${svc}:latest"
    if(-not $present.ContainsKey($image)){ $bad+="$svc ($image ausente)"; continue }
    $info=Get-ImageInfo $image
    if($null -eq $info){ $bad+="$svc ($image desaparecio durante la verificacion)"; continue }
    $label=Get-LabelValue $info 'org.opencontainers.image.version'
    if($label -ne $currentVersion){
      if([string]::IsNullOrWhiteSpace($label)){ $bad+="$svc (imagen sin etiqueta de version; se requiere $currentVersion)" }
      else { $bad+="$svc (imagen de version '$label'; se requiere $currentVersion)" }
      continue
    }
    $okCount++; Add-Log "OK $svc -> $image version=$label"
  }

  if($bad.Count -gt 0){
    Add-Log "Imagenes incompletas: $okCount/17 correctas. En instalacion limpia esto es NORMAL y obliga a compilar."
    foreach($item in $bad){ Add-Log " - $item" }
    Save-State @('Estado=INCOMPLETO',"ImagenesCorrectas=$okCount/17","Version=$currentVersion","Problemas=$($bad -join ' | ')")
    $exitCode=2
  } else {
    Add-Log "Imagenes locales completas y coherentes con ${currentVersion}: MySQL + 16 componentes (17/17)."
    Save-State @('Estado=OK','Imagenes=17/17',"Version=$currentVersion","Fecha=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
    $exitCode=0
  }
} catch {
  Add-Log "ERROR verificando imagenes: $($_.Exception.Message)"
  Save-State @('Estado=ERROR_VERIFICADOR',"Version=$currentVersion","Detalle=$($_.Exception.Message)")
  $exitCode=1
} finally {
  Save-Messages
}
exit $exitCode
