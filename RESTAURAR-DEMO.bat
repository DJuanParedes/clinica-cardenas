@echo off
if "%~1"=="" (
  echo Uso: RESTAURAR-DEMO.bat "ruta-a-la-carpeta-del-respaldo"
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0RESTAURAR-DEMO.ps1" -BackupFolder "%~1"
pause
