$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$destination = Join-Path $PSScriptRoot "backups\$stamp"
New-Item -ItemType Directory -Force -Path $destination | Out-Null
Write-Host "Deteniendo servicios para crear un respaldo consistente..." -ForegroundColor Cyan
docker compose stop | Out-Host
$volumes = docker volume ls --filter "label=com.docker.compose.project.working_dir=$PSScriptRoot" --format "{{.Name}}"
if (-not $volumes) {
  $project = (Split-Path $PSScriptRoot -Leaf).ToLower() -replace '[^a-z0-9_-]',''
  $volumes = docker volume ls --filter "label=com.docker.compose.project=$project" --format "{{.Name}}"
}
$files = @()
foreach ($volume in $volumes) {
  Write-Host "Respaldando $volume"
  $archive = "$volume.tar.gz"
  docker run --rm -v "${volume}:/data:ro" -v "${destination}:/backup" alpine:3.22 sh -c "cd /data && tar czf /backup/$archive ."
  if ($LASTEXITCODE -ne 0) { throw "No se pudo respaldar $volume" }
  docker run --rm -v "${destination}:/backup:ro" alpine:3.22 sh -c "tar tzf /backup/$archive >/dev/null"
  if ($LASTEXITCODE -ne 0) { throw "El archivo $archive no superó la prueba de integridad" }
  $path = Join-Path $destination $archive
  $hash = (Get-FileHash $path -Algorithm SHA256).Hash
  $files += @{ volume=$volume; archive=$archive; sizeBytes=(Get-Item $path).Length; sha256=$hash; integrityTest="OK" }
}
$manifest = @{
  createdAt=(Get-Date).ToString("o")
  project=(Split-Path $PSScriptRoot -Leaf)
  encrypted=$false
  offsiteCopy=$false
  verification="ARCHIVES_READABLE"
  files=$files
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $destination "manifest.json") -Encoding UTF8
(Get-FileHash (Join-Path $destination "manifest.json") -Algorithm SHA256).Hash | Set-Content (Join-Path $destination "manifest.sha256") -Encoding ASCII
docker compose start | Out-Host
Write-Host "Respaldo creado y verificado en: $destination" -ForegroundColor Green
Write-Host "Registra este respaldo en: Auditoría y respaldos > Registrar verificación" -ForegroundColor Yellow
