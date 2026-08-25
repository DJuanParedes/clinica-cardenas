#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
ENV_FILE="${1:-.env.production}"
fail(){ echo "PRODUCCION: $*" >&2; exit 1; }
getv(){ grep -m1 -E "^$1=" "$ENV_FILE" | cut -d= -f2- || true; }
require_values(){ local name; for name in "$@"; do [[ -n "$(getv "$name")" ]] || fail "Falta $name"; done; }

[[ -f "$ENV_FILE" ]] || fail "No existe $ENV_FILE"
require_values DOMAIN JWT_SECRET BOOTSTRAP_ADMIN_NAME BOOTSTRAP_ADMIN_EMAIL BOOTSTRAP_ADMIN_PHONE \
  BOOTSTRAP_ADMIN_RECOVERY_CHANNEL OTP_PROVIDER MYSQL_ROOT_PASSWORD AUTH_DB_PASSWORD \
  PATIENT_DB_PASSWORD APPOINTMENT_DB_PASSWORD CLINICAL_DB_PASSWORD FINANCE_DB_PASSWORD \
  LAB_DB_PASSWORD OPERATIONS_DB_PASSWORD CONTENT_DB_PASSWORD INVENTORY_DB_PASSWORD \
  ENCOUNTER_DB_PASSWORD SURVEY_DB_PASSWORD
[[ "$(getv DOMAIN)" =~ ^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || fail "DOMAIN inválido"
[[ "$(getv BOOTSTRAP_ADMIN_EMAIL)" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || fail "Correo administrador inválido"
[[ "$(getv BOOTSTRAP_ADMIN_PHONE)" =~ ^\+[1-9][0-9]{7,14}$ ]] || fail "Teléfono administrador inválido"
[[ "$(getv JWT_SECRET)" =~ ^.{64,}$ ]] || fail "JWT_SECRET demasiado corto"

channel="$(getv BOOTSTRAP_ADMIN_RECOVERY_CHANNEL)"; channel="${channel^^}"
provider="$(getv OTP_PROVIDER)"; provider="${provider,,}"
case "$channel" in
  EMAIL)
    [[ "$provider" == email ]] || fail "EMAIL requiere OTP_PROVIDER=email"
    require_values MAIL_HOST MAIL_PORT MAIL_USERNAME MAIL_PASSWORD MAIL_FROM MAIL_OTP_TTL_MINUTES
    [[ "$(getv MAIL_PORT)" =~ ^[0-9]{2,5}$ ]] || fail "MAIL_PORT inválido"
    [[ "$(getv MAIL_FROM)" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || fail "MAIL_FROM inválido"
    ttl="$(getv MAIL_OTP_TTL_MINUTES)"; (( ttl >= 2 && ttl <= 30 )) || fail "MAIL_OTP_TTL_MINUTES fuera de rango"
    ;;
  SMS|WHATSAPP)
    [[ "$provider" == twilio ]] || fail "$channel requiere OTP_PROVIDER=twilio"
    require_values TWILIO_VERIFY_SERVICE_SID TWILIO_API_KEY TWILIO_API_SECRET
    [[ "$(getv TWILIO_VERIFY_SERVICE_SID)" =~ ^VA[A-Za-z0-9]{20,}$ ]] || fail "Twilio Verify SID inválido"
    [[ "$(getv TWILIO_API_KEY)" =~ ^SK[A-Za-z0-9]{20,}$ ]] || fail "Twilio API Key inválida"
    ;;
  *) fail "Canal de recuperación inválido" ;;
esac

for name in MYSQL_ROOT_PASSWORD AUTH_DB_PASSWORD PATIENT_DB_PASSWORD APPOINTMENT_DB_PASSWORD \
  CLINICAL_DB_PASSWORD FINANCE_DB_PASSWORD LAB_DB_PASSWORD OPERATIONS_DB_PASSWORD \
  CONTENT_DB_PASSWORD INVENTORY_DB_PASSWORD ENCOUNTER_DB_PASSWORD SURVEY_DB_PASSWORD; do
  value="$(getv "$name")"
  [[ "$value" =~ ^[A-Za-z0-9_#@.-]{12,100}$ ]] || fail "$name no cumple formato seguro"
done
grep -q 'image: mysql:8.4' docker-compose.production-secure.yml || fail "Producción no usa mysql:8.4"
grep -q 'CREATE DATABASE IF NOT EXISTS' database/mysql/init-databases.sh || fail "Inicialización MySQL incompleta"
docker info >/dev/null 2>&1 || fail "Docker no disponible"
docker compose --env-file "$ENV_FILE" -f docker-compose.production-secure.yml config --quiet || fail "docker compose config falló"
echo "OK - Preflight producción MySQL aprobado con recuperación por $channel"