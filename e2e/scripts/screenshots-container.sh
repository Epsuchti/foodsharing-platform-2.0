#!/usr/bin/env bash
# Runs inside the Playwright image, started by scripts/screenshots.sh (working directory /work/e2e). Not meant to be
# called directly.
#
# Env: E2E_SHARDS (count), E2E_BASE_URLS (one base URL per shard, space-separated), E2E_PROXY_TARGETS (Docker Desktop:
# host:port per shard; a TCP proxy on 127.0.0.1:port forwards to it so the browser sees http://localhost:port).
# Arguments: passed to `playwright test` (already normalised by screenshots.sh).
set -euo pipefail

SHARDS="${E2E_SHARDS:-1}"
read -r -a BASE_URLS <<<"${E2E_BASE_URLS:?E2E_BASE_URLS is required}"
read -r -a PROXY_TARGETS <<<"${E2E_PROXY_TARGETS:-}"
PROJECTS=(--project=screenshots-desktop --project=screenshots-mobile)

for target in ${PROXY_TARGETS[@]+"${PROXY_TARGETS[@]}"}; do
  E2E_PROXY_TARGET="${target}" node -e '
    const net = require("net");
    const [h, p] = process.env.E2E_PROXY_TARGET.split(":");
    net.createServer((c) => {
      const s = net.connect(+p, h);
      c.pipe(s);
      s.pipe(c);
      c.on("error", () => s.destroy());
      s.on("error", () => c.destroy());
    }).listen(+p, "127.0.0.1");' &
done

npm ci --no-audit --no-fund --loglevel=error

if ((SHARDS == 1)); then
  # The classic run (CI): the config's reporters (list, results.json, HTML report).
  E2E_BASE_URL="${BASE_URLS[0]}" npx playwright test "${PROJECTS[@]}" "$@"
  exit
fi

BLOB_DIR=blob-report/screenshots
rm -rf "${BLOB_DIR}" test-results/results.json
mkdir -p "${BLOB_DIR}"
PIDS=()
for ((shard = 1; shard <= SHARDS; shard++)); do
  dir="test-results/instances/screenshots-${shard}"
  mkdir -p "${dir}"
  # Full output in the shard's log; failures and the start line are echoed live with a shard prefix.
  (
    E2E_INSTANCE="screenshots-${shard}" E2E_BASE_URL="${BASE_URLS[shard - 1]}" \
      PLAYWRIGHT_BLOB_OUTPUT_FILE="${BLOB_DIR}/report-${shard}.zip" FORCE_COLOR=0 \
      npx playwright test "${PROJECTS[@]}" --shard="${shard}/${SHARDS}" --reporter=blob,list "$@" 2>&1 |
      tee "${dir}/output.log" |
      { grep --line-buffered -E '^Running |✘' || true; } |
      sed -u "s/^/[shard ${shard}] /"
  ) &
  PIDS+=("$!")
done

status=0
for ((shard = 1; shard <= SHARDS; shard++)); do
  if wait "${PIDS[shard - 1]}"; then
    echo "[screenshots] shard ${shard}: ok"
  else
    echo "[screenshots] shard ${shard}: failed (see e2e/test-results/instances/screenshots-${shard}/output.log)"
    status=1
  fi
done

PLAYWRIGHT_JSON_OUTPUT_NAME=test-results/results.json PLAYWRIGHT_HTML_OPEN=never \
  npx playwright merge-reports --reporter=json,html "${BLOB_DIR}" >/dev/null || status=1
if [[ -f test-results/results.json ]]; then
  node scripts/run-sharded.mjs --summary test-results/results.json || status=1
else
  echo "[screenshots] merge-reports wrote no results.json" >&2
  status=1
fi
exit "${status}"
