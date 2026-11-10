#!/bin/bash
# Si hay cambios nuevos en GitHub: copia la base de datos, descarga el código y reconstruye lo que cambió.
# Lo ejecuta el temporizador clinica-actualizar cada 5 minutos. Manual:  sudo bash deploy/actualizar.sh --forzar
set -euo pipefail
cd "$(dirname "$0")/.."
RAMA=$(git rev-parse --abbrev-ref HEAD)
git fetch -q origin "$RAMA"
if [ "$(git rev-parse HEAD)" = "$(git rev-parse "origin/$RAMA")" ] && [ "${1:-}" != "--forzar" ]; then exit 0; fi
echo "== $(date '+%F %T') actualizando a $(git rev-parse --short "origin/$RAMA"): $(git log -1 --format=%s "origin/$RAMA")"
bash deploy/respaldo.sh || echo "aviso: no se pudo hacer la copia previa"
git reset -q --hard "origin/$RAMA"
chown -R "$(getent passwd 1000 | cut -d: -f1 || echo root)": . 2>/dev/null || true
docker compose -f docker-compose.yml -f deploy/docker-compose.nube.yml up -d --build --remove-orphans
docker image prune -f > /dev/null
echo "== $(date '+%F %T') listo"
