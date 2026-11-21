@echo off
chcp 65001 >nul
title Compilando APK - Clinica Cardenas
cd /d "%~dp0"
if not exist apk mkdir apk
ipconfig > apk\ip-de-esta-pc.txt
echo.
echo  Compilando la app movil (Kotlin Multiplatform) con Docker...
echo  La primera vez descarga el SDK de Android y las librerias: puede tardar 10-20 minutos.
echo.
docker build -t clinica-apk-builder movil\docker > apk\docker-build.log 2>&1
if errorlevel 1 (
  echo  ERROR al preparar la imagen. Revisa apk\docker-build.log
  echo FALLO-IMAGEN > apk\estado.txt
  pause
  exit /b 1
)
docker run --rm -v "%cd%\movil:/src:ro" -v "%cd%\apk:/salida" -v clinica-gradle-cache:/home/gradle/.gradle clinica-apk-builder
if errorlevel 1 (
  echo  ERROR en la compilacion. Revisa apk\compilacion.log
  pause
  exit /b 1
)
echo.
echo  Listo: apk\ClinicaCardenas.apk
echo  Copialo a tu celular Android o arrastralo al emulador de Android Studio.
timeout /t 15
