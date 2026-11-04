#!/usr/bin/env bash
# Rebuilds the committed map tile fixture e2e/fixtures/tiles/basemap.pmtiles (MT1, docs/dev-environment.md): central
# Zurich (8.48,47.35,8.6,47.41, where the seeded stores and regions are) up to zoom 13 in the same Protomaps v4
# schema as production, at most 3 MB. Zoom 0-9 alone take 2.4 MB, so the plan's wider box (8.4,47.3,8.65,47.45)
# would come to 6 MB. Local dev and e2e serve it as /tiles/basemap.pmtiles. Needs Docker and network access.
#
#   scripts/tiles-fixture.sh                      extract from the Protomaps world build (a few MB of range reads)
#   scripts/tiles-fixture.sh --method=planetiler  our own Planetiler run on the Geofabrik Switzerland extract
#                                                 (downloads about 3 GB of sources, mostly the low-zoom layers)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
METHOD="extract"
for arg in "$@"; do
  case "${arg}" in
    --method=*) METHOD="${arg#*=}" ;;
    *) echo "unknown argument ${arg}" >&2; exit 2 ;;
  esac
done
OUT="${ROOT}/e2e/fixtures/tiles"

TILES_WORK_DIR="${TILES_WORK_DIR:-${ROOT}/e2e/test-results/tiles-work}" \
  "${ROOT}/server/deploy/tiles.sh" --method="${METHOD}" --area=europe/switzerland --bbox=8.48,47.35,8.6,47.41 \
  --maxzoom=13 --out="${OUT}" --min-size-mb=0 --min-free-gb=5 --no-previous

size=$(wc -c < "${OUT}/basemap.pmtiles")
if ((size > 3 * 1024 * 1024)); then
  echo "ERROR fixture is ${size} bytes, more than 3 MB" >&2
  exit 1
fi
echo "fixture ${OUT}/basemap.pmtiles: ${size} bytes"
