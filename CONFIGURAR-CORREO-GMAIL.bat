@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0CONFIGURAR-CORREO-GMAIL.ps1"
if errorlevel 1 (
  echo.
  echo No se pudo completar la configuracion.
  pause
)
