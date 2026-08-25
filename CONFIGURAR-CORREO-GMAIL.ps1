[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envPath = Join-Path $root '.env'
$adminEmail = $null

if (-not (Test-Path -LiteralPath $envPath)) {
  throw 'No existe .env. Ejecuta primero PREPARAR-ENTORNO-LOCAL.bat.'
}

foreach ($line in Get-Content -LiteralPath $envPath) {
  if ($line -match '^BOOTSTRAP_ADMIN_EMAIL=(.+)$') {
    $adminEmail = $Matches[1].Trim()
    break
  }
}
if ([string]::IsNullOrWhiteSpace($adminEmail)) {
  throw 'Falta BOOTSTRAP_ADMIN_EMAIL en .env.'
}

Write-Host ''
Write-Host 'CONFIGURACIÓN SEGURA DE RECUPERACIÓN POR GMAIL' -ForegroundColor Cyan
Write-Host 'Correo administrador:' $adminEmail -ForegroundColor White
Write-Host ''
Write-Host 'Usa una contraseña de aplicación de Google, no la contraseña normal de Gmail.' -ForegroundColor Yellow
Write-Host 'La contraseña se guardará únicamente en el archivo privado .env de esta laptop.' -ForegroundColor DarkGray
Write-Host ''

$secure = Read-Host 'Pega la contraseña de aplicación de Google' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
  $appPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
$appPassword = $appPassword.Replace(' ', '').Trim()
if ($appPassword.Length -lt 16) {
  throw 'La contraseña de aplicación no parece válida. Debe tener al menos 16 caracteres.'
}

function Set-EnvValue([string]$key, [string]$value) {
  $lines = [System.Collections.Generic.List[string]]::new()
  foreach ($line in Get-Content -LiteralPath $envPath) { $lines.Add([string]$line) }
  $found = $false
  for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match ('^' + [regex]::Escape($key) + '=')) {
      $lines[$i] = $key + '=' + $value
      $found = $true
      break
    }
  }
  if (-not $found) { $lines.Add($key + '=' + $value) }
  [IO.File]::WriteAllLines($envPath, $lines, (New-Object System.Text.UTF8Encoding($false)))
}

Set-EnvValue 'BOOTSTRAP_ADMIN_EMAIL' $adminEmail
Set-EnvValue 'BOOTSTRAP_ADMIN_RECOVERY_CHANNEL' 'EMAIL'
Set-EnvValue 'OTP_PROVIDER' 'email'
Set-EnvValue 'MAIL_HOST' 'smtp.gmail.com'
Set-EnvValue 'MAIL_PORT' '587'
Set-EnvValue 'MAIL_USERNAME' $adminEmail
Set-EnvValue 'MAIL_PASSWORD' $appPassword
Set-EnvValue 'MAIL_FROM' $adminEmail
Set-EnvValue 'MAIL_OTP_TTL_MINUTES' '10'
$appPassword = $null

Write-Host ''
Write-Host 'Reiniciando autenticación con la nueva configuración...' -ForegroundColor Cyan
Push-Location $root
try {
  & docker compose up -d --no-build --no-deps --force-recreate auth-service frontend
  if ($LASTEXITCODE -ne 0) { throw 'Docker no pudo reconstruir los servicios.' }

  $healthy = $false
  for ($attempt = 1; $attempt -le 60; $attempt++) {
    $status = (& docker inspect -f '{{.State.Health.Status}}' clinica-auth-service 2>$null)
    if ($status -eq 'healthy') { $healthy = $true; break }
    Start-Sleep -Seconds 3
  }
  if (-not $healthy) { throw 'El servicio de autenticación no llegó a estado saludable.' }

  $body = @{ email = $adminEmail } | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/auth/password-reset/request' -ContentType 'application/json' -Body $body | Out-Null
  Start-Sleep -Seconds 2
  $recentLogs = (& docker logs --since 1m clinica-auth-service 2>&1) -join [Environment]::NewLine
  if ($recentLogs -match 'Código de recuperación enviado por correo') {
    Write-Host ''
    Write-Host 'LISTO: se envió un código de prueba al correo administrador.' -ForegroundColor Green
    Write-Host 'Revisa Recibidos y también la carpeta Spam.' -ForegroundColor Green
  } else {
    Write-Host ''
    Write-Host 'La configuración se guardó, pero no se confirmó el envío.' -ForegroundColor Yellow
    Write-Host 'Revisa que la contraseña sea una contraseña de aplicación válida de Google.' -ForegroundColor Yellow
  }
} finally {
  Pop-Location
}
Write-Host ''
Read-Host 'Presiona ENTER para cerrar'
