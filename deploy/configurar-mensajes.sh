#!/bin/bash
# Pide el token de WhatsApp y la contraseña de aplicación de Gmail y los guarda en el .env del servidor.
# Las claves se escriben sin mostrarse en pantalla. Uso:  sudo bash /opt/clinica/deploy/configurar-mensajes.sh
set -euo pipefail
cd "$(dirname "$0")/.."
ENVF=.env
poner() { # poner CLAVE VALOR
  if grep -q "^$1=" "$ENVF"; then sed -i "s|^$1=.*|$1=$(printf '%s' "$2" | sed 's/[|&]/\\&/g')|" "$ENVF"; else echo "$1=$2" >> "$ENVF"; fi
}
actual() { grep "^$1=" "$ENVF" | cut -d= -f2- || true; }
preguntar() { # preguntar CLAVE "texto"  (muestra el valor actual)
  local v; read -r -p "$2 [$(actual "$1")]: " v; [ -n "$v" ] && poner "$1" "$v" || true
}
secreto() { # secreto CLAVE "texto"
  local v; read -r -s -p "$2 (Enter para dejar la actual): " v; echo; [ -n "$v" ] && poner "$1" "$(printf '%s' "$v" | tr -d ' ')" || true
}
echo "== WhatsApp (Meta) =="
secreto WA_TOKEN "Pega el token permanente de WhatsApp"
preguntar WA_PHONE_NUMBER_ID "Phone Number ID"
preguntar WA_WABA_ID "Identificador de la cuenta de WhatsApp (WABA)"
preguntar WA_PLANTILLA "Plantilla aprobada"
preguntar WA_NUMEROS_PERMITIDOS "Celulares de prueba permitidos"
echo "== Correo (Gmail) =="
preguntar SMTP_USUARIO "Tu Gmail"
secreto SMTP_CLAVE "Pega la contraseña de aplicación de Gmail"
[ -n "$(actual SMTP_HOST)" ] || poner SMTP_HOST smtp.gmail.com
[ -n "$(actual SMTP_PORT)" ] || poner SMTP_PORT 587
[ -n "$(actual SMTP_REMITENTE)" ] || poner SMTP_REMITENTE "$(actual SMTP_USUARIO)"
[ -n "$(actual WA_PLANTILLA_IDIOMA)" ] || poner WA_PLANTILLA_IDIOMA es
chmod 600 "$ENVF"
docker compose -f docker-compose.yml -f deploy/docker-compose.nube.yml up -d notificaciones
echo "Listo. WhatsApp y correo actualizados."
