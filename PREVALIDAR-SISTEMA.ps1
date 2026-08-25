[CmdletBinding()]
param()
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$ErrorActionPreference = 'Continue'
Set-Location -LiteralPath $PSScriptRoot
$log = Join-Path $PSScriptRoot 'PREVALIDACION.txt'
$script:Failures = 0
$script:Warnings = 0
$currentVersion = ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'VERSION.txt'),[Text.Encoding]::UTF8)).Trim()
function Out-Line([string]$Text = '') {
    Add-Content -LiteralPath $log -Value $Text -Encoding UTF8
    Write-Host $Text
}
function Pass([string]$Text) { Write-Host "OK    $Text" -ForegroundColor Green; Add-Content -LiteralPath $log -Value "OK    $Text" -Encoding UTF8 }
function Warn([string]$Text) { $script:Warnings++; Write-Host "AVISO $Text" -ForegroundColor Yellow; Add-Content -LiteralPath $log -Value "AVISO $Text" -Encoding UTF8 }
function Fail([string]$Text) { $script:Failures++; Write-Host "ERROR $Text" -ForegroundColor Red; Add-Content -LiteralPath $log -Value "ERROR $Text" -Encoding UTF8 }
Set-Content -LiteralPath $log -Value @(
'======================================================'
"CLINICA CARDENAS - PREVALIDACION PREVENTIVA V$currentVersion"
'======================================================'
"Fecha: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
"Carpeta: $PSScriptRoot"
) -Encoding UTF8
Write-Host "`nPrevalidando entorno y estructura antes de compilar/iniciar..." -ForegroundColor Cyan
$required = @(
 'VERSION.txt','pom.xml','docker-compose.yml','docker/Dockerfile.service','frontend/Dockerfile','frontend/package.json',
 'frontend/scripts/check-inline-templates.mjs','frontend/src/app/pages/reception-desk.component.ts','frontend/src/app/core/clinic-config.service.ts',
 'frontend/public/clinic-config.js','frontend/clinic-config.template.js','frontend/40-clinic-config.sh','clinic-brand/LEEME.txt',
 'CONFIGURAR-CLINICA.bat','CONFIGURAR-CLINICA-PRODUCCION.bat','CONFIGURAR-CLINICA.ps1','docs/MULTICLINICA.md','VERIFICAR-REQUISITOS-RECEPCION.ps1','COMPILAR-ACTUAL.bat',
 'docker/Dockerfile.artifacts','docker/Dockerfile.runtime.local','docker/Dockerfile.gateway.repair','database/mysql/init-databases.sh','VERIFICAR-GATEWAY-RUNTIME.ps1','REPARAR-GATEWAY.bat','ASEGURAR-CONTENEDORES.ps1','VERIFICAR-MYSQL-INICIALIZADO.ps1','INICIAR-OPTIMIZADO.ps1','EJECUTAR-PRUEBAS.ps1','VERIFICAR-RUTEO-LOCAL.ps1','VERIFICAR-BACKUP-LOCAL.ps1','06-VALIDAR-SISTEMA.bat','07-REPARAR-RUTEO-Y-VALIDAR.bat','08-VALIDAR-FINAL.bat','05-GENERAR-DIAGNOSTICO.ps1','VERIFICAR-RECURSOS-BUILD.ps1'
)
foreach ($item in $required) {
    $path = Join-Path $PSScriptRoot $item
    if (Test-Path -LiteralPath $path) { Pass "Archivo requerido presente: $item" } else { Fail "Falta archivo requerido: $item" }
}
# CMD de Windows interpreta un BOM UTF-8 al inicio como caracteres del comando (@echo off).
try {
    $badBom = @()
    foreach($bat in @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.bat' -File -ErrorAction Stop)) {
        $bytes=[IO.File]::ReadAllBytes($bat.FullName)
        if($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF){ $badBom += $bat.Name }
    }
    if($badBom.Count -eq 0){ Pass 'Compatibilidad CMD: archivos BAT sin BOM UTF-8' }
    else { Fail "Archivos BAT con BOM incompatible: $($badBom -join ', ')" }
} catch { Fail "No se pudo validar codificacion de BAT: $($_.Exception.Message)" }
# Windows PowerShell 5.1 interpreta scripts UTF-8 sin BOM usando la pagina ANSI del sistema.
# Por eso los .ps1 con textos acentuados deben llevar BOM UTF-8.
try {
    $badPsBom = @()
    foreach($ps in @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File -ErrorAction Stop)) {
        $bytes = [IO.File]::ReadAllBytes($ps.FullName)
        if($bytes.Length -lt 3 -or $bytes[0] -ne 0xEF -or $bytes[1] -ne 0xBB -or $bytes[2] -ne 0xBF){ $badPsBom += $ps.Name }
    }
    if($badPsBom.Count -eq 0){ Pass 'Compatibilidad PowerShell 5.1: archivos PS1 con BOM UTF-8' }
    else { Fail "Archivos PS1 sin BOM UTF-8: $($badPsBom -join ', ')" }
} catch { Fail "No se pudo validar BOM de archivos PS1: $($_.Exception.Message)" }

# Parser oficial de PowerShell: detecta errores de sintaxis en TODOS los PS1 antes de compilar.
try {
    $parseProblems = New-Object System.Collections.Generic.List[string]
    foreach($ps in @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File -ErrorAction Stop)) {
        $tokens=$null; $errors=$null
        [void][System.Management.Automation.Language.Parser]::ParseFile($ps.FullName,[ref]$tokens,[ref]$errors)
        foreach($err in @($errors)) { $parseProblems.Add("$($ps.Name):$($err.Extent.StartLineNumber) $($err.Message)") | Out-Null }
    }
    if($parseProblems.Count -eq 0){ Pass 'PowerShell: todos los scripts pasan el parser oficial sin errores de sintaxis' }
    else { Fail "Errores de sintaxis PowerShell: $($parseProblems -join ' | ')" }
} catch { Fail "No se pudo ejecutar el parser PowerShell: $($_.Exception.Message)" }

# Flujo BAT: todos los GOTO deben apuntar a una etiqueta que exista en el mismo archivo.
try {
    $gotoProblems = New-Object System.Collections.Generic.List[string]
    foreach($bat in @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.bat' -File -ErrorAction Stop)) {
        $batText=[IO.File]::ReadAllText($bat.FullName,[Text.Encoding]::UTF8)
        $labels=@{}
        foreach($m in [regex]::Matches($batText,'(?im)^\s*:(?<label>[A-Za-z0-9_.-]+)\s*$')){ $labels[$m.Groups['label'].Value.ToLowerInvariant()]=$true }
        foreach($m in [regex]::Matches($batText,'(?im)\bgoto\s+:?(?<label>[A-Za-z0-9_.-]+)')){
            $label=$m.Groups['label'].Value.ToLowerInvariant()
            if($label -ne 'eof' -and -not $labels.ContainsKey($label)){ $gotoProblems.Add("$($bat.Name) -> $label") | Out-Null }
        }
    }
    if($gotoProblems.Count -eq 0){ Pass 'CMD: todos los GOTO apuntan a etiquetas existentes' }
    else { Fail "GOTO sin etiqueta destino: $($gotoProblems -join ', ')" }
} catch { Fail "No se pudo auditar flujo GOTO de BAT: $($_.Exception.Message)" }

# Propiedad de logs: el verificador escribe verificacion-imagenes.log; los BAT capturan consola en otro archivo.
try {
    $logOwnershipProblems=@()
    foreach($name in @('01-INSTALAR-O-ACTUALIZAR.bat','02-INICIAR.bat','COMPILAR-ACTUAL.bat')){
        $bt=[IO.File]::ReadAllText((Join-Path $PSScriptRoot $name),[Text.Encoding]::UTF8)
        if($bt -match 'VERIFICAR-IMAGENES-LOCALES\.ps1[^\r\n]*verificacion-imagenes\.log'){ $logOwnershipProblems += $name }
    }
    if($logOwnershipProblems.Count -eq 0){ Pass 'Logs: verificacion de imagenes no abre el mismo archivo desde dos procesos' }
    else { Fail "Logs: redireccion conflictiva detectada en $($logOwnershipProblems -join ', ')" }
} catch { Fail "No se pudo auditar propiedad de logs: $($_.Exception.Message)" }

# Docker build de reparación del Gateway: usar contexto relativo `.` tras cd /d al proyecto.
# Un `%~dp0` como argumento de contexto termina en backslash y, entre comillas, puede llegar a Docker como `\\\"` en Windows.
try {
    $gatewayRepairBat = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'REPARAR-GATEWAY.bat'),[Text.Encoding]::UTF8)
    $badRootContext = [regex]::IsMatch($gatewayRepairBat,'(?im)^\s*docker\s+build[^\r\n]*"%~dp0"\s*(?:>|>>|$)')
    $hasRelativeContext = [regex]::IsMatch($gatewayRepairBat,'(?im)^\s*docker\s+build[^\r\n]*-f\s+"docker/Dockerfile\.gateway\.repair"[^\r\n]*\s\.\s*(?:>|>>|$)')
    $hasProjectCd = $gatewayRepairBat -match '(?im)^\s*cd\s+/d\s+"%~dp0"\s*$'
    if(-not $badRootContext -and $hasRelativeContext -and $hasProjectCd){ Pass 'Gateway repair: contexto Docker relativo seguro para rutas Windows' }
    else { Fail 'Gateway repair: usa un contexto Docker inseguro. Debe hacer cd /d "%~dp0" y construir con contexto final `.`.' }
} catch { Fail "No se pudo auditar contexto Docker del Gateway repair: $($_.Exception.Message)" }

# PowerShell puede interpretar una variable normal seguida inmediatamente por dos puntos como una referencia con scope/unidad.
# Este control exige delimitacion explicita con llaves cuando corresponda y evita InvalidVariableReferenceWithDrive.
try {
    $allowedScopes = @('env','script','global','local','private','using')
    $ambiguousInterpolations = New-Object System.Collections.Generic.List[string]
    foreach($ps in @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File -ErrorAction Stop)) {
        $psText = [IO.File]::ReadAllText($ps.FullName,[Text.Encoding]::UTF8)
        foreach($match in [regex]::Matches($psText,'(?<!\{)\$(?<name>[A-Za-z_][A-Za-z0-9_]*):')) {
            $name = $match.Groups['name'].Value
            if($allowedScopes -notcontains $name){
                $line = 1 + (($psText.Substring(0,$match.Index) -split "`n").Count - 1)
                $ambiguousInterpolations.Add(("$($ps.Name):$line -> " + '$' + $name + ':')) | Out-Null
            }
        }
    }
    if($ambiguousInterpolations.Count -eq 0){ Pass "PowerShell seguro: no hay variables ambiguas seguidas inmediatamente por dos puntos" }
    else { Fail "Interpolaciones PowerShell ambiguas detectadas: $($ambiguousInterpolations -join ' | ')" }
} catch { Fail "No se pudo auditar interpolaciones PowerShell: $($_.Exception.Message)" }

$versionPath = Join-Path $PSScriptRoot 'VERSION.txt' 
if (Test-Path -LiteralPath $versionPath) {
    $version = (Get-Content -LiteralPath $versionPath -Raw).Trim()
    if (-not [string]::IsNullOrWhiteSpace($version)) { Pass "VERSION.txt = $version" } else { Fail 'VERSION.txt esta vacio' }
}
if ($PSVersionTable.PSVersion -ge [version]'5.1') { Pass "PowerShell compatible: $($PSVersionTable.PSVersion)" } else { Fail "PowerShell 5.1 o superior requerido; detectado $($PSVersionTable.PSVersion)" }
try {
    $rootPath = [IO.Path]::GetFullPath($PSScriptRoot)
    if ($rootPath.Length -gt 150) { Warn "Ruta larga ($($rootPath.Length) caracteres). Extrae el ZIP cerca de C:\ para reducir problemas de Windows/Docker." }
    else { Pass "Longitud de ruta segura ($($rootPath.Length) caracteres)" }
    if ($rootPath -match '[!&()]') { Warn 'La ruta contiene !, & o parentesis. Se recomienda extraer en C:\ClinicaCardenas para evitar interpretacion especial en BAT/Docker.' }
} catch { Warn "No se pudo medir la longitud de la ruta: $($_.Exception.Message)" }
try {
    $driveName = [IO.Path]::GetPathRoot($PSScriptRoot).TrimEnd('\').TrimEnd(':')
    $drive = Get-PSDrive -Name $driveName -ErrorAction Stop
    $freeGB = [math]::Round($drive.Free/1GB,2)
    if ($freeGB -lt 8) { Fail "Espacio libre insuficiente: $freeGB GB. Para una compilacion Docker segura libera espacio hasta superar 8 GB." }
    elseif ($freeGB -lt 20) { Warn "Espacio libre moderado: $freeGB GB. El build ahora es secuencial, pero Docker conserva muchas capas; se recomiendan 20 GB cuando sea posible." }
    else { Pass "Espacio libre: $freeGB GB" }
} catch { Warn "No se pudo medir el disco: $($_.Exception.Message)" }
try {
    $os = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
    $ramGB = [math]::Round(($os.TotalVisibleMemorySize*1KB)/1GB,1)
    if ($ramGB -lt 6) { Warn "RAM detectada: $ramGB GB. Docker + Angular + 15 servicios Java pueden requerir mas memoria." }
    else { Pass "RAM detectada: $ramGB GB" }
} catch { Warn "No se pudo consultar RAM: $($_.Exception.Message)" }
$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($null -eq $docker) {
    Fail 'Docker no esta instalado o no esta en PATH.'
} else {
    Pass "Docker encontrado: $($docker.Source)"
    & docker info *> $null
    if ($LASTEXITCODE -eq 0) {
        Pass 'Docker Desktop/Engine responde correctamente'
        try {
            $dockerMemRaw = (& docker info --format '{{.MemTotal}}' 2>$null | Select-Object -First 1)
            [double]$dockerBytes = 0
            if ([double]::TryParse([string]$dockerMemRaw,[ref]$dockerBytes) -and $dockerBytes -gt 0) {
                $dockerGB = [math]::Round($dockerBytes/1GB,1)
                if ($dockerGB -lt 4) { Warn "Docker tiene solo $dockerGB GB de memoria disponible. Para los servicios de la plataforma y MySQL se recomienda aumentar recursos." }
                else { Pass "Memoria disponible para Docker: $dockerGB GB" }
            }
        } catch { Warn "No se pudo consultar memoria de Docker: $($_.Exception.Message)" }
        try {
            $dfLines=@(& docker system df 2>$null)
            $imageLine=($dfLines | Where-Object { $_ -match '^Images\s+' } | Select-Object -First 1)
            $cacheLine=($dfLines | Where-Object { $_ -match '^Build Cache\s+' } | Select-Object -First 1)
            if($imageLine -match '^Images\s+(\d+)\s+\d+\s+([0-9.]+)GB'){
                $imgCount=[int]$matches[1]; $imgGB=[double]$matches[2]
                if($imgGB -ge 80 -or $imgCount -ge 300){ Warn "Docker acumula $imgCount imagenes (~$imgGB GB). No bloquea el build secuencial, pero conviene limpiar imagenes antiguas cuando confirmes que ya no las necesitas." }
            }
            if($cacheLine -match '^Build Cache\s+(\d+)\s+\d+\s+([0-9.]+)GB'){
                $cacheCount=[int]$matches[1]; $cacheGB=[double]$matches[2]
                if($cacheGB -ge 30){ Warn "BuildKit acumula ~$cacheGB GB de cache ($cacheCount entradas). Si el disco baja de 8 GB, limpia cache no usada desde Docker Desktop antes de recompilar." }
            }
        } catch { Warn "No se pudo evaluar almacenamiento interno de Docker: $($_.Exception.Message)" }
    } else { Fail 'Docker Desktop esta cerrado o el Engine no responde' }
    & docker compose version *> $null
    if ($LASTEXITCODE -eq 0) { Pass 'Docker Compose esta disponible' } else { Fail 'docker compose no esta disponible' }
    & docker buildx version *> $null
    if ($LASTEXITCODE -eq 0) { Pass 'Docker Buildx esta disponible' } else { Fail 'docker buildx no esta disponible' }
}
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $envFile) {
    $envText = [IO.File]::ReadAllText($envFile,[Text.Encoding]::UTF8)
    if ($envText -match '(?m)^JWT_SECRET=[^\r\n]{32,}\r?$') { Pass '.env contiene JWT_SECRET con longitud minima' } else { Fail '.env no contiene JWT_SECRET valido; ejecuta PREPARAR-ENTORNO-LOCAL.bat' }
    if ($envText -match '(?m)^LOCAL_OTP_CODE=\d{6}\r?$') { Pass '.env contiene OTP local de 6 digitos' } else { Fail '.env no contiene LOCAL_OTP_CODE valido' }
    if ($envText -match '(?m)^BOOTSTRAP_ADMIN_EMAIL=[^@\s]+@[^@\s]+\.[^@\s]+\r?$') { Pass '.env contiene usuario privado del superadministrador global' } else { Fail '.env no contiene BOOTSTRAP_ADMIN_EMAIL valido' }
    if ($envText -match '(?m)^BOOTSTRAP_ADMIN_PHONE=\+[1-9]\d{7,14}\r?$') { Pass '.env contiene telefono privado de recuperacion global' } else { Fail '.env no contiene BOOTSTRAP_ADMIN_PHONE valido' }
    if ($envText -match '(?m)^BOOTSTRAP_ADMIN_RECOVERY_CHANNEL=(SMS|WHATSAPP)\r?$') { Pass '.env contiene canal de recuperacion global' } else { Fail '.env no contiene BOOTSTRAP_ADMIN_RECOVERY_CHANNEL valido' }
    $mysqlSecretNames = @('MYSQL_ROOT_PASSWORD','AUTH_DB_PASSWORD','PATIENT_DB_PASSWORD','APPOINTMENT_DB_PASSWORD','CLINICAL_DB_PASSWORD','FINANCE_DB_PASSWORD','LAB_DB_PASSWORD','OPERATIONS_DB_PASSWORD','CONTENT_DB_PASSWORD','INVENTORY_DB_PASSWORD','ENCOUNTER_DB_PASSWORD','SURVEY_DB_PASSWORD')
    foreach($secretName in $mysqlSecretNames){
        if($envText -match "(?m)^$([regex]::Escape($secretName))=[A-Za-z0-9_#@.-]{12,100}\r?$"){ Pass ".env contiene $secretName" } else { Fail ".env no contiene $secretName valido" }
    }
} else { Fail 'Falta .env; ejecuta PREPARAR-ENTORNO-LOCAL.bat' }
if ($null -ne $docker) {
    $services = @(& docker compose config --services 2>$null)
    if ($LASTEXITCODE -ne 0) { Fail 'docker compose config detecto YAML/variables invalidas' }
    else {
        $expected = @('mysql','eureka-server','auth-service','patient-service','appointment-service','clinical-service','finance-service','lab-service','operations-service','inventory-service','content-service','encounter-service','survey-service','dashboard-service','backup-service','api-gateway','frontend')
        $missing = @($expected | Where-Object { $_ -notin $services })
        $extra = @($services | Where-Object { $_ -notin $expected })
        if ($missing.Count -eq 0 -and $extra.Count -eq 0 -and $services.Count -eq 17) { Pass 'docker-compose.yml define MySQL + los 16 componentes esperados' }
        else { Fail "Servicios Compose inconsistentes. Faltan: $($missing -join ', ') Extras: $($extra -join ', ') Total: $($services.Count)" }
        try {
            $composeText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'docker-compose.yml'),[Text.Encoding]::UTF8)
            if($composeText -match '(?m)^\s{4}build:'){ Fail 'Compose local no debe compilar servicios; el instalador construye imagenes secuencialmente.' }
            else { Pass 'Docker local: Compose no contiene builds paralelos' }
            $imageRefs=[regex]::Matches($composeText,'(?m)^\s{4}image:\s+clinica-cardenas-local-[a-z0-9-]+:latest\s*$').Count
            if($imageRefs -eq 16){ Pass 'Docker local: 16 imagenes de aplicacion declaradas explicitamente' } else { Fail "Docker local: se esperaban 16 imagenes explicitas y se encontraron $imageRefs" }
            if($composeText -match '(?m)^\s{4}pull_policy:\s+never\s*$'){ Pass 'Docker local: imagenes de aplicacion no se intentan descargar de un registro' } else { Fail 'Docker local: falta pull_policy never' }
            $buildBat=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'COMPILAR-ACTUAL.bat'),[Text.Encoding]::UTF8)
            if($buildBat -match '(?i)-f\s+Dockerfile(?:\s|$)'){ Fail 'Build local: detectado -f Dockerfile relativo; debe usarse docker\Dockerfile.runtime.local con ruta explicita.' }
            elseif($buildBat -match '(?i)-f\s+"%~dp0docker\\Dockerfile\.runtime\.local"'){ Pass 'Build local: Dockerfile runtime se referencia con ruta absoluta de Windows' }
            else { Fail 'Build local: no se encontro referencia segura a docker\Dockerfile.runtime.local' }
            $installerText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '01-INSTALAR-O-ACTUALIZAR.bat'),[Text.Encoding]::UTF8)
            $dailyText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '02-INICIAR.bat'),[Text.Encoding]::UTF8)
            if($installerText -match 'ASEGURAR-CONTENEDORES\.ps1' -and $dailyText -match 'ASEGURAR-CONTENEDORES\.ps1'){ Pass 'Contenedores: instalacion y uso diario crean/verifican contenedores faltantes' } else { Fail 'Contenedores: falta ASEGURAR-CONTENEDORES.ps1 en instalacion o uso diario' }
        } catch { Fail "No se pudo validar politica de imagenes/contendores Compose: $($_.Exception.Message)" }
    }
}
# Gateway reactivo: evita depender de WebClient.Builder autoconfigurado y exige reparacion dirigida.
try {
    $gatewayFilterText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'api-gateway/src/main/java/pe/clinica/cardenas/gateway/ActiveAccountValidationFilter.java'),[Text.Encoding]::UTF8)
    $installerText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot '01-INSTALAR-O-ACTUALIZAR.bat'),[Text.Encoding]::UTF8)
    $gatewayRepairText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'REPARAR-GATEWAY.bat'),[Text.Encoding]::UTF8)
    if($gatewayFilterText -match 'WebClient\.builder\(\)' -and $gatewayFilterText -notmatch 'WebClient\.Builder\s+builder'){ Pass 'Gateway: cliente WebClient se construye explicitamente y no depende de bean autoconfigurado' } else { Fail 'Gateway: riesgo de arranque por dependencia WebClient.Builder no resuelta' }
    if($installerText -match 'VERIFICAR-GATEWAY-RUNTIME\.ps1' -and $installerText -match 'REPARAR-GATEWAY\.bat'){ Pass 'Gateway: instalador detecta y repara exclusivamente runtime obsoleto' } else { Fail 'Gateway: instalador no contiene reparacion dirigida' }
    if($gatewayRepairText -match 'Dockerfile\.gateway\.repair' -and $gatewayRepairText -notmatch 'COMPILAR-ACTUAL\.bat'){ Pass 'Gateway: reparacion dirigida no recompila los 15 servicios' } else { Fail 'Gateway: reparacion dirigida no esta aislada' }
    $composeLocalText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'docker-compose.yml'),[Text.Encoding]::UTF8)
    $gatewayAppText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'api-gateway/src/main/resources/application.yml'),[Text.Encoding]::UTF8)
    $buildText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'COMPILAR-ACTUAL.bat'),[Text.Encoding]::UTF8)
    if($buildText -match 'pe\.clinica\.cardenas\.gateway\.runtime=docker-dns-auth-scope-v3'){ Pass 'Gateway: build completo tambien etiqueta el runtime corregido' } else { Fail 'Gateway: build completo no marca el runtime corregido' }
    if($gatewayAppText -match 'CONTENT_ROUTE_URI:lb://CONTENT-SERVICE'){ Pass 'Gateway: rutas permiten override Docker local y fallback Eureka' } else { Fail 'Gateway: falta override CONTENT_ROUTE_URI con fallback Eureka' }
    if($composeLocalText -match 'CONTENT_ROUTE_URI:\s*http://content-service:8089' -and $composeLocalText -match 'AUTH_ROUTE_URI:\s*http://auth-service:8081' -and $composeLocalText -match 'SPRING_CLOUD_DISCOVERY_ENABLED:\s*[\"'']?false'){ Pass 'Gateway local: usa DNS directo de Docker y no depende de DiscoveryClient' } else { Fail 'Gateway local: faltan URI directas Docker o sigue dependiendo de DiscoveryClient' }
    if($gatewayFilterText -match 'private Mono<HttpStatus> validateActiveSession' -and $gatewayFilterText -match '\.timeout\(authValidationTimeout\)' -and $gatewayFilterText -match '\.flatMap\(validationStatus'){ Pass 'Gateway sesion: timeout aislado en validacion auth y no en la operacion clinica' } else { Fail 'Gateway sesion: timeout no esta aislado correctamente de chain.filter' }
    if($gatewayFilterText -notmatch '\.timeout\(Duration\.ofSeconds\(3\)\)'){ Pass 'Gateway sesion: eliminado timeout global de 3s que cancelaba respuestas de microservicios' } else { Fail 'Gateway sesion: persiste timeout global de 3s sobre la cadena completa' }
    if($gatewayFilterText -match 'Retry\.max\(1\)'){ Pass 'Gateway sesion: un solo reintento para fallos transitorios de validacion auth' } else { Fail 'Gateway sesion: falta reintento transitorio controlado' }
    if($composeLocalText -match 'AUTH_VALIDATION_TIMEOUT_SECONDS:\s*["'']?10["'']?'){ Pass 'Gateway local: validacion auth tolera picos de latencia sin afectar seguridad' } else { Fail 'Gateway local: falta timeout auth local explicito' }
} catch { Fail "No se pudo auditar prevencion de arranque Gateway: $($_.Exception.Message)" }

# Validacion estructural Maven sin requerir Maven local.
# IMPORTANTE: el POM raiz hereda de Spring Boot (4.0.7), mientras que los POM de
# los 16 modulos deben heredar del proyecto Clinica Cardenas (VERSION.txt).
# PowerShell 5.1 puede adaptar nodos XML de texto como cadenas; por eso NO se usa
# .InnerText sobre $rootPom.project.modules.module. Se usa XPath con namespace.
try {
    $rootPomPath = Join-Path $PSScriptRoot 'pom.xml'
    [xml]$rootPom = Get-Content -LiteralPath $rootPomPath -Raw
    $rootNs = New-Object System.Xml.XmlNamespaceManager($rootPom.NameTable)
    $rootNs.AddNamespace('m','http://maven.apache.org/POM/4.0.0')

    $rootGroupNode = $rootPom.SelectSingleNode('/m:project/m:groupId',$rootNs)
    $rootArtifactNode = $rootPom.SelectSingleNode('/m:project/m:artifactId',$rootNs)
    $rootVersionNode = $rootPom.SelectSingleNode('/m:project/m:version',$rootNs)
    if ($null -eq $rootGroupNode -or $null -eq $rootArtifactNode -or $null -eq $rootVersionNode) {
        throw 'El POM raiz no contiene groupId/artifactId/version propios.'
    }
    $rootGroup = $rootGroupNode.InnerText.Trim()
    $rootArtifact = $rootArtifactNode.InnerText.Trim()
    $rootVersion = $rootVersionNode.InnerText.Trim()

    $expectedProjectVersion = if ([string]::IsNullOrWhiteSpace([string]$version)) { $currentVersion } else { [string]$version }
    if ($rootGroup -ne 'pe.clinica.cardenas') { Fail "POM raiz groupId inesperado: $rootGroup" }
    if ($rootArtifact -ne 'clinica-cardenas-microservices') { Fail "POM raiz artifactId inesperado: $rootArtifact" }
    if ($rootVersion -ne $expectedProjectVersion) { Fail "POM raiz version $rootVersion; VERSION.txt indica $expectedProjectVersion" }
    else { Pass "POM raiz version de aplicacion = $rootVersion" }

    $bootParentArtifact = $rootPom.SelectSingleNode('/m:project/m:parent/m:artifactId',$rootNs)
    $bootParentVersion = $rootPom.SelectSingleNode('/m:project/m:parent/m:version',$rootNs)
    if ($null -ne $bootParentArtifact -and $null -ne $bootParentVersion) {
        if ($bootParentArtifact.InnerText.Trim() -eq 'spring-boot-starter-parent') {
            Pass "POM raiz usa Spring Boot parent $($bootParentVersion.InnerText.Trim()) (independiente de la version $rootVersion de la aplicacion)"
        } else {
            Warn "POM raiz usa parent Maven inesperado: $($bootParentArtifact.InnerText.Trim())"
        }
    }

    $moduleNodes = @($rootPom.SelectNodes('/m:project/m:modules/m:module',$rootNs))
    $modules = @($moduleNodes | ForEach-Object { $_.InnerText.Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($modules.Count -eq 16) { Pass 'POM raiz declara 16 modulos Maven' }
    else { Fail "POM raiz declara $($modules.Count) modulos validos; se esperaban 16" }

    $duplicateModules = @($modules | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name })
    if ($duplicateModules.Count -gt 0) { Fail "POM raiz contiene modulos duplicados: $($duplicateModules -join ', ')" }

    $moduleFailuresBefore = $script:Failures
    foreach ($m in $modules) {
        if ([string]::IsNullOrWhiteSpace($m)) { Fail 'POM raiz contiene un nombre de modulo vacio'; continue }
        if ([IO.Path]::IsPathRooted($m) -or $m -match '(^|[\\/])\.\.([\\/]|$)') { Fail "Nombre de modulo Maven no seguro: $m"; continue }

        $modulePomPath = Join-Path (Join-Path $PSScriptRoot $m) 'pom.xml'
        if (-not (Test-Path -LiteralPath $modulePomPath)) { Fail "Falta POM del modulo $m"; continue }

        $modulePomText = [IO.File]::ReadAllText($modulePomPath,[Text.Encoding]::UTF8)
        $modulePomDoc = New-Object System.Xml.XmlDocument
        $modulePomDoc.LoadXml($modulePomText)
        $moduleNs = New-Object System.Xml.XmlNamespaceManager($modulePomDoc.NameTable)
        $moduleNs.AddNamespace('m','http://maven.apache.org/POM/4.0.0')

        $parentGroupNode = $modulePomDoc.SelectSingleNode('/m:project/m:parent/m:groupId',$moduleNs)
        $parentArtifactNode = $modulePomDoc.SelectSingleNode('/m:project/m:parent/m:artifactId',$moduleNs)
        $parentVersionNode = $modulePomDoc.SelectSingleNode('/m:project/m:parent/m:version',$moduleNs)
        $artifactNode = $modulePomDoc.SelectSingleNode('/m:project/m:artifactId',$moduleNs)

        if ($null -eq $parentGroupNode -or $null -eq $parentArtifactNode -or $null -eq $parentVersionNode) {
            Fail "Modulo $m no declara parent Maven completo"
            continue
        }

        $parentGroup = $parentGroupNode.InnerText.Trim()
        $parentArtifact = $parentArtifactNode.InnerText.Trim()
        $parentVersion = $parentVersionNode.InnerText.Trim()
        $moduleArtifact = if ($null -ne $artifactNode) { $artifactNode.InnerText.Trim() } else { '' }

        if ($moduleArtifact -ne $m) { Fail "Modulo $m declara artifactId '$moduleArtifact'" }
        if ($parentGroup -ne $rootGroup -or $parentArtifact -ne $rootArtifact -or $parentVersion -ne $rootVersion) {
            Fail "Modulo $m usa parent ${parentGroup}:${parentArtifact}:${parentVersion}; debe usar ${rootGroup}:${rootArtifact}:${rootVersion}"
        }
    }
    if ($script:Failures -eq $moduleFailuresBefore) { Pass "Los 16 modulos Maven heredan correctamente del POM raiz $rootVersion" }
} catch { Fail "No se pudieron validar POMs: $($_.Exception.Message)" }
# Persistencia MySQL: un único motor para local y producción.
try {
    $mysqlProfiles = @(Get-ChildItem -LiteralPath $PSScriptRoot -Recurse -Filter 'application-mysql.yml' -File -ErrorAction Stop |
        Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' -and $_.FullName -notmatch '[\\/]build-artifacts[\\/]' })
    if($mysqlProfiles.Count -eq 11){ Pass 'Persistencia: 11 perfiles MySQL presentes' } else { Fail "Persistencia: se esperaban 11 perfiles MySQL y hay $($mysqlProfiles.Count)" }
    foreach($profile in $mysqlProfiles){
        $txt=[IO.File]::ReadAllText($profile.FullName,[Text.Encoding]::UTF8)
        if($txt -notmatch 'com\.mysql\.cj\.jdbc\.Driver' -or $txt -notmatch 'MySQLDialect'){ Fail "Perfil MySQL incompleto: $($profile.FullName)" }
    }
    $dataModules=@('auth-service','patient-service','appointment-service','clinical-service','finance-service','lab-service','operations-service','inventory-service','content-service','encounter-service','survey-service')
    foreach($module in $dataModules){
        $modulePomFile=Join-Path (Join-Path $PSScriptRoot $module) 'pom.xml'
        $pomText=[IO.File]::ReadAllText($modulePomFile,[Text.Encoding]::UTF8)
        if($pomText -notmatch '<groupId>com\.mysql</groupId>\s*<artifactId>mysql-connector-j</artifactId>'){ Fail "MySQL Connector/J falta en $module" }
    }
    if($script:Failures -eq 0){ Pass 'Persistencia: los 11 modulos incluyen MySQL Connector/J' }
    $composeLocalText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'docker-compose.yml'),[Text.Encoding]::UTF8)
    if($composeLocalText -match '(?s)auth-service:.*?SPRING_PROFILES_ACTIVE:\s*mysql,local'){
        Pass 'Instalacion limpia: auth-service usa MySQL + perfil local para crear ADMIN y habilitar validacion OTP'
    } else {
        Fail 'Instalacion limpia: auth-service debe usar SPRING_PROFILES_ACTIVE=mysql,local'
    }

    $transfer=Join-Path $PSScriptRoot 'common-security\src\main\java\pe\clinica\cardenas\security\DataTransferService.java'
    $transferText=[IO.File]::ReadAllText($transfer,[Text.Encoding]::UTF8)
    if($transferText -match 'return "`" \+ id\.replace\("`", "``"\) \+ "`"'){ Pass 'Copias/importacion: identificadores SQL compatibles con MySQL' } else { Fail 'Copias/importacion: escape de identificadores MySQL no validado' }
    $mysqlInit=Join-Path $PSScriptRoot 'database\mysql\init-databases.sh'
    $initText=[IO.File]::ReadAllText($mysqlInit,[Text.Encoding]::UTF8)
    if($initText -match 'CREATE DATABASE IF NOT EXISTS' -and $initText -match 'GRANT ALL PRIVILEGES'){ Pass 'MySQL: inicialización de bases y usuarios presente'
            $initText=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'database/mysql/init-databases.sh') -Raw
            if($initText -notmatch '`' -and $initText -match 'MYSQL_PWD='){ Pass 'MySQL init: sin backticks de shell y sin password CLI' } else { Fail 'MySQL init: usa construcciones inseguras para /bin/sh' }
            $mysqlValidator=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'VERIFICAR-MYSQL-INICIALIZADO.ps1') -Raw
            if($mysqlValidator -match 'Repair-Initialization' -and $mysqlValidator -match 'SELECT\(1\)' -and $mysqlValidator -match "'--env'" -and $mysqlValidator -notmatch "'sh','-lc'"){ Pass 'MySQL: validador repara y prueba accesos sin quoting fragil de sh -lc' } else { Fail 'MySQL: validador aun depende de quoting fragil o no prueba conexiones reales' }
            $prepareLocal=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'PREPARAR-ENTORNO-LOCAL.ps1') -Raw
            if($prepareLocal -match 'Get-ContainerEnvironment' -and $prepareLocal -match 'Continuidad MySQL'){ Pass 'Actualizacion local: conserva secretos MySQL del contenedor existente' } else { Fail 'Actualizacion local: no conserva secretos MySQL existentes' }
            if($composeLocalText -match 'MYSQL_PWD="\$\$\{MYSQL_ROOT_PASSWORD\}" mysqladmin ping'){ Pass 'MySQL healthcheck: no expone password con -p' } else { Fail 'MySQL healthcheck: sigue usando password por argumento CLI' } } else { Fail 'MySQL: inicialización incompleta' }
} catch { Fail "No se pudo validar MySQL: $($_.Exception.Message)" }

# Higiene de entrega: la carpeta distribuida contiene exclusivamente la version vigente.
try {
    $allFiles = @(Get-ChildItem -LiteralPath $PSScriptRoot -File -Recurse -ErrorAction SilentlyContinue)
    $legacy = @($allFiles | Where-Object {
        $_.Name -like 'CAMBIOS-V*' -or $_.Name -like 'VALIDACION-INTEGRAL-V*' -or $_.Name -like 'VALIDACION-HOTFIX*' -or $_.Name -eq 'HOTFIX-ID.txt' -or
        (($_.Name -match '(?i)(?:v)?4\.\d+(?:\.\d+)+') -and ($_.Name -notmatch [regex]::Escape($expectedProjectVersion)))
    })
    if ($legacy.Count -eq 0) { Pass "Paquete limpio: solo contiene la entrega vigente $expectedProjectVersion" }
    else {
        $relativeLegacy = @($legacy | ForEach-Object { $_.FullName.Substring($PSScriptRoot.Length).TrimStart('\','/') })
        Fail "El paquete contiene archivos de otra entrega: $($relativeLegacy -join ', ')"
    }
} catch { Warn "No se pudo auditar limpieza de la entrega: $($_.Exception.Message)" }

# Puertos: solo aviso porque una ejecucion previa de la propia clinica puede ocuparlos.
try {
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        $ports = @(3307,8080,8090,8761,8081,8082,8083,8084,8085,8086,8087,8088,8089,8091,8092,8093,8094)
        $busy = @()
        foreach ($port in $ports) {
            if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { $busy += $port }
        }
        if ($busy.Count -gt 0) { Warn "Puertos ya en escucha: $($busy -join ', '). Si no pertenecen a Clinica Cardenas, Docker puede no iniciar." }
        else { Pass 'Puertos locales esperados libres antes del arranque' }
    }
} catch { Warn "No se pudieron revisar puertos: $($_.Exception.Message)" }
Out-Line ''
Out-Line "Resultado: $($script:Failures) error(es), $($script:Warnings) aviso(s)."
if ($script:Failures -gt 0) {
    Write-Host "PREVALIDACION FALLIDA. Revisa PREVALIDACION.txt" -ForegroundColor Red
    exit 1
}
Write-Host "PREVALIDACION OK. Los avisos no bloquean la ejecucion." -ForegroundColor Green
exit 0
