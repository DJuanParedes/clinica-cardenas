#!/bin/bash
# Copia de la base de datos en /opt/clinica-respaldos (se guardan las últimas 14).
set -euo pipefail
cd "$(dirname "$0")/.."
D=/opt/clinica-respaldos
mkdir -p "$D"; chmod 700 "$D"
F="$D/clinica-$(date +%F-%H%M).sql.gz"
docker compose -f docker-compose.yml -f deploy/docker-compose.nube.yml exec -T db \
  sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers clinica' | gzip > "$F"
ls -1t "$D"/clinica-*.sql.gz | tail -n +15 | xargs -r rm -f
echo "copia: $F ($(du -h "$F" | cut -f1))"
