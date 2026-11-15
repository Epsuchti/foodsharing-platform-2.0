#!/usr/bin/env bash
# Runs inside the Playwright image, started by scripts/screenshots.sh (working directory /work/e2e). Not meant to be
# called directly.
#
# Env: E2E_BASE_URLS (one base URL per jar, space-separated; Playwright runs one worker per jar, decision 180),
# E2E_PROXY_TARGETS (Docker Desktop: host:port per jar; a TCP proxy on 127.0.0.1:port forwards to it so the browser
# sees http://localhost:port), E2E_PROJECTS (space-separated Playwright projects).
# Arguments: passed to `playwright test` (already normalised by screenshots.sh).
set -euo pipefail

read -r -a BASE_URLS <<<"${E2E_BASE_URLS:?E2E_BASE_URLS is required}"
read -r -a PROXY_TARGETS <<<"${E2E_PROXY_TARGETS:-}"
read -r -a PROJECT_NAMES <<<"${E2E_PROJECTS:?E2E_PROJECTS is required}"
PROJECTS=()
for project in "${PROJECT_NAMES[@]}"; do PROJECTS+=("--project=${project}"); done

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

# One worker per jar (playwright.config.ts counts the URLs); the config's reporters (list, results.json, HTML report,
# on CI also the blob report).
E2E_BASE_URL="${BASE_URLS[*]}" npx playwright test "${PROJECTS[@]}" "$@"
