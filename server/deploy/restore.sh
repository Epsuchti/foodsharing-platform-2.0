#!/usr/bin/env bash
# Restores a backup into the running compose stack (D19.2, runbook §7). DESTRUCTIVE: replaces the database and the
# uploads. Verify the backup first (verify-backup.sh) and take a fresh backup of the current state if it matters.
#
#   restore.sh backups/foodsharing-20261029T021500Z.dump --yes
#
# Steps: checksums → stop app → drop + recreate the database → pg_restore → uploads (if the archive exists) →
# start app → wait for readiness. Uses the compose project of server/deploy (COMPOSE_* variables as in backup.sh).
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP="${1:?usage: restore.sh <backup>.dump --yes}"
[[ "${2:-}" == "--yes" ]] || { echo "Refusing to restore without --yes (this replaces the database)." >&2; exit 1; }
[[ -f "${DUMP}" ]] || { echo "No such file: ${DUMP}" >&2; exit 1; }
DUMP="$(cd "$(dirname "${DUMP}")" && pwd)/$(basename "${DUMP}")"
DIR="$(dirname "${DUMP}")"
NAME="$(basename "${DUMP}" .dump)"
DB_USER="${DB_USER:-foodsharing}"
DB_NAME="${DB_NAME:-foodsharing}"
UPLOADS="${DIR}/${NAME}.uploads.tar.gz"

sha256() { if command -v sha256sum > /dev/null; then sha256sum "$@"; else shasum -a 256 "$@"; fi; }

cd "${DEPLOY_DIR}"

echo "==> Checksums"
(cd "${DIR}" && sha256 -c "${NAME}.sha256")

echo "==> Stopping app"
docker compose stop app

echo "==> Recreating database ${DB_NAME}"
docker compose exec -T postgres psql -U "${DB_USER}" -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE)" -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER}"
docker compose exec -T postgres pg_restore -U "${DB_USER}" -d "${DB_NAME}" --no-owner --no-privileges \
  --exit-on-error < "${DUMP}"

if [[ -f "${UPLOADS}" ]]; then
  echo "==> Restoring uploads"
  docker compose run --rm --no-deps -T --entrypoint sh app -c 'rm -rf /data/uploads && tar -C /data -xzf -' \
    < "${UPLOADS}"
fi

echo "==> Starting app"
docker compose start app
for _ in $(seq 1 90); do
  if docker compose exec -T app curl -fsS http://127.0.0.1:9090/actuator/health/readiness > /dev/null 2>&1; then
    echo "OK: restored ${NAME}; app ready"
    exit 0
  fi
  sleep 2
done
echo "App not ready within 180 s after the restore: docker compose logs app" >&2
exit 1
