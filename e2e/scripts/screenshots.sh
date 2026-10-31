#!/usr/bin/env bash
# Screenshot (visual regression) tests inside the official Playwright image, so baselines match between macOS, Linux
# and CI (plan §18.4, plan-m1 risk 6). The app runs on the host (packaged jar, profile e2e); the browsers run in Docker.
#
#   e2e/scripts/screenshots.sh                                 # compare against e2e/__screenshots__
#   e2e/scripts/screenshots.sh --update-snapshots=changed      # deliberately (re)write baselines — review them in the PR
#   e2e/scripts/screenshots.sh --update-snapshots=all          # rewrite every baseline, also unchanged ones
#   e2e/scripts/screenshots.sh screenshots/auth.screen.ts --grep "@A22"
#   e2e/scripts/screenshots.sh --shards=4                      # 4 jars on the host, 4 Playwright shards in one container
#
# --update-snapshots takes an optional mode in Playwright ≥ 1.50 (all|changed|missing|none): a bare
# `--update-snapshots screenshots/auth.screen.ts` would swallow the file as the mode. This script therefore rewrites a
# bare `--update-snapshots` / `-u` to `--update-snapshots=changed` (Playwright's default mode) and only joins a
# following word that is a valid mode (`--update-snapshots all` → `--update-snapshots=all`).
#
# --shards=N (or E2E_SHARDS; default 1, decision 131): N jars, each with its own port, in-memory database, log
# (test-results/server-screenshots-<i>.log) and upload folder; inside the container N `playwright test --shard=i/N`
# processes (output in test-results/instances/screenshots-<i>/output.log) whose blob reports are merged into
# playwright-report/ and test-results/results.json. Updating baselines works the same way sharded.
#
# Env: E2E_BASE_URL (reuse a running app reachable from the container; only with one shard), E2E_JAR, E2E_BUILD=1
# (build the jar when missing), E2E_PORT (one shard only), E2E_JAVA_OPTS, E2E_SHARDS,
# PLAYWRIGHT_IMAGE (default mcr.microsoft.com/playwright:v1.64.1-noble).
set -euo pipefail

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "${E2E_DIR}/.." && pwd)"
SERVER_DIR="${REPO_DIR}/server"
IMAGE="${PLAYWRIGHT_IMAGE:-mcr.microsoft.com/playwright:v1.64.1-noble}"
STARTUP_TIMEOUT="${E2E_STARTUP_TIMEOUT:-120}"
SHARDS="${E2E_SHARDS:-1}"
APP_PIDS=()

cleanup() {
  local pid
  for pid in ${APP_PIDS[@]+"${APP_PIDS[@]}"}; do
    if kill -0 "${pid}" 2>/dev/null; then
      kill "${pid}" 2>/dev/null || true
      wait "${pid}" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT INT TERM

# --- arguments: --shards, normalised --update-snapshots, the rest goes to `playwright test` ---
PW_ARGS=()
ARGS=("$@")
for ((i = 0; i < ${#ARGS[@]}; i++)); do
  arg="${ARGS[i]}"
  case "${arg}" in
    --shards=*) SHARDS="${arg#--shards=}" ;;
    --shards)
      i=$((i + 1))
      SHARDS="${ARGS[i]:-}"
      ;;
    --shard | --shard=*)
      echo "Use --shards=N; the script sets --shard itself." >&2
      exit 1
      ;;
    --update-snapshots | -u)
      next="${ARGS[i + 1]:-}"
      if [[ "${next}" =~ ^(all|changed|missing|none)$ ]]; then
        PW_ARGS+=("--update-snapshots=${next}")
        i=$((i + 1))
      else
        PW_ARGS+=("--update-snapshots=changed")
      fi
      ;;
    *) PW_ARGS+=("${arg}") ;;
  esac
done
if ! [[ "${SHARDS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "Invalid shard count: ${SHARDS}" >&2
  exit 1
fi
if ((SHARDS > 1)) && [[ -n "${E2E_BASE_URL:-}" || -n "${E2E_PORT:-}" ]]; then
  echo "E2E_BASE_URL / E2E_PORT only work with one shard (every shard needs its own jar and database)." >&2
  exit 1
fi

find_jar() {
  if [[ -n "${E2E_JAR:-}" ]]; then
    printf '%s\n' "${E2E_JAR}"
    return
  fi
  # newest foodsharing-platform-*.jar in server/target (not *.jar.original, not -plain.jar)
  local jar newest=""
  for jar in "${SERVER_DIR}"/target/foodsharing-platform-*.jar; do
    [[ -f "${jar}" && "${jar}" != *-plain.jar ]] || continue
    if [[ -z "${newest}" || "${jar}" -nt "${newest}" ]]; then newest="${jar}"; fi
  done
  [[ -z "${newest}" ]] || printf '%s\n' "${newest}"
}

free_port() {
  node -e 'const s=require("net").createServer();s.listen(0,"127.0.0.1",()=>{console.log(s.address().port);s.close();});'
}

is_ready() {
  curl -fsS "http://localhost:$1/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'
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

BASE_URLS=()
PROXY_TARGETS=()
if [[ -n "${E2E_BASE_URL:-}" ]]; then
  BASE_URLS=("${E2E_BASE_URL}")
else
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
  mkdir -p "${E2E_DIR}/test-results"
  PORTS=()
  LOGS=()
  for ((shard = 1; shard <= SHARDS; shard++)); do
    port="${E2E_PORT:-$(free_port)}"
    extra=()
    if ((SHARDS == 1)); then
      log="${E2E_DIR}/test-results/server-screenshots.log"
    else
      log="${E2E_DIR}/test-results/server-screenshots-${shard}.log"
      # Own upload folder per jar (relative to server/, the jar's working directory), see global-setup.ts.
      extra=("--app.uploads.dir=../e2e/test-results/instances/screenshots-${shard}/uploads")
      rm -rf "${E2E_DIR}/test-results/instances/screenshots-${shard}"
    fi
    echo "[screenshots] starting $(basename "${JAR}") on port ${port} (log: ${log})"
    # shellcheck disable=SC2086 # E2E_JAVA_OPTS is a list of JVM options
    (cd "${SERVER_DIR}" && E2E_PORT="${port}" exec java ${E2E_JAVA_OPTS:-} -jar "${JAR}" \
        --spring.profiles.active=e2e --server.port="${port}" --server.address=0.0.0.0 ${extra[@]+"${extra[@]}"}) \
        >"${log}" 2>&1 &
    APP_PIDS+=("$!")
    PORTS+=("${port}")
    LOGS+=("${log}")
  done
  for ((n = 0; n < STARTUP_TIMEOUT * 2; n++)); do
    ready=0
    for ((s = 0; s < SHARDS; s++)); do
      if is_ready "${PORTS[s]}"; then
        ready=$((ready + 1))
      elif ! kill -0 "${APP_PIDS[s]}" 2>/dev/null; then
        echo "The jar on port ${PORTS[s]} exited during startup:" >&2
        tail -n 60 "${LOGS[s]}" >&2
        exit 1
      fi
    done
    ((ready < SHARDS)) || break
    sleep 0.5
  done
  for ((s = 0; s < SHARDS; s++)); do
    if ! is_ready "${PORTS[s]}"; then
      echo "The jar on port ${PORTS[s]} was not ready within ${STARTUP_TIMEOUT}s" >&2
      tail -n 60 "${LOGS[s]}" >&2
      exit 1
    fi
    # Docker Desktop: the browser must see the app as http://localhost (a secure context, so WebAuthn and other
    # secure-only APIs exist, and the same origin as app.base-url). A tiny TCP proxy inside the container forwards
    # localhost:PORT to the host.
    BASE_URLS+=("http://localhost:${PORTS[s]}")
    if [[ "${APP_HOST}" != "localhost" ]]; then PROXY_TARGETS+=("${APP_HOST}:${PORTS[s]}"); fi
  done
fi

MOUNT_DIR="${REPO_DIR}"
case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*)
    # Git Bash on Windows: stop MSYS from rewriting container paths (/work/e2e → C:/Program Files/Git/work/e2e) and
    # hand Docker Desktop a Windows path for the mount.
    export MSYS_NO_PATHCONV=1
    MOUNT_DIR="$(cd "${REPO_DIR}" && pwd -W)"
    ;;
esac

echo "[screenshots] running ${SHARDS} shard(s) in ${IMAGE} against ${BASE_URLS[*]}"
docker run --rm --ipc=host --init \
  "${NETWORK_ARGS[@]}" \
  ${USER_ARGS[@]+"${USER_ARGS[@]}"} \
  -v "${MOUNT_DIR}:/work" \
  ${MODULES_ARGS[@]+"${MODULES_ARGS[@]}"} \
  -w /work/e2e \
  -e CI="${CI:-}" \
  -e E2E_SHARDS="${SHARDS}" \
  -e E2E_BASE_URLS="${BASE_URLS[*]}" \
  -e E2E_PROXY_TARGETS="${PROXY_TARGETS[*]:-}" \
  -e E2E_SCREENSHOTS=1 \
  -e npm_config_cache=/tmp/.npm \
  "${IMAGE}" \
  bash scripts/screenshots-container.sh ${PW_ARGS[@]+"${PW_ARGS[@]}"}
