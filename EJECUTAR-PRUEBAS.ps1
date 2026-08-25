[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot
$log = Join-Path $PSScriptRoot "PRUEBA-FUNCIONAL.txt"
if (Test-Path -LiteralPath $log) { Remove-Item -LiteralPath $log -Force }
try {
    & (Join-Path $PSScriptRoot "PROBAR-SISTEMA.ps1") *>&1 | Tee-Object -FilePath $log
    exit 0
}
catch {
    $_ | Out-String | Tee-Object -FilePath $log -Append | Write-Host
    exit 1
}
