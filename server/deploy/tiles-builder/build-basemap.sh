#!/usr/bin/env bash
# Builds the self-hosted map basemap (MT1/MT2, decisions 159 and 163, runbook §10): a PMTiles v3 file in the
# Protomaps basemap v4 schema, served as /tiles/basemap.pmtiles. Runs inside the builder image
# ghcr.io/epsuchti/foodsharing-tiles-builder (server/deploy/tiles-builder/Dockerfile): monthly as a K3s CronJob, or on a
# compose host through the wrapper server/deploy/tiles.sh.
#
#   build-basemap.sh [--area=europe/dach] [--method=auto|planetiler|extract] [--out=DIR] [--work=DIR]
#                    [--bbox=W,S,E,N] [--maxzoom=N] [--min-size-mb=N] [--min-free-gb=N] [--no-previous]
#
# Environment: TILES_OUT (--out, default /tiles/basemap), TILES_WORK (--work, default /tiles/work),
# TILES_METHOD (--method), TILES_MEM_AVAILABLE_MB (fakes the memory reading, e.g. to test the switch).
# Methods (auto picks one from min(container memory limit, MemAvailable), decision 163 D4):
#   planetiler  Planetiler run of the pinned Protomaps profile on the Geofabrik extract of --area
#               (>= 16 GiB: node map in RAM; 8-16 GiB: --storage=mmap, slower)
#   extract     `pmtiles extract` of --bbox from the Protomaps daily world build (same v4 schema). Used under 8 GiB
#               or when the Planetiler run fails or runs out of memory; every fallback logs WARN with the reason.
# The new file is written as basemap-<date>.pmtiles next to the current one, checked (PMTiles magic, version 3,
# minimum size) and renamed over basemap.pmtiles atomically; the old file stays as basemap.previous.pmtiles
# (rollback). Any failure leaves the current file untouched. A lock prevents overlapping runs. The result
# (method, seconds, peak memory, size, OSM date) goes to <work>/last-build.json and into one log line.
set -euo pipefail

SCRIPT="${BASH_SOURCE[0]}"
BASEMAPS_JAR="${BASEMAPS_JAR:-/opt/basemaps/protomaps-basemap.jar}"
PMTILES="${PMTILES:-pmtiles}"
SCHEMA_MAJOR=4
WORLD_BUILDS="https://build.protomaps.com"
WORLD_BUILDS_INDEX="https://build-metadata.protomaps.dev/builds.json"
GEOFABRIK="https://download.geofabrik.de"

AREA="europe/dach"
METHOD="${TILES_METHOD:-auto}"
OUT="${TILES_OUT:-/tiles/basemap}"
WORK="${TILES_WORK:-/tiles/work}"
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
    --work=*) WORK="${arg#*=}" ;;
    --bbox=*) BBOX="${arg#*=}"; BBOX_GIVEN=true ;;
    --maxzoom=*) MAXZOOM="${arg#*=}" ;;
    --min-size-mb=*) MIN_SIZE_MB="${arg#*=}" ;;
    --min-free-gb=*) MIN_FREE_GB="${arg#*=}" ;;
    --no-previous) KEEP_PREVIOUS=false ;;
    -h | --help) sed -n '2,21p' "${SCRIPT}"; exit 0 ;;
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
  flock -n 9 || die "another build holds ${LOCK}"
else
  mkdir "${LOCK}.d" 2> /dev/null || die "another build holds ${LOCK}.d (remove it if no build is active)"
  trap 'rmdir "${LOCK}.d" 2> /dev/null || true' EXIT
fi

days_ago() { date -u -d "-$1 day" +%Y%m%d 2> /dev/null || date -u -v-"$1"d +%Y%m%d; }
free_gb() { df -Pk "$1" | awk 'NR == 2 { print int($4 / 1024 / 1024) }'; }
md5_of() { if command -v md5sum > /dev/null; then md5sum "$1" | cut -d' ' -f1; else md5 -q "$1"; fi; }
size_mb() { echo $(($(wc -c < "$1") / 1024 / 1024)); }

# Memory the build may use: the container limit (cgroup v2 memory.max or v1 limit_in_bytes) capped by the host's
# MemAvailable, so the method follows the K8s limit and not the node (D4).
cgroup_limit_mb() {
  local raw=""
  if [[ -r /sys/fs/cgroup/memory.max ]]; then
    raw="$(cat /sys/fs/cgroup/memory.max)"
  elif [[ -r /sys/fs/cgroup/memory/memory.limit_in_bytes ]]; then
    raw="$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)"
  fi
  # "max", empty or the v1 "unlimited" value (close to 2^63) mean no limit
  if [[ "${raw}" =~ ^[0-9]+$ ]] && ((${#raw} < 16)); then echo $((raw / 1024 / 1024)); fi
}
mem_available_mb() {
  if [[ -n "${TILES_MEM_AVAILABLE_MB:-}" ]]; then
    echo "${TILES_MEM_AVAILABLE_MB}"
    return
  fi
  local avail=0 limit
  [[ -r /proc/meminfo ]] && avail="$(awk '/^MemAvailable:/ { print int($2 / 1024) }' /proc/meminfo)"
  limit="$(cgroup_limit_mb)"
  if [[ -n "${limit}" ]] && ((limit < avail || avail == 0)); then avail="${limit}"; fi
  echo "${avail}"
}
# CPUs: the cgroup quota (cpu.max "<quota> <period>", rounded up), else the online cores.
cpu_count() {
  local quota period
  if [[ -r /sys/fs/cgroup/cpu.max ]]; then
    read -r quota period < /sys/fs/cgroup/cpu.max
    if [[ "${quota}" =~ ^[0-9]+$ && "${period}" =~ ^[0-9]+$ ]] && ((period > 0)); then
      echo $(((quota + period - 1) / period))
      return
    fi
  fi
  getconf _NPROCESSORS_ONLN 2> /dev/null || echo 1
}
peak_mb() {
  local raw=""
  if [[ -r /sys/fs/cgroup/memory.peak ]]; then
    raw="$(cat /sys/fs/cgroup/memory.peak)"
  elif [[ -r /sys/fs/cgroup/memory/memory.max_usage_in_bytes ]]; then
    raw="$(cat /sys/fs/cgroup/memory/memory.max_usage_in_bytes)"
  fi
  if [[ "${raw}" =~ ^[0-9]+$ ]]; then echo $((raw / 1024 / 1024)); else echo null; fi
}

STAMP="$(date -u +%Y%m%d)"
NEW="basemap-${STAMP}.pmtiles"
START="$(date +%s)"
OSM_DATE=""

FREE="$(free_gb "${WORK}")"
((FREE >= MIN_FREE_GB)) || die "only ${FREE} GB free in ${WORK}, need ${MIN_FREE_GB} GB; current file left in place"
if [[ "$(free_gb "${OUT}")" -lt "${MIN_FREE_GB}" && "$(df -P "${OUT}" | awk 'NR==2{print $1}')" != "$(df -P "${WORK}" | awk 'NR==2{print $1}')" ]]; then
  die "less than ${MIN_FREE_GB} GB free in ${OUT}; current file left in place"
fi

build_planetiler() {
  local mem_mb="$1" storage heap_mb osm cpus run
  if ((mem_mb >= 16384)); then
    storage=ram
    heap_mb=$((mem_mb - 4096 > 24576 ? 24576 : mem_mb - 4096))
  else
    storage=mmap
    heap_mb=4096
  fi
  cpus="$(cpu_count)"
  log INFO "planetiler: ${AREA}, storage=${storage}, heap ${heap_mb} MB, ${MAXZOOM} max zoom, ${cpus} CPUs"
  [[ -r "${BASEMAPS_JAR}" ]] || { log WARN "${BASEMAPS_JAR} not found"; return 1; }
  mkdir -p "${WORK}/sources" "${WORK}/tmp" "${WORK}/run"
  osm="${WORK}/sources/$(basename "${AREA}").osm.pbf"
  log INFO "downloading ${GEOFABRIK}/${AREA}-latest.osm.pbf"
  rm -f "${osm}" "${osm}.md5"
  curl -fsSL -R --retry 3 -o "${osm}" "${GEOFABRIK}/${AREA}-latest.osm.pbf" || return 1
  curl -fsSL --retry 3 -o "${osm}.md5" "${GEOFABRIK}/${AREA}-latest.osm.pbf.md5" || return 1
  [[ "$(md5_of "${osm}")" == "$(cut -d' ' -f1 < "${osm}.md5")" ]] || { log WARN "md5 of ${osm} does not match"; return 1; }
  OSM_DATE="$(date -u -r "${osm}" +%Y-%m-%dT%H:%M:%SZ 2> /dev/null || true)"
  # Planetiler resolves its default source paths against ./data; run/data points at the work folder.
  run="${WORK}/run"
  ln -sfn .. "${run}/data"
  local args=(--area="${AREA}" --osm-path="data/sources/$(basename "${osm}")" --download --force
    --storage="${storage}" --threads="${cpus}" --tmpdir="${WORK}/tmp" --maxzoom="${MAXZOOM}" --output="${OUT}/${NEW}")
  # an explicit --bbox clips the Planetiler output too (the fixture); otherwise the whole extract is built
  if [[ "${BBOX_GIVEN}" == true ]]; then args+=(--bounds="${BBOX}"); fi
  # The heap stays 4 GiB under the limit and an OutOfMemoryError ends the JVM, so running out of memory gives a
  # failed run (and the extract fallback) instead of the kernel killing the whole container.
  (cd "${run}" && nice -n 10 java -Xmx"${heap_mb}"m -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir="${WORK}/tmp" \
    -jar "${BASEMAPS_JAR}" "${args[@]}")
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
  OSM_DATE="${build:0:4}-${build:4:2}-${build:6:2}"
  log INFO "extract: bbox ${BBOX}, ${MAXZOOM} max zoom, from ${WORLD_BUILDS}/${build} (tiles ${version})"
  (cd "${OUT}" && nice -n 10 "${PMTILES}" extract "${WORLD_BUILDS}/${build}" "${OUT}/${NEW}" \
    --bbox="${BBOX}" --maxzoom="${MAXZOOM}")
}

check_file() {
  local file="$1" magic version
  [[ -s "${file}" ]] || { log WARN "${file} is missing or empty"; return 1; }
  magic="$(head -c 7 "${file}")"
  version="$(od -An -tu1 -j7 -N1 "${file}" | tr -d ' ')"
  [[ "${magic}" == "PMTiles" && "${version}" == "3" ]] || { log WARN "${file} is not PMTiles v3"; return 1; }
  (($(size_mb "${file}") >= MIN_SIZE_MB)) || { log WARN "${file} is only $(size_mb "${file}") MB (< ${MIN_SIZE_MB})"; return 1; }
}

# One line and <work>/last-build.json, because Prometheus and Loki keep only 7 days (D5).
record() {
  local status="$1" size_bytes=null seconds peak
  seconds=$(($(date +%s) - START))
  peak="$(peak_mb)"
  [[ "${status}" == ok ]] && size_bytes="$(wc -c < "${OUT}/basemap.pmtiles" | tr -d ' ')"
  if ! printf '{"status":"%s","finished":"%s","area":"%s","method":"%s","requested":"%s","memoryMb":%s,"peakMemoryMb":%s,"seconds":%s,"sizeBytes":%s,"osmDate":"%s"}\n' \
    "${status}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${AREA}" "${CHOSEN}" "${METHOD}" "${MEM_MB}" "${peak}" "${seconds}" \
    "${size_bytes}" "${OSM_DATE}" > "${WORK}/last-build.json.tmp" \
    || ! mv -f "${WORK}/last-build.json.tmp" "${WORK}/last-build.json"; then
    log WARN "could not write ${WORK}/last-build.json"
  fi
  log INFO "result status=${status} method=${CHOSEN} seconds=${seconds} peakMemoryMb=${peak} memoryMb=${MEM_MB} sizeBytes=${size_bytes} osmDate=${OSM_DATE:-unknown}"
}

MEM_MB="$(mem_available_mb)"
CHOSEN="${METHOD}"
if [[ "${METHOD}" == "auto" ]]; then
  if ((MEM_MB >= 8192)); then
    CHOSEN=planetiler
  else
    log WARN "only ${MEM_MB} MB of memory available (< 8192): falling back to the extract of the Protomaps world build"
    CHOSEN=extract
  fi
fi
log INFO "method ${CHOSEN} (requested ${METHOD}, ${MEM_MB} MB available, $(cpu_count) CPUs), output ${OUT}/${NEW}"

rm -f "${OUT}/${NEW}"
BUILT=false
if [[ "${CHOSEN}" == "planetiler" ]]; then
  status=0
  build_planetiler "${MEM_MB}" || status=$?
  if ((status == 0)) && check_file "${OUT}/${NEW}"; then
    BUILT=true
  elif [[ "${METHOD}" == "auto" ]]; then
    reason="exit ${status}"
    ((status == 3)) && reason="out of memory (exit 3)"
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
  record failed
  die "no new basemap built (method ${CHOSEN}); current file left in place"
fi

chmod 644 "${OUT}/${NEW}"
if [[ "${KEEP_PREVIOUS}" == true && -f "${OUT}/basemap.pmtiles" ]]; then
  ln -f "${OUT}/basemap.pmtiles" "${OUT}/basemap.previous.pmtiles"
fi
mv -f "${OUT}/${NEW}" "${OUT}/basemap.pmtiles"
log INFO "basemap.pmtiles replaced: $(size_mb "${OUT}/basemap.pmtiles") MB, method ${CHOSEN}, $(($(date +%s) - START)) s"
record ok
