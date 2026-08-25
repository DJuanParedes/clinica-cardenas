param([string]$EnvFile = ".env.production")
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path; Set-Location $Root
function Fail([string]$Message) { throw "PRODUCCION: $Message" }
if (-not (Test-Path $EnvFile)) { Fail "No existe $EnvFile. Ejecuta PREPARAR-PRODUCCION.ps1." }
if (-not (Test-Path "docker-compose.production-secure.yml")) { Fail "Falta docker-compose.production-secure.yml" }
$vars=@{}; Get-Content $EnvFile | ForEach-Object { $line=$_.Trim(); if(-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')){return}; $idx=$line.IndexOf('='); $vars[$line.Substring(0,$idx).Trim()]=$line.Substring($idx+1).Trim() }
$required=@('DOMAIN','JWT_SECRET','BOOTSTRAP_ADMIN_NAME','BOOTSTRAP_ADMIN_EMAIL','BOOTSTRAP_ADMIN_PHONE','BOOTSTRAP_ADMIN_RECOVERY_CHANNEL','OTP_PROVIDER','MYSQL_ROOT_PASSWORD','AUTH_DB_PASSWORD','PATIENT_DB_PASSWORD','APPOINTMENT_DB_PASSWORD','CLINICAL_DB_PASSWORD','FINANCE_DB_PASSWORD','LAB_DB_PASSWORD','OPERATIONS_DB_PASSWORD','CONTENT_DB_PASSWORD','INVENTORY_DB_PASSWORD','ENCOUNTER_DB_PASSWORD','SURVEY_DB_PASSWORD')
foreach($name in $required){ if(-not $vars.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($vars[$name])){ Fail "Falta $name en $EnvFile" } }
foreach($name in $required){ if($vars[$name] -match 'CHANGE_ME|REEMPLAZAR|example\.com|tudominio'){ Fail "$name todavía contiene un valor de ejemplo" } }
if($vars['DOMAIN'] -notmatch '^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'){ Fail 'DOMAIN inválido' }
if($vars['JWT_SECRET'].Length -lt 64){ Fail 'JWT_SECRET debe tener al menos 64 caracteres' }
if($vars['BOOTSTRAP_ADMIN_EMAIL'] -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$'){ Fail 'BOOTSTRAP_ADMIN_EMAIL inválido' }
if($vars['BOOTSTRAP_ADMIN_PHONE'] -notmatch '^\+[1-9]\d{7,14}$'){ Fail 'BOOTSTRAP_ADMIN_PHONE debe usar E.164' }
if($vars['BOOTSTRAP_ADMIN_RECOVERY_CHANNEL'].ToUpperInvariant() -notin @('EMAIL','SMS','WHATSAPP')){ Fail 'Canal de recuperación inválido' }
$provider=$vars['OTP_PROVIDER'].ToLowerInvariant()
if($provider -eq 'email'){
  if($vars['BOOTSTRAP_ADMIN_RECOVERY_CHANNEL'].ToUpperInvariant() -ne 'EMAIL'){ Fail 'OTP_PROVIDER=email requiere canal EMAIL' }
  foreach($name in @('MAIL_HOST','MAIL_PORT','MAIL_USERNAME','MAIL_PASSWORD','MAIL_FROM')){
    if(-not $vars.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($vars[$name])){ Fail "Falta $name para correo" }
    if($vars[$name] -match 'CHANGE_ME|REEMPLAZAR'){ Fail "$name todavía contiene un valor de ejemplo" }
  }
  if($vars['MAIL_USERNAME'] -notmatch '^[^@s]+@[^@s]+.[^@s]+$' -or $vars['MAIL_FROM'] -notmatch '^[^@s]+@[^@s]+.[^@s]+$'){ Fail 'Correo SMTP inválido' }
} elseif($provider -eq 'twilio'){
  if($vars['BOOTSTRAP_ADMIN_RECOVERY_CHANNEL'].ToUpperInvariant() -notin @('SMS','WHATSAPP')){ Fail 'Twilio requiere SMS o WHATSAPP' }
  foreach($name in @('TWILIO_VERIFY_SERVICE_SID','TWILIO_API_KEY','TWILIO_API_SECRET')){
    if(-not $vars.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($vars[$name])){ Fail "Falta $name para Twilio" }
  }
} else { Fail 'OTP_PROVIDER debe ser email o twilio en producción' }
$dbNames=@('MYSQL_ROOT_PASSWORD','AUTH_DB_PASSWORD','PATIENT_DB_PASSWORD','APPOINTMENT_DB_PASSWORD','CLINICAL_DB_PASSWORD','FINANCE_DB_PASSWORD','LAB_DB_PASSWORD','OPERATIONS_DB_PASSWORD','CONTENT_DB_PASSWORD','INVENTORY_DB_PASSWORD','ENCOUNTER_DB_PASSWORD','SURVEY_DB_PASSWORD')
foreach($name in $dbNames){ if($vars[$name] -notmatch '^[A-Za-z0-9_#@.-]{12,100}$'){ Fail "$name no cumple formato seguro" } }
$mysqlProfiles=@(Get-ChildItem -Recurse -Filter application-mysql.yml -File)
if($mysqlProfiles.Count -ne 11){ Fail "Se esperaban 11 perfiles application-mysql.yml y se encontraron $($mysqlProfiles.Count)" }
foreach($p in $mysqlProfiles){ $t=Get-Content $p.FullName -Raw; if($t -notmatch 'com\.mysql\.cj\.jdbc\.Driver' -or $t -notmatch 'MySQLDialect'){ Fail "Perfil MySQL incompleto: $($p.FullName)" } }
$init=Get-Content 'database/mysql/init-databases.sh' -Raw
if($init -notmatch 'CREATE DATABASE IF NOT EXISTS' -or $init -notmatch 'GRANT ALL PRIVILEGES'){ Fail 'Inicialización MySQL incompleta' }
$compose=Get-Content 'docker-compose.production-secure.yml' -Raw
if($compose -notmatch 'image:\s*mysql:8\.4'){ Fail 'Producción no usa mysql:8.4' }
if($compose -match '(?m)^\s*-\s+3306:3306'){ Fail 'MySQL no debe publicar 3306 en producción' }
$gitignore=Get-Content '.gitignore' -Raw; if($gitignore -notmatch '(?m)^\.env\.production$'){ Fail '.env.production debe estar ignorado por Git' }
$dockerignore=Get-Content '.dockerignore' -Raw; if($dockerignore -notmatch '(?m)^\.env\.\*$'){ Fail '.env.* debe estar excluido del contexto Docker' }
$portLines=[regex]::Matches($compose,'(?m)^\s*-\s+(\d+):(\d+)'); foreach($m in $portLines){ if($m.Groups[1].Value -notin @('80','443')){ Fail "Puerto público no permitido: $($m.Value.Trim())" } }
try { docker info *> $null } catch { Fail 'Docker no está disponible' }
& docker compose --env-file $EnvFile -f docker-compose.production-secure.yml config --quiet; if($LASTEXITCODE -ne 0){ Fail 'docker compose config detectó un error' }
Write-Host 'OK - Preflight de producción MySQL aprobado' -ForegroundColor Green
Write-Host "Dominio: https://$($vars['DOMAIN'])"
Write-Host 'Puertos públicos esperados: 80/443 únicamente'
Write-Host 'MySQL: red interna, sin puerto 3306 publicado'
