@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set /p APP_VERSION=<"%~dp0VERSION.txt"
echo ======================================================
echo   CLINICA CARDENAS %APP_VERSION% - VALIDACION FUNCIONAL
echo ======================================================
echo.
echo Este proceso NO recompila imagenes y NO recrea contenedores sanos.
echo Verifica el sistema que ya esta ejecutandose; puede iniciar backup-service si estaba detenido.
echo.
call "%~dp0PREPARAR-ENTORNO-LOCAL.bat"
if errorlevel 1 goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0PREVALIDAR-SISTEMA.ps1"
if errorlevel 1 goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-GATEWAY-RUNTIME.ps1"
if errorlevel 1 goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-MYSQL-INICIALIZADO.ps1"
if errorlevel 1 goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-RUTEO-LOCAL.ps1"
if errorlevel 1 goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-BACKUP-LOCAL.ps1"
if errorlevel 1 goto :error
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0EJECUTAR-PRUEBAS.ps1"
if errorlevel 1 goto :error
> "%~dp0VALIDACION-OK.txt" echo CLINICA CARDENAS %APP_VERSION% - OK - %DATE% %TIME%
echo.
echo ======================================================
echo   VALIDACION FUNCIONAL COMPLETADA CORRECTAMENTE
echo ======================================================
echo No fue necesario recompilar ni recrear contenedores sanos.
pause
exit /b 0

:error
echo.
echo ERROR: la validacion funcional encontro un problema real.
call "%~dp005-GENERAR-DIAGNOSTICO.bat" auto
echo Se genero DIAGNOSTICO-CLINICA.txt con el punto exacto de fallo.
pause
exit /b 1
