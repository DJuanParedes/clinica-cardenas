@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ======================================================
echo   CLINICA CARDENAS V4.9.17 - ESTADO
echo ======================================================
docker compose ps -a
echo.
echo Portal:  http://localhost:8080
echo Gateway: http://localhost:8090/actuator/health
echo Eureka:  http://localhost:8761
echo MySQL:   localhost:3307
pause
