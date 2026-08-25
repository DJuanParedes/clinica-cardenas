[CmdletBinding()]
param(
    [switch]$Auto
)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference = 'Continue'
Set-Location -LiteralPath $PSScriptRoot
$out = Join-Path $PSScriptRoot 'DIAGNOSTICO-CLINICA.txt'
function Add-Line {
    param([AllowEmptyString()][string]$Text = '')
    Add-Content -LiteralPath $out -Value $Text -Encoding UTF8
}
function Add-Section {
    param([string]$Title)
    Add-Line ''
    Add-Line "===== $Title ====="
}
function Add-CommandOutput {
    param(
        [string]$Label,
        [scriptblock]$Command
    )
    try {
        $result = & $Command 2>&1
        if ($null -eq $result -or @($result).Count -eq 0) {
            Add-Line "[$Label] Sin salida."
        } else {
            foreach ($line in @($result)) { Add-Line ([string]$line) }
        }
    }
    catch {
        Add-Line "[$Label] ERROR: $($_.Exception.Message)"
    }
}
Set-Content -LiteralPath $out -Value @(
    '======================================================'
    'CLINICA CARDENAS - DIAGNOSTICO AUTOMATICO'
    '======================================================'
    "Fecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    "Carpeta: $PSScriptRoot"
) -Encoding UTF8
Add-Section 'VERSION'
$versionFile = Join-Path $PSScriptRoot 'VERSION.txt'
if (Test-Path -LiteralPath $versionFile) {
    Get-Content -LiteralPath $versionFile | ForEach-Object { Add-Line $_ }
} else {
    Add-Line 'VERSION.txt no encontrado.'
}
Add-Section 'POWERSHELL'
Add-Line "Version: $($PSVersionTable.PSVersion)"
Add-Line "Edition: $($PSVersionTable.PSEdition)"
Add-Section 'DOCKER INFO'
$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($null -eq $docker) {
    Add-Line 'Docker no se encuentra en PATH.'
} else {
    Add-Line "Docker ejecutable: $($docker.Source)"
    Add-CommandOutput 'docker version' { docker version }
    Add-Line ''
    Add-CommandOutput 'docker info' { docker info }
}
Add-Section 'ALMACENAMIENTO DOCKER Y DISCO'
if ($null -eq $docker) {
    Add-Line 'Docker no está disponible para consultar almacenamiento.'
} else {
    Add-CommandOutput 'docker system df' { docker system df }
}
try {
    $driveName = [System.IO.Path]::GetPathRoot($PSScriptRoot).TrimEnd('\').TrimEnd(':')
    $drive = Get-PSDrive -Name $driveName -ErrorAction Stop
    Add-Line ("Disco ${driveName}: libre {0:N2} GB de {1:N2} GB" -f ($drive.Free/1GB), (($drive.Used+$drive.Free)/1GB))
} catch {
    Add-Line "No se pudo calcular espacio libre del disco: $($_.Exception.Message)"
}
Add-Section 'DOCKER COMPOSE PS'
if ($null -eq $docker) {
    Add-Line 'No se puede consultar Docker Compose porque Docker no está disponible.'
} else {
    Add-CommandOutput 'docker compose ps' { docker compose ps }
}
Add-Section 'INTERPRETACION RAPIDA'
if ($null -ne $docker) {
    try {
        $containerIds = @(& docker compose ps -aq 2>$null)
        if ($containerIds.Count -eq 0) {
            Add-Line 'ESTADO: no existen contenedores de Clinica Cardenas.'
            $expected=@('mysql:8.4','clinica-cardenas-local-eureka-server:latest','clinica-cardenas-local-auth-service:latest','clinica-cardenas-local-patient-service:latest','clinica-cardenas-local-appointment-service:latest','clinica-cardenas-local-clinical-service:latest','clinica-cardenas-local-finance-service:latest','clinica-cardenas-local-lab-service:latest','clinica-cardenas-local-operations-service:latest','clinica-cardenas-local-inventory-service:latest','clinica-cardenas-local-dashboard-service:latest','clinica-cardenas-local-content-service:latest','clinica-cardenas-local-encounter-service:latest','clinica-cardenas-local-survey-service:latest','clinica-cardenas-local-backup-service:latest','clinica-cardenas-local-api-gateway:latest','clinica-cardenas-local-frontend:latest')
            $present=0
            foreach($img in $expected){ & docker image inspect $img *> $null; if($LASTEXITCODE -eq 0){ $present++ } }
            if($present -eq 17){
                Add-Line 'Las 17 imagenes necesarias existen localmente; no es necesario recompilar para crear contenedores.'
                Add-Line 'Ejecuta 02-INICIAR.bat: verificara las imagenes, creara los 17 contenedores y arrancara el sistema.'
            } else {
                Add-Line "Imagenes disponibles: $present/17. La instalacion necesita completar las imagenes faltantes."
                Add-Line 'Revisa VERIFICACION DE IMAGENES LOCALES y LOGS DE COMPILACION.'
            }
        } else {
            Add-Line "Contenedores de la clinica detectados: $($containerIds.Count)."
        }
    } catch { Add-Line "No se pudo interpretar el estado Compose: $($_.Exception.Message)" }
}
Add-Section 'MYSQL'
if ($null -eq $docker) { Add-Line 'Docker no disponible.' }
else {
    & docker inspect clinica-mysql *> $null
    if($LASTEXITCODE -eq 0){ Add-CommandOutput 'mysql health' { docker inspect --format 'status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}} restarts={{.RestartCount}}' clinica-mysql } }
    else { Add-Line 'MySQL aun no fue creado.' }
}

Add-Section 'GATEWAY HEALTH'
try {
    $health = Invoke-RestMethod -Uri 'http://localhost:8090/actuator/health' -Method Get -TimeoutSec 10 -ErrorAction Stop
    ($health | ConvertTo-Json -Depth 8) -split "`r?`n" | ForEach-Object { Add-Line $_ }
}
catch {
    Add-Line "ERROR consultando Gateway: $($_.Exception.Message)"
}
Add-Section 'PORTAL WEB'
try {
    $response = Invoke-WebRequest -Uri 'http://localhost:8080' -Method Get -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
    Add-Line "HTTP $([int]$response.StatusCode) - Portal accesible"
}
catch {
    Add-Line "ERROR consultando Portal: $($_.Exception.Message)"
}
Add-Section 'EUREKA'
try {
    $response = Invoke-WebRequest -Uri 'http://localhost:8761' -Method Get -TimeoutSec 10 -UseBasicParsing -ErrorAction Stop
    Add-Line "HTTP $([int]$response.StatusCode) - Eureka accesible"
}
catch {
    Add-Line "ERROR consultando Eureka: $($_.Exception.Message)"
}
Add-Section 'PREVALIDACION PREVENTIVA'
$preflightLog = Join-Path $PSScriptRoot 'PREVALIDACION.txt'
if (Test-Path -LiteralPath $preflightLog) { Get-Content -LiteralPath $preflightLog | ForEach-Object { Add-Line $_ } }
else { Add-Line 'PREVALIDACION.txt no existe todavia.' }
Add-Section 'VERIFICACION FUNCIONAL / REGLAS ESTATICAS'
$functionalVerifyLog = Join-Path $PSScriptRoot 'VERIFICACION-FUNCIONAL.txt'
if (Test-Path -LiteralPath $functionalVerifyLog) { Get-Content -LiteralPath $functionalVerifyLog | ForEach-Object { Add-Line $_ } }
else { Add-Line 'VERIFICACION-FUNCIONAL.txt no existe todavia.' }
Add-Section 'VERIFICACION DE IMAGENES LOCALES'
$imageState = Join-Path $PSScriptRoot 'IMAGENES-ESTADO.txt'
if(Test-Path -LiteralPath $imageState){ Get-Content -LiteralPath $imageState | ForEach-Object { Add-Line $_ } } else { Add-Line 'IMAGENES-ESTADO.txt no existe todavia.' }
$imageVerifyLog = Join-Path $PSScriptRoot 'build-logs\verificacion-imagenes.log'
if(Test-Path -LiteralPath $imageVerifyLog){ Get-Content -LiteralPath $imageVerifyLog -Tail 120 | ForEach-Object { Add-Line ([string]$_) } } else { Add-Line 'build-logs\verificacion-imagenes.log no existe todavia.' }
$imageVerifyConsoleLog = Join-Path $PSScriptRoot 'build-logs\verificacion-imagenes-console.log'
if(Test-Path -LiteralPath $imageVerifyConsoleLog){ Get-Content -LiteralPath $imageVerifyConsoleLog -Tail 120 | ForEach-Object { Add-Line ([string]$_) } } else { Add-Line 'build-logs\verificacion-imagenes-console.log no existe todavia.' }
Add-Section 'VERIFICACION RUNTIME API GATEWAY'
$gatewayRuntimeState=Join-Path $PSScriptRoot 'GATEWAY-RUNTIME-ESTADO.txt'
if(Test-Path -LiteralPath $gatewayRuntimeState){ Get-Content -LiteralPath $gatewayRuntimeState | ForEach-Object { Add-Line $_ } } else { Add-Line 'GATEWAY-RUNTIME-ESTADO.txt no existe todavia.' }
$gatewayRepairLog=Join-Path $PSScriptRoot 'build-logs\gateway-repair.log'
if(Test-Path -LiteralPath $gatewayRepairLog){ Get-Content -LiteralPath $gatewayRepairLog -Tail 100 | ForEach-Object { Add-Line ([string]$_) } } else { Add-Line 'build-logs\gateway-repair.log no existe todavia.' }

Add-Section 'RUTEO LOCAL GATEWAY / DOCKER'
$routingState=Join-Path $PSScriptRoot 'RUTEO-LOCAL-ESTADO.txt'
if(Test-Path -LiteralPath $routingState){ Get-Content -LiteralPath $routingState | ForEach-Object { Add-Line $_ } } else { Add-Line 'RUTEO-LOCAL-ESTADO.txt no existe todavia.' }
$routingLog=Join-Path $PSScriptRoot 'startup-logs\ruteo-local.log'
if(Test-Path -LiteralPath $routingLog){ Get-Content -LiteralPath $routingLog | Select-Object -Last 120 | ForEach-Object { Add-Line $_ } } else { Add-Line 'startup-logs\ruteo-local.log no existe todavia.' }

Add-Section 'VALIDACION BACKUP LOCAL'
$backupState=Join-Path $PSScriptRoot 'BACKUP-ESTADO.txt'
if(Test-Path -LiteralPath $backupState){ Get-Content -LiteralPath $backupState | ForEach-Object { Add-Line $_ } } else { Add-Line 'BACKUP-ESTADO.txt no existe todavia.' }
$backupValidationLog=Join-Path $PSScriptRoot 'startup-logs\backup-validacion.log'
if(Test-Path -LiteralPath $backupValidationLog){ Get-Content -LiteralPath $backupValidationLog | Select-Object -Last 220 | ForEach-Object { Add-Line $_ } } else { Add-Line 'startup-logs\backup-validacion.log no existe todavia.' }

Add-Section 'VALIDACION DE INICIALIZACION MYSQL'
$mysqlState = Join-Path $PSScriptRoot 'MYSQL-ESTADO.txt'
if(Test-Path -LiteralPath $mysqlState){ Get-Content -LiteralPath $mysqlState | ForEach-Object { Add-Line $_ } } else { Add-Line 'MYSQL-ESTADO.txt no existe todavia.' }
$mysqlValidationLog = Join-Path $PSScriptRoot 'startup-logs\mysql-validacion.log'
if(Test-Path -LiteralPath $mysqlValidationLog){ Get-Content -LiteralPath $mysqlValidationLog -Tail 120 | ForEach-Object { Add-Line ([string]$_) } } else { Add-Line 'startup-logs\mysql-validacion.log no existe todavia.' }
Add-Section 'CREACION / VERIFICACION DE CONTENEDORES'
$containerState = Join-Path $PSScriptRoot 'CONTENEDORES-ESTADO.txt'
if(Test-Path -LiteralPath $containerState){ Get-Content -LiteralPath $containerState | ForEach-Object { Add-Line $_ } } else { Add-Line 'CONTENEDORES-ESTADO.txt no existe todavia.' }
$containerCreateLog = Join-Path $PSScriptRoot 'build-logs\contenedores.log'
if(Test-Path -LiteralPath $containerCreateLog){ Get-Content -LiteralPath $containerCreateLog -Tail 120 | ForEach-Object { Add-Line ([string]$_) } } else { Add-Line 'build-logs\contenedores.log no existe todavia.' }
Add-Section 'ESTADO DETALLADO DE CONTENEDORES'
if ($null -eq $docker) { Add-Line 'Docker no disponible.' }
else {
    foreach ($name in @('clinica-mysql','clinica-eureka-server','clinica-auth-service','clinica-patient-service','clinica-appointment-service','clinica-clinical-service','clinica-finance-service','clinica-lab-service','clinica-operations-service','clinica-content-service','clinica-dashboard-service','clinica-inventory-service','clinica-encounter-service','clinica-survey-service','clinica-backup-service','clinica-api-gateway','clinica-frontend')) {
        & docker inspect $name *> $null
        if($LASTEXITCODE -eq 0){ Add-CommandOutput $name { docker inspect --format 'name={{.Name}} status={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} error={{.State.Error}} restarts={{.RestartCount}}' $name } }
        else { Add-Line "$name : no creado" }
    }
}
Add-Section 'ERRORES DE ARRANQUE'
$startupDir = Join-Path $PSScriptRoot 'startup-logs'
if (Test-Path -LiteralPath $startupDir) {
    foreach ($f in Get-ChildItem -LiteralPath $startupDir -Filter '*.log' -File -ErrorAction SilentlyContinue) {
        Add-Line "--- $($f.Name) ---"
        Get-Content -LiteralPath $f.FullName -Tail 140 -ErrorAction SilentlyContinue | ForEach-Object { Add-Line ([string]$_) }
    }
}
# Clasificacion runtime de fallos conocidos que no pertenecen al build.
$gatewayStartupLog=Join-Path $startupDir 'clinica-api-gateway.log'
if(Test-Path -LiteralPath $gatewayStartupLog){
    $gt=[IO.File]::ReadAllText($gatewayStartupLog,[Text.Encoding]::UTF8)
    if($gt -match 'WebClient\$Builder.*could not be found'){ Add-Line 'DIAGNOSTICO GATEWAY: falta runtime vigente del Gateway; ejecuta 07-REPARAR-RUTEO-Y-VALIDAR.bat para reparacion dirigida.' }
}
Add-Section 'CLASIFICACION AUTOMATICA DE ERRORES'
$classLog = Join-Path $PSScriptRoot 'CLASIFICACION-ERRORES.txt'
if (Test-Path -LiteralPath $classLog) { Get-Content -LiteralPath $classLog -Tail 80 | ForEach-Object { Add-Line $_ } }
else { Add-Line 'CLASIFICACION-ERRORES.txt no existe todavia.' }

Add-Section 'ESTADO DEL BUILD'
$buildState=Join-Path $PSScriptRoot 'BUILD-ESTADO.txt'
if(Test-Path -LiteralPath $buildState){ Get-Content -LiteralPath $buildState | ForEach-Object { Add-Line $_ } } else { Add-Line 'BUILD-ESTADO.txt no existe todavia.' }
Add-Section 'COMPILACION ACTUAL'
$compileLog = Join-Path $PSScriptRoot 'COMPILACION-ACTUAL.txt'
if (Test-Path -LiteralPath $compileLog) {
    Get-Content -LiteralPath $compileLog | ForEach-Object { Add-Line $_ }
} else {
    Add-Line 'COMPILACION-ACTUAL.txt no existe todavia.'
}
Add-Section 'LOGS DE COMPILACION'
$buildLogDir = Join-Path $PSScriptRoot 'build-logs'
if (Test-Path -LiteralPath $buildLogDir) {
    foreach ($name in @('recursos.log','java-artifacts.log','imagenes.log','frontend.log','gateway-repair.log','verificacion-imagenes.log','verificacion-imagenes-console.log','contenedores.log')) {
        $lp = Join-Path $buildLogDir $name
        if (Test-Path -LiteralPath $lp) {
            Add-Line "--- $name ---"
            Get-Content -LiteralPath $lp -Tail 120 -ErrorAction SilentlyContinue | ForEach-Object { Add-Line ([string]$_) }
        }
    }
    $imageLogDir=Join-Path $buildLogDir 'images'
    if(Test-Path -LiteralPath $imageLogDir){
        foreach($f in Get-ChildItem -LiteralPath $imageLogDir -Filter '*.log' -File -ErrorAction SilentlyContinue){
            $tail=@(Get-Content -LiteralPath $f.FullName -Tail 30 -ErrorAction SilentlyContinue)
            if(($tail -join "`n") -match '(?i)error|failed|grpc|no space|killed|canceled'){
                Add-Line "--- images/$($f.Name) (posible fallo) ---"
                $tail | ForEach-Object { Add-Line ([string]$_) }
            }
        }
    }
} else { Add-Line 'No existen logs de compilacion todavia.' }
Add-Section 'PRUEBA FUNCIONAL'
$functionalLog = Join-Path $PSScriptRoot 'PRUEBA-FUNCIONAL.txt'
if (Test-Path -LiteralPath $functionalLog) {
    Get-Content -LiteralPath $functionalLog | ForEach-Object { Add-Line $_ }
} else {
    Add-Line 'PRUEBA-FUNCIONAL.txt no existe todavía.'
}
Add-Section 'ULTIMOS LOGS'
if ($null -eq $docker) {
    Add-Line 'No se pueden obtener logs porque Docker no está disponible.'
} else {
    Add-CommandOutput 'docker compose logs' { docker compose logs --tail 180 }
}
Add-Section 'FIN DEL DIAGNOSTICO'
Add-Line 'El diagnóstico terminó. Los errores de una sección no impiden recopilar las demás.'
Write-Host 'Diagnóstico generado en:' -ForegroundColor Green
Write-Host $out
if (-not $Auto) {
    Write-Host 'Presiona Enter para cerrar.'
    [void](Read-Host)
}
exit 0
