@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0PREPARAR-ENTORNO-LOCAL.ps1"
exit /b %ERRORLEVEL%
