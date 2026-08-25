[CmdletBinding()]
param(
  [int]$TimeoutSeconds = 180
)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$statePath=Join-Path $PSScriptRoot 'BACKUP-ESTADO.txt'
$logDir=Join-Path $PSScriptRoot 'startup-logs'
if(-not (Test-Path -LiteralPath $logDir)){ New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$logPath=Join-Path $logDir 'backup-validacion.log'
if(Test-Path -LiteralPath $logPath){ Remove-Item -LiteralPath $logPath -Force }

function Log([string]$text){ $text | Tee-Object -FilePath $logPath -Append | Write-Host }
function Get-ContainerInfo {
  $previous=$ErrorActionPreference
  $ErrorActionPreference='Continue'
  $raw=@(& docker inspect clinica-backup-service 2>$null)
  $rc=$LASTEXITCODE
  $ErrorActionPreference=$previous
  if($rc -ne 0 -or $raw.Count -eq 0){ return $null }
  $parsed=(($raw -join "`n") | ConvertFrom-Json -ErrorAction Stop)
  if($parsed -is [System.Array]){ return $parsed[0] }
  return $parsed
}
function Save-ContainerLogs {
  try{
    $stdout=Join-Path $logDir 'backup-docker.stdout.tmp'
    $stderr=Join-Path $logDir 'backup-docker.stderr.tmp'
    $p=Start-Process -FilePath 'docker' -ArgumentList @('logs','clinica-backup-service','--tail','220') -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Log '--- docker logs clinica-backup-service ---'
    if(Test-Path $stdout){ Get-Content $stdout -ErrorAction SilentlyContinue | ForEach-Object { Log ([string]$_) } }
    if(Test-Path $stderr){ Get-Content $stderr -ErrorAction SilentlyContinue | ForEach-Object { Log ([string]$_) } }
    Remove-Item $stdout,$stderr -Force -ErrorAction SilentlyContinue
  } catch { Log "No se pudieron capturar logs Backup: $($_.Exception.Message)" }
}

try{
  & docker info *> $null
  if($LASTEXITCODE -ne 0){ throw 'Docker Desktop no responde' }

  $info=Get-ContainerInfo
  $status=if($null -eq $info){'missing'}else{([string]$info.State.Status).ToLowerInvariant()}
  $health=if($null -ne $info -and $null -ne $info.State.Health){([string]$info.State.Health.Status).ToLowerInvariant()}else{'n/a'}
  Log "Estado inicial Backup: status=$status health=$health"

  if($null -eq $info -or $status -ne 'running'){
    Log 'Iniciando backup-service sin recompilar ni recrear otros servicios...'
    & docker compose up -d --no-build --no-deps backup-service
    if($LASTEXITCODE -ne 0){ throw 'docker compose no pudo iniciar backup-service' }
  } elseif($health -ne 'healthy'){
    Log 'backup-service esta running pero no healthy; reiniciando solo este contenedor...'
    & docker restart clinica-backup-service *> $null
    if($LASTEXITCODE -ne 0){ throw 'docker restart no pudo reiniciar backup-service' }
  }

  $deadline=(Get-Date).AddSeconds($TimeoutSeconds)
  $lastStatus='unknown'; $lastHealth='unknown'; $lastExit=-1; $lastOom=$false
  while((Get-Date) -lt $deadline){
    $info=Get-ContainerInfo
    if($null -eq $info){
      $lastStatus='missing'; $lastHealth='missing'
    } else {
      $lastStatus=([string]$info.State.Status).ToLowerInvariant()
      $lastHealth=if($null -ne $info.State.Health){([string]$info.State.Health.Status).ToLowerInvariant()}else{$lastStatus}
      $lastExit=[int]$info.State.ExitCode
      $lastOom=[bool]$info.State.OOMKilled
      if($lastStatus -eq 'running' -and $lastHealth -eq 'healthy'){ break }
      if($lastStatus -in @('exited','dead')){
        throw "backup-service se detuvo: status=$lastStatus health=$lastHealth exit=$lastExit oom=$lastOom"
      }
    }
    Start-Sleep -Seconds 3
  }
  if($lastStatus -ne 'running' -or $lastHealth -ne 'healthy'){
    throw "backup-service no quedo healthy en ${TimeoutSeconds}s: status=$lastStatus health=$lastHealth exit=$lastExit oom=$lastOom"
  }

  $healthBody=$null
  for($i=1;$i -le 5;$i++){
    try{
      $healthBody=Invoke-RestMethod -Uri 'http://localhost:8094/actuator/health' -Method Get -DisableKeepAlive -TimeoutSec 10 -ErrorAction Stop
      if([string]$healthBody.status -eq 'UP'){ break }
    } catch {
      if($i -lt 5){ Start-Sleep -Seconds 2 }
      else { throw }
    }
  }
  if($null -eq $healthBody -or [string]$healthBody.status -ne 'UP'){ throw 'actuator/health de backup-service no devolvio UP' }

  @(
    'Estado=OK',
    'Container=running',
    'Health=healthy',
    'HttpHealth=UP',
    'RequeridoParaValidacion=SI',
    "Fecha=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
  ) | Set-Content -LiteralPath $statePath -Encoding UTF8
  Log 'Backup local verificado: contenedor running + Docker health healthy + HTTP health UP.'
  exit 0
} catch {
  $msg=$_.Exception.Message
  Save-ContainerLogs
  @(
    'Estado=ERROR',
    "Detalle=$msg",
    "Fecha=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
  ) | Set-Content -LiteralPath $statePath -Encoding UTF8
  Log "ERROR: $msg"
  exit 1
}
