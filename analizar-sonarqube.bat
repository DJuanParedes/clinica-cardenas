@echo off
chcp 65001 >nul
title Clinica Cardenas - analisis de calidad con SonarQube
cd /d "%~dp0"
echo Analisis de calidad del codigo con SonarQube (en Docker). Puede tardar 10-20 minutos la primera vez.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sonar\analizar.ps1" > sonar\analisis.log 2>&1
if errorlevel 1 (echo FALLO>> sonar\analisis.log & type sonar\analisis.log & echo. & echo FALLO: revisa sonar\analisis.log & pause & exit /b 1)
type sonar\resumen.txt
echo LISTO>> sonar\analisis.log
start http://localhost:9000
timeout /t 20
