#!/bin/bash
# Activa los pagos en línea con Mercado Pago (Checkout Pro: tarjeta, Yape y saldo de Mercado Pago).
# Pide el Access Token sin mostrarlo en pantalla y lo guarda en el .env del servidor. Uso:
#   sudo bash /opt/clinica/deploy/configurar-pagos.sh
# Para volver al simulador:  sudo bash /opt/clinica/deploy/configurar-pagos.sh simulado
set -euo pipefail
cd "$(dirname "$0")/.."
ENVF=.env
poner() { # poner CLAVE VALOR
  if grep -q "^$1=" "$ENVF"; then sed -i "s|^$1=.*|$1=$(printf '%s' "$2" | sed 's/[|&]/\\&/g')|" "$ENVF"; else echo "$1=$2" >> "$ENVF"; fi
}
actual() { grep "^$1=" "$ENVF" | cut -d= -f2- || true; }
reiniciar() { docker compose -f docker-compose.yml -f deploy/docker-compose.nube.yml up -d backend; }

if [ "${1:-}" = "simulado" ]; then
  poner PAGOS_MODO simulado; chmod 600 "$ENVF"; reiniciar
  echo "Listo. Los pagos en línea vuelven al simulador (no se cobra dinero)."; exit 0
fi

DOM=$(actual DOMINIO)
echo "== Mercado Pago =="
echo "1) Entra a https://www.mercadopago.com.pe/developers/panel , crea (o abre) tu aplicación y copia el Access Token."
echo "   - Credenciales de PRUEBA: el token empieza con TEST-  (no se cobra dinero real)."
echo "   - Credenciales de PRODUCCIÓN: el token empieza con APP_USR-  (cobros reales)."
read -r -s -p "Pega el Access Token: " TOKEN; echo
TOKEN=$(printf '%s' "$TOKEN" | tr -d ' \r\n')
case "$TOKEN" in
  TEST-*) echo "Token de PRUEBA detectado." ;;
  APP_USR-*) echo "Token de PRODUCCIÓN detectado: los cobros serán reales." ;;
  *) echo "El token debe empezar con TEST- o APP_USR-. No se cambió nada."; exit 1 ;;
esac
# Verifica el token contra la API de Mercado Pago antes de guardarlo.
CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" https://api.mercadopago.com/users/me || true)
if [ "$CODE" != "200" ]; then echo "Mercado Pago rechazó el token (HTTP $CODE). No se cambió nada."; exit 1; fi
read -r -s -p "Clave secreta de las notificaciones (webhook), opcional — Enter para omitir: " SECRETO; echo
poner PAGOS_MODO mercadopago
poner MP_ACCESS_TOKEN "$TOKEN"
[ -n "$SECRETO" ] && poner MP_WEBHOOK_SECRET "$(printf '%s' "$SECRETO" | tr -d ' ')"
chmod 600 "$ENVF"
reiniciar
echo
echo "Listo. Pagos en línea con Mercado Pago activos."
echo "2) En el panel de Mercado Pago (Webhooks) registra esta URL de notificaciones, evento 'Pagos':"
echo "   https://${DOM:-TU_DOMINIO}/api/public/mercadopago/webhook"
echo "3) Comprueba en la web: Caja → cobro en línea (debe decir 'Mercado Pago en modo de pruebas' o 'activo')."
