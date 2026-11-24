#!/bin/bash
# Copia el proyecto (montado en /src como solo lectura), compila el APK y lo deja en /salida.
set -o pipefail
LOG=/salida/compilacion.log
mkdir -p /salida
: > "$LOG"
echo "== Compilando app Clínica Cárdenas ($(date)) ==" | tee -a "$LOG"
rsync -a --delete --exclude build --exclude .gradle --exclude .kotlin /src/ /work/ 2>&1 | tee -a "$LOG"
cd /work
echo "sdk.dir=$ANDROID_HOME" > local.properties
# Gradle del sistema (8.10.2) para no descargar el wrapper otra vez
gradle --no-daemon --console=plain :composeApp:assembleDebug 2>&1 | tee -a "$LOG"
RC=${PIPESTATUS[0]}
if [ $RC -ne 0 ]; then
  echo "== ERROR: la compilación falló (código $RC) ==" | tee -a "$LOG"
  echo FALLO > /salida/estado.txt
  exit $RC
fi
cp composeApp/build/outputs/apk/debug/composeApp-debug.apk /salida/ClinicaCardenas.apk
ls -la /salida | tee -a "$LOG"
echo OK > /salida/estado.txt
echo "== Listo: apk/ClinicaCardenas.apk ==" | tee -a "$LOG"
