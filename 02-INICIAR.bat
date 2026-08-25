@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set /p APP_VERSION=<"%~dp0VERSION.txt"
echo ======================================================
echo   CLINICA CARDENAS %APP_VERSION% - USO DIARIO
echo ======================================================
echo NO se compilara nada.
echo.
call "%~dp0PREPARAR-ENTORNO-LOCAL.bat"
if errorlevel 1 goto :error
if not exist "%~dp0build-logs" mkdir "%~dp0build-logs" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-IMAGENES-LOCALES.ps1" > "%~dp0build-logs\verificacion-imagenes-console.log" 2>&1
set "IMAGE_VERIFY_RC=%ERRORLEVEL%"
type "%~dp0build-logs\verificacion-imagenes-console.log"
if "%IMAGE_VERIFY_RC%"=="2" (
  echo.
  echo Debes completar primero 01-INSTALAR-O-ACTUALIZAR.bat.
  pause
  exit /b 2
)
if not "%IMAGE_VERIFY_RC%"=="0" goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-GATEWAY-RUNTIME.ps1"
set "GATEWAY_VERIFY_RC=%ERRORLEVEL%"
if not "%GATEWAY_VERIFY_RC%"=="0" (
  echo.
  echo El API Gateway necesita una reparacion de runtime. Ejecuta 01-INSTALAR-O-ACTUALIZAR.bat.
  pause
  exit /b 2
)
echo Verificando/creando contenedores faltantes sin recompilar...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ASEGURAR-CONTENEDORES.ps1"
if errorlevel 1 goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0INICIAR-OPTIMIZADO.ps1"
if errorlevel 1 goto :error
start "" http://localhost:8080
exit /b 0
:error
call "%~dp005-GENERAR-DIAGNOSTICO.bat" auto
pause
exit /b 1
