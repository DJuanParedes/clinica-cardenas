@echo off
setlocal
cd /d "%~dp0"
cls
echo ======================================================
echo   CLINICA CARDENAS V4.9.17 - PREPARAR PRODUCCION
echo ======================================================
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0PREPARAR-PRODUCCION.ps1"
if errorlevel 1 (
  echo.
  echo ERROR: no se pudo preparar .env.production.
  pause
  exit /b 1
)
echo.
echo Archivo .env.production creado. No lo compartas ni lo subas a Git.
pause
endlocal
