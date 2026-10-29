#!/usr/bin/env bash
# Backup of the production stack (D19.2, runbook §7): PostgreSQL custom-format dump, row counts, uploads archive and
# SHA-256 checksums in BACKUP_DIR (default server/deploy/backups), then removal of own backups older than KEEP_DAYS.
#
#   backup.sh                       run from cron, e.g. "15 2 * * * /opt/foodsharing/server/deploy/backup.sh"
#
# Uses the compose project of server/deploy (override with the standard COMPOSE_PROJECT_NAME / COMPOSE_FILE /
# COMPOSE_ENV_FILES variables). Copy BACKUP_DIR off the host afterwards (runbook §7.3).
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-${DEPLOY_DIR}/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
DB_USER="${DB_USER:-foodsharing}"
DB_NAME="${DB_NAME:-foodsharing}"

cd "${DEPLOY_DIR}"
mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"
umask 077

sha256() { if command -v sha256sum > /dev/null; then sha256sum "$@"; else shasum -a 256 "$@"; fi; }

NAME="foodsharing-$(date -u +%Y%m%dT%H%M%SZ)"
DUMP="${BACKUP_DIR}/${NAME}.dump"

echo "==> Dumping ${DB_NAME} to ${DUMP}"
docker compose exec -T postgres pg_dump -U "${DB_USER}" -d "${DB_NAME}" --format=custom --no-owner --no-privileges \
  > "${DUMP}.partial"
mv "${DUMP}.partial" "${DUMP}"

docker compose exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" -At -v ON_ERROR_STOP=1 \
  < "${DEPLOY_DIR}/lib/table-counts.sql" > "${BACKUP_DIR}/${NAME}.counts"

echo "==> Archiving uploads"
docker compose exec -T app tar -C /data -czf - uploads > "${BACKUP_DIR}/${NAME}.uploads.tar.gz"

(cd "${BACKUP_DIR}" && sha256 "${NAME}.dump" "${NAME}.counts" "${NAME}.uploads.tar.gz" > "${NAME}.sha256")

TABLES="$(wc -l < "${BACKUP_DIR}/${NAME}.counts" | tr -d ' ')"
ROWS="$(awk -F'|' '{s += $2} END {print s + 0}' "${BACKUP_DIR}/${NAME}.counts")"
SIZE="$(du -h "${DUMP}" | cut -f1)"
echo "OK: ${NAME} (${SIZE} dump, ${TABLES} tables, ${ROWS} rows)"

find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'foodsharing-*' -mtime "+${KEEP_DAYS}" -print -delete
