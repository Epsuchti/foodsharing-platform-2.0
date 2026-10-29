#!/usr/bin/env bash
# Release script of the foodsharing platform image (D19.1, runbook §2).
#
#   release.sh [--build] --check   build linux/amd64 + linux/arm64 from the packaged jar, push nothing
#   release.sh [--build] --load    build for the local platform and load it as <image>:<version> and :local (smoke.sh)
#   release.sh [--build] --push    clean tree only: build both platforms, push <image>:<version> + :latest,
#                                  create the annotated git tag v<version> (push the tag yourself)
#
#   --build      run ./mvnw clean package -DskipTests first (Angular client included)
#   --version V  override server/deploy/version.txt
#
# Environment: IMAGE_REPOSITORY (default ghcr.io/epsuchti/foodsharing-platform), PLATFORMS, BUILDER_NAME.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd "${DEPLOY_DIR}/.." && pwd)"
REPO_DIR="$(cd "${SERVER_DIR}/.." && pwd)"
IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-ghcr.io/epsuchti/foodsharing-platform}"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"
BUILDER_NAME="${BUILDER_NAME:-foodsharing-platform-multiarch}"

usage() {
  sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit "${1:-1}"
}

MODE=""
BUILD=false
VERSION=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --check|--load|--push) [[ -z "${MODE}" ]] || usage; MODE="${1#--}" ;;
    --build) BUILD=true ;;
    --version) [[ $# -ge 2 ]] || usage; VERSION="$2"; shift ;;
    -h|--help) usage 0 ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
  shift
done
[[ -n "${MODE}" ]] || usage

if [[ -z "${VERSION}" ]]; then
  VERSION="$(tr -d '[:space:]' < "${DEPLOY_DIR}/version.txt")"
fi
if [[ ! "${VERSION}" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
  echo "Invalid version '${VERSION}' (expected digits, e.g. 44 or 1.2.0)" >&2
  exit 1
fi
GIT_TAG="v${VERSION}"

if [[ "${MODE}" == "push" ]]; then
  if [[ -n "$(git -C "${REPO_DIR}" status --porcelain)" ]]; then
    echo "Refusing to release from a dirty working tree (commit or stash first)." >&2
    exit 1
  fi
  if git -C "${REPO_DIR}" rev-parse -q --verify "refs/tags/${GIT_TAG}" > /dev/null; then
    echo "Git tag ${GIT_TAG} already exists; bump server/deploy/version.txt." >&2
    exit 1
  fi
fi

if [[ "${BUILD}" == true ]]; then
  echo "==> Building the jar (client included)"
  (cd "${SERVER_DIR}" && ./mvnw -B -q clean package -DskipTests)
fi

shopt -s nullglob
JARS=("${SERVER_DIR}"/target/foodsharing-platform-*.jar)
shopt -u nullglob
if [[ ${#JARS[@]} -ne 1 ]]; then
  echo "Expected exactly one server/target/foodsharing-platform-*.jar (found ${#JARS[@]}); run with --build." >&2
  exit 1
fi
JAR_REL="target/$(basename "${JARS[0]}")"
if ! unzip -l "${JARS[0]}" | grep -q 'BOOT-INF/classes/static/index.html'; then
  echo "Warning: ${JAR_REL} contains no Angular client (built with -Pskip-client?)." >&2
fi

if ! docker buildx inspect "${BUILDER_NAME}" > /dev/null 2>&1; then
  docker buildx create --name "${BUILDER_NAME}" --driver docker-container > /dev/null
fi

COMMON=(--builder "${BUILDER_NAME}" --file "${SERVER_DIR}/Dockerfile"
  --build-arg "JAR_FILE=${JAR_REL}" --build-arg "APP_VERSION=${VERSION}"
  --label "org.opencontainers.image.version=${VERSION}"
  --label "org.opencontainers.image.revision=$(git -C "${REPO_DIR}" rev-parse HEAD)"
  --label "org.opencontainers.image.source=https://github.com/epsuchti/foodsharing-platform-2.0")

case "${MODE}" in
  check)
    echo "==> Checking ${IMAGE_REPOSITORY}:${VERSION} for ${PLATFORMS} (nothing is pushed)"
    docker buildx build "${COMMON[@]}" --platform "${PLATFORMS}" "${SERVER_DIR}"
    echo "OK: image builds for ${PLATFORMS} (version ${VERSION}, jar ${JAR_REL})"
    ;;
  load)
    echo "==> Building ${IMAGE_REPOSITORY}:${VERSION} for the local platform"
    docker buildx build "${COMMON[@]}" --load \
      --tag "${IMAGE_REPOSITORY}:${VERSION}" --tag "${IMAGE_REPOSITORY}:local" "${SERVER_DIR}"
    echo "OK: loaded ${IMAGE_REPOSITORY}:${VERSION} and :local"
    ;;
  push)
    echo "==> Pushing ${IMAGE_REPOSITORY}:${VERSION} and :latest for ${PLATFORMS}"
    docker buildx build "${COMMON[@]}" --platform "${PLATFORMS}" --push \
      --tag "${IMAGE_REPOSITORY}:${VERSION}" --tag "${IMAGE_REPOSITORY}:latest" "${SERVER_DIR}"
    git -C "${REPO_DIR}" tag -a "${GIT_TAG}" -m "Release ${VERSION}"
    echo "OK: pushed ${IMAGE_REPOSITORY}:${VERSION}; created git tag ${GIT_TAG} (git push origin ${GIT_TAG})"
    ;;
esac
