@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CONFIGURAR-CLINICA.ps1"
if errorlevel 1 (
  echo.
  echo No se pudo guardar la configuracion de la clinica.
  pause
  exit /b 1
)
echo.
echo Configuracion terminada.
pause
