#!/usr/bin/env bash
# ER diagram from the Liquibase schema (D15.1): Postgres 17 in Docker, the packaged jar with profile `prod` runs the
# changelogs against it (schema only, no seed), then SchemaSpy renders the HTML/SVG documentation into docs/er/.
#
#   scripts/er-diagram.sh            # needs a jar in server/target (E2E_JAR / ER_JAR to override, ER_BUILD=1 to build)
#
# Output: docs/er/index.html (+ diagrams). CI uploads it as an artifact; the implementer commits it per milestone.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${REPO_DIR}/server"
OUT_DIR="${ER_OUT_DIR:-${REPO_DIR}/docs/er}"
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
  exec java -jar "${JAR}" --spring.profiles.active=prod --server.port="${APP_PORT}" --app.scheduling.enabled=false
) >"${LOG}" 2>&1 &
APP_PID=$!
ready=false
for _ in $(seq 1 240); do
  if curl -fsS "http://localhost:${APP_PORT}/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then ready=true; break; fi
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
echo "[er-diagram] done: ${OUT_DIR}/index.html"
