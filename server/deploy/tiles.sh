#!/usr/bin/env bash
# Builds the self-hosted map basemap on a compose host (MT1/MT2, decisions 159 and 163, runbook §10): a PMTiles v3
# file in the Protomaps basemap v4 schema, served by the app as /tiles/basemap.pmtiles. Run monthly by the fs-tiles
# systemd timer. The build itself runs in the builder image (server/deploy/tiles-builder/build-basemap.sh), the same one
# the K3s CronJob runs.
#
#   tiles.sh [--area=europe/dach] [--method=auto|planetiler|extract] [--out=DIR] [--bbox=W,S,E,N] [--maxzoom=N]
#            [--min-size-mb=N] [--min-free-gb=N] [--no-previous] [--build-image]
#
# Methods (auto picks one from the memory the container gets; TILES_MEM_AVAILABLE_MB overrides the reading):
#   planetiler  our own Planetiler run of the pinned Protomaps profile on the Geofabrik extract of --area
#               (>= 16 GB available: node map in RAM; 8-16 GB: --storage=mmap, slower)
#   extract     `pmtiles extract` of --bbox from the Protomaps daily world build (same v4 schema). Used under 8 GB
#               or when the Planetiler run fails or runs out of memory; every fallback logs WARN with the reason.
# The new file is checked and renamed over basemap.pmtiles atomically; the old one stays as basemap.previous.pmtiles.
# The container gets MemAvailable (--memory) and half the cores (--cpus). Image: TILES_IMAGE, default
# ghcr.io/epsuchti/foodsharing-tiles-builder:<server/deploy/tiles-builder/version.txt>, pulled when missing; --build-image
# (or a failed pull) builds it locally from server/deploy/tiles-builder. Folders: --out or TILES_DIR, TILES_WORK_DIR.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILDER_DIR="${DEPLOY_DIR}/tiles-builder"
VERSION="$(tr -d '[:space:]' < "${BUILDER_DIR}/version.txt")"
IMAGE="${TILES_IMAGE:-ghcr.io/epsuchti/foodsharing-tiles-builder:${VERSION}}"

OUT="${TILES_DIR:-${DEPLOY_DIR}/tiles}"
WORK="${TILES_WORK_DIR:-${DEPLOY_DIR}/tiles-work}"
BUILD_IMAGE=false
ARGS=()

for arg in "$@"; do
  case "${arg}" in
    --out=*) OUT="${arg#*=}" ;;
    --build-image) BUILD_IMAGE=true ;;
    --area=* | --method=* | --bbox=* | --maxzoom=* | --min-size-mb=* | --min-free-gb=* | --no-previous) ARGS+=("${arg}") ;;
    -h | --help) sed -n '2,19p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "ERROR unknown argument ${arg}" >&2; exit 2 ;;
  esac
done

log() { echo "$1 [tiles] ${*:2}"; }
die() { log ERROR "$*" >&2; exit 1; }

command -v docker > /dev/null || die "docker is needed"
mkdir -p "${OUT}" "${WORK}"
OUT="$(cd "${OUT}" && pwd)"
WORK="$(cd "${WORK}" && pwd)"

build_image() {
  log INFO "building ${IMAGE} from ${BUILDER_DIR}"
  docker build -q --build-arg TILES_BUILDER_VERSION="${VERSION}" -t "${IMAGE}" "${BUILDER_DIR}" > /dev/null
}
if [[ "${BUILD_IMAGE}" == true ]]; then
  build_image || die "building ${IMAGE} failed"
elif ! docker image inspect "${IMAGE}" > /dev/null 2>&1; then
  if ! docker pull -q "${IMAGE}" > /dev/null; then
    log WARN "could not pull ${IMAGE}: building it locally"
    build_image || die "building ${IMAGE} failed"
  fi
fi

run=(docker run --rm --name fs-tiles-build --user "$(id -u):$(id -g)"
  -v "${OUT}:/tiles/basemap" -v "${WORK}:/tiles/work")
if [[ -r /proc/meminfo ]]; then
  mem_mb="$(awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo)"
  ((mem_mb > 0)) && run+=(--memory="${mem_mb}m")
fi
cores="$(getconf _NPROCESSORS_ONLN 2> /dev/null || echo 2)"
run+=(--cpus="$((cores > 1 ? cores / 2 : 1))")
[[ -n "${TILES_MEM_AVAILABLE_MB:-}" ]] && run+=(-e TILES_MEM_AVAILABLE_MB="${TILES_MEM_AVAILABLE_MB}")

"${run[@]}" "${IMAGE}" ${ARGS[@]+"${ARGS[@]}"}
