@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set "AUTO_ARG="
if /I "%~1"=="auto" set "AUTO_ARG=-Auto"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp005-GENERAR-DIAGNOSTICO.ps1" %AUTO_ARG%
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo ERROR: no se pudo generar el diagnostico. Codigo %RC%.
  if /I not "%~1"=="auto" pause
)
exit /b %RC%
