@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Deteniendo Clinica Cardenas sin borrar datos ni imagenes...
docker compose stop
if errorlevel 1 (
  echo ERROR: no se pudo detener. Verifica Docker Desktop.
  pause
  exit /b 1
)
echo Sistema detenido. Para volver a usarlo ejecuta 02-INICIAR.bat
pause
