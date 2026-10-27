#!/usr/bin/env bash
# Screenshot (visual regression) tests inside the official Playwright image, so baselines match between macOS, Linux
# and CI (plan §18.4, plan-m1 risk 6). The app runs on the host (packaged jar, profile e2e); the browsers run in Docker.
#
#   e2e/scripts/screenshots.sh                       # compare against e2e/__screenshots__
#   e2e/scripts/screenshots.sh --update-snapshots    # deliberately (re)write baselines — review them in the PR
#   e2e/scripts/screenshots.sh screenshots/auth.screen.ts --grep "@A22"
#
# Env: E2E_BASE_URL (reuse a running app reachable from the container), E2E_JAR, E2E_BUILD=1 (build the jar when
# missing), E2E_PORT, PLAYWRIGHT_IMAGE (default mcr.microsoft.com/playwright:v1.64.1-noble).
set -euo pipefail

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "${E2E_DIR}/.." && pwd)"
SERVER_DIR="${REPO_DIR}/server"
IMAGE="${PLAYWRIGHT_IMAGE:-mcr.microsoft.com/playwright:v1.64.1-noble}"
STARTUP_TIMEOUT="${E2E_STARTUP_TIMEOUT:-120}"
APP_PID=""

cleanup() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

find_jar() {
  if [[ -n "${E2E_JAR:-}" ]]; then
    printf '%s\n' "${E2E_JAR}"
    return
  fi
  # newest foodsharing-platform-*.jar in server/target (not *.jar.original)
  ls -t "${SERVER_DIR}"/target/foodsharing-platform-*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -n 1 || true
}

free_port() {
  node -e 'const s=require("net").createServer();s.listen(0,"127.0.0.1",()=>{console.log(s.address().port);s.close();});'
}

if [[ "$(uname -s)" == "Linux" ]]; then
  # Host networking: the container reaches the jar as localhost, so redirects to app.base-url (fake OIDC, mail links)
  # resolve the same way as in the normal e2e run. Files are written as the calling user.
  NETWORK_ARGS=(--network host)
  USER_ARGS=(--user "$(id -u):$(id -g)" -e HOME=/tmp)
  # Same platform as the host: the container may (re)install into e2e/node_modules.
  MODULES_ARGS=()
  APP_HOST="localhost"
else
  # Docker Desktop (macOS/Windows): the host is reachable as host.docker.internal.
  NETWORK_ARGS=(--add-host=host.docker.internal:host-gateway)
  USER_ARGS=()
  # Keep the host's (darwin) node_modules untouched: the container installs into an anonymous volume.
  MODULES_ARGS=(-v /work/e2e/node_modules)
  APP_HOST="host.docker.internal"
fi

BASE_URL="${E2E_BASE_URL:-}"
if [[ -z "${BASE_URL}" ]]; then
  JAR="$(find_jar)"
  if [[ -z "${JAR}" && "${E2E_BUILD:-}" =~ ^(1|true)$ ]]; then
    echo "[screenshots] building the jar (./mvnw -q -DskipTests package)…"
    (cd "${SERVER_DIR}" && ./mvnw -q -DskipTests package)
    JAR="$(find_jar)"
  fi
  if [[ -z "${JAR}" ]]; then
    echo "No jar in server/target. Run 'cd server && ./mvnw -DskipTests package' or set E2E_BUILD=1 / E2E_BASE_URL." >&2
    exit 1
  fi
  PORT="${E2E_PORT:-$(free_port)}"
  mkdir -p "${E2E_DIR}/test-results"
  LOG="${E2E_DIR}/test-results/server-screenshots.log"
  echo "[screenshots] starting $(basename "${JAR}") on port ${PORT} (log: ${LOG})"
  (cd "${SERVER_DIR}" && E2E_PORT="${PORT}" exec java ${E2E_JAVA_OPTS:-} -jar "${JAR}" \
      --spring.profiles.active=e2e --server.port="${PORT}" --server.address=0.0.0.0) >"${LOG}" 2>&1 &
  APP_PID=$!
  for ((i = 0; i < STARTUP_TIMEOUT * 2; i++)); do
    if curl -fsS "http://localhost:${PORT}/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then
      break
    fi
    if ! kill -0 "${APP_PID}" 2>/dev/null; then
      echo "The jar exited during startup:" >&2
      tail -n 60 "${LOG}" >&2
      exit 1
    fi
    sleep 0.5
  done
  if ! curl -fsS "http://localhost:${PORT}/actuator/health/readiness" >/dev/null 2>&1; then
    echo "The jar was not ready within ${STARTUP_TIMEOUT}s" >&2
    tail -n 60 "${LOG}" >&2
    exit 1
  fi
  BASE_URL="http://${APP_HOST}:${PORT}"
fi

echo "[screenshots] running in ${IMAGE} against ${BASE_URL}"
docker run --rm --ipc=host --init \
  "${NETWORK_ARGS[@]}" \
  ${USER_ARGS[@]+"${USER_ARGS[@]}"} \
  -v "${REPO_DIR}:/work" \
  ${MODULES_ARGS[@]+"${MODULES_ARGS[@]}"} \
  -w /work/e2e \
  -e CI="${CI:-}" \
  -e E2E_BASE_URL="${BASE_URL}" \
  -e E2E_SCREENSHOTS=1 \
  -e npm_config_cache=/tmp/.npm \
  "${IMAGE}" \
  bash -c 'npm ci --no-audit --no-fund --loglevel=error && npx playwright test --project=screenshots-desktop --project=screenshots-mobile "$@"' \
  screenshots "$@"
