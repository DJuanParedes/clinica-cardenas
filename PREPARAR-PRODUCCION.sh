#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
TARGET=".env.production"
[[ ! -f "$TARGET" ]] || { echo "Ya existe $TARGET" >&2; exit 1; }
random_hex(){ od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'; }
need(){ local prompt="$1" value=""; while [[ -z "$value" ]]; do read -rp "$prompt" value; done; printf '%s' "$value"; }
need_secret(){ local prompt="$1" value=""; while [[ -z "$value" ]]; do read -rsp "$prompt" value; echo >&2; done; printf '%s' "$value"; }

echo "CLINICA CARDENAS V4.9.17 - PREPARAR PRODUCCION MYSQL"
domain="$(need 'Dominio (ej. clinica.midominio.pe): ')"
[[ "$domain" =~ ^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || { echo "Dominio público inválido" >&2; exit 1; }
admin_name="$(need 'Nombre del administrador principal: ')"
admin_email="$(need 'Correo real del administrador: ')"
[[ "$admin_email" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || { echo "Correo inválido" >&2; exit 1; }
admin_phone="$(need 'Teléfono administrador (+51...): ')"
admin_phone="$(printf '%s' "$admin_phone" | tr -d ' -')"
[[ "$admin_phone" =~ ^9[0-9]{8}$ ]] && admin_phone="+51$admin_phone"
[[ "$admin_phone" =~ ^\+[1-9][0-9]{7,14}$ ]] || { echo "Teléfono inválido; usa formato +51987654321" >&2; exit 1; }

read -rp "Canal de recuperación EMAIL/SMS/WHATSAPP [EMAIL]: " admin_channel
admin_channel="${admin_channel:-EMAIL}"
admin_channel="${admin_channel^^}"
[[ "$admin_channel" == EMAIL || "$admin_channel" == SMS || "$admin_channel" == WHATSAPP ]] || { echo "Canal inválido" >&2; exit 1; }

provider="twilio"
mail_host=""; mail_port=""; mail_username=""; mail_password=""; mail_from=""; mail_ttl=""
verify_sid=""; twilio_key=""; twilio_secret=""
if [[ "$admin_channel" == EMAIL ]]; then
  provider="email"
  read -rp "Servidor SMTP [smtp.gmail.com]: " mail_host; mail_host="${mail_host:-smtp.gmail.com}"
  read -rp "Puerto SMTP [587]: " mail_port; mail_port="${mail_port:-587}"
  read -rp "Usuario SMTP [$admin_email]: " mail_username; mail_username="${mail_username:-$admin_email}"
  mail_password="$(need_secret 'Contraseña de aplicación SMTP: ')"
  mail_password="${mail_password// /}"
  read -rp "Correo remitente [$admin_email]: " mail_from; mail_from="${mail_from:-$admin_email}"
  mail_ttl="10"
else
  verify_sid="$(need 'Twilio Verify Service SID (VA...): ')"
  twilio_key="$(need 'Twilio API Key (SK...): ')"
  twilio_secret="$(need_secret 'Twilio API Secret: ')"
fi

jwt="$(random_hex 64)"
mysql_root="Root_$(random_hex 24)"
auth="Db_$(random_hex 24)"; patient="Db_$(random_hex 24)"; appointment="Db_$(random_hex 24)"
clinical="Db_$(random_hex 24)"; finance="Db_$(random_hex 24)"; lab="Db_$(random_hex 24)"
operations="Db_$(random_hex 24)"; content="Db_$(random_hex 24)"; inventory="Db_$(random_hex 24)"
encounter="Db_$(random_hex 24)"; survey="Db_$(random_hex 24)"

cat > "$TARGET" <<EOF
DOMAIN=$domain
JWT_SECRET=$jwt
BOOTSTRAP_ADMIN_NAME=$admin_name
BOOTSTRAP_ADMIN_EMAIL=$admin_email
BOOTSTRAP_ADMIN_PHONE=$admin_phone
BOOTSTRAP_ADMIN_RECOVERY_CHANNEL=$admin_channel
OTP_PROVIDER=$provider
MAIL_HOST=$mail_host
MAIL_PORT=$mail_port
MAIL_USERNAME=$mail_username
MAIL_PASSWORD=$mail_password
MAIL_FROM=$mail_from
MAIL_OTP_TTL_MINUTES=$mail_ttl
TWILIO_VERIFY_SERVICE_SID=$verify_sid
TWILIO_API_KEY=$twilio_key
TWILIO_API_SECRET=$twilio_secret
MYSQL_ROOT_PASSWORD=$mysql_root
AUTH_DB_PASSWORD=$auth
PATIENT_DB_PASSWORD=$patient
APPOINTMENT_DB_PASSWORD=$appointment
CLINICAL_DB_PASSWORD=$clinical
FINANCE_DB_PASSWORD=$finance
LAB_DB_PASSWORD=$lab
OPERATIONS_DB_PASSWORD=$operations
CONTENT_DB_PASSWORD=$content
INVENTORY_DB_PASSWORD=$inventory
ENCOUNTER_DB_PASSWORD=$encounter
SURVEY_DB_PASSWORD=$survey
EOF
chmod 600 "$TARGET"
unset mail_password twilio_secret
echo "OK - $TARGET creado para MySQL con recuperación por $admin_channel."