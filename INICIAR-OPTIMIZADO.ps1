[CmdletBinding()]
param()
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference = 'Continue'
Set-Location -LiteralPath $PSScriptRoot

$logDir = Join-Path $PSScriptRoot 'startup-logs'
if (Test-Path -LiteralPath $logDir) { Remove-Item -LiteralPath $logDir -Recurse -Force -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

function Get-State([string]$container) {
    $state = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $container 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace([string]$state)) { return 'missing' }
    return ([string]$state).Trim().ToLowerInvariant()
}
function Save-Logs([string[]]$containers) {
    foreach ($c in $containers | Select-Object -Unique) {
        $safe = $c -replace '[^a-zA-Z0-9_.-]','_'
        $final = Join-Path $logDir ($safe + '.log')
        $stdout = Join-Path $logDir ($safe + '.stdout.tmp')
        $stderr = Join-Path $logDir ($safe + '.stderr.tmp')
        try {
            $p = Start-Process -FilePath 'docker' -ArgumentList @('logs',$c,'--tail','260') -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
            $lines = @()
            if(Test-Path -LiteralPath $stdout){ $lines += @(Get-Content -LiteralPath $stdout -ErrorAction SilentlyContinue) }
            if(Test-Path -LiteralPath $stderr){ $lines += @(Get-Content -LiteralPath $stderr -ErrorAction SilentlyContinue) }
            [IO.File]::WriteAllLines($final,[string[]]$lines,(New-Object Text.UTF8Encoding($true)))
        } catch {
            [IO.File]::WriteAllText($final,"No se pudieron capturar logs: $($_.Exception.Message)",(New-Object Text.UTF8Encoding($true)))
        } finally {
            Remove-Item -LiteralPath $stdout,$stderr -Force -ErrorAction SilentlyContinue
        }
    }
}
function Get-RuntimeStatus([string]$container) {
    try {
        $raw=@(& docker inspect $container 2>$null)
        if($LASTEXITCODE -ne 0 -or $raw.Count -eq 0){ return [pscustomobject]@{Status='missing';Health='missing';Restarts=0;ExitCode=-1;Oom=$false} }
        $info=(($raw -join "`n") | ConvertFrom-Json); if($info -is [Array]){$info=$info[0]}
        $health=if($null -ne $info.State.Health){[string]$info.State.Health.Status}else{[string]$info.State.Status}
        return [pscustomobject]@{Status=([string]$info.State.Status).ToLowerInvariant();Health=$health.ToLowerInvariant();Restarts=[int]$info.RestartCount;ExitCode=[int]$info.State.ExitCode;Oom=[bool]$info.State.OOMKilled}
    } catch { return [pscustomobject]@{Status='unknown';Health='unknown';Restarts=0;ExitCode=-1;Oom=$false} }
}
function Wait-Containers([hashtable]$map,[int]$timeoutSeconds=300) {
    $deadline=(Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $bad=@()
        $fatal=@()
        foreach ($svc in $map.Keys) {
            $container=$map[$svc]
            $state=Get-State $container
            $runtime=Get-RuntimeStatus $container
            if ($svc -eq 'frontend') {
                if ($state -notin @('running','healthy')) { $bad += "$svc=$state" }
            } else {
                if ($state -ne 'healthy') { $bad += "$svc=$state" }
            }
            if ($runtime.Status -in @('exited','dead') -or $runtime.Oom -or ($runtime.Status -eq 'restarting' -and $runtime.Restarts -ge 3)) {
                $fatal += $container
                Write-Host "ERROR runtime: $svc status=$($runtime.Status) health=$($runtime.Health) restarts=$($runtime.Restarts) exit=$($runtime.ExitCode) oom=$($runtime.Oom)" -ForegroundColor Red
            }
        }
        if ($fatal.Count -gt 0) {
            Save-Logs $fatal
            Write-Host "ERROR: servicio detenido: $($fatal -join ', ')" -ForegroundColor Red
            return $false
        }
        if ($bad.Count -eq 0) { return $true }
        Write-Host ('Esperando: ' + ($bad -join ', ')) -ForegroundColor DarkYellow
        Start-Sleep -Seconds 4
    }
    $containers=@($map.Values)
    Save-Logs $containers
    Write-Host 'ERROR: timeout esperando servicios. Revisa startup-logs\' -ForegroundColor Red
    return $false
}
function Wait-OptionalHealthy([string]$service,[string]$container,[int]$timeoutSeconds=90) {
    $deadline=(Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $state=Get-State $container
        if ($state -eq 'healthy') { return $true }
        if ($state -in @('exited','dead')) { break }
        Start-Sleep -Seconds 4
    }
    Save-Logs @($container)
    Write-Host "AVISO: $service no quedo saludable. El resto del sistema continuara y sus logs quedaron en startup-logs\." -ForegroundColor Yellow
    return $false
}
function Start-Group([string]$title,[string[]]$services,[hashtable]$containers,[int]$timeout=300) {
    Write-Host "`n$title" -ForegroundColor Cyan
    $dockerArgs=@('compose','up','-d','--no-build','--no-deps') + $services
    & docker @dockerArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'ERROR: Docker no pudo iniciar el grupo. Si faltan imagenes ejecuta 01-INSTALAR-O-ACTUALIZAR.bat.' -ForegroundColor Red
        return $false
    }
    return (Wait-Containers $containers $timeout)
}

& docker info *> $null
if ($LASTEXITCODE -ne 0) { Write-Host 'ERROR: Docker Desktop no responde.' -ForegroundColor Red; exit 1 }

# Arranque por etapas: MySQL primero y luego las JVM en grupos.
if (-not (Start-Group 'Etapa 1/9 - MySQL' @('mysql') @{ 'mysql'='clinica-mysql' } 300)) { exit 1 }
Write-Host 'Verificando inicializacion real de las 11 bases MySQL...' -ForegroundColor Cyan
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'VERIFICAR-MYSQL-INICIALIZADO.ps1')
if ($LASTEXITCODE -ne 0) { Write-Host 'ERROR: MySQL no quedo inicializado correctamente. Revisa MYSQL-ESTADO.txt y startup-logs\mysql-validacion.log.' -ForegroundColor Red; exit 1 }
if (-not (Start-Group 'Etapa 2/9 - Eureka' @('eureka-server') @{ 'eureka-server'='clinica-eureka-server' } 180)) { exit 1 }
if (-not (Start-Group 'Etapa 3/9 - Servicios base A' @('auth-service','patient-service','finance-service','appointment-service') @{
    'auth-service'='clinica-auth-service'; 'patient-service'='clinica-patient-service'; 'finance-service'='clinica-finance-service'; 'appointment-service'='clinica-appointment-service'
} 360)) { exit 1 }
if (-not (Start-Group 'Etapa 4/9 - Servicios base B' @('clinical-service','lab-service','operations-service','content-service') @{
    'clinical-service'='clinica-clinical-service'; 'lab-service'='clinica-lab-service'; 'operations-service'='clinica-operations-service'; 'content-service'='clinica-content-service'
} 360)) { exit 1 }
if (-not (Start-Group 'Etapa 5/9 - Inventario y encuestas' @('inventory-service','survey-service') @{
    'inventory-service'='clinica-inventory-service'; 'survey-service'='clinica-survey-service'
} 300)) { exit 1 }
if (-not (Start-Group 'Etapa 6/9 - Encuentros clinicos' @('encounter-service') @{ 'encounter-service'='clinica-encounter-service' } 360)) { exit 1 }
if (-not (Start-Group 'Etapa 7/9 - Dashboard' @('dashboard-service') @{ 'dashboard-service'='clinica-dashboard-service' } 300)) { exit 1 }
# Backup forma parte del alcance funcional local (copias, plantillas Excel e importacion).
# Por eso el arranque no declara exito si Backup no esta healthy.
if (-not (Start-Group 'Etapa 8/9 - Backup y Gateway' @('backup-service','api-gateway') @{
    'backup-service'='clinica-backup-service'; 'api-gateway'='clinica-api-gateway'
} 360)) { exit 1 }
if (-not (Start-Group 'Etapa 9/9 - Portal web' @('frontend') @{ 'frontend'='clinica-frontend' } 120)) { exit 1 }

try {
    $health=Invoke-RestMethod -Uri 'http://localhost:8090/actuator/health' -TimeoutSec 15 -ErrorAction Stop
    if ($health.status -ne 'UP') { throw "Gateway=$($health.status)" }
    $front=Invoke-WebRequest -Uri 'http://localhost:8080' -UseBasicParsing -TimeoutSec 15 -ErrorAction Stop
    if ($front.StatusCode -ne 200) { throw "Portal HTTP $($front.StatusCode)" }
} catch {
    Save-Logs @('clinica-api-gateway','clinica-frontend')
    Write-Host "ERROR: servicios iniciados pero portal/gateway no responden: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
Write-Host "`nSISTEMA INICIADO CORRECTAMENTE" -ForegroundColor Green
Write-Host 'Portal:  http://localhost:8080'
Write-Host 'Gateway: http://localhost:8090/actuator/health'
Write-Host 'Eureka:  http://localhost:8761'
Write-Host 'MySQL:   localhost:3307 (solo local)'
exit 0
