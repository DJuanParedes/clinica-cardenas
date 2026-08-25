@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
set "DOCKER_BUILDKIT=1"
set "BUILDKIT_PROGRESS=plain"
set "COMPOSE_PROGRESS=plain"
set /p APP_VERSION=<"%~dp0VERSION.txt"
if not defined APP_VERSION (
  echo ERROR: VERSION.txt esta vacio.
  exit /b 1
)

set "JAVA_SERVICES=eureka-server auth-service patient-service appointment-service clinical-service finance-service lab-service operations-service inventory-service dashboard-service content-service encounter-service survey-service backup-service api-gateway"

echo ======================================================
echo   CLINICA CARDENAS %APP_VERSION% - COMPILACION SEGURA
echo ======================================================
echo.
echo Estrategia anti-saturacion:
echo - Maven compila los 15 JAR una sola vez.
echo - Los JAR se exportan al host; NO se crea una imagen gigante de artefactos.
echo - Las 15 imagenes Java se construyen UNA POR UNA.
echo - El frontend se construye al final, sin competir por RAM con otros builds.
echo - Un fallo transitorio BuildKit/gRPC reintenta solo el componente afectado.
echo.

if not exist "%~dp0build-logs" mkdir "%~dp0build-logs" >nul 2>&1
if not exist "%~dp0build-logs\images" mkdir "%~dp0build-logs\images" >nul 2>&1
for %%F in (mysql.log java-artifacts.log imagenes.log frontend.log restauracion.log recursos.log cleanup.log contenedores.log verificacion-imagenes.log verificacion-imagenes-console.log) do if exist "%~dp0build-logs\%%F" del /q "%~dp0build-logs\%%F" >nul 2>&1
for %%F in (CLASIFICACION-ERRORES.txt COMPILACION-ACTUAL.txt BUILD-ESTADO.txt CONTENEDORES-ESTADO.txt IMAGENES-ESTADO.txt) do if exist "%~dp0%%F" del /q "%~dp0%%F" >nul 2>&1
for %%F in ("%~dp0build-logs\images\*.log") do if exist "%%~fF" del /q "%%~fF" >nul 2>&1
> "%~dp0BUILD-ESTADO.txt" echo Estado=INICIANDO
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
>> "%~dp0BUILD-ESTADO.txt" echo Fecha=%DATE% %TIME%

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-RECURSOS-BUILD.ps1" -Stage "antes del build"
if errorlevel 1 goto :resource_error

docker info >nul 2>&1
if errorlevel 1 (
  echo ERROR: Docker Desktop no esta iniciado.
  goto :fail_no_restore
)
docker buildx version >nul 2>&1
if errorlevel 1 (
  echo ERROR: Docker Buildx no esta disponible.
  goto :fail_no_restore
)

set "HAD_CONTAINERS=0"
for /f "delims=" %%C in ('docker compose ps -aq 2^>nul') do set "HAD_CONTAINERS=1"
if "!HAD_CONTAINERS!"=="1" (
  echo [1/5] Deteniendo temporalmente la instalacion existente para liberar memoria...
  docker compose stop > "%~dp0build-logs\restauracion.log" 2>&1
  if errorlevel 1 echo AVISO: algunos contenedores no pudieron detenerse; se continuara con cuidado.
) else (
  echo [1/5] Instalacion nueva: no hay contenedores anteriores que detener.
)

echo [2/5] Verificando imagen MySQL 8.4...
> "%~dp0BUILD-ESTADO.txt" echo Estado=MYSQL
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
docker image inspect mysql:8.4 >nul 2>&1
if errorlevel 1 (
  docker pull mysql:8.4 > "%~dp0build-logs\mysql.log" 2>&1
  if errorlevel 1 (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLASIFICAR-ERROR-BUILD.ps1" -LogPath "%~dp0build-logs\mysql.log" -Module "mysql-pull"
    goto :restore_previous
  )
) else (
  > "%~dp0build-logs\mysql.log" echo REUTILIZADA: mysql:8.4 ya existe localmente.
)

rem Las imagenes clinica-cardenas-artifacts:* son solo portadores temporales de versiones anteriores.
rem Ningun contenedor de ejecucion depende de ellas; eliminarlas libera espacio sin tocar volumenes/datos.
for /f "delims=" %%I in ('docker image ls "clinica-cardenas-artifacts" -q 2^>nul') do docker image rm %%I >> "%~dp0build-logs\cleanup.log" 2>&1

set "REUSE_JARS=0"
if exist "%~dp0build-artifacts\BUILD_VERSION.txt" (
  set /p EXPORTED_VERSION=<"%~dp0build-artifacts\BUILD_VERSION.txt"
  if "!EXPORTED_VERSION!"=="%APP_VERSION%" (
    set "REUSE_JARS=1"
    for %%S in (%JAVA_SERVICES%) do if not exist "%~dp0build-artifacts\runtime\%%S\app.jar" set "REUSE_JARS=0"
  )
)
if "!REUSE_JARS!"=="1" (
  echo [3/5] Reutilizando 15 JAR ya compilados de esta misma version.
  > "%~dp0build-logs\java-artifacts.log" echo REUTILIZADO: build-artifacts corresponde a %APP_VERSION% y contiene 15 JAR.
) else (
  echo [3/5] Compilando los 15 componentes Java en un unico reactor Maven...
  if exist "%~dp0build-artifacts" rmdir /s /q "%~dp0build-artifacts" >nul 2>&1
  call :build_java_artifacts
  if errorlevel 1 goto :build_java_error
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-RECURSOS-BUILD.ps1" -Stage "despues de Maven"
if errorlevel 1 goto :resource_error_restore

echo [4/5] Construyendo imagenes Java secuencialmente...
> "%~dp0BUILD-ESTADO.txt" echo Estado=IMAGENES_JAVA
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
> "%~dp0build-logs\imagenes.log" echo CLINICA CARDENAS %APP_VERSION% - BUILD SECUENCIAL DE IMAGENES
for %%S in (%JAVA_SERVICES%) do (
  call :build_service %%S
  if errorlevel 1 goto :build_images_error
)

echo [5/5] Compilando frontend de forma aislada...
> "%~dp0BUILD-ESTADO.txt" echo Estado=FRONTEND
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
call :build_frontend
if errorlevel 1 goto :build_images_error

> "%~dp0BUILD-ESTADO.txt" echo Estado=VERIFICANDO_IMAGENES
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-IMAGENES-LOCALES.ps1" > "%~dp0build-logs\verificacion-imagenes-console.log" 2>&1
set "IMAGE_VERIFY_RC=%ERRORLEVEL%"
type "%~dp0build-logs\verificacion-imagenes-console.log"
if not "%IMAGE_VERIFY_RC%"=="0" goto :verify_images_error

> "%~dp0COMPILACION-ACTUAL.txt" echo CLINICA CARDENAS %APP_VERSION% - COMPILACION OK
>> "%~dp0COMPILACION-ACTUAL.txt" echo Fecha: %DATE% %TIME%
>> "%~dp0COMPILACION-ACTUAL.txt" echo Maven: reactor unico completado/exportado
>> "%~dp0COMPILACION-ACTUAL.txt" echo Docker: 15 imagenes Java construidas secuencialmente
>> "%~dp0COMPILACION-ACTUAL.txt" echo Frontend: build aislado completado
>> "%~dp0COMPILACION-ACTUAL.txt" echo Verificacion: todas las imagenes corresponden a %APP_VERSION%
> "%~dp0BUILD-ESTADO.txt" echo Estado=OK
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
>> "%~dp0BUILD-ESTADO.txt" echo Fecha=%DATE% %TIME%

echo.
echo COMPILACION COMPLETADA.
echo Los contenedores nuevos se crearan/actualizaran en la siguiente etapa.
exit /b 0

:build_java_artifacts
> "%~dp0BUILD-ESTADO.txt" echo Estado=MAVEN
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
docker buildx build --progress=plain -f docker/Dockerfile.artifacts --output "type=local,dest=build-artifacts" . > "%~dp0build-logs\java-artifacts.log" 2>&1
if not errorlevel 1 goto :verify_java_artifacts
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLASIFICAR-ERROR-BUILD.ps1" -LogPath "%~dp0build-logs\java-artifacts.log" -Module "maven-reactor" -TransientOnly
if errorlevel 1 exit /b 1
echo AVISO: fallo transitorio de red/BuildKit. Reintentando solo Maven una vez...
if exist "%~dp0build-artifacts" rmdir /s /q "%~dp0build-artifacts" >nul 2>&1
docker buildx inspect --bootstrap >nul 2>&1
timeout /t 5 /nobreak >nul
docker buildx build --progress=plain -f docker/Dockerfile.artifacts --output "type=local,dest=build-artifacts" . >> "%~dp0build-logs\java-artifacts.log" 2>&1
if errorlevel 1 exit /b 1
:verify_java_artifacts
set "JAR_COUNT=0"
for %%S in (%JAVA_SERVICES%) do if exist "%~dp0build-artifacts\runtime\%%S\app.jar" set /a JAR_COUNT+=1
if not "!JAR_COUNT!"=="15" (
  echo ERROR: Maven termino pero solo se encontraron !JAR_COUNT!/15 JAR exportados.>> "%~dp0build-logs\java-artifacts.log"
  exit /b 1
)
if not exist "%~dp0build-artifacts\BUILD_VERSION.txt" exit /b 1
set /p EXPORTED_VERSION=<"%~dp0build-artifacts\BUILD_VERSION.txt"
if not "!EXPORTED_VERSION!"=="%APP_VERSION%" (
  echo ERROR: artefactos exportados son !EXPORTED_VERSION! y se esperaba %APP_VERSION%.>> "%~dp0build-logs\java-artifacts.log"
  exit /b 1
)
exit /b 0

:build_service
set "SVC=%~1"
set "SVC_LOG=%~dp0build-logs\images\%~1.log"
echo Construyendo %SVC%...
echo [%DATE% %TIME%] BUILD %SVC%>> "%~dp0build-logs\imagenes.log"
if not exist "%~dp0docker\Dockerfile.runtime.local" (
  echo ERROR: falta docker\Dockerfile.runtime.local.>> "!SVC_LOG!"
  > "%~dp0BUILD-ESTADO.txt" echo Estado=ERROR_DOCKERFILE
  >> "%~dp0BUILD-ESTADO.txt" echo Servicio=%SVC%
  >> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
  exit /b 1
)
if not exist "%~dp0build-artifacts\runtime\%SVC%\app.jar" (
  echo ERROR: falta build-artifacts\runtime\%SVC%\app.jar.>> "!SVC_LOG!"
  > "%~dp0BUILD-ESTADO.txt" echo Estado=ERROR_JAR_AUSENTE
  >> "%~dp0BUILD-ESTADO.txt" echo Servicio=%SVC%
  >> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
  exit /b 1
)
set "EXTRA_LABEL="
if /I "%SVC%"=="api-gateway" set "EXTRA_LABEL=--label pe.clinica.cardenas.gateway.runtime=docker-dns-auth-scope-v3"
docker build --progress=plain !EXTRA_LABEL! --build-arg "APP_VERSION=%APP_VERSION%" --build-arg "MODULE=%SVC%" -f "%~dp0docker\Dockerfile.runtime.local" -t "clinica-cardenas-local-%SVC%:latest" "%~dp0build-artifacts\runtime\%SVC%" > "!SVC_LOG!" 2>&1
if not errorlevel 1 (
  echo OK %SVC%>> "%~dp0build-logs\imagenes.log"
  exit /b 0
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLASIFICAR-ERROR-BUILD.ps1" -LogPath "!SVC_LOG!" -Module "%SVC%" -TransientOnly
if errorlevel 1 goto :service_failed
echo AVISO %SVC%: fallo transitorio; reintentando SOLO este servicio...>> "%~dp0build-logs\imagenes.log"
docker buildx inspect --bootstrap >nul 2>&1
timeout /t 5 /nobreak >nul
docker build --progress=plain !EXTRA_LABEL! --build-arg "APP_VERSION=%APP_VERSION%" --build-arg "MODULE=%SVC%" -f "%~dp0docker\Dockerfile.runtime.local" -t "clinica-cardenas-local-%SVC%:latest" "%~dp0build-artifacts\runtime\%SVC%" >> "!SVC_LOG!" 2>&1
if not errorlevel 1 (
  echo OK %SVC% ^(reintento^)>> "%~dp0build-logs\imagenes.log"
  exit /b 0
)
:service_failed
echo ERROR %SVC%>> "%~dp0build-logs\imagenes.log"
echo ---- ULTIMAS LINEAS %SVC% ---->> "%~dp0build-logs\imagenes.log"
powershell -NoProfile -Command "Get-Content -LiteralPath '!SVC_LOG!' -Tail 100 -ErrorAction SilentlyContinue" >> "%~dp0build-logs\imagenes.log" 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLASIFICAR-ERROR-BUILD.ps1" -LogPath "!SVC_LOG!" -Module "%SVC%"
> "%~dp0BUILD-ESTADO.txt" echo Estado=ERROR_IMAGEN
>> "%~dp0BUILD-ESTADO.txt" echo Servicio=%SVC%
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
exit /b 1

:build_frontend
echo [%DATE% %TIME%] BUILD frontend>> "%~dp0build-logs\imagenes.log"
docker build --progress=plain --build-arg "APP_VERSION=%APP_VERSION%" -t "clinica-cardenas-local-frontend:latest" frontend > "%~dp0build-logs\frontend.log" 2>&1
if not errorlevel 1 (
  echo OK frontend>> "%~dp0build-logs\imagenes.log"
  exit /b 0
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLASIFICAR-ERROR-BUILD.ps1" -LogPath "%~dp0build-logs\frontend.log" -Module "frontend" -TransientOnly
if errorlevel 1 goto :frontend_failed
echo AVISO frontend: fallo transitorio; reintentando SOLO frontend...>> "%~dp0build-logs\imagenes.log"
docker buildx inspect --bootstrap >nul 2>&1
timeout /t 5 /nobreak >nul
docker build --progress=plain --build-arg "APP_VERSION=%APP_VERSION%" -t "clinica-cardenas-local-frontend:latest" frontend >> "%~dp0build-logs\frontend.log" 2>&1
if not errorlevel 1 (
  echo OK frontend ^(reintento^)>> "%~dp0build-logs\imagenes.log"
  exit /b 0
)
:frontend_failed
echo ERROR frontend>> "%~dp0build-logs\imagenes.log"
echo ---- ULTIMAS LINEAS frontend ---->> "%~dp0build-logs\imagenes.log"
powershell -NoProfile -Command "Get-Content -LiteralPath '%~dp0build-logs\frontend.log' -Tail 120 -ErrorAction SilentlyContinue" >> "%~dp0build-logs\imagenes.log" 2>&1
> "%~dp0BUILD-ESTADO.txt" echo Estado=ERROR_FRONTEND
>> "%~dp0BUILD-ESTADO.txt" echo Servicio=frontend
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
exit /b 1

:build_java_error
echo.
echo ERROR: fallo la compilacion/exportacion Java.
powershell -NoProfile -Command "Get-Content -LiteralPath '%~dp0build-logs\java-artifacts.log' -Tail 140 -ErrorAction SilentlyContinue"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLASIFICAR-ERROR-BUILD.ps1" -LogPath "%~dp0build-logs\java-artifacts.log" -Module "maven-reactor"
> "%~dp0BUILD-ESTADO.txt" echo Estado=ERROR_MAVEN
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
goto :restore_previous

:build_images_error
echo.
echo ERROR: fallo la creacion secuencial de imagenes o frontend.
powershell -NoProfile -Command "Get-Content -LiteralPath '%~dp0build-logs\imagenes.log' -Tail 160 -ErrorAction SilentlyContinue"
if exist "%~dp0BUILD-ESTADO.txt" findstr /b /c:"Estado=ERROR_FRONTEND" "%~dp0BUILD-ESTADO.txt" >nul 2>&1
if not errorlevel 1 if exist "%~dp0build-logs\frontend.log" powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLASIFICAR-ERROR-BUILD.ps1" -LogPath "%~dp0build-logs\frontend.log" -Module "frontend"
goto :restore_previous


:verify_images_error
echo.
echo ERROR: las imagenes se construyeron, pero la verificacion posterior no fue satisfactoria.
echo No se clasificara este caso como error de frontend.
> "%~dp0BUILD-ESTADO.txt" echo Estado=ERROR_VERIFICACION_IMAGENES
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
if exist "%~dp0IMAGENES-ESTADO.txt" type "%~dp0IMAGENES-ESTADO.txt"
if exist "%~dp0build-logs\verificacion-imagenes-console.log" type "%~dp0build-logs\verificacion-imagenes-console.log"
goto :restore_previous

:resource_error
> "%~dp0BUILD-ESTADO.txt" echo Estado=ERROR_RECURSOS
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
goto :fail_no_restore

:resource_error_restore
> "%~dp0BUILD-ESTADO.txt" echo Estado=ERROR_RECURSOS
>> "%~dp0BUILD-ESTADO.txt" echo Version=%APP_VERSION%
goto :restore_previous

:restore_previous
if "!HAD_CONTAINERS!"=="1" (
  echo.
  echo Restaurando contenedores de la instalacion anterior...
  docker compose start >> "%~dp0build-logs\restauracion.log" 2>&1
  if errorlevel 1 (
    echo AVISO: no se pudo restaurar completamente la instalacion anterior. Revisa build-logs\restauracion.log
  ) else (
    echo Instalacion anterior restaurada. El fallo de actualizacion no elimino tus contenedores.
  )
) else (
  echo Instalacion nueva: no habia contenedores anteriores que restaurar.
)
exit /b 1

:fail_no_restore
exit /b 1
