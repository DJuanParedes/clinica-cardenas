@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
echo ADVERTENCIA: se eliminaran MySQL y documentos del entorno local de Clinica Cardenas.
choice /M "Desea continuar"
if errorlevel 2 exit /b 0
docker compose down -v --remove-orphans
if errorlevel 1 (echo ERROR: no se pudo limpiar & pause & exit /b 1)
echo Datos locales eliminados. La proxima instalacion creara un MySQL nuevo.
endlocal
pause
