@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"
set "DOCKER_BUILDKIT=1"
set "BUILDKIT_PROGRESS=plain"
set /p APP_VERSION=<"%~dp0VERSION.txt"
if not defined APP_VERSION exit /b 1
if not exist "%~dp0build-logs" mkdir "%~dp0build-logs" >nul 2>&1

echo Reparando exclusivamente api-gateway; no se recompilaran los otros servicios...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-RECURSOS-BUILD.ps1" -Stage "antes reparacion gateway"
if errorlevel 1 exit /b 1
call :build_gateway
if not errorlevel 1 goto :verify
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLASIFICAR-ERROR-BUILD.ps1" -LogPath "%~dp0build-logs\gateway-repair.log" -Module "api-gateway-repair" -TransientOnly
if errorlevel 1 goto :failed
echo AVISO: fallo transitorio reparando Gateway. Reintentando una sola vez...
docker buildx inspect --bootstrap >nul 2>&1
timeout /t 5 /nobreak >nul
call :build_gateway_append
if errorlevel 1 goto :failed

:verify
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-GATEWAY-RUNTIME.ps1"
if errorlevel 1 goto :failed
echo Gateway reparado correctamente.
exit /b 0

:build_gateway
docker build --progress=plain --build-arg "APP_VERSION=%APP_VERSION%" -f "docker/Dockerfile.gateway.repair" -t "clinica-cardenas-local-api-gateway:latest" . > "%~dp0build-logs\gateway-repair.log" 2>&1
exit /b %ERRORLEVEL%

:build_gateway_append
docker build --progress=plain --build-arg "APP_VERSION=%APP_VERSION%" -f "docker/Dockerfile.gateway.repair" -t "clinica-cardenas-local-api-gateway:latest" . >> "%~dp0build-logs\gateway-repair.log" 2>&1
exit /b %ERRORLEVEL%

:failed
echo ERROR: no se pudo reconstruir/verificar api-gateway.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CLASIFICAR-ERROR-BUILD.ps1" -LogPath "%~dp0build-logs\gateway-repair.log" -Module "api-gateway-repair"
exit /b 1
