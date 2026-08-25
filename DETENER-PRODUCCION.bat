@echo off
setlocal
cd /d "%~dp0"
if not exist .env.production (echo ERROR: falta .env.production& pause& exit /b 1)
docker compose --env-file .env.production -f docker-compose.production-secure.yml down
echo Produccion detenida. Los volumenes persistentes no se eliminan.
pause
endlocal
