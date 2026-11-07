@echo off
title Clinica Cardenas COMPLETO - reinicio limpio
cd /d "%~dp0"
echo Reconstruyendo con base de datos limpia (se borran los datos)...
(docker compose down -v && docker compose up -d --build) > arranque.log 2>&1
if errorlevel 1 (echo FALLO>> arranque.log & pause & exit /b 1)
echo LISTO>> arranque.log
docker compose ps >> arranque.log 2>&1
