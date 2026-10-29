#!/usr/bin/env bash
# Smoke test of the production image with the local compose variant (D19.1, runbook §2).
#
#   smoke.sh [--keep]     uses IMAGE/VERSION from the environment (default: <release.sh image>:local)
#
# Starts postgres + mailpit + app (prod profile, throwaway secrets in .env.local), waits for readiness on the
# management port and checks: / answers 200 with the SPA, app-info reports version.txt, dev endpoints are 404,
# health/Prometheus are not served on the public port but on the management port, the logs are JSON lines.
# --keep leaves the stack running (backup/restore rehearsal); otherwise it is removed with its volumes.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEEP=false
[[ "${1:-}" == "--keep" ]] && KEEP=true

export IMAGE="${IMAGE:-ghcr.io/epsuchti/foodsharing-platform}"
export VERSION="${VERSION:-local}"
export HTTP_PORT="${HTTP_PORT:-18080}"
export MANAGEMENT_HTTP_PORT="${MANAGEMENT_HTTP_PORT:-19090}"
EXPECTED_VERSION="$(tr -d '[:space:]' < "${DEPLOY_DIR}/version.txt")"
ENV_FILE="${DEPLOY_DIR}/.env.local"
COMPOSE=(docker compose --project-name foodsharing-smoke -f "${DEPLOY_DIR}/compose.yaml"
  -f "${DEPLOY_DIR}/compose.local.yaml" --env-file "${ENV_FILE}")
BASE="http://127.0.0.1:${HTTP_PORT}"
MGMT="http://127.0.0.1:${MANAGEMENT_HTTP_PORT}"

rand() { openssl rand -hex 24; }

if [[ ! -f "${ENV_FILE}" ]]; then
  umask 077
  {
    echo "IMAGE=${IMAGE}"
    echo "VERSION=${VERSION}"
    echo "POSTGRES_PASSWORD=$(rand)"
    echo "APP_IP_SALT=$(rand)"
    echo "APP_SIGNING_SECRET=$(rand)"
    echo "NEWSLETTER_SECRET=$(rand)"
    echo "OAUTH_SIGNING_KEY=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2> /dev/null | tr -d '\n')"
  } > "${ENV_FILE}"
fi

cleanup() {
  local status=$?
  if [[ ${status} -ne 0 ]]; then
    echo "--- last app log lines ---" >&2
    "${COMPOSE[@]}" logs --no-color --tail 15 app >&2 || true
  fi
  if [[ "${KEEP}" != true ]]; then
    "${COMPOSE[@]}" down --volumes --remove-orphans > /dev/null 2>&1 || true
    rm -f "${ENV_FILE}"
  fi
  exit "${status}"
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "ok   $*"; }
status_of() { curl -s -o /dev/null -w '%{http_code}' "$1"; }

echo "==> Starting ${IMAGE}:${VERSION} (prod profile) with postgres and mailpit"
"${COMPOSE[@]}" up -d --quiet-pull > /dev/null

echo "==> Waiting for readiness on the management port"
for _ in $(seq 1 90); do
  if [[ "$(status_of "${MGMT}/actuator/health/readiness")" == "200" ]]; then
    break
  fi
  sleep 2
done
[[ "$(status_of "${MGMT}/actuator/health/readiness")" == "200" ]] || fail "readiness not UP within 180 s"
ok "readiness UP on :9090"
[[ "$(status_of "${MGMT}/actuator/health/liveness")" == "200" ]] || fail "liveness"
ok "liveness UP on :9090"

[[ "$(status_of "${BASE}/")" == "200" ]] || fail "/ is not 200"
curl -s "${BASE}/" | grep -qi '<app-root\|<fs-root\|<html' || fail "/ does not serve the SPA"
ok "/ 200 (SPA)"

APP_INFO="$(curl -s "${BASE}/api/public/app-info")"
echo "${APP_INFO}" | grep -q "\"version\":\"${EXPECTED_VERSION}\"" \
  || fail "app-info version != ${EXPECTED_VERSION}: ${APP_INFO}"
ok "app-info version ${EXPECTED_VERSION}"

for path in /api/dev/seed/reset /api/dev/mails /dev/oidc/jwks /h2-console; do
  code="$(status_of "${BASE}${path}")"
  [[ "${code}" == "404" ]] || fail "dev endpoint ${path} answered ${code}"
done
ok "dev endpoints 404"

for path in /actuator/health /actuator/prometheus; do
  code="$(status_of "${BASE}${path}")"
  [[ "${code}" == "404" ]] || fail "${path} on the public port answered ${code}"
done
ok "actuator not on the public port"

SCRAPE="$(curl -s "${MGMT}/actuator/prometheus")"
grep -q '^jvm_memory_used_bytes' <<< "${SCRAPE}" || fail "prometheus scrape"
grep -q '^fs_outbox_pending' <<< "${SCRAPE}" || fail "operations metrics (fs_*) missing"
ok "prometheus on :9090 ($(grep -c '^fs_' <<< "${SCRAPE}") fs_* series)"

LINE="$("${COMPOSE[@]}" logs --no-color --no-log-prefix app | grep -m1 'Started FoodsharingApplication' || true)"
echo "${LINE}" | grep -q '^{".*"level":"INFO".*"app":"foodsharing-platform"' || fail "log line is not JSON: ${LINE}"
ok "JSON log lines"

[[ "$("${COMPOSE[@]}" exec -T app id -u)" == "10001" ]] || fail "app does not run as uid 10001"
ok "runs as non-root (uid 10001)"

echo "SMOKE OK (${IMAGE}:${VERSION}, version ${EXPECTED_VERSION})"
