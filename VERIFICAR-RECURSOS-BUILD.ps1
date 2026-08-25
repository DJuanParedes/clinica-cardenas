[CmdletBinding()]
param([string]$Stage='build')
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference='Continue'
Set-Location -LiteralPath $PSScriptRoot
$logDir=Join-Path $PSScriptRoot 'build-logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$log=Join-Path $logDir 'recursos.log'
function Log([string]$m){ Add-Content -LiteralPath $log -Value $m -Encoding UTF8; Write-Host $m }
Log "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Recursos - $Stage"
try {
  $driveName=[IO.Path]::GetPathRoot($PSScriptRoot).TrimEnd('\').TrimEnd(':')
  $drive=Get-PSDrive -Name $driveName -ErrorAction Stop
  $free=[math]::Round($drive.Free/1GB,2)
  Log "Disco libre: $free GB"
  if($free -lt 8){ Write-Host 'ERROR: menos de 8 GB libres. Deteniendo para evitar corrupcion/fallo de BuildKit.' -ForegroundColor Red; exit 2 }
  if($free -lt 12){ Write-Host 'AVISO: espacio bajo; la compilacion continuara secuencialmente.' -ForegroundColor Yellow }
} catch { Log "No se pudo medir disco: $($_.Exception.Message)" }
& docker info *> $null
if($LASTEXITCODE -ne 0){ Write-Host 'ERROR: Docker Engine dejo de responder.' -ForegroundColor Red; exit 3 }
try {
  $memRaw=(& docker info --format '{{.MemTotal}}' 2>$null | Select-Object -First 1)
  [double]$bytes=0
  if([double]::TryParse([string]$memRaw,[ref]$bytes) -and $bytes -gt 0){
    $gb=[math]::Round($bytes/1GB,1); Log "RAM Docker: $gb GB"
    if($gb -lt 4){ Write-Host 'ERROR: Docker tiene menos de 4 GB de RAM.' -ForegroundColor Red; exit 4 }
  }
} catch { Log "No se pudo medir RAM Docker: $($_.Exception.Message)" }
try { (& docker system df 2>&1) | ForEach-Object { Log ([string]$_) } } catch {}
exit 0
