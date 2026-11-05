@echo off
title Clinica Cardenas - actualizar
cd /d "%~dp0"
echo Actualizando la aplicacion (se conservan los datos)...
docker compose up -d --build > arranque.log 2>&1
if errorlevel 1 (echo FALLO>> arranque.log & pause & exit /b 1)
echo LISTO>> arranque.log
docker compose ps >> arranque.log 2>&1
start http://localhost:8081
