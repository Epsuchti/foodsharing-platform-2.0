#!/usr/bin/env bash
# Builds the self-hosted map basemap (MT1, decision 159, runbook "Map tiles"): a PMTiles v3 file in the Protomaps
# basemap v4 schema, served by the app as /tiles/basemap.pmtiles. Run monthly by the fs-tiles systemd timer.
#
#   tiles.sh [--area=europe/dach] [--method=auto|planetiler|extract] [--out=DIR] [--bbox=W,S,E,N] [--maxzoom=N]
#            [--min-size-mb=N] [--min-free-gb=N] [--no-previous]
#
# Methods (auto picks one from MemAvailable; TILES_MEM_AVAILABLE_MB overrides the reading, e.g. to test the switch):
#   planetiler  our own Planetiler run of the pinned Protomaps profile on the Geofabrik extract of --area
#               (>= 16 GB available: node map in RAM; 8-16 GB: --storage=mmap, slower)
#   extract     `pmtiles extract` of --bbox from the Protomaps daily world build (same v4 schema). Used under 8 GB
#               or when the Planetiler run fails or is killed (OOM); every fallback logs WARN with the reason.
# The new file is written as basemap-<date>.pmtiles next to the current one, checked (PMTiles magic, version 3,
# minimum size) and renamed over basemap.pmtiles atomically; the old file stays as basemap.previous.pmtiles
# (rollback). Any failure leaves the current file untouched. A lock prevents overlapping runs.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Pinned tools: the Protomaps basemaps profile (tiles 4.15.2), its JDK and the pmtiles CLI.
BASEMAPS_REPO="https://github.com/protomaps/basemaps.git"
BASEMAPS_COMMIT="ca93fc06efbaff1c5f069d06edbe5de18839c1c8"
BASEMAPS_IMAGE="fs-protomaps-basemaps:${BASEMAPS_COMMIT:0:12}"
PMTILES_IMAGE="protomaps/go-pmtiles:v1.31.2"
SCHEMA_MAJOR=4
WORLD_BUILDS="https://build.protomaps.com"
WORLD_BUILDS_INDEX="https://build-metadata.protomaps.dev/builds.json"
GEOFABRIK="https://download.geofabrik.de"

AREA="europe/dach"
METHOD="auto"
OUT="${TILES_DIR:-${DEPLOY_DIR}/tiles}"
WORK="${TILES_WORK_DIR:-${DEPLOY_DIR}/tiles-work}"
BBOX=""
BBOX_GIVEN=false
MAXZOOM=15
MIN_SIZE_MB=1000
MIN_FREE_GB=50
KEEP_PREVIOUS=true

for arg in "$@"; do
  case "${arg}" in
    --area=*) AREA="${arg#*=}" ;;
    --method=*) METHOD="${arg#*=}" ;;
    --out=*) OUT="${arg#*=}" ;;
    --bbox=*) BBOX="${arg#*=}"; BBOX_GIVEN=true ;;
    --maxzoom=*) MAXZOOM="${arg#*=}" ;;
    --min-size-mb=*) MIN_SIZE_MB="${arg#*=}" ;;
    --min-free-gb=*) MIN_FREE_GB="${arg#*=}" ;;
    --no-previous) KEEP_PREVIOUS=false ;;
    -h | --help) sed -n '2,16p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "ERROR unknown argument ${arg}" >&2; exit 2 ;;
  esac
done

log() { echo "$1 [tiles] ${*:2}"; }
die() { log ERROR "$*" >&2; exit 1; }

case "${METHOD}" in auto | planetiler | extract) ;; *) die "--method must be auto, planetiler or extract" ;; esac
[[ "${MAXZOOM}" =~ ^[0-9]+$ ]] || die "--maxzoom must be a number"
if [[ -z "${BBOX}" ]]; then
  case "${AREA}" in
    # Geofabrik europe/dach (DE, AT, CH, LI) with a small margin for the border towns
    europe/dach) BBOX="5.5,45.6,17.4,55.2" ;;
    *) [[ "${METHOD}" == "planetiler" ]] || die "--bbox is needed for area ${AREA} (extract method)" ;;
  esac
fi
[[ -z "${BBOX}" || "${BBOX}" =~ ^-?[0-9.]+,-?[0-9.]+,-?[0-9.]+,-?[0-9.]+$ ]] || die "--bbox must be W,S,E,N"

mkdir -p "${OUT}" "${WORK}"
OUT="$(cd "${OUT}" && pwd)"
WORK="$(cd "${WORK}" && pwd)"

# --- lock against overlapping runs ---
LOCK="${WORK}/.tiles.lock"
if command -v flock > /dev/null; then
  exec 9> "${LOCK}"
  flock -n 9 || die "another tiles.sh run holds ${LOCK}"
else
  mkdir "${LOCK}.d" 2> /dev/null || die "another tiles.sh run holds ${LOCK}.d (remove it if no run is active)"
  trap 'rmdir "${LOCK}.d" 2> /dev/null || true' EXIT
fi

days_ago() { date -u -d "-$1 day" +%Y%m%d 2> /dev/null || date -u -v-"$1"d +%Y%m%d; }
free_gb() { df -Pk "$1" | awk 'NR == 2 { print int($4 / 1024 / 1024) }'; }
mem_available_mb() {
  if [[ -n "${TILES_MEM_AVAILABLE_MB:-}" ]]; then
    echo "${TILES_MEM_AVAILABLE_MB}"
  elif [[ -r /proc/meminfo ]]; then
    awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo
  else
    echo 0
  fi
}
md5_of() { if command -v md5sum > /dev/null; then md5sum "$1" | cut -d' ' -f1; else md5 -q "$1"; fi; }
size_mb() { echo $(($(wc -c < "$1") / 1024 / 1024)); }
cpu_limit() {
  local cores
  cores="$(getconf _NPROCESSORS_ONLN 2> /dev/null || echo 2)"
  echo $((cores > 1 ? cores / 2 : 1))
}

STAMP="$(date -u +%Y%m%d)"
NEW="basemap-${STAMP}.pmtiles"
START="$(date +%s)"

FREE="$(free_gb "${WORK}")"
((FREE >= MIN_FREE_GB)) || die "only ${FREE} GB free in ${WORK}, need ${MIN_FREE_GB} GB; current file left in place"
if [[ "$(free_gb "${OUT}")" -lt "${MIN_FREE_GB}" && "$(df -P "${OUT}" | awk 'NR==2{print $1}')" != "$(df -P "${WORK}" | awk 'NR==2{print $1}')" ]]; then
  die "less than ${MIN_FREE_GB} GB free in ${OUT}; current file left in place"
fi

build_planetiler() {
  local mem_mb="$1" storage heap_mb osm
  if ((mem_mb >= 16384)); then
    storage=ram
    heap_mb=$((mem_mb - 4096 > 24576 ? 24576 : mem_mb - 4096))
  else
    storage=mmap
    heap_mb=4096
  fi
  log INFO "planetiler: ${AREA}, storage=${storage}, heap ${heap_mb} MB, ${MAXZOOM} max zoom, $(cpu_limit) CPUs"
  if ! docker image inspect "${BASEMAPS_IMAGE}" > /dev/null 2>&1; then
    log INFO "building ${BASEMAPS_IMAGE} from ${BASEMAPS_REPO}#${BASEMAPS_COMMIT}"
    docker build -q -t "${BASEMAPS_IMAGE}" "${BASEMAPS_REPO}#${BASEMAPS_COMMIT}:tiles" > /dev/null || return 1
  fi
  mkdir -p "${WORK}/sources"
  osm="${WORK}/sources/$(basename "${AREA}").osm.pbf"
  log INFO "downloading ${GEOFABRIK}/${AREA}-latest.osm.pbf"
  rm -f "${osm}" "${osm}.md5"
  curl -fsSL --retry 3 -o "${osm}" "${GEOFABRIK}/${AREA}-latest.osm.pbf" || return 1
  curl -fsSL --retry 3 -o "${osm}.md5" "${GEOFABRIK}/${AREA}-latest.osm.pbf.md5" || return 1
  [[ "$(md5_of "${osm}")" == "$(cut -d' ' -f1 < "${osm}.md5")" ]] || { log WARN "md5 of ${osm} does not match"; return 1; }
  local args=(--area="${AREA}" --osm-path="data/sources/$(basename "${osm}")" --download --force
    --storage="${storage}" --maxzoom="${MAXZOOM}" --output="/out/${NEW}")
  # an explicit --bbox clips the Planetiler output too (the fixture); otherwise the whole extract is built
  if [[ "${BBOX_GIVEN}" == true ]]; then args+=(--bounds="${BBOX}"); fi
  docker run --rm --name fs-tiles-planetiler --cpus="$(cpu_limit)" --user "$(id -u):$(id -g)" -e HOME=/tmp \
    -v "${WORK}:/tiles/data" -v "${OUT}:/out" --entrypoint nice "${BASEMAPS_IMAGE}" \
    -n 10 java -Xmx"${heap_mb}"m -jar /tiles/protomaps-basemap.jar "${args[@]}"
}

build_extract() {
  local build="" day version
  for day in 0 1 2 3 4 5 6; do
    if curl -fsS -o /dev/null -r 0-0 "${WORLD_BUILDS}/$(days_ago "${day}").pmtiles" 2> /dev/null; then
      build="$(days_ago "${day}").pmtiles"
      break
    fi
  done
  [[ -n "${build}" ]] || { log WARN "no Protomaps world build of the last 7 days found"; return 1; }
  version="$(curl -fsS "${WORLD_BUILDS_INDEX}" | tr '}' '\n' | grep "\"${build}\"" | sed -n 's/.*"version":"\([0-9.]*\)".*/\1/p')"
  [[ "${version%%.*}" == "${SCHEMA_MAJOR}" ]] \
    || { log WARN "world build ${build} has schema ${version:-unknown}, the client expects ${SCHEMA_MAJOR}.x"; return 1; }
  log INFO "extract: bbox ${BBOX}, ${MAXZOOM} max zoom, from ${WORLD_BUILDS}/${build} (tiles ${version})"
  nice -n 10 docker run --rm --name fs-tiles-extract --user "$(id -u):$(id -g)" -v "${OUT}:/out" "${PMTILES_IMAGE}" \
    extract "${WORLD_BUILDS}/${build}" "/out/${NEW}" --bbox="${BBOX}" --maxzoom="${MAXZOOM}"
}

check_file() {
  local file="$1" magic version
  [[ -s "${file}" ]] || { log WARN "${file} is missing or empty"; return 1; }
  magic="$(head -c 7 "${file}")"
  version="$(od -An -tu1 -j7 -N1 "${file}" | tr -d ' ')"
  [[ "${magic}" == "PMTiles" && "${version}" == "3" ]] || { log WARN "${file} is not PMTiles v3"; return 1; }
  (($(size_mb "${file}") >= MIN_SIZE_MB)) || { log WARN "${file} is only $(size_mb "${file}") MB (< ${MIN_SIZE_MB})"; return 1; }
}

MEM_MB="$(mem_available_mb)"
CHOSEN="${METHOD}"
if [[ "${METHOD}" == "auto" ]]; then
  if ((MEM_MB >= 8192)); then
    CHOSEN=planetiler
  else
    log WARN "only ${MEM_MB} MB MemAvailable (< 8192): falling back to the extract of the Protomaps world build"
    CHOSEN=extract
  fi
fi
log INFO "method ${CHOSEN} (requested ${METHOD}, ${MEM_MB} MB available), output ${OUT}/${NEW}"

rm -f "${OUT}/${NEW}"
BUILT=false
if [[ "${CHOSEN}" == "planetiler" ]]; then
  status=0
  build_planetiler "${MEM_MB}" || status=$?
  if ((status == 0)) && check_file "${OUT}/${NEW}"; then
    BUILT=true
  elif [[ "${METHOD}" == "auto" ]]; then
    reason="exit ${status}"
    ((status == 137)) && reason="killed (exit 137, likely out of memory)"
    log WARN "planetiler run failed (${reason}): falling back to the extract of the Protomaps world build"
    rm -f "${OUT}/${NEW}"
    CHOSEN=extract
  fi
fi
if [[ "${CHOSEN}" == "extract" ]]; then
  build_extract && check_file "${OUT}/${NEW}" && BUILT=true
fi
if [[ "${BUILT}" != true ]]; then
  rm -f "${OUT}/${NEW}"
  die "no new basemap built (method ${CHOSEN}); current file left in place"
fi

chmod 644 "${OUT}/${NEW}"
if [[ "${KEEP_PREVIOUS}" == true && -f "${OUT}/basemap.pmtiles" ]]; then
  ln -f "${OUT}/basemap.pmtiles" "${OUT}/basemap.previous.pmtiles"
fi
mv -f "${OUT}/${NEW}" "${OUT}/basemap.pmtiles"
log INFO "basemap.pmtiles replaced: $(size_mb "${OUT}/basemap.pmtiles") MB, method ${CHOSEN}, $(($(date +%s) - START)) s"
