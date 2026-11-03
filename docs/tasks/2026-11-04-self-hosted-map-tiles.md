# Self-hosted map tiles (MT1)

## Goal
Every `fs-map-view` (/map and the 17 other usages) draws its background from one PMTiles file served by our own server with HTTP range requests. The browser makes no request to any host but ours for the map: no OSM tile server, no fonts, glyphs or sprites from elsewhere. A missing or broken file shows the existing tile-error overlay with retry; markers stay usable. Dark theme gets a dark map. The file is rebuilt monthly from OSM data. This closes the "Map tiles" item in `todo.md` and Eric's browser check in `todo-eric.md`.

## Decisions
Record as decision 158 and ledger row MT1. It amends `plan.md` line 141 ("Leaflet + OpenStreetMap tiles") and A58 (CSP `img-src` loses `https://tile.openstreetmap.org`). No recorded decision conflicts: none covers tiles or third parties for maps. Decision 31 (Photon geocoding in prod) is a separate third-party call and stays out of scope.
- D1 **Rendering: keep Leaflet and add `protomaps-leaflet` (option a).** Reasons:
  - Marker code stays as it is: divIcons, `data-kind`, keyboard Enter/Space, `alt`/`title`, count bubbles, the draggable position pin and the hidden label list. There is no clustering code to port.
  - About 40–60 kB gzip, lazy-loaded with Leaflet (MapLibre: about 250 kB). Labels are drawn on a canvas with the app's own CSS fonts, so there are no glyph or sprite files to host.
  - No web worker, so the CSP stays at `worker-src 'self'` (MapLibre needs `blob:` workers or its CSP build). Dark mode is built in (`flavor: 'dark'`). The raster stub for screenshots keeps working. One small dependency.
  - Rejected (b), MapLibre GL: a rewrite of markers and their a11y, WebGL in headless CI and the screenshot container, and self-hosted glyphs and sprites, all for smoother zooming that we don't need.
  - Rejected (c), TileServer GL: a second container with native dependencies and CPU rendering, and a second rendered set for dark mode.
- D2 **Tile schema:** Protomaps basemap (v4), built with the Protomaps Planetiler profile (`protomaps/basemaps`, pinned release) from a Geofabrik extract. `protomaps-leaflet` styles this schema; OpenMapTiles would need our own paint rules.
- D3 **Area: Geofabrik `europe/dach`** (owner, 2026-11-04), which covers the border towns and the seeded DE/AT regions. The profile adds Natural Earth for the low zooms worldwide. The file is 8–10 GB (estimate; the implementer measures it). Changing the area later is one argument of the build script.
- D4 **Served by the app** from a host folder mounted into the container, with no object storage (planner's choice):
  - Range reads are seeks, so 10 GB costs no app memory and the OS page cache keeps the hot tiles. The host needs about 50 GB of free disk (see Size).
  - It is a separate bind mount, so `backup.sh` (Postgres and the uploads volume) leaves it out; it can be rebuilt at any time.
  - Object storage only becomes worth it if the host disk is too small or traffic outgrows the host. It would add CORS and a `connect-src` entry, and visitors' IPs would go to that provider.
  - `GET /tiles/basemap.pmtiles` through `StaticResourceConfig` (`ResourceHttpRequestHandler` answers `Range` with 206), not a controller, so ArchUnit is unaffected. `app.map.tiles-dir` (prod `/tiles`, read-only).
  - `Cache-Control: public, max-age=86400` with ETag; when the file changes mid-session, the PMTiles client sees the new ETag and reloads. Media type `application/vnd.pmtiles`, not in `server.compression.mime-types`, so never gzipped (that would break byte ranges). The reverse proxy may serve the path directly later (optional, runbook).
- D5 **No contract change.** The URL is fixed (`/tiles/basemap.pmtiles`). Attribution and flavor are client constants.
- D6 **Refresh: a monthly cron job on the production host** (owner), `server/deploy/tiles.sh`, building with our own Planetiler run (owner):
  - Runs a pinned JDK container with the pinned basemaps jar, under `nice`, with a CPU limit of half the cores so the app stays responsive. Build time is about 1 h.
  - Downloads `dach-latest.osm.pbf` (about 5.5 GB) once per run and checks its `.md5`.
  - **Choosing the method is automatic** (planner's choice; `--method=planetiler|extract` forces one):
    - Before the build it reads `MemAvailable`. 16 GB or more: Planetiler in memory. 8–16 GB: Planetiler with `--storage=mmap` (node map on disk, slower).
    - Under 8 GB, or a failed or OOM-killed Planetiler run: the fallback, `pmtiles extract` of the DACH bbox from the Protomaps daily world build (`build.protomaps.com`). It is the same v4 schema, so the client is unchanged. It is a third-party download at build time only, never at runtime.
    - Every fallback logs `WARN` with the reason, so a host that is too small is noticed. A silent stale map would be worse than a slower build.
  - Checks 50 GB free disk first. Writes `basemap-<date>.pmtiles`, checks the header magic (`PMTiles`, v3) and a minimum size, renames it atomically to `basemap.pmtiles` and keeps `basemap.previous.pmtiles` for rollback. Logs to the journal; any failure leaves the current file in place.
  - Schedule: 1st of the month at 03:30, with a lock against overlapping runs.
- D7 **Attribution:** "© OpenStreetMap contributors", linked to `https://www.openstreetmap.org/copyright`, on every map, in both modes. This satisfies ODbL; Protomaps itself needs no credit.
- D8 **Tests use two modes.** If `tileUrl` or `window.__fsTileUrl` is set, MapView uses the existing raster `L.tileLayer`. Screenshots set it, so the baselines stay deterministic across engines. Otherwise MapView uses PMTiles, and e2e and local runs exercise that path against a small committed fixture.

## Scope
In: MapView rendering, serving, CSP, the fixture, the build and refresh script, compose, runbook and dev docs, tests, and the smoke check.
Out: the landing page (it loads no tiles), Photon geocoding, a CDN or object storage, offline caching of tiles, MapLibre, and areas outside D3.

## Changes
**Client** (`client/`)
- `package.json`: add pinned `protomaps-leaflet` and `pmtiles` (header probe).
- `src/app/shared/map-view/map-view.component.ts`:
  - Replace `OSM_TILE_URL` with `BASEMAP_URL = '/tiles/basemap.pmtiles'`.
  - In `init()`: with a raster override, keep today's code. Otherwise:
    - Probe with `new PMTiles(url).getHeader()`. If that fails, set `tileState('error')` and add no layer.
    - On success, add `leafletLayer({ url, flavor, lang, attribution })` with `maxZoom` from the header, and set `'ok'` on the layer's `load`.
  - `reloadTiles()` probes again. A lazy `import('protomaps-leaflet')` sits next to Leaflet.
  - Flavor follows `ThemeService.isDark` through an `effect`, which swaps the layer. `lang` follows the UI language. New host attribute `data-basemap="light|dark|raster"` for tests.
- New `src/app/shared/map-view/basemap.ts`, pure: `basemapFlavor(isDark)`, `basemapLang(lang)` (`gws`→`de`, else itself), `OSM_ATTRIBUTION`.
- `ngsw-config.json`: no change. `/tiles/*.pmtiles` matches no asset group, so the service worker passes it through. Add a note comment in the runbook so nobody adds it to the cache.
- i18n: none new. `shared.map.tilesFailed` and `shared.map.retry` cover the error.

**Server** (`server/`)
- `config/AppProperties.java`: new `map.tilesDir` (default empty: not served, 404); `security.csp.imgSrc` default becomes `""`.
- `config/StaticResourceConfig.java`: when `tilesDir` is set, `/tiles/**` → `file:<dir>/`, `PathResourceResolver` only (no `.br` resolver), with the cache control from D4.
- `application.properties` and `application-prod.properties`: `app.map.tiles-dir=/tiles`. Add a mime mapping `pmtiles=application/vnd.pmtiles` (`WebServerFactoryCustomizer` in `StaticResourceConfig`). `application-local.properties` and `application-e2e.properties`: `app.map.tiles-dir=../e2e/fixtures/tiles/`.
- `SecurityConfig`: nothing to add, since `anyRequest().permitAll()` covers it. `ApiRateLimitFilter` only counts `/api/**`, so the 20–40 range requests per map view are not limited.

**Fixture and scripts**
- `e2e/fixtures/tiles/basemap.pmtiles`: Zurich bbox (8.4,47.3,8.65,47.45), maxzoom 13, at most 3 MB. It is built once by `scripts/tiles-fixture.sh` (same pinned profile, `--bounds`), and the commit message notes how. Local dev uses it too: a real map around Zurich with no download.
- `server/deploy/tiles.sh` (D6), with `--area` (default `europe/dach`), `--method` and `--out` (default `${TILES_DIR}`).
- `server/deploy/compose.yaml`: on `app`, add the volume `${TILES_DIR:-./tiles}:/tiles:ro`. `compose.local.yaml`: mount `../../e2e/fixtures/tiles`.
- `server/deploy/smoke.sh`: `curl -r 0-126` on `/tiles/basemap.pmtiles` must give 206 and the `PMTiles` magic.

**Docs**
- `docs/runbook.md`: how to install the timer, how to roll back (swap in `basemap.previous.pmtiles`), disk and RAM needs, an optional proxy `file_server` for `/tiles/`, and how to check with `curl -r`.
- `docs/dev-environment.md` (fixture, `scripts/tiles-fixture.sh`), `docs/conventions.md` A58 (new `img-src`), `plan.md:141`, decision 158, ledger MT1.
- `todo.md`: close "Map tiles". `todo-eric.md`: rephrase the check as "map background loads on the server".

**Size, host needs, traffic (estimates; the implementer measures the real DACH build)**

- DACH: extract about 5.5 GB → PMTiles z0–15 about 8–10 GB.
- RAM: 16 GB free for the in-memory build, on top of the app and Postgres, so a host with about 24–32 GB in total. Below that the fallback in D6 applies.
- Disk: about 50 GB free (extract 5.5, Planetiler temp about 10, new, current and previous file 10 each).
- Traffic: a map view fetches about 0.3–1 MB in 20–40 range requests, gzipped inside the file; the area does not change that. 100k map views a month come to about 30–100 GB, and 1M to about 0.3–1 TB. Both are inside the traffic a typical Swiss VPS includes (often several TB to unmetered). If Tomcat threads get busy at peaks, the proxy can serve `/tiles/` directly (D4).

## UI
- Visible change: a vector map with German or local labels instead of OSM raster, and a dark map in dark mode. The attribution text changes.
- No new controls or texts, so no designer mockup is needed. The dark flavor needs a check by eye for contrast with the marker colours (`#25834a`, `#1c6f91` …).

## Tests
- Client unit: `basemap.spec.ts` (flavor and lang mapping). `m2-shared.spec.ts`, updated on purpose: the existing case runs in raster mode. New cases:
    - A failed probe gives `data-tiles="error"`, and retry probes again.
    - A theme switch gives `data-basemap="dark"`.
- Server:
  - `StaticResourceTilesIT` (MockMvc on a temp dir):
    - Range gives 206 with `Content-Range` and `Accept-Ranges: bytes`.
    - Media type and cache control are right.
    - With `Accept-Encoding: gzip` there is no `Content-Encoding`.
    - A missing file gives 404. An empty `tilesDir` means `/tiles/**` is not served.
  - `ContentSecurityPolicyTest:36` expectation changes on purpose: no OSM host.
- e2e `e2e/tests/map/mt1-tiles.spec.ts`, `@MT1`, all projects:
  1. Smoke check (todo.md): a range request for bytes 0–126 gives 206 and the magic `PMTiles` with version 3.
  2. `/map` reaches `data-tiles="ok"`, and a tile canvas is drawn.
  3. Every request during `/map` goes to the app origin (no third-party host).
  4. Routing `/tiles/**` to 404 shows `map.page.map.tileError`. After unroute, retry gives `ok`.
  5. In dark theme the map has `data-basemap="dark"`.
- Screenshots: `e2e/screenshots/_setup.ts` sets `window.__fsTileUrl='/__fs-stub-tile/{z}/{x}/{y}.png'` (addInitScript) and routes that path to `tile.png`. Drop `TILE_HOSTS`.
  - New screens: `/map` with the vector fixture, light and dark (`map.screen.ts`, `@MT1`), with a larger tolerance or masked tile pane only if the engines differ. Never update a baseline just to make a test pass.
  - The attribution text changes map baselines on purpose. They are regenerated in the end batch (decision 69).
- `server/deploy/smoke.sh`: the range check.

## Done when
- [ ] No reference to `tile.openstreetmap.org` is left in client, server, CSP or e2e (`git grep`).
- [ ] `/map` and a store page show the vector map in local dev and in e2e, light and dark, and every request goes to our own origin.
- [ ] A missing tile file shows the overlay with retry, and markers keep working.
- [ ] Range responses are 206 and uncompressed, and `/api` rate limits are unaffected.
- [ ] `tiles.sh` built a DACH file on a test machine with both methods, the automatic switch to the fallback was tested (forced low RAM and a killed run), and the file size, RAM, disk and time are recorded in the runbook.
- [ ] Unit, IT, e2e `@MT1` and `smoke.sh` are green. `npm run lint` and `i18n:check` pass. Feature coverage knows MT1.
- [ ] Docs, decision 158, ledger MT1, `todo.md` and `todo-eric.md` are updated.

## Risks
- `protomaps-leaflet` may not fire `tileerror`. The header probe covers a missing file, but not a corrupt range mid-session. Accept that, or count failed tile fetches through the layer's fetch wrapper.
- The hourlong DACH build on the production host competes with the app for CPU, disk I/O and page cache. Mitigated by `nice`, the CPU limit and the 03:30 slot. Watch latency on the first run.
- A host with less than 16 GB free falls back every month: still correct, but the build then depends on Protomaps' world build. The `WARN` makes that visible.
- 8–10 GB in 3 files plus the temp files can fill a small disk. The script checks for 50 GB free before it starts and aborts without touching the current file.
- Canvas text rendering differs between engines, so vector screenshots can be flaky. The raster stub keeps the existing baselines stable.
- Canvas rendering on old phones is slower than WebGL. Watch /map on a mid-range phone.
- The pinned basemaps jar and Planetiler need periodic upgrades. Renovate or `todo.md` tracks them.
- Hosting is not chosen yet (`docs/launch-report.md`). The host size above (24–32 GB RAM, about 50 GB free disk) belongs in that choice.
