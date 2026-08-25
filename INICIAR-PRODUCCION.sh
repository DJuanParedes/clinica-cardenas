#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
ENV_FILE="${1:-.env.production}"
./VALIDAR-PRODUCCION.sh "$ENV_FILE"
echo "Construyendo e iniciando Clínica Cárdenas V4.9.17..."
docker compose --env-file "$ENV_FILE" -f docker-compose.production-secure.yml up --build -d
./ESPERAR-PRODUCCION.sh "$ENV_FILE" 600
docker compose --env-file "$ENV_FILE" -f docker-compose.production-secure.yml ps
echo "Producción iniciada y validada por HTTPS. Solo Caddy publica 80/443."
