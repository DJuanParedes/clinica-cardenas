[CmdletBinding()]
param()
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$statePath = Join-Path $PSScriptRoot 'RUTEO-LOCAL-ESTADO.txt'
$logDir = Join-Path $PSScriptRoot 'startup-logs'
if(-not (Test-Path -LiteralPath $logDir)){ New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$logPath = Join-Path $logDir 'ruteo-local.log'
if(Test-Path -LiteralPath $logPath){ Remove-Item -LiteralPath $logPath -Force }

function Log([string]$text){
  $text | Tee-Object -FilePath $logPath -Append | Write-Host
}
function Probe-Json([string]$label,[string]$url,[int]$attempts=4){
  $last=$null
  for($i=1;$i -le $attempts;$i++){
    try{
      $sw=[Diagnostics.Stopwatch]::StartNew()
      $r=Invoke-RestMethod -Uri $url -Method Get -Headers @{Accept='application/json';'Cache-Control'='no-cache'} -DisableKeepAlive -TimeoutSec 10 -ErrorAction Stop
      $sw.Stop()
      Log ("OK {0} {1}ms -> {2}" -f $label,$sw.ElapsedMilliseconds,$url)
      return $r
    } catch {
      $last=$_
      if($i -lt $attempts){ Start-Sleep -Seconds 2 }
    }
  }
  $detail = if($null -ne $last){ $last.Exception.Message } else { 'sin detalle' }
  throw ("{0} no responde: {1} :: {2}" -f $label,$url,$detail)
}
function Assert-HomeContract($value,[string]$source){
  if($null -eq $value){ throw "$source devolvio cuerpo JSON vacio" }
  $names=@($value.PSObject.Properties.Name)
  foreach($required in @('clinic','tagline','specialties','hours','phone','address')){
    if($names -notcontains $required){ throw "$source no contiene propiedad '$required'" }
  }
  if([string]::IsNullOrWhiteSpace([string]$value.clinic)){ throw "$source devolvio clinic vacio" }
  if(@($value.specialties).Count -lt 5){ throw "$source devolvio specialties incompleto" }
  $hours=[string]$value.hours
  if($hours -notmatch '08:00' -or $hours -notmatch '22:00'){ throw "$source devolvio horario inesperado" }
  # No comparar literalmente textos con tildes: Windows PowerShell 5.1 puede mostrar mojibake
  # aunque el JSON sea valido. Se valida contrato estructural + horario ASCII.
}

try {
  $direct = Probe-Json 'CONTENT_DIRECTO' 'http://localhost:8089/api/public/home'
  try { Assert-HomeContract $direct 'CONTENT_DIRECTO' } catch { throw ("ERROR_CONTRATO_DESTINO: " + $_.Exception.Message) }
  Log 'OK CONTENT_DIRECTO_CONTRATO -> JSON publico estructuralmente valido'

  $gatewayHealth = Probe-Json 'GATEWAY_HEALTH' 'http://localhost:8090/actuator/health' 20
  if([string]$gatewayHealth.status -ne 'UP'){ throw 'ERROR_RUTEO_GATEWAY: Gateway health no esta UP' }

  $viaGateway = Probe-Json 'CONTENT_VIA_GATEWAY' 'http://localhost:8090/api/public/home' 8
  try { Assert-HomeContract $viaGateway 'CONTENT_VIA_GATEWAY' } catch { throw ("ERROR_RUTEO_GATEWAY: " + $_.Exception.Message) }
  Log 'OK CONTENT_VIA_GATEWAY_CONTRATO -> JSON publico estructuralmente valido'

  $previous=$ErrorActionPreference
  $ErrorActionPreference='Continue'
  $rawInspect=@(& docker inspect clinica-api-gateway 2>$null)
  $rc=$LASTEXITCODE
  $ErrorActionPreference=$previous
  if($rc -ne 0 -or $rawInspect.Count -eq 0){ throw 'ERROR_RUTEO_GATEWAY: no se pudo inspeccionar clinica-api-gateway' }
  $parsed=(($rawInspect -join "`n") | ConvertFrom-Json -ErrorAction Stop)
  if($parsed -is [System.Array]){ $parsed=$parsed[0] }
  $envLines=@($parsed.Config.Env | ForEach-Object { [string]$_ })
  $required=@(
    'AUTH_ROUTE_URI=http://auth-service:8081','PATIENT_ROUTE_URI=http://patient-service:8082',
    'APPOINTMENT_ROUTE_URI=http://appointment-service:8083','CLINICAL_ROUTE_URI=http://clinical-service:8084',
    'FINANCE_ROUTE_URI=http://finance-service:8085','LAB_ROUTE_URI=http://lab-service:8086',
    'OPERATIONS_ROUTE_URI=http://operations-service:8087','DASHBOARD_ROUTE_URI=http://dashboard-service:8088',
    'CONTENT_ROUTE_URI=http://content-service:8089','INVENTORY_ROUTE_URI=http://inventory-service:8091',
    'ENCOUNTER_ROUTE_URI=http://encounter-service:8092','SURVEY_ROUTE_URI=http://survey-service:8093',
    'BACKUP_ROUTE_URI=http://backup-service:8094'
  )
  if(-not ($envLines -contains 'SPRING_CLOUD_DISCOVERY_ENABLED=false')){ throw 'ERROR_RUTEO_GATEWAY: DiscoveryClient sigue habilitado en Gateway local' }
  foreach($expected in $required){
    if(-not ($envLines -contains $expected)){ throw "ERROR_RUTEO_GATEWAY: falta $expected" }
  }

  @(
    'Estado=OK',
    'ContentDirecto=OK',
    'ContratoContentDirecto=OK',
    'Gateway=UP',
    'ContentViaGateway=OK',
    'ContratoContentViaGateway=OK',
    'RutasDockerDirectas=13/13',
    'DiscoveryGateway=DESACTIVADO_LOCAL',
    "Fecha=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
  ) | Set-Content -LiteralPath $statePath -Encoding UTF8
  Log 'Ruteo local verificado: content-service directo + Gateway + 13 URI Docker directas.'
  exit 0
} catch {
  $msg=$_.Exception.Message
  $kind='ERROR_RUTEO_GATEWAY'
  if($msg -match '^CONTENT_DIRECTO no responde'){ $kind='ERROR_SERVICIO_DESTINO' }
  elseif($msg -match '^ERROR_CONTRATO_DESTINO:'){ $kind='ERROR_CONTRATO_DESTINO' }
  @("Estado=$kind","Detalle=$msg","Fecha=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')") | Set-Content -LiteralPath $statePath -Encoding UTF8
  Log "ERROR: $msg"
  if($kind -eq 'ERROR_SERVICIO_DESTINO'){ exit 2 }
  if($kind -eq 'ERROR_CONTRATO_DESTINO'){ exit 3 }
  exit 4
}
