@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set /p APP_VERSION=<"%~dp0VERSION.txt"
echo ======================================================
echo   CLINICA CARDENAS %APP_VERSION% - INSTALAR / ACTUALIZAR
echo ======================================================
echo.
echo Este proceso compila solo cuando instalas o recibes una actualizacion.
echo Si existe una instalacion previa, no se elimina hasta que el nuevo build este listo.
echo El uso diario se hace con 02-INICIAR.bat y NO recompila.
echo.
echo MODO INSTALACION LIMPIA COMPATIBLE:
echo - Si Docker esta vacio, descargara bases, compilara, creara 17 imagenes y 17 contenedores.
echo - Inicializara y verificara las 11 bases MySQL antes de continuar.
echo - Solo declarara exito despues de las pruebas funcionales.
echo.
call "%~dp0PREPARAR-ENTORNO-LOCAL.bat"
if errorlevel 1 goto :error
for %%F in (VALIDACION-OK.txt MYSQL-ESTADO.txt CONTENEDORES-ESTADO.txt IMAGENES-ESTADO.txt BUILD-ESTADO.txt COMPILACION-ACTUAL.txt PRUEBA-FUNCIONAL.txt CLASIFICACION-ERRORES.txt) do if exist "%~dp0%%F" del /q "%~dp0%%F" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0PREVALIDAR-SISTEMA.ps1"
if errorlevel 1 goto :error

echo.
echo Verificando reglas funcionales esenciales...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-REQUISITOS-RECEPCION.ps1" > "%~dp0VERIFICACION-FUNCIONAL.txt" 2>&1
set "VERIFY_RC=%ERRORLEVEL%"
type "%~dp0VERIFICACION-FUNCIONAL.txt"
if not "%VERIFY_RC%"=="0" goto :error

echo.
echo Comprobando si las imagenes de esta misma version ya estan disponibles...
if not exist "%~dp0build-logs" mkdir "%~dp0build-logs" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-IMAGENES-LOCALES.ps1" > "%~dp0build-logs\verificacion-imagenes-console.log" 2>&1
set "IMAGE_VERIFY_RC=%ERRORLEVEL%"
type "%~dp0build-logs\verificacion-imagenes-console.log"
if "%IMAGE_VERIFY_RC%"=="0" (
  echo Imagenes completas detectadas. Se omitira la recompilacion y se continuara con contenedores.
) else if "%IMAGE_VERIFY_RC%"=="2" (
  echo Imagenes incompletas: se realizara la compilacion/actualizacion necesaria.
  call "%~dp0COMPILAR-ACTUAL.bat"
  if errorlevel 1 goto :error
) else (
  echo ERROR: no se pudo verificar de forma segura las imagenes locales.
  goto :error
)

echo.
echo Verificando runtime especifico del API Gateway...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-GATEWAY-RUNTIME.ps1"
set "GATEWAY_VERIFY_RC=%ERRORLEVEL%"
if "%GATEWAY_VERIFY_RC%"=="2" (
  call "%~dp0REPARAR-GATEWAY.bat"
  if errorlevel 1 goto :error
) else if not "%GATEWAY_VERIFY_RC%"=="0" (
  goto :error
)

echo.
echo Creando/verificando los 17 contenedores si no existen...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ASEGURAR-CONTENEDORES.ps1" -ForceRecreate
if errorlevel 1 goto :error

echo.
echo Iniciando sistema por etapas para reducir consumo de RAM/CPU...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0INICIAR-OPTIMIZADO.ps1"
if errorlevel 1 goto :error

echo.
echo Verificando ruteo local Gateway - servicios Docker...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VERIFICAR-RUTEO-LOCAL.ps1"
if errorlevel 1 goto :error

echo.
echo Ejecutando prueba funcional final...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0EJECUTAR-PRUEBAS.ps1"
if errorlevel 1 goto :error

> "%~dp0VALIDACION-OK.txt" echo CLINICA CARDENAS %APP_VERSION% - OK - %DATE% %TIME%
if exist "%~dp0build-artifacts" rmdir /s /q "%~dp0build-artifacts" >nul 2>&1
echo.
echo ======================================================
echo   INSTALACION / ACTUALIZACION COMPLETADA
echo ======================================================
echo En adelante usa 02-INICIAR.bat. No volvera a compilar.
start "" http://localhost:8080
pause
exit /b 0

:error
echo.
echo ERROR: el proceso no termino correctamente.
call "%~dp005-GENERAR-DIAGNOSTICO.bat" auto
echo Se genero DIAGNOSTICO-CLINICA.txt y, cuando aplica, logs por servicio.
pause
exit /b 1
