#!/usr/bin/env bash
# ER diagram from the Liquibase schema (D15.1): Postgres 17 in Docker, the packaged jar with profile `prod` runs the
# changelogs against it (schema only, no seed), then SchemaSpy renders the HTML/SVG documentation into docs/er/.
#
#   scripts/er-diagram.sh            # needs a jar in server/target (E2E_JAR / ER_JAR to override, ER_BUILD=1 to build)
#   scripts/er-diagram.sh --check    # renders into a temp dir and fails when the committed summary is stale (CI)
#
# Output: docs/er/index.html (+ diagrams). Committed (see .gitignore): docs/er/deletionOrder.txt and
# docs/er/diagrams/summary/ — regenerate with the plain command whenever a changelog changes. --check compares
# deletionOrder.txt and the .dot sources (line-sorted, so a different layout order is no difference); the .svg
# renderings are not compared.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${REPO_DIR}/server"
COMMITTED_DIR="${REPO_DIR}/docs/er"
CHECK=false
case "${1:-}" in
  --check) CHECK=true ;;
  "") ;;
  *) echo "usage: $0 [--check]" >&2; exit 2 ;;
esac
if [[ "${CHECK}" == true ]]; then
  OUT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/er-diagram-check.XXXXXX")"
else
  OUT_DIR="${ER_OUT_DIR:-${COMMITTED_DIR}}"
fi
POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgres:17}"
SCHEMASPY_IMAGE="${SCHEMASPY_IMAGE:-schemaspy/schemaspy:7.0.2}"
SUFFIX="$$"
NETWORK="fs-er-${SUFFIX}"
PG="fs-er-pg-${SUFFIX}"
DB=foodsharing
DB_USER=foodsharing
DB_PASSWORD=foodsharing
APP_PID=""

cleanup() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
  docker rm -f "${PG}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK}" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

JAR="${ER_JAR:-${E2E_JAR:-}}"
if [[ -z "${JAR}" ]]; then
  JAR="$(ls -t "${SERVER_DIR}"/target/foodsharing-platform-*.jar 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "${JAR}" && "${ER_BUILD:-}" =~ ^(1|true)$ ]]; then
  (cd "${SERVER_DIR}" && ./mvnw -q -Pskip-client -DskipTests package)
  JAR="$(ls -t "${SERVER_DIR}"/target/foodsharing-platform-*.jar | head -n 1)"
fi
[[ -n "${JAR}" ]] || { echo "No jar in server/target (build it or set ER_BUILD=1)." >&2; exit 1; }

free_port() {
  node -e 'const s=require("net").createServer();s.listen(0,"127.0.0.1",()=>{console.log(s.address().port);s.close();});'
}
PG_PORT="$(free_port)"
APP_PORT="$(free_port)"
# prod serves actuator on the management port only (decision 81)
MANAGEMENT_PORT="$(free_port)"

docker network create "${NETWORK}" >/dev/null
docker run -d --name "${PG}" --network "${NETWORK}" -p "127.0.0.1:${PG_PORT}:5432" \
  -e POSTGRES_DB="${DB}" -e POSTGRES_USER="${DB_USER}" -e POSTGRES_PASSWORD="${DB_PASSWORD}" \
  "${POSTGRES_IMAGE}" >/dev/null
for _ in $(seq 1 60); do
  docker exec "${PG}" pg_isready -U "${DB_USER}" -d "${DB}" >/dev/null 2>&1 && break
  sleep 1
done

LOG="$(mktemp "${TMPDIR:-/tmp}/er-diagram-app.XXXXXX")"
echo "[er-diagram] running Liquibase via $(basename "${JAR}") (profile prod) — log: ${LOG}"
(
  cd "${SERVER_DIR}"
  SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${PG_PORT}/${DB}" \
  SPRING_DATASOURCE_USERNAME="${DB_USER}" SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}" \
  APP_DOMAIN=localhost APP_BASE_URL="http://localhost:${APP_PORT}" APP_IP_SALT=er-diagram \
  GOOGLE_CLIENT_ID=er-diagram GOOGLE_CLIENT_SECRET=er-diagram \
  APP_SIGNING_SECRET="$(openssl rand -hex 24)" NEWSLETTER_SECRET="$(openssl rand -hex 24)" \
  OAUTH_SIGNING_KEY="$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null | tr -d '\n')" \
  exec java -jar "${JAR}" --spring.profiles.active=prod --server.port="${APP_PORT}" \
    --management.server.port="${MANAGEMENT_PORT}" --app.scheduling.enabled=false
) >"${LOG}" 2>&1 &
APP_PID=$!
ready=false
for _ in $(seq 1 240); do
  if curl -fsS "http://localhost:${MANAGEMENT_PORT}/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then ready=true; break; fi
  kill -0 "${APP_PID}" 2>/dev/null || break
  sleep 0.5
done
if [[ "${ready}" != true ]]; then
  echo "The app did not become ready; last log lines:" >&2
  tail -n 60 "${LOG}" >&2
  exit 1
fi
kill "${APP_PID}" && wait "${APP_PID}" 2>/dev/null || true
APP_PID=""

rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"
echo "[er-diagram] rendering with ${SCHEMASPY_IMAGE} → ${OUT_DIR}"
docker run --rm --network "${NETWORK}" --user "$(id -u):$(id -g)" -v "${OUT_DIR}:/output" "${SCHEMASPY_IMAGE}" \
  -t pgsql11 -host "${PG}" -port 5432 -db "${DB}" -u "${DB_USER}" -p "${DB_PASSWORD}" -s public -noimplied -nopages \
  -vizjs >/dev/null
if [[ "${CHECK}" != true ]]; then
  echo "[er-diagram] done: ${OUT_DIR}/index.html"
  exit 0
fi

stale=false
compare() {
  local committed="$1" fresh="$2"
  if [[ ! -f "${committed}" ]]; then
    echo "[er-diagram] missing: ${committed#"${REPO_DIR}/"}" >&2
    stale=true
  elif ! diff -q <(sort "${committed}") <(sort "${fresh}") >/dev/null; then
    echo "[er-diagram] stale: ${committed#"${REPO_DIR}/"}" >&2
    diff <(sort "${committed}") <(sort "${fresh}") | head -n 20 >&2 || true
    stale=true
  fi
}
compare "${COMMITTED_DIR}/deletionOrder.txt" "${OUT_DIR}/deletionOrder.txt"
for dot in "${OUT_DIR}"/diagrams/summary/*.dot; do
  compare "${COMMITTED_DIR}/diagrams/summary/$(basename "${dot}")" "${dot}"
done
rm -rf "${OUT_DIR}"
if [[ "${stale}" == true ]]; then
  echo "[er-diagram] docs/er is stale: run scripts/er-diagram.sh and commit docs/er/deletionOrder.txt + docs/er/diagrams/summary." >&2
  exit 1
fi
echo "[er-diagram] docs/er is up to date."
