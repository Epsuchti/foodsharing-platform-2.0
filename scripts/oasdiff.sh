#!/usr/bin/env bash
# Breaking-change check of the OpenAPI contract against a base revision (docs/conventions.md A5, plan §18.6).
#
#   scripts/oasdiff.sh [base-ref]        # default: origin/main
#
# The base revision is checked out into a temporary git worktree so the split spec's relative $refs resolve, then
# `oasdiff breaking` (Docker image tufin/oasdiff) compares base → working tree and fails on ERR-level changes.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_REF="${1:-origin/main}"
IMAGE="${OASDIFF_IMAGE:-tufin/oasdiff:v1.34.1}"
SPEC="server/src/main/resources/openapi/openapi.yaml"
FAIL_ON="${OASDIFF_FAIL_ON:-ERR}"

cd "${REPO_DIR}"
if ! git rev-parse --verify --quiet "${BASE_REF}^{commit}" >/dev/null; then
  echo "Base ref ${BASE_REF} not found (fetch it first, e.g. git fetch origin main)." >&2
  exit 1
fi
if ! git cat-file -e "${BASE_REF}:${SPEC}" 2>/dev/null; then
  echo "[oasdiff] ${BASE_REF} has no ${SPEC} yet; nothing to compare."
  exit 0
fi

BASE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/oasdiff-base.XXXXXX")"
cleanup() {
  git -C "${REPO_DIR}" worktree remove --force "${BASE_DIR}" >/dev/null 2>&1 || rm -rf "${BASE_DIR}"
}
trap cleanup EXIT
git worktree add --detach --quiet "${BASE_DIR}" "${BASE_REF}"

echo "[oasdiff] ${BASE_REF} → working tree (fail on ${FAIL_ON})"
docker run --rm \
  -v "${BASE_DIR}:/base:ro" \
  -v "${REPO_DIR}:/rev:ro" \
  "${IMAGE}" breaking "/base/${SPEC}" "/rev/${SPEC}" --fail-on "${FAIL_ON}" --format text
