#!/bin/sh
set -eu

escape_json() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

CLINIC_ID=$(escape_json "${CLINIC_ID:-CARDENAS}")
CLINIC_NAME=$(escape_json "${CLINIC_NAME:-Clínica Cárdenas}")
CLINIC_SHORT_NAME=$(escape_json "${CLINIC_SHORT_NAME:-Cárdenas}")
CLINIC_TAGLINE=$(escape_json "${CLINIC_TAGLINE:-Atención odontológica en La Floresta}")
CLINIC_DESCRIPTION=$(escape_json "${CLINIC_DESCRIPTION:-Atención odontológica para pacientes de La Floresta y zonas cercanas.}")
CLINIC_LOGO_URL=$(escape_json "${CLINIC_LOGO_URL:-/brand/logo-cardenas.svg}")
CLINIC_HERO_IMAGE_URL=$(escape_json "${CLINIC_HERO_IMAGE_URL:-/brand/cardenas-fachada.png}")
CLINIC_PRIMARY_COLOR=$(escape_json "${CLINIC_PRIMARY_COLOR:-#3B78A6}")
CLINIC_PRIMARY_DARK_COLOR=$(escape_json "${CLINIC_PRIMARY_DARK_COLOR:-#245C80}")
CLINIC_ACCENT_COLOR=$(escape_json "${CLINIC_ACCENT_COLOR:-#D06B4F}")
CLINIC_ACCENT_DARK_COLOR=$(escape_json "${CLINIC_ACCENT_DARK_COLOR:-#B8533C}")
CLINIC_SIDEBAR_DARK_COLOR=$(escape_json "${CLINIC_SIDEBAR_DARK_COLOR:-#193D52}")
CLINIC_PHONE=$(escape_json "${CLINIC_PHONE:-}")
CLINIC_EMAIL=$(escape_json "${CLINIC_EMAIL:-}")
CLINIC_ADDRESS=$(escape_json "${CLINIC_ADDRESS:-La Floresta, Lima, Perú}")
CLINIC_WORKING_HOURS=$(escape_json "${CLINIC_WORKING_HOURS:-Atención previa coordinación}")

export CLINIC_ID CLINIC_NAME CLINIC_SHORT_NAME CLINIC_TAGLINE CLINIC_DESCRIPTION
export CLINIC_LOGO_URL CLINIC_HERO_IMAGE_URL CLINIC_PRIMARY_COLOR CLINIC_PRIMARY_DARK_COLOR
export CLINIC_ACCENT_COLOR CLINIC_ACCENT_DARK_COLOR CLINIC_SIDEBAR_DARK_COLOR
export CLINIC_PHONE CLINIC_EMAIL CLINIC_ADDRESS CLINIC_WORKING_HOURS

envsubst '${CLINIC_ID} ${CLINIC_NAME} ${CLINIC_SHORT_NAME} ${CLINIC_TAGLINE} ${CLINIC_DESCRIPTION} ${CLINIC_LOGO_URL} ${CLINIC_HERO_IMAGE_URL} ${CLINIC_PRIMARY_COLOR} ${CLINIC_PRIMARY_DARK_COLOR} ${CLINIC_ACCENT_COLOR} ${CLINIC_ACCENT_DARK_COLOR} ${CLINIC_SIDEBAR_DARK_COLOR} ${CLINIC_PHONE} ${CLINIC_EMAIL} ${CLINIC_ADDRESS} ${CLINIC_WORKING_HOURS}' \
  < /etc/clinic-config/clinic-config.template.js \
  > /usr/share/nginx/html/clinic-config.js.tmp
mv /usr/share/nginx/html/clinic-config.js.tmp /usr/share/nginx/html/clinic-config.js
