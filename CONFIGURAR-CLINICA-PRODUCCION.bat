@echo off
setlocal
cd /d "%~dp0"
if not exist "%~dp0.env.production" (
  echo Primero ejecuta PREPARAR-PRODUCCION.bat.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CONFIGURAR-CLINICA.ps1" -EnvironmentFile .env.production
if errorlevel 1 (
  echo.
  echo No se pudo guardar la identidad de produccion.
  pause
  exit /b 1
)
echo.
echo Identidad de produccion configurada.
pause
