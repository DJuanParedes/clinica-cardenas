@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"
set /p APP_VERSION=<"%~dp0VERSION.txt"
echo ======================================================
echo   CLINICA CARDENAS %APP_VERSION% - REPARAR RUTEO Y VALIDAR
echo ======================================================
echo.
echo Conserva MySQL, documentos y los servicios sanos.
echo Solo reconstruye API Gateway si su marcador runtime de ruteo/sesion es antiguo.
echo Si el ruteo actual ya esta correcto, NO recrea Gateway ni Frontend.
echo Backup se inicia/verifica porque forma parte de la validacion funcional.
echo.
call "%~dp0PREPARAR-ENTORNO-LOCAL.bat"
if errorlevel 1 goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0PREVALIDAR-SISTEMA.ps1"
if errorlevel 1 goto :error

set "GATEWAY_REPAIRED=0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-GATEWAY-RUNTIME.ps1"
set "GRC=%ERRORLEVEL%"
if "%GRC%"=="2" (
  call "%~dp0REPARAR-GATEWAY.bat"
  if errorlevel 1 goto :error
  set "GATEWAY_REPAIRED=1"
) else if not "%GRC%"=="0" (
  goto :error
)

if "%GATEWAY_REPAIRED%"=="1" (
  echo.
  echo Aplicando al contenedor Gateway las rutas DNS directas de Docker...
  docker compose up -d --no-build --no-deps --force-recreate api-gateway frontend
  if errorlevel 1 goto :error
)

echo.
echo Verificando ruteo local existente...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-RUTEO-LOCAL.ps1"
set "RRC=%ERRORLEVEL%"
if not "%RRC%"=="0" (
  if "%RRC%"=="4" (
    if "%GATEWAY_REPAIRED%"=="0" (
      echo El Gateway no enruta con el contenedor actual. Reaplicando configuracion solo a Gateway + Frontend...
      docker compose up -d --no-build --no-deps --force-recreate api-gateway frontend
      if errorlevel 1 goto :error
      powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-RUTEO-LOCAL.ps1"
      if errorlevel 1 goto :error
    ) else (
      goto :error
    )
  ) else (
    goto :error
  )
)

echo.
echo Verificando modulo de copias de seguridad...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-BACKUP-LOCAL.ps1"
if errorlevel 1 goto :error

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-MYSQL-INICIALIZADO.ps1"
if errorlevel 1 goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0EJECUTAR-PRUEBAS.ps1"
if errorlevel 1 goto :error
> "%~dp0VALIDACION-OK.txt" echo CLINICA CARDENAS %APP_VERSION% - OK - %DATE% %TIME%
echo.
echo ======================================================
echo   RUTEO Y VALIDACION COMPLETADOS CORRECTAMENTE
echo ======================================================
start "" http://localhost:8080
pause
exit /b 0

:error
echo.
echo ERROR: la reparacion/validacion encontro un problema.
call "%~dp005-GENERAR-DIAGNOSTICO.bat" auto
echo Se genero DIAGNOSTICO-CLINICA.txt con el punto exacto de fallo.
pause
exit /b 1
