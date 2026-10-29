#!/usr/bin/env bash
# Verifies a backup without touching production (D19.2, runbook §7): checks the SHA-256 sums, restores the dump into
# a throwaway postgres:17 container and compares the row count of every table with the .counts file of the backup.
#
#   verify-backup.sh backups/foodsharing-20261029T021500Z.dump
#
# Exit 0 = restorable and complete; 1 = checksum, restore or table set mismatch. Row-count differences are reported
# (the counts are taken right after the dump, so a busy system may differ by a few rows) and fail only for
# databasechangelog (schema version).
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DUMP="${1:?usage: verify-backup.sh <backup>.dump}"
[[ -f "${DUMP}" ]] || { echo "No such file: ${DUMP}" >&2; exit 1; }
DIR="$(cd "$(dirname "${DUMP}")" && pwd)"
NAME="$(basename "${DUMP}" .dump)"
CONTAINER="foodsharing-verify-$$"
WORK="$(mktemp -d)"

sha256() { if command -v sha256sum > /dev/null; then sha256sum "$@"; else shasum -a 256 "$@"; fi; }

# shellcheck disable=SC2329 # invoked by the EXIT trap
cleanup() {
  docker rm -f "${CONTAINER}" > /dev/null 2>&1 || true
  rm -rf "${WORK}"
}
trap cleanup EXIT

echo "==> Checksums"
(cd "${DIR}" && sha256 -c "${NAME}.sha256")

echo "==> Restoring into a throwaway postgres:17"
docker run -d --rm --name "${CONTAINER}" -e POSTGRES_PASSWORD=verify -e POSTGRES_USER=foodsharing \
  -e POSTGRES_DB=foodsharing postgres:17 > /dev/null
for _ in $(seq 1 60); do
  if docker exec "${CONTAINER}" pg_isready -U foodsharing -d foodsharing -h 127.0.0.1 > /dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec -i "${CONTAINER}" pg_restore -U foodsharing -d foodsharing --no-owner --no-privileges --exit-on-error \
  < "${DUMP}"
docker exec -i "${CONTAINER}" psql -U foodsharing -d foodsharing -At -v ON_ERROR_STOP=1 \
  < "${DEPLOY_DIR}/lib/table-counts.sql" > "${WORK}/restored.counts"

echo "==> Comparing row counts"
STATUS=0
EXPECTED_TABLES="$(cut -d'|' -f1 "${DIR}/${NAME}.counts")"
RESTORED_TABLES="$(cut -d'|' -f1 "${WORK}/restored.counts")"
if [[ "${EXPECTED_TABLES}" != "${RESTORED_TABLES}" ]]; then
  echo "FAIL: table set differs" >&2
  diff <(echo "${EXPECTED_TABLES}") <(echo "${RESTORED_TABLES}") >&2 || true
  STATUS=1
fi
while IFS='|' read -r table expected; do
  actual="$(grep "^${table}|" "${WORK}/restored.counts" | cut -d'|' -f2 || true)"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "diff: ${table} backup=${expected} restored=${actual:-missing}"
    [[ "${table}" == "databasechangelog" ]] && STATUS=1
  fi
done < "${DIR}/${NAME}.counts"

if [[ -f "${DIR}/${NAME}.uploads.tar.gz" ]]; then
  FILES="$(tar -tzf "${DIR}/${NAME}.uploads.tar.gz" | grep -vc '/$' || true)"
  echo "uploads archive readable (${FILES} files)"
fi

TABLES="$(wc -l < "${WORK}/restored.counts" | tr -d ' ')"
ROWS="$(awk -F'|' '{s += $2} END {print s + 0}' "${WORK}/restored.counts")"
CHANGESETS="$(grep '^databasechangelog|' "${WORK}/restored.counts" | cut -d'|' -f2)"
if [[ ${STATUS} -eq 0 ]]; then
  echo "VERIFIED: ${NAME} restores (${TABLES} tables, ${ROWS} rows, ${CHANGESETS} changesets)"
else
  echo "NOT VERIFIED: ${NAME}" >&2
fi
exit "${STATUS}"
