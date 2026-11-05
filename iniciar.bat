@echo off
title Clinica Cardenas - arranque
cd /d "%~dp0"
echo Construyendo y levantando Clinica Cardenas (la primera vez tarda varios minutos)...
docker compose up -d --build
if errorlevel 1 (pause & exit /b 1)
start http://localhost:8081
timeout /t 10
