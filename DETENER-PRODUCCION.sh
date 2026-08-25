#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
ENV_FILE="${1:-.env.production}"
docker compose --env-file "$ENV_FILE" -f docker-compose.production-secure.yml down
