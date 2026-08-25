[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envPath = Join-Path $root ".env"
$version = (Get-Content -LiteralPath (Join-Path $root 'VERSION.txt') -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($version)) { throw 'VERSION.txt esta vacio' }
$values = @{}
if (Test-Path $envPath) {
  foreach ($line in Get-Content $envPath) {
    if ($line -match '^\s*#' -or [string]::IsNullOrWhiteSpace($line) -or $line -notmatch '=') { continue }
    $parts = $line -split '=',2; $values[$parts[0].Trim()] = $parts[1]
  }
}

function Get-ContainerEnvironment([string]$containerName) {
  if(-not (Get-Command docker -ErrorAction SilentlyContinue)){ return @{} }
  $previous=$ErrorActionPreference
  try {
    $ErrorActionPreference='Continue'
    $raw=@(& docker inspect $containerName 2>$null)
    if($LASTEXITCODE -ne 0 -or $raw.Count -eq 0){ return @{} }
    try { $obj=(($raw -join "`n") | ConvertFrom-Json)[0] } catch { return @{} }
    $map=@{}
    foreach($entry in @($obj.Config.Env)){
      $parts=([string]$entry) -split '=',2
      if($parts.Count -eq 2){ $map[$parts[0]]=$parts[1] }
    }
    return $map
  } finally { $ErrorActionPreference=$previous }
}

function New-SecureToken([int]$bytes = 48) {
  $buffer = New-Object byte[] $bytes; $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }; return [Convert]::ToBase64String($buffer)
}
function New-HexSecret([int]$bytes = 18) {
  $buffer = New-Object byte[] $bytes; $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }; return ([BitConverter]::ToString($buffer)).Replace('-','')
}
function New-OtpCode {
  $buffer = New-Object byte[] 4; $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }; $n = [BitConverter]::ToUInt32($buffer,0) % 1000000; return $n.ToString('D6')
}
# Si existe una instalacion local previa en Docker, conserva sus secretos antes de
# recrear contenedores. Esto permite extraer una actualizacion en otra carpeta sin
# dejar el volumen MySQL con contrasenas distintas a las del nuevo .env.
$existingMysql=Get-ContainerEnvironment 'clinica-mysql'
$existingAuth=Get-ContainerEnvironment 'clinica-auth-service'
$existingFrontend=Get-ContainerEnvironment 'clinica-frontend'
$mysqlKeys = @('MYSQL_ROOT_PASSWORD','AUTH_DB_PASSWORD','PATIENT_DB_PASSWORD','APPOINTMENT_DB_PASSWORD','CLINICAL_DB_PASSWORD','FINANCE_DB_PASSWORD','LAB_DB_PASSWORD','OPERATIONS_DB_PASSWORD','CONTENT_DB_PASSWORD','INVENTORY_DB_PASSWORD','ENCOUNTER_DB_PASSWORD','SURVEY_DB_PASSWORD')
$reusedMysql=0
foreach($key in $mysqlKeys){
  $existing=[string]$existingMysql[$key]
  if($existing -match '^[A-F0-9]{36}$'){
    $values[$key]=$existing
    $reusedMysql++
  }
}
$existingJwt=[string]$existingAuth['JWT_SECRET']
if(-not [string]::IsNullOrWhiteSpace($existingJwt) -and $existingJwt.Length -ge 64){ $values['JWT_SECRET']=$existingJwt }
$existingOtp=[string]$existingAuth['LOCAL_OTP_CODE']
if($existingOtp -match '^\d{6}$'){ $values['LOCAL_OTP_CODE']=$existingOtp }
$adminKeys=@('BOOTSTRAP_ADMIN_NAME','BOOTSTRAP_ADMIN_EMAIL','BOOTSTRAP_ADMIN_PHONE','BOOTSTRAP_ADMIN_RECOVERY_CHANNEL')
foreach($key in $adminKeys){
  $runningValue=[string]$existingAuth[$key]
  if(-not [string]::IsNullOrWhiteSpace($runningValue)){ $values[$key]=$runningValue }
}
$mailKeys=@('OTP_PROVIDER','MAIL_HOST','MAIL_PORT','MAIL_USERNAME','MAIL_PASSWORD','MAIL_FROM','MAIL_OTP_TTL_MINUTES')
foreach($key in $mailKeys){
  $runningValue=[string]$existingAuth[$key]
  if(-not [string]::IsNullOrWhiteSpace($runningValue)){ $values[$key]=$runningValue }
}

# La identidad visual pertenece a la instalación de cada clínica. Al actualizar
# desde otra carpeta se conserva la configuración del frontend ya instalado.
$clinicDefaults = [ordered]@{
  CLINIC_ID='CARDENAS'; CLINIC_NAME='Clínica Cárdenas'; CLINIC_SHORT_NAME='Cárdenas';
  CLINIC_TAGLINE='Atención odontológica en La Floresta';
  CLINIC_DESCRIPTION='Atención odontológica para pacientes de La Floresta y zonas cercanas.';
  CLINIC_LOGO_URL='/brand/logo-cardenas.svg'; CLINIC_HERO_IMAGE_URL='/brand/cardenas-fachada.png';
  CLINIC_PRIMARY_COLOR='#3B78A6'; CLINIC_PRIMARY_DARK_COLOR='#245C80';
  CLINIC_ACCENT_COLOR='#D06B4F'; CLINIC_ACCENT_DARK_COLOR='#B8533C';
  CLINIC_SIDEBAR_DARK_COLOR='#193D52'; CLINIC_PHONE='';
  CLINIC_EMAIL=''; CLINIC_ADDRESS='La Floresta, Lima, Perú';
  CLINIC_WORKING_HOURS='Atención previa coordinación'
}
foreach($key in $clinicDefaults.Keys){
  $runningValue=[string]$existingFrontend[$key]
  if(-not [string]::IsNullOrWhiteSpace($runningValue)){ $values[$key]=$runningValue }
  if([string]::IsNullOrWhiteSpace([string]$values[$key])){ $values[$key]=$clinicDefaults[$key] }
}

# La identidad del superadministrador solo vive en .env. En una instalación
# nueva se solicita una vez; nunca se incorpora al código ni al paquete ZIP.
function Read-PrivateAdminValue([string]$key,[string]$label,[string]$pattern) {
  $current=[string]$values[$key]
  while($current -notmatch $pattern){
    Write-Host "Configura $label para el superadministrador global." -ForegroundColor Cyan
    $current=(Read-Host $label).Trim()
  }
  $values[$key]=$current
}
Read-PrivateAdminValue 'BOOTSTRAP_ADMIN_NAME' 'nombre completo' '^.{3,120}$'
Read-PrivateAdminValue 'BOOTSTRAP_ADMIN_EMAIL' 'correo de acceso' '^[^@\s]+@[^@\s]+\.[^@\s]+$'
Read-PrivateAdminValue 'BOOTSTRAP_ADMIN_PHONE' 'teléfono de recuperación (+51...)' '^(\+?[1-9]\d{7,14}|9\d{8})$'
$adminPhone=([string]$values['BOOTSTRAP_ADMIN_PHONE']).Replace(' ','').Replace('-','')
if($adminPhone -match '^9\d{8}$'){ $adminPhone='+51'+$adminPhone }
$values['BOOTSTRAP_ADMIN_PHONE']=$adminPhone
$adminChannel=([string]$values['BOOTSTRAP_ADMIN_RECOVERY_CHANNEL']).Trim().ToUpperInvariant()
while($adminChannel -notin @('EMAIL','SMS','WHATSAPP')){
  $adminChannel=(Read-Host 'canal de recuperación (EMAIL, SMS o WHATSAPP)').Trim().ToUpperInvariant()
}
$values['BOOTSTRAP_ADMIN_RECOVERY_CHANNEL']=$adminChannel
if([string]::IsNullOrWhiteSpace([string]$values['OTP_PROVIDER'])){ $values['OTP_PROVIDER']='local' }
if([string]::IsNullOrWhiteSpace([string]$values['MAIL_HOST'])){ $values['MAIL_HOST']='smtp.gmail.com' }
if([string]::IsNullOrWhiteSpace([string]$values['MAIL_PORT'])){ $values['MAIL_PORT']='587' }
if([string]::IsNullOrWhiteSpace([string]$values['MAIL_USERNAME'])){ $values['MAIL_USERNAME']=$values['BOOTSTRAP_ADMIN_EMAIL'] }
if($null -eq $values['MAIL_PASSWORD']){ $values['MAIL_PASSWORD']='' }
if([string]::IsNullOrWhiteSpace([string]$values['MAIL_FROM'])){ $values['MAIL_FROM']=$values['BOOTSTRAP_ADMIN_EMAIL'] }
if([string]::IsNullOrWhiteSpace([string]$values['MAIL_OTP_TTL_MINUTES'])){ $values['MAIL_OTP_TTL_MINUTES']='10' }
$jwt = [string]$values['JWT_SECRET']
if ([string]::IsNullOrWhiteSpace($jwt) -or $jwt.Length -lt 64 -or $jwt -match 'REEMPLAZAR|INSEGURO|DEFAULT') { $values['JWT_SECRET'] = New-SecureToken 64 }
$otp = [string]$values['LOCAL_OTP_CODE']; if ($otp -notmatch '^\d{6}$') { $values['LOCAL_OTP_CODE'] = New-OtpCode }
foreach ($key in $mysqlKeys) {
  $current = [string]$values[$key]
  if ([string]::IsNullOrWhiteSpace($current) -or $current.Length -lt 12 -or $current -match 'REEMPLAZAR|CHANGE_ME|DEFAULT') { $values[$key] = New-HexSecret 18 }
}
$lines = @(
  '# CLINICA CARDENAS - entorno LOCAL generado automaticamente',
  '# MySQL local: localhost:3307. No publicar ni subir este archivo a Git.',
  "APP_VERSION=$version",
  "JWT_SECRET=$($values['JWT_SECRET'])",
  "LOCAL_OTP_CODE=$($values['LOCAL_OTP_CODE'])",
  "OTP_PROVIDER=$($values['OTP_PROVIDER'])",
  "MAIL_HOST=$($values['MAIL_HOST'])",
  "MAIL_PORT=$($values['MAIL_PORT'])",
  "MAIL_USERNAME=$($values['MAIL_USERNAME'])",
  "MAIL_PASSWORD=$($values['MAIL_PASSWORD'])",
  "MAIL_FROM=$($values['MAIL_FROM'])",
  "MAIL_OTP_TTL_MINUTES=$($values['MAIL_OTP_TTL_MINUTES'])",
  "BOOTSTRAP_ADMIN_NAME=$($values['BOOTSTRAP_ADMIN_NAME'])",
  "BOOTSTRAP_ADMIN_EMAIL=$($values['BOOTSTRAP_ADMIN_EMAIL'])",
  "BOOTSTRAP_ADMIN_PHONE=$($values['BOOTSTRAP_ADMIN_PHONE'])",
  "BOOTSTRAP_ADMIN_RECOVERY_CHANNEL=$($values['BOOTSTRAP_ADMIN_RECOVERY_CHANNEL'])",
  "MYSQL_ROOT_PASSWORD=$($values['MYSQL_ROOT_PASSWORD'])",
  "AUTH_DB_PASSWORD=$($values['AUTH_DB_PASSWORD'])",
  "PATIENT_DB_PASSWORD=$($values['PATIENT_DB_PASSWORD'])",
  "APPOINTMENT_DB_PASSWORD=$($values['APPOINTMENT_DB_PASSWORD'])",
  "CLINICAL_DB_PASSWORD=$($values['CLINICAL_DB_PASSWORD'])",
  "FINANCE_DB_PASSWORD=$($values['FINANCE_DB_PASSWORD'])",
  "LAB_DB_PASSWORD=$($values['LAB_DB_PASSWORD'])",
  "OPERATIONS_DB_PASSWORD=$($values['OPERATIONS_DB_PASSWORD'])",
  "CONTENT_DB_PASSWORD=$($values['CONTENT_DB_PASSWORD'])",
  "INVENTORY_DB_PASSWORD=$($values['INVENTORY_DB_PASSWORD'])",
  "ENCOUNTER_DB_PASSWORD=$($values['ENCOUNTER_DB_PASSWORD'])",
  "SURVEY_DB_PASSWORD=$($values['SURVEY_DB_PASSWORD'])",
  '',
  '# Identidad independiente de esta clínica',
  "CLINIC_ID=$($values['CLINIC_ID'])",
  "CLINIC_NAME=$($values['CLINIC_NAME'])",
  "CLINIC_SHORT_NAME=$($values['CLINIC_SHORT_NAME'])",
  "CLINIC_TAGLINE=$($values['CLINIC_TAGLINE'])",
  "CLINIC_DESCRIPTION=$($values['CLINIC_DESCRIPTION'])",
  "CLINIC_LOGO_URL=$($values['CLINIC_LOGO_URL'])",
  "CLINIC_HERO_IMAGE_URL=$($values['CLINIC_HERO_IMAGE_URL'])",
  "CLINIC_PRIMARY_COLOR=$($values['CLINIC_PRIMARY_COLOR'])",
  "CLINIC_PRIMARY_DARK_COLOR=$($values['CLINIC_PRIMARY_DARK_COLOR'])",
  "CLINIC_ACCENT_COLOR=$($values['CLINIC_ACCENT_COLOR'])",
  "CLINIC_ACCENT_DARK_COLOR=$($values['CLINIC_ACCENT_DARK_COLOR'])",
  "CLINIC_SIDEBAR_DARK_COLOR=$($values['CLINIC_SIDEBAR_DARK_COLOR'])",
  "CLINIC_PHONE=$($values['CLINIC_PHONE'])",
  "CLINIC_EMAIL=$($values['CLINIC_EMAIL'])",
  "CLINIC_ADDRESS=$($values['CLINIC_ADDRESS'])",
  "CLINIC_WORKING_HOURS=$($values['CLINIC_WORKING_HOURS'])"
)
$tmpEnv = $envPath + '.tmp'
[System.IO.File]::WriteAllLines($tmpEnv, $lines, (New-Object System.Text.UTF8Encoding($false)))
$verifyText=[System.IO.File]::ReadAllText($tmpEnv,[System.Text.Encoding]::UTF8)
if($verifyText -notmatch '(?m)^JWT_SECRET=[^\r\n]{64,}\r?$'){ throw 'No se pudo generar JWT_SECRET correctamente.' }
if($verifyText -notmatch '(?m)^LOCAL_OTP_CODE=\d{6}\r?$'){ throw 'No se pudo generar LOCAL_OTP_CODE correctamente.' }
if($verifyText -notmatch '(?m)^BOOTSTRAP_ADMIN_EMAIL=[^@\s]+@[^@\s]+\.[^@\s]+\r?$'){ throw 'No se pudo configurar el correo privado del superadministrador.' }
if($verifyText -notmatch '(?m)^BOOTSTRAP_ADMIN_PHONE=\+[1-9]\d{7,14}\r?$'){ throw 'No se pudo configurar el teléfono privado del superadministrador.' }
if($verifyText -notmatch '(?m)^BOOTSTRAP_ADMIN_RECOVERY_CHANNEL=(EMAIL|SMS|WHATSAPP)\r?$'){ throw 'No se pudo configurar el canal privado del superadministrador.' }
foreach($key in $mysqlKeys){
  $pattern='(?m)^' + [regex]::Escape($key) + '=[A-F0-9]{36}\r?$'
  if($verifyText -notmatch $pattern){ throw "No se pudo generar $key correctamente." }
}
Move-Item -LiteralPath $tmpEnv -Destination $envPath -Force
Write-Host "Entorno local seguro preparado y verificado (.env)." -ForegroundColor Green
if($reusedMysql -gt 0){ Write-Host "Continuidad MySQL: se reutilizaron $reusedMysql/12 secretos del contenedor existente." -ForegroundColor DarkGray }
Write-Host "Base de datos: MySQL en localhost:3307 (contenedor clinica-mysql)." -ForegroundColor DarkGray
if(([string]$values['OTP_PROVIDER']).ToLowerInvariant() -eq 'local'){
  Write-Host "Para ver el OTP local: MOSTRAR-OTP-LOCAL.bat" -ForegroundColor DarkGray
} else {
  Write-Host "Recuperación: código enviado por $($values['BOOTSTRAP_ADMIN_RECOVERY_CHANNEL'])." -ForegroundColor DarkGray
}
