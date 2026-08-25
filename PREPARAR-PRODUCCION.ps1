param([switch]$Force)
$ErrorActionPreference='Stop'
$Root=Split-Path -Parent $MyInvocation.MyCommand.Path; Set-Location $Root; $target=Join-Path $Root '.env.production'
if((Test-Path $target) -and -not $Force){ throw 'Ya existe .env.production. Usa -Force solo si deseas reemplazarlo conscientemente.' }
function Secure-ToPlain([Security.SecureString]$Value){ $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value); try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) } }
function Random-Hex([int]$Bytes=36){ $buffer=New-Object byte[] $Bytes; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); try{$rng.GetBytes($buffer)}finally{$rng.Dispose()}; return ([BitConverter]::ToString($buffer)).Replace('-','') }
function Need([string]$Prompt){ do { $v=(Read-Host $Prompt).Trim() } while([string]::IsNullOrWhiteSpace($v)); return $v }
function Need-Secret([string]$Prompt){ do { $v=Secure-ToPlain (Read-Host $Prompt -AsSecureString) } while([string]::IsNullOrWhiteSpace($v)); return $v }

Write-Host 'CLINICA CARDENAS V4.9.17 - PREPARAR PRODUCCION MYSQL' -ForegroundColor Cyan
$domain=Need 'Dominio (ej. clinica.midominio.pe)'; if($domain -notmatch '^[A-Za-z0-9.-]+.[A-Za-z]{2,}$'){ throw 'Dominio público inválido.' }
$adminName=Need 'Nombre del Administrador principal'
$adminEmail=Need 'Correo real del Administrador'; if($adminEmail -notmatch '^[^@s]+@[^@s]+.[^@s]+$'){ throw 'Correo inválido.' }
$adminPhone=Need 'Teléfono de recuperación (+519XXXXXXXX)'; $adminPhone=$adminPhone.Replace(' ','').Replace('-',''); if($adminPhone -match '^9d{8}$'){ $adminPhone='+51'+$adminPhone }; if($adminPhone -notmatch '^+[1-9]d{7,14}$'){ throw 'Teléfono inválido.' }
do { $adminChannel=(Need 'Canal de recuperación: EMAIL, SMS o WHATSAPP').ToUpperInvariant() } while($adminChannel -notin @('EMAIL','SMS','WHATSAPP'))

$provider='twilio'
$mailHost=''; $mailPort=''; $mailUsername=''; $mailPassword=''; $mailFrom=''; $mailTtl=''
$verifySid=''; $twilioKey=''; $twilioSecret=''
if($adminChannel -eq 'EMAIL'){
  $provider='email'
  $mailHost='smtp.gmail.com'
  $mailPort='587'
  $mailUsername=$adminEmail
  $mailFrom=$adminEmail
  $mailTtl='10'
  $mailPassword=Need-Secret 'Contraseña de aplicación de Gmail'
} else {
  $verifySid=Need 'Twilio Verify Service SID (VA...)'
  $twilioKey=Need 'Twilio API Key (SK...)'
  $twilioSecret=Need-Secret 'Twilio API Secret'
}

$jwt=Random-Hex 64
$mysqlRoot='Root_'+(Random-Hex 24)
$db=@{}
foreach($name in @('AUTH','PATIENT','APPOINTMENT','CLINICAL','FINANCE','LAB','OPERATIONS','CONTENT','INVENTORY','ENCOUNTER','SURVEY')){ $db[$name]='Db_'+(Random-Hex 24) }

$lines=@(
  '# PLATAFORMA ODONTOLOGICA V4.9.17 - PRODUCCIÓN MYSQL',
  "DOMAIN=$domain",
  "JWT_SECRET=$jwt",
  "BOOTSTRAP_ADMIN_NAME=$adminName",
  "BOOTSTRAP_ADMIN_EMAIL=$adminEmail",
  "BOOTSTRAP_ADMIN_PHONE=$adminPhone",
  "BOOTSTRAP_ADMIN_RECOVERY_CHANNEL=$adminChannel",
  "OTP_PROVIDER=$provider",
  "MAIL_HOST=$mailHost",
  "MAIL_PORT=$mailPort",
  "MAIL_USERNAME=$mailUsername",
  "MAIL_PASSWORD=$mailPassword",
  "MAIL_FROM=$mailFrom",
  "MAIL_OTP_TTL_MINUTES=$mailTtl",
  "TWILIO_VERIFY_SERVICE_SID=$verifySid",
  "TWILIO_API_KEY=$twilioKey",
  "TWILIO_API_SECRET=$twilioSecret",
  "MYSQL_ROOT_PASSWORD=$mysqlRoot",
  "AUTH_DB_PASSWORD=$($db.AUTH)",
  "PATIENT_DB_PASSWORD=$($db.PATIENT)",
  "APPOINTMENT_DB_PASSWORD=$($db.APPOINTMENT)",
  "CLINICAL_DB_PASSWORD=$($db.CLINICAL)",
  "FINANCE_DB_PASSWORD=$($db.FINANCE)",
  "LAB_DB_PASSWORD=$($db.LAB)",
  "OPERATIONS_DB_PASSWORD=$($db.OPERATIONS)",
  "CONTENT_DB_PASSWORD=$($db.CONTENT)",
  "INVENTORY_DB_PASSWORD=$($db.INVENTORY)",
  "ENCOUNTER_DB_PASSWORD=$($db.ENCOUNTER)",
  "SURVEY_DB_PASSWORD=$($db.SURVEY)",
  '',
  '# Identidad independiente de esta clínica',
  'CLINIC_ID=CARDENAS',
  'CLINIC_NAME=Clínica Cárdenas',
  'CLINIC_SHORT_NAME=Cárdenas',
  'CLINIC_TAGLINE=Atención odontológica en La Floresta',
  'CLINIC_DESCRIPTION=Atención odontológica para pacientes de La Floresta y zonas cercanas.',
  'CLINIC_LOGO_URL=/brand/logo-cardenas.svg',
  'CLINIC_HERO_IMAGE_URL=/brand/cardenas-fachada.png',
  'CLINIC_PRIMARY_COLOR=#3B78A6',
  'CLINIC_PRIMARY_DARK_COLOR=#245C80',
  'CLINIC_ACCENT_COLOR=#D06B4F',
  'CLINIC_ACCENT_DARK_COLOR=#B8533C',
  'CLINIC_SIDEBAR_DARK_COLOR=#193D52',
  'CLINIC_PHONE=',
  'CLINIC_EMAIL=',
  'CLINIC_ADDRESS=La Floresta, Lima, Perú',
  'CLINIC_WORKING_HOURS=Atención previa coordinación'
)
[IO.File]::WriteAllLines($target,$lines,(New-Object Text.UTF8Encoding($false)))
$mailPassword=$null
$twilioSecret=$null
Write-Host "OK - $target creado para MySQL con recuperación por $adminChannel." -ForegroundColor Green
