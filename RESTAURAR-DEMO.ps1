param([Parameter(Mandatory=$true)][string]$BackupFolder)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$folder = (Resolve-Path $BackupFolder).Path
$manifestPath = Join-Path $folder "manifest.json"
if (-not (Test-Path $manifestPath)) { throw "La carpeta no contiene manifest.json" }
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
Write-Warning "La restauración reemplazará los datos actuales del entorno local."
docker compose down | Out-Host
foreach ($entry in $manifest.files) {
  $volume = [string]$entry.volume
  $archive = Join-Path $folder "$volume.tar.gz"
  if (-not (Test-Path $archive)) { throw "Falta el archivo $archive" }
  docker volume create $volume | Out-Null
  docker run --rm -v "${volume}:/data" -v "${folder}:/backup:ro" alpine:3.22 sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null || true; tar xzf /backup/$volume.tar.gz -C /data"
  if ($LASTEXITCODE -ne 0) { throw "No se pudo restaurar $volume" }
}
docker compose up -d | Out-Host
Write-Host "Restauración completada." -ForegroundColor Green
