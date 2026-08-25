[CmdletBinding()]
param()
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot

$statePath=Join-Path $PSScriptRoot 'GATEWAY-RUNTIME-ESTADO.txt'
$currentVersion=([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'VERSION.txt'),[Text.Encoding]::UTF8)).Trim()
$image='clinica-cardenas-local-api-gateway:latest'
$requiredMarker='docker-dns-auth-scope-v3'
try {
    $previous=$ErrorActionPreference
    $ErrorActionPreference='Continue'
    $raw=@(& docker image inspect $image 2>$null)
    $rc=$LASTEXITCODE
    $ErrorActionPreference=$previous
    if($rc -ne 0 -or $raw.Count -eq 0){
        @('Estado=REPARACION_REQUERIDA',"Version=$currentVersion",'Motivo=imagen gateway ausente') | Set-Content -LiteralPath $statePath -Encoding UTF8
        Write-Host 'Gateway runtime: imagen ausente; se requiere build/repair.' -ForegroundColor Yellow
        exit 2
    }
    $parsed=(($raw -join "`n") | ConvertFrom-Json -ErrorAction Stop)
    if($parsed -is [System.Array]){ $parsed=$parsed[0] }
    $labels=$parsed.Config.Labels
    $version=''
    $marker=''
    if($null -ne $labels){
        $vp=$labels.PSObject.Properties['org.opencontainers.image.version']; if($null -ne $vp){$version=[string]$vp.Value}
        $mp=$labels.PSObject.Properties['pe.clinica.cardenas.gateway.runtime']; if($null -ne $mp){$marker=[string]$mp.Value}
    }
    if($version -ne $currentVersion -or $marker -ne $requiredMarker){
        @('Estado=REPARACION_REQUERIDA',"Version=$currentVersion","ImagenVersion=$version","RuntimeMarker=$marker") | Set-Content -LiteralPath $statePath -Encoding UTF8
        Write-Host "Gateway runtime requiere reparacion: version='$version' marker='$marker'." -ForegroundColor Yellow
        exit 2
    }
    @('Estado=OK',"Version=$currentVersion","RuntimeMarker=$marker","Fecha=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')") | Set-Content -LiteralPath $statePath -Encoding UTF8
    Write-Host "Gateway runtime verificado: $requiredMarker" -ForegroundColor Green
    exit 0
} catch {
    @('Estado=ERROR',"Version=$currentVersion","Detalle=$($_.Exception.Message)") | Set-Content -LiteralPath $statePath -Encoding UTF8
    Write-Host "ERROR verificando runtime Gateway: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
