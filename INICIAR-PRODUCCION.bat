@echo off
setlocal
cd /d "%~dp0"
cls
echo ======================================================
echo   CLINICA CARDENAS V4.9.17 - PRODUCCION SEGURA
echo ======================================================
echo.
if not exist .env.production (
  echo ERROR: falta .env.production
  echo Copia .env.production.example como .env.production y configura dominio, secretos y Twilio.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0VALIDAR-PRODUCCION.ps1" -EnvFile ".env.production"
if errorlevel 1 (
  echo.
  echo ERROR: la validacion de produccion no fue aprobada.
  pause
  exit /b 1
)
echo.
echo Construyendo e iniciando produccion...
docker compose --env-file .env.production -f docker-compose.production-secure.yml up --build -d
if errorlevel 1 (
  echo ERROR: fallo el despliegue de produccion.
  docker compose --env-file .env.production -f docker-compose.production-secure.yml logs --tail 150
  pause
  exit /b 1
)
echo.
echo Esperando servicios, Gateway interno y HTTPS publico...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ESPERAR-PRODUCCION.ps1" -EnvFile ".env.production" -TimeoutSeconds 600
if errorlevel 1 (
  echo.
  echo ERROR: los contenedores iniciaron, pero la validacion final de produccion no fue aprobada.
  docker compose --env-file .env.production -f docker-compose.production-secure.yml logs --tail 150
  pause
  exit /b 1
)
echo.
docker compose --env-file .env.production -f docker-compose.production-secure.yml ps
echo.
echo PRODUCCION INICIADA Y VALIDADA.
echo HTTPS publico responde y solo Caddy publica 80/443.
echo MySQL no publica 3306 al host; Gateway, Eureka y los microservicios tampoco publican puertos internos.
echo.
pause
endlocal
