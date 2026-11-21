#!/bin/bash
# Copia de seguridad diaria de la clínica (la ejecuta clinica-respaldo.timer a las 03:00 y actualizar.sh).
#   1. Base de datos  -> /opt/clinica-respaldos/clinica-AAAA-MM-DD-HHMM.sql.gz
#   2. Archivos (fotos, PDF firmados, adjuntos) -> /opt/clinica-respaldos/archivos-AAAA-MM-DD-HHMM.tar.gz
#   3. Copia fuera del servidor en Azure Storage, si /etc/clinica-respaldo.conf define RESPALDO_BLOB.
#      Usa la identidad administrada de la VM: no hay claves ni tokens guardados en el servidor.
#   4. Prueba de restauración: los domingos (o con --probar) carga la copia en un MySQL temporal
#      y compara la cantidad de pacientes, citas y comprobantes con la base real.
# Se guardan las últimas 14 copias de cada tipo en el servidor.
#
# Uso: respaldo.sh [--probar] [--sin-nube]
set -euo pipefail
cd "$(dirname "$0")/.."
D=/opt/clinica-respaldos
CONF=/etc/clinica-respaldo.conf
mkdir -p "$D"; chmod 700 "$D"
SELLO=$(date +%F-%H%M)
PROBAR=0; NUBE=1
for a in "$@"; do
  case "$a" in --probar) PROBAR=1 ;; --sin-nube) NUBE=0 ;; esac
done
[ "$(date +%u)" = "7" ] && PROBAR=1
RESPALDO_BLOB=""
[ -f "$CONF" ] && . "$CONF"
DC="docker compose -f docker-compose.yml -f deploy/docker-compose.nube.yml"

# ---------------------------------------------------------------- 1. base de datos
F="$D/clinica-$SELLO.sql.gz"
$DC exec -T db sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers clinica 2>/dev/null' | gzip > "$F"
gzip -t "$F"
echo "copia: $F ($(du -h "$F" | cut -f1))"

# ---------------------------------------------------------------- 2. archivos
VOL=$(docker volume ls -q | grep -E '_files-data$' | head -1 || true)
A=""
if [ -n "$VOL" ]; then
  ORIGEN=$(docker volume inspect -f '{{ .Mountpoint }}' "$VOL")
  A="$D/archivos-$SELLO.tar.gz"
  tar czf "$A" -C "$ORIGEN" .
  echo "archivos: $A ($(du -h "$A" | cut -f1), $(find "$ORIGEN" -type f | wc -l) archivos)"
else
  echo "aviso: no se encontró el volumen de archivos" >&2
fi

ls -1t "$D"/clinica-*.sql.gz 2>/dev/null | tail -n +15 | xargs -r rm -f
ls -1t "$D"/archivos-*.tar.gz 2>/dev/null | tail -n +15 | xargs -r rm -f

# ---------------------------------------------------------------- 3. copia fuera del servidor
subir() { # $1 archivo local; usa el token de la identidad administrada de la VM
  local nombre token
  nombre="$(hostname)/$(basename "$1")"
  token=$(curl -fsS -H Metadata:true \
    "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https%3A%2F%2Fstorage.azure.com%2F" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
  [ -n "$token" ] || { echo "nube: la VM no tiene identidad administrada" >&2; return 1; }
  curl -fsS -X PUT -T "$1" \
    -H "Authorization: Bearer $token" -H "x-ms-version: 2021-08-06" -H "x-ms-blob-type: BlockBlob" \
    "$RESPALDO_BLOB/$nombre" >/dev/null
  echo "nube: $RESPALDO_BLOB/$nombre"
}
if [ "$NUBE" = 1 ] && [ -n "$RESPALDO_BLOB" ]; then
  ERR=0
  subir "$F" || ERR=1
  [ -n "$A" ] && { subir "$A" || ERR=1; }
  [ "$ERR" = 0 ] || echo "AVISO: la copia en la nube falló; la copia local sí quedó" >&2
fi

# ---------------------------------------------------------------- 4. prueba de restauración
if [ "$PROBAR" = 1 ]; then
  P=clinica-prueba-restauracion
  docker rm -f "$P" >/dev/null 2>&1 || true
  IMG=$($DC images db -q 2>/dev/null | head -1); IMG=${IMG:-mysql:8.4}
  docker run -d --rm --name "$P" -e MYSQL_ROOT_PASSWORD=prueba -e MYSQL_DATABASE=clinica \
    --tmpfs /var/lib/mysql:size=1g --memory 512m "$IMG" \
    --innodb-buffer-pool-size=32M --performance-schema=OFF --skip-log-bin >/dev/null
  trap 'docker rm -f '"$P"' >/dev/null 2>&1 || true' EXIT
  for _ in $(seq 1 60); do
    # el primer arranque levanta un servidor temporal (sin puerto); esperamos al definitivo en el 3306
    docker logs "$P" 2>&1 | grep -q 'port: 3306' && docker exec "$P" mysql -uroot -pprueba -e 'select 1' clinica >/dev/null 2>&1 && break; sleep 2
  done
  gunzip -c "$F" | docker exec -i "$P" mysql -uroot -pprueba clinica 2>/dev/null
  Q="select concat_ws(' ', (select count(*) from information_schema.tables where table_schema='clinica'),
     (select count(*) from patients), (select count(*) from appointments), (select count(*) from invoices))"
  COPIA=$(docker exec "$P" mysql -uroot -pprueba -N -e "$Q" clinica 2>/dev/null)
  REAL=$($DC exec -T db sh -c "exec mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -e \"$Q\" clinica 2>/dev/null")
  if [ -n "$A" ]; then tar tzf "$A" >/dev/null && T="archivos OK" || T="ARCHIVOS DAÑADOS"; else T="sin archivos"; fi
  echo "prueba de restauración: copia=[tablas pacientes citas comprobantes] $COPIA · real=$REAL · $T"
  # Las cifras pueden diferir si alguien registró algo durante la prueba; lo grave es que la copia esté vacía.
  set -- $COPIA
  if [ "${1:-0}" -lt 30 ]; then echo "ERROR: la copia no se pudo restaurar" >&2; exit 1; fi
  echo "$(date '+%F %T') OK $COPIA | $T" >> "$D/pruebas-restauracion.log"
fi
