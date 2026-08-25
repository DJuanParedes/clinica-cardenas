[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envPath = Join-Path $root '.env'
if (!(Test-Path $envPath)) {
  throw 'No existe .env. Ejecuta primero PREPARAR-ENTORNO-LOCAL.bat o 01-INSTALAR-O-ACTUALIZAR.bat.'
}
$code = $null
foreach ($line in Get-Content $envPath) {
  if ($line -match '^LOCAL_OTP_CODE=(\d{6})$') { $code = $Matches[1]; break }
}
if (!$code) { throw 'LOCAL_OTP_CODE no existe o no tiene 6 dígitos. Ejecuta PREPARAR-ENTORNO-LOCAL.bat.' }
Write-Host ''
Write-Host 'SOLO DESARROLLO LOCAL' -ForegroundColor Yellow
Write-Host 'Código OTP local actual:' -NoNewline
Write-Host " $code" -ForegroundColor Cyan
Write-Host 'Úsalo en Olvidé mi contraseña para establecer tu clave ADMIN local.' -ForegroundColor DarkGray
Write-Host 'Este código no se utiliza en producción ni debe compartirse.' -ForegroundColor DarkGray
