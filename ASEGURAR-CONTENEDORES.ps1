[CmdletBinding()]
param([switch]$ForceRecreate)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$logDir = Join-Path $PSScriptRoot 'build-logs'
if(-not (Test-Path -LiteralPath $logDir)){ New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$logPath = Join-Path $logDir 'contenedores.log'
$statusPath = Join-Path $PSScriptRoot 'CONTENEDORES-ESTADO.txt'

function Get-ContainerName([string]$service){
    if($service -eq 'mysql'){ return 'clinica-mysql' }
    return "clinica-$service"
}
function Invoke-DockerSafe([string[]]$DockerArgs,[switch]$MergeError){
    $previous=$ErrorActionPreference
    try{
        $ErrorActionPreference='Continue'
        if($MergeError){ $raw=@(& docker @DockerArgs 2>&1) }
        else { $raw=@(& docker @DockerArgs 2>$null) }
        $rc=$LASTEXITCODE
        $output=@($raw | ForEach-Object { [string]$_ })
        return [pscustomobject]@{ExitCode=$rc;Output=$output}
    } finally { $ErrorActionPreference=$previous }
}
function Get-ExistingContainerSet {
    $r=Invoke-DockerSafe @('ps','-a','--format','{{.Names}}')
    if($r.ExitCode -ne 0){ throw 'No se pudo listar los contenedores Docker.' }
    $set=@{}
    foreach($line in $r.Output){
        $name=([string]$line).Trim()
        if(-not [string]::IsNullOrWhiteSpace($name)){ $set[$name]=$true }
    }
    return $set
}

$dockerInfo=Invoke-DockerSafe @('info')
if($dockerInfo.ExitCode -ne 0){
    'Estado=ERROR_DOCKER' | Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-Host 'ERROR: Docker Desktop no responde.' -ForegroundColor Red
    exit 1
}

$compose=Invoke-DockerSafe @('compose','config','--services')
$services=@($compose.Output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if($compose.ExitCode -ne 0 -or $services.Count -ne 17){
    @('Estado=ERROR_COMPOSE',"ServiciosDetectados=$($services.Count)") | Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-Host "ERROR: Compose debe declarar 17 servicios y detecto $($services.Count)." -ForegroundColor Red
    exit 1
}

try { $existingBefore=Get-ExistingContainerSet }
catch {
    @('Estado=ERROR_LISTADO',"Detalle=$($_.Exception.Message)") | Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
$missingBefore=@()
foreach($svc in $services){
    $name=Get-ContainerName $svc
    if(-not $existingBefore.ContainsKey($name)){ $missingBefore += $svc }
}

if($ForceRecreate -or $missingBefore.Count -gt 0){
    if($ForceRecreate){
        Write-Host 'Recreando contenedores con la configuracion/imagenes vigentes y preservando volumenes...' -ForegroundColor Cyan
        $createArgs=@('compose','create','--force-recreate','--no-build','--pull','never')
    } else {
        Write-Host "Creando/verificando contenedores Docker ($($missingBefore.Count) faltantes)..." -ForegroundColor Cyan
        $createArgs=@('compose','create','--no-build','--pull','never')
    }
    # Esta fase nunca compila y no renueva/elimina volumenes nombrados.
    $create=Invoke-DockerSafe $createArgs -MergeError
    $create.Output | Set-Content -LiteralPath $logPath -Encoding UTF8
    $create.Output | ForEach-Object { Write-Host ([string]$_) }
    if($create.ExitCode -ne 0){
        @('Estado=ERROR_CREACION',"Faltaban=$($missingBefore -join ',')","ExitCode=$($create.ExitCode)") | Set-Content -LiteralPath $statusPath -Encoding UTF8
        Write-Host 'ERROR: Docker Compose no pudo crear los contenedores. Revisa build-logs\contenedores.log.' -ForegroundColor Red
        exit 1
    }
} else {
    'Todos los contenedores ya existian; no fue necesario recrearlos en uso diario.' | Set-Content -LiteralPath $logPath -Encoding UTF8
}

try { $existingAfter=Get-ExistingContainerSet }
catch {
    @('Estado=ERROR_LISTADO_POST',"Detalle=$($_.Exception.Message)") | Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
$missingAfter=@()
foreach($svc in $services){
    $name=Get-ContainerName $svc
    if(-not $existingAfter.ContainsKey($name)){ $missingAfter += "$svc ($name)" }
}
$createdCount=17-$missingAfter.Count
if($missingAfter.Count -gt 0){
    @('Estado=INCOMPLETO',"Contenedores=$createdCount/17","Faltan=$($missingAfter -join ',')") | Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-Host "ERROR: solo existen $createdCount/17 contenedores. Faltan: $($missingAfter -join ', ')" -ForegroundColor Red
    exit 1
}

# Verifica los volumenes persistentes por las etiquetas oficiales que Compose asigna a los volumenes nombrados.
$mysqlVol=Invoke-DockerSafe @('volume','ls','--filter','label=com.docker.compose.project=clinica-cardenas-local','--filter','label=com.docker.compose.volume=mysql-data','--quiet')
$docsVol=Invoke-DockerSafe @('volume','ls','--filter','label=com.docker.compose.project=clinica-cardenas-local','--filter','label=com.docker.compose.volume=clinical-documents','--quiet')
if($mysqlVol.ExitCode -ne 0 -or $docsVol.ExitCode -ne 0){
    @('Estado=ERROR_VOLUMENES','Detalle=No se pudieron listar volumenes Docker por etiquetas Compose') | Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-Host 'ERROR: no se pudieron verificar los volumenes persistentes.' -ForegroundColor Red
    exit 1
}
$mysqlVolume=@($mysqlVol.Output | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }).Count -gt 0
$docsVolume=@($docsVol.Output | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) }).Count -gt 0
if(-not $mysqlVolume -or -not $docsVolume){
    @('Estado=ERROR_VOLUMENES',"MySQLVolume=$mysqlVolume","ClinicalDocumentsVolume=$docsVolume") | Set-Content -LiteralPath $statusPath -Encoding UTF8
    Write-Host 'ERROR: los 17 contenedores existen, pero faltan volumenes persistentes esperados.' -ForegroundColor Red
    exit 1
}

@('Estado=OK','Contenedores=17/17','VolumenMySQL=OK','VolumenDocumentos=OK',"Fecha=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')") | Set-Content -LiteralPath $statusPath -Encoding UTF8
Write-Host 'Contenedores Docker verificados: 17/17 (MySQL + 16 componentes).' -ForegroundColor Green
Write-Host 'Volumenes persistentes verificados: MySQL + documentos clinicos.' -ForegroundColor Green
exit 0
