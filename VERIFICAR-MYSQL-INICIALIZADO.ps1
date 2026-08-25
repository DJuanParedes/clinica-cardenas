[CmdletBinding()]
param()
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$statusPath=Join-Path $PSScriptRoot 'MYSQL-ESTADO.txt'
$logDir=Join-Path $PSScriptRoot 'startup-logs'
if(-not (Test-Path -LiteralPath $logDir)){ New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$logPath=Join-Path $logDir 'mysql-validacion.log'
$expected=@(
  @{db='auth_db';user='auth_user';key='AUTH_DB_PASSWORD'},
  @{db='patient_db';user='patient_user';key='PATIENT_DB_PASSWORD'},
  @{db='appointment_db';user='appointment_user';key='APPOINTMENT_DB_PASSWORD'},
  @{db='clinical_db';user='clinical_user';key='CLINICAL_DB_PASSWORD'},
  @{db='finance_db';user='finance_user';key='FINANCE_DB_PASSWORD'},
  @{db='lab_db';user='lab_user';key='LAB_DB_PASSWORD'},
  @{db='operations_db';user='operations_user';key='OPERATIONS_DB_PASSWORD'},
  @{db='inventory_db';user='inventory_user';key='INVENTORY_DB_PASSWORD'},
  @{db='content_db';user='content_user';key='CONTENT_DB_PASSWORD'},
  @{db='encounter_db';user='encounter_user';key='ENCOUNTER_DB_PASSWORD'},
  @{db='survey_db';user='survey_user';key='SURVEY_DB_PASSWORD'}
)
$lines=New-Object System.Collections.Generic.List[string]
function Log([string]$s){$script:lines.Add($s)|Out-Null;Write-Host $s}
function SaveLog{try{$script:lines|Set-Content -LiteralPath $script:logPath -Encoding UTF8}catch{}}
function Invoke-DockerSafe([string[]]$DockerArgs){
  $previous=$ErrorActionPreference
  try{
    $ErrorActionPreference='Continue'
    $raw=@(& docker @DockerArgs 2>&1)
    $rc=$LASTEXITCODE
    $out=@($raw | ForEach-Object { [string]$_ })
    return [pscustomobject]@{ExitCode=$rc;Output=$out}
  } finally { $ErrorActionPreference=$previous }
}
function Get-ContainerInfo {
  $r=Invoke-DockerSafe @('inspect','clinica-mysql')
  if($r.ExitCode -ne 0){ throw 'El contenedor clinica-mysql no existe.' }
  try{
    $obj=(($r.Output -join "`n") | ConvertFrom-Json)[0]
    return $obj
  }catch{ throw 'No se pudo interpretar el JSON de clinica-mysql.' }
}
function Get-ContainerHealth([object]$containerInfo) {
  if($containerInfo.State.Health){ return ([string]$containerInfo.State.Health.Status).Trim().ToLowerInvariant() }
  return ([string]$containerInfo.State.Status).Trim().ToLowerInvariant()
}
function Get-ContainerEnvironment([object]$containerInfo){
  $map=@{}
  foreach($entry in @($containerInfo.Config.Env)){
    $value=[string]$entry
    $idx=$value.IndexOf('=')
    if($idx -gt 0){ $map[$value.Substring(0,$idx)]=$value.Substring($idx+1) }
  }
  return $map
}
function Invoke-MySqlDirect([string]$user,[string]$password,[string]$database,[string]$sql,[switch]$Tcp){
  if([string]::IsNullOrWhiteSpace($password)){ return [pscustomobject]@{ExitCode=97;Output=@('Password MySQL vacio para '+$user)} }
  $args=@('exec','--env',('MYSQL_PWD='+$password),'clinica-mysql','mysql')
  if($Tcp){ $args += @('--protocol=TCP','--host=127.0.0.1') } else { $args += '--protocol=SOCKET' }
  $args += ('--user='+$user)
  if(-not [string]::IsNullOrWhiteSpace($database)){ $args += ('--database='+$database) }
  $args += @('--batch','--skip-column-names',('--execute='+$sql))
  return (Invoke-DockerSafe $args)
}
function Repair-Initialization {
  Log 'Asegurando de forma idempotente las 11 bases y usuarios MySQL...'
  $repair=Invoke-DockerSafe @('exec','clinica-mysql','sh','/docker-entrypoint-initdb.d/01-init-databases.sh')
  foreach($line in $repair.Output){ if(-not [string]::IsNullOrWhiteSpace($line)){ Log ([string]$line) } }
  if($repair.ExitCode -ne 0){ throw "El reparador MySQL fallo con exit code $($repair.ExitCode)." }
  Start-Sleep -Seconds 2
}
function Test-AllServiceConnections([hashtable]$envMap,[switch]$LogSuccess){
  $failed=New-Object System.Collections.Generic.List[string]
  foreach($item in $expected){
    $password=[string]$envMap[$item.key]
    if([string]::IsNullOrWhiteSpace($password)){
      $failed.Add("$($item.user)/$($item.db): variable $($item.key) ausente en clinica-mysql")|Out-Null
      continue
    }
    # PowerShell -> Docker -> mysql, sin sh -lc ni comillas anidadas.
    # SELECT(1) no contiene espacios y evita el quoting fragil de Windows PowerShell 5.1.
    $probe=Invoke-MySqlDirect -user $item.user -password $password -database $item.db -sql 'SELECT(1)' -Tcp
    $values=@($probe.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if($probe.ExitCode -ne 0 -or $values -notcontains '1'){
      $detail=($probe.Output -join ' ').Trim()
      if([string]::IsNullOrWhiteSpace($detail)){ $detail="exit code $($probe.ExitCode)" }
      $failed.Add("$($item.user)/$($item.db): $detail")|Out-Null
    } elseif($LogSuccess){
      Log "OK $($item.db) / $($item.user)"
    }
  }
  return @($failed)
}
try{
  $info=Get-ContainerInfo
  $health=Get-ContainerHealth $info
  if($health -ne 'healthy'){throw "MySQL no esta healthy; estado=$health"}
  $envMap=Get-ContainerEnvironment $info

  # Prueba accesos reales. Si cualquiera falla, repara una sola vez y repite las 11 pruebas.
  $failed=@(Test-AllServiceConnections $envMap)
  if($failed.Count -gt 0){
    Log ('Accesos incompletos antes de reparar: ' + ($failed -join ' | '))
    Repair-Initialization
    $info=Get-ContainerInfo
    $envMap=Get-ContainerEnvironment $info
    $failed=@(Test-AllServiceConnections $envMap -LogSuccess)
    if($failed.Count -gt 0){ throw ('La reparacion termino pero aun fallan: ' + ($failed -join ' | ')) }
  } else {
    $failed=@(Test-AllServiceConnections $envMap -LogSuccess)
    if($failed.Count -gt 0){ throw ('La segunda comprobacion detecto fallos: ' + ($failed -join ' | ')) }
  }

  @('Estado=OK','Bases=11/11','Usuarios=11/11','ConexionReal=11/11','ReparacionAutomatica=HABILITADA',"Fecha=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')") | Set-Content -LiteralPath $statusPath -Encoding UTF8
  Log 'MySQL inicializado correctamente: 11/11 bases y 11/11 usuarios con acceso real.'
  SaveLog; exit 0
}catch{
  Log "ERROR MySQL: $($_.Exception.Message)"
  @('Estado=ERROR',"Detalle=$($_.Exception.Message)","Fecha=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')") | Set-Content -LiteralPath $statusPath -Encoding UTF8
  $logs=Invoke-DockerSafe @('logs','clinica-mysql','--tail','260')
  try{$logs.Output|Set-Content -LiteralPath (Join-Path $logDir 'clinica-mysql.log') -Encoding UTF8}catch{}
  SaveLog; exit 1
}
