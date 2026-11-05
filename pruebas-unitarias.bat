@echo off
title Clinica Cardenas - pruebas unitarias
cd /d "%~dp0"
echo Ejecutando pruebas unitarias del backend (JUnit)...
docker run --rm -v "%~dp0backend":/src -v clinica-m2:/root/.m2 -w /src maven:3.9.9-eclipse-temurin-21-alpine mvn -B test > pruebas-unitarias.log 2>&1
echo FIN>> pruebas-unitarias.log
