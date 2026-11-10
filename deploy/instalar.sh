#!/bin/bash
# Instala (o reinstala) el sistema en el servidor. Ejecutar como root:  sudo bash /opt/clinica/deploy/instalar.sh
set -euo pipefail
export HOME=${HOME:-/root}
cd "$(dirname "$0")/.."
DIR=$(pwd)
COMPOSE="docker compose -f docker-compose.yml -f deploy/docker-compose.nube.yml"
rand() { openssl rand -base64 96 | tr -dc 'A-Za-z0-9' | head -c "${1:-32}"; }

IP=$(curl -fsS --max-time 15 https://api.ipify.org || curl -fsS --max-time 15 https://ifconfig.me)
DOMINIO_AUTO="${IP//./-}.sslip.io"   # dominio gratis que apunta a la IP del servidor, sin registrarse

if [ ! -f .env ]; then
  cat > .env <<ENV
# Generado por deploy/instalar.sh el $(date '+%F %T'). No se sube a GitHub.
DOMINIO=$DOMINIO_AUTO
DB_PASSWORD=$(rand 24)
DB_ROOT_PASSWORD=$(rand 24)
JWT_SECRET=$(rand 64)
# contraseña inicial de los usuarios del personal (cámbiala desde la web)
APP_SEED_PASSWORD=$(rand 12)
# en internet no se muestran los códigos de recuperación en pantalla
APP_DEMO_MODE=false
APP_SEED_DEMO=true
# WhatsApp y correo reales: completar con deploy/configurar-mensajes.sh
DESTINOS_REALES=pacientes
# token para verificar el webhook de WhatsApp en Meta (https://DOMINIO/webhook/whatsapp)
WA_VERIFY_TOKEN=$(rand 24)
ENV
fi
grep -q '^WA_VERIFY_TOKEN=' .env || echo "WA_VERIFY_TOKEN=$(rand 24)" >> .env
grep -q '^DOMINIO=' .env || echo "DOMINIO=$DOMINIO_AUTO" >> .env
ADMIN=$(getent passwd 1000 | cut -d: -f1 || echo root)
chown -R "$ADMIN":"$ADMIN" "$DIR" 2>/dev/null || true
chmod 600 .env
git config --global --add safe.directory "$DIR"

$COMPOSE up -d --build --remove-orphans

# Actualización automática desde GitHub cada 5 minutos y copia de la base de datos todos los días a las 3:00 a. m.
cat > /etc/systemd/system/clinica-actualizar.service <<UNIT
[Unit]
Description=Actualiza la clínica desde GitHub
After=docker.service
[Service]
Type=oneshot
ExecStart=/bin/bash $DIR/deploy/actualizar.sh
StandardOutput=append:/var/log/clinica-actualizar.log
StandardError=append:/var/log/clinica-actualizar.log
UNIT
cat > /etc/systemd/system/clinica-actualizar.timer <<UNIT
[Unit]
Description=Revisa GitHub cada 5 minutos
[Timer]
OnBootSec=3min
OnUnitActiveSec=5min
[Install]
WantedBy=timers.target
UNIT
cat > /etc/systemd/system/clinica-respaldo.service <<UNIT
[Unit]
Description=Copia de seguridad de la base de datos de la clínica
[Service]
Type=oneshot
ExecStart=/bin/bash $DIR/deploy/respaldo.sh
UNIT
cat > /etc/systemd/system/clinica-respaldo.timer <<UNIT
[Unit]
Description=Copia diaria de la base de datos
[Timer]
OnCalendar=*-*-* 03:00:00
Persistent=true
[Install]
WantedBy=timers.target
UNIT
timedatectl set-timezone America/Lima || true
systemctl daemon-reload
systemctl enable --now clinica-actualizar.timer clinica-respaldo.timer

DOM=$(grep '^DOMINIO=' .env | cut -d= -f2)
CLAVE=$(grep '^APP_SEED_PASSWORD=' .env | cut -d= -f2)
cat > "$DIR/ACCESOS.txt" <<TXT
Clínica Dental Cárdenas en la nube
==================================
Web de la clínica:   https://$DOM
Servidor para la app móvil (pantalla "Servidor"):  https://$DOM

Usuarios del personal: admin, recepcion, dra.cardenas, dr.ramos, caja, director
Contraseña inicial de todos:  $CLAVE
(Cámbiala desde la web: Usuarios → editar, o la opción de cambiar contraseña.)

Paciente de prueba (app): DNI 45872196, contraseña Paciente2026

Comandos útiles (en el servidor):
  sudo bash $DIR/deploy/actualizar.sh --forzar     actualizar ya desde GitHub
  sudo bash $DIR/deploy/configurar-mensajes.sh     poner token de WhatsApp y clave de Gmail
  sudo bash $DIR/deploy/respaldo.sh                copia de la base de datos ahora
  tail -f /var/log/clinica-actualizar.log          ver las actualizaciones
TXT
chown "$ADMIN":"$ADMIN" "$DIR/ACCESOS.txt"; chmod 600 "$DIR/ACCESOS.txt"
echo "LISTO: https://$DOM  (accesos en $DIR/ACCESOS.txt)"
