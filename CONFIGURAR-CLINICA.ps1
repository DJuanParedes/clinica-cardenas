[CmdletBinding()]
param(
  [ValidateSet('.env','.env.production')]
  [string]$EnvironmentFile='.env',
  [string]$ClinicId,
  [string]$Name,
  [string]$ShortName,
  [string]$Tagline,
  [string]$Description,
  [string]$Phone,
  [string]$Email,
  [string]$Address,
  [string]$WorkingHours,
  [string]$PrimaryColor,
  [string]$PrimaryDarkColor,
  [string]$AccentColor,
  [string]$AccentDarkColor,
  [string]$SidebarDarkColor,
  [string]$LogoPath,
  [string]$HeroImagePath,
  [switch]$NonInteractive,
  [switch]$Apply
)

[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envPath = Join-Path $root $EnvironmentFile
$brandDir = Join-Path $root 'clinic-brand'

if(-not (Test-Path -LiteralPath $envPath)){
  if($EnvironmentFile -eq '.env.production'){ throw 'Primero ejecuta PREPARAR-PRODUCCION.bat para crear .env.production.' }
  & (Join-Path $root 'PREPARAR-ENTORNO-LOCAL.ps1')
}

$current=@{}
foreach($line in Get-Content -LiteralPath $envPath){
  if($line -match '^\s*#' -or $line -notmatch '='){ continue }
  $parts=$line -split '=',2
  $current[$parts[0].Trim()]=$parts[1].Trim().Trim('"').Trim("'")
}

function Ask([string]$label,[string]$provided,[string]$key,[string]$fallback){
  if(-not [string]::IsNullOrWhiteSpace($provided)){ return $provided.Trim() }
  $default=[string]$current[$key]
  if([string]::IsNullOrWhiteSpace($default)){ $default=$fallback }
  if($NonInteractive){ return $default }
  $value=Read-Host "$label [$default]"
  if([string]::IsNullOrWhiteSpace($value)){ return $default }
  return $value.Trim()
}

function Assert-PlainText([string]$label,[string]$value,[int]$max){
  if([string]::IsNullOrWhiteSpace($value) -or $value.Length -gt $max -or $value -match '[\r\n=#"'']'){
    throw "$label contiene caracteres no permitidos o supera $max caracteres."
  }
}

function Assert-Color([string]$label,[string]$value){
  if($value -notmatch '^#[0-9A-Fa-f]{6}$'){ throw "$label debe usar formato hexadecimal, por ejemplo #245C80." }
}

$config=[ordered]@{}
$config.CLINIC_ID=(Ask 'Código único (letras, números, guion)' $ClinicId 'CLINIC_ID' 'CARDENAS').ToUpperInvariant()
if($config.CLINIC_ID -notmatch '^[A-Z0-9_-]{2,40}$'){ throw 'El código debe tener entre 2 y 40 caracteres.' }
$config.CLINIC_NAME=Ask 'Nombre completo' $Name 'CLINIC_NAME' 'Clínica Cárdenas'
$config.CLINIC_SHORT_NAME=Ask 'Nombre corto' $ShortName 'CLINIC_SHORT_NAME' 'Cárdenas'
$config.CLINIC_TAGLINE=Ask 'Frase institucional' $Tagline 'CLINIC_TAGLINE' 'Atención odontológica en La Floresta'
$config.CLINIC_DESCRIPTION=Ask 'Descripción breve' $Description 'CLINIC_DESCRIPTION' 'Atención odontológica para pacientes de La Floresta y zonas cercanas.'
$config.CLINIC_PHONE=Ask 'Teléfono' $Phone 'CLINIC_PHONE' ''
$config.CLINIC_EMAIL=Ask 'Correo de contacto' $Email 'CLINIC_EMAIL' ''
$config.CLINIC_ADDRESS=Ask 'Dirección o ciudad' $Address 'CLINIC_ADDRESS' 'La Floresta, Lima, Perú'
$config.CLINIC_WORKING_HOURS=Ask 'Horario mostrado' $WorkingHours 'CLINIC_WORKING_HOURS' 'Atención previa coordinación'
$config.CLINIC_PRIMARY_COLOR=Ask 'Color principal' $PrimaryColor 'CLINIC_PRIMARY_COLOR' '#3B78A6'
$config.CLINIC_PRIMARY_DARK_COLOR=Ask 'Color principal oscuro' $PrimaryDarkColor 'CLINIC_PRIMARY_DARK_COLOR' '#245C80'
$config.CLINIC_ACCENT_COLOR=Ask 'Color de acciones' $AccentColor 'CLINIC_ACCENT_COLOR' '#D06B4F'
$config.CLINIC_ACCENT_DARK_COLOR=Ask 'Color de acciones oscuro' $AccentDarkColor 'CLINIC_ACCENT_DARK_COLOR' '#B8533C'
$config.CLINIC_SIDEBAR_DARK_COLOR=Ask 'Color final del menú' $SidebarDarkColor 'CLINIC_SIDEBAR_DARK_COLOR' '#193D52'

Assert-PlainText 'Nombre' $config.CLINIC_NAME 100
Assert-PlainText 'Nombre corto' $config.CLINIC_SHORT_NAME 50
Assert-PlainText 'Frase institucional' $config.CLINIC_TAGLINE 120
Assert-PlainText 'Descripción' $config.CLINIC_DESCRIPTION 240
if(-not [string]::IsNullOrWhiteSpace($config.CLINIC_PHONE)){ Assert-PlainText 'Teléfono' $config.CLINIC_PHONE 40 }
if(-not [string]::IsNullOrWhiteSpace($config.CLINIC_EMAIL)){ Assert-PlainText 'Correo' $config.CLINIC_EMAIL 120 }
Assert-PlainText 'Dirección' $config.CLINIC_ADDRESS 180
Assert-PlainText 'Horario' $config.CLINIC_WORKING_HOURS 120
if(-not [string]::IsNullOrWhiteSpace($config.CLINIC_EMAIL) -and $config.CLINIC_EMAIL -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$'){ throw 'El correo de contacto no es válido.' }
foreach($key in @('CLINIC_PRIMARY_COLOR','CLINIC_PRIMARY_DARK_COLOR','CLINIC_ACCENT_COLOR','CLINIC_ACCENT_DARK_COLOR','CLINIC_SIDEBAR_DARK_COLOR')){ Assert-Color $key $config[$key] }

New-Item -ItemType Directory -Path $brandDir -Force | Out-Null
function Copy-BrandAsset([string]$source,[string]$baseName,[string]$currentUrl){
  if([string]::IsNullOrWhiteSpace($source)){ return $currentUrl }
  $resolved=(Resolve-Path -LiteralPath $source).Path
  $extension=[IO.Path]::GetExtension($resolved).ToLowerInvariant()
  if($extension -notin @('.svg','.png','.jpg','.jpeg','.webp')){ throw "Formato no permitido para $baseName." }
  $target=Join-Path $brandDir ($baseName+$extension)
  Copy-Item -LiteralPath $resolved -Destination $target -Force
  return '/clinic-brand/'+[IO.Path]::GetFileName($target)
}

$logoDefault=[string]$current['CLINIC_LOGO_URL']; if([string]::IsNullOrWhiteSpace($logoDefault)){ $logoDefault='/brand/logo-cardenas.svg' }
$heroDefault=[string]$current['CLINIC_HERO_IMAGE_URL']; if([string]::IsNullOrWhiteSpace($heroDefault)){ $heroDefault='/brand/cardenas-fachada.png' }
if(-not $NonInteractive -and [string]::IsNullOrWhiteSpace($LogoPath)){ $LogoPath=Read-Host 'Ruta del logo (Enter para conservar)' }
if(-not $NonInteractive -and [string]::IsNullOrWhiteSpace($HeroImagePath)){ $HeroImagePath=Read-Host 'Ruta de la fachada (Enter para conservar)' }
$config.CLINIC_LOGO_URL=Copy-BrandAsset $LogoPath 'logo' $logoDefault
$config.CLINIC_HERO_IMAGE_URL=Copy-BrandAsset $HeroImagePath 'fachada' $heroDefault

$managed=@($config.Keys)
$lines=New-Object System.Collections.Generic.List[string]
foreach($line in Get-Content -LiteralPath $envPath){
  $remove=$false
  foreach($key in $managed){ if($line -match ('^'+[regex]::Escape($key)+'=')){ $remove=$true; break } }
  if(-not $remove -and $line -ne '# Identidad independiente de esta clínica'){ $lines.Add($line) }
}
while($lines.Count -gt 0 -and [string]::IsNullOrWhiteSpace($lines[$lines.Count-1])){ $lines.RemoveAt($lines.Count-1) }
$lines.Add('')
$lines.Add('# Identidad independiente de esta clínica')
foreach($key in $config.Keys){ $lines.Add("$key=$($config[$key])") }
[IO.File]::WriteAllLines($envPath,$lines,(New-Object Text.UTF8Encoding($false)))

Write-Host "`nIdentidad guardada para $($config.CLINIC_NAME)." -ForegroundColor Green
Write-Host 'Los secretos y las contraseñas existentes no fueron modificados.' -ForegroundColor DarkGray

$applyNow=$Apply
if(-not $Apply -and -not $NonInteractive){ $answer=Read-Host '¿Aplicar ahora al frontend en Docker? (S/N)'; $applyNow=$answer -match '^[sS]' }
if($applyNow){
  if($EnvironmentFile -eq '.env.production'){
    & docker compose --project-directory $root --env-file $envPath -f (Join-Path $root 'docker-compose.production-secure.yml') up -d --no-build --no-deps --force-recreate frontend
  } else {
    & docker compose --project-directory $root up -d --no-build --no-deps --force-recreate frontend
  }
  if($LASTEXITCODE -ne 0){ throw 'Docker no pudo recrear el frontend.' }
  Write-Host 'Interfaz actualizada en http://localhost:8080' -ForegroundColor Green
} else {
  Write-Host 'Para aplicar después, ejecuta 02-INICIAR.bat o recrea el frontend.' -ForegroundColor Yellow
}
