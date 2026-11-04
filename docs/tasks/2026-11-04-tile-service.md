# Map tiles as their own service on K3s (MT2)

## Goal
In K3s, `/tiles/basemap.pmtiles` is served by a small always-on tile server, not by the Spring app, and the monthly build runs as a CronJob that only gets a lot of RAM while it runs. Memory, duration, success and the tile server are visible in Grafana (no alerts in this task, owner 2026-11-04). Browsers see no change: the URL, the origin, the CSP, the range responses and the ETag behaviour stay as in MT1. Local dev, e2e and the compose deploy keep working unchanged.

## Decisions
Record as decision 163 (amends 159 D4 and D6) and ledger row MT2. `main` ends at 160, but 161 (screenshots CI, `ci/screenshots-update-workflow`) and 162 (UI polish batch, `feat/ui-polish-batch`) are already taken on open branches; check again when merging. Owner answers 2026-11-04: server has 32 GB or more (Q1 a), test shares prod's tiles (Q2 a), Caddy (Q3 a), compose stays (Q4 a), no alerts for now (Q5). No conflict with a recorded decision: 159 explicitly allowed "the reverse proxy may serve the path directly later".
- D1 **Tile server: Caddy** (pinned `caddy:2.x-alpine`), `file_server` on a read-only volume. It answers ranges with 206 and sends a strong ETag (modtime and size), so the PMTiles client notices a swapped file as before. It has built-in Prometheus metrics and JSON access logs. The Caddyfile sits in a ConfigMap: only `/tiles/basemap.pmtiles` is served (everything else gives 404, so `basemap.previous.pmtiles` is not public), `Content-Type: application/vnd.pmtiles`, `Cache-Control: public, max-age=86400`, `X-Content-Type-Options: nosniff`, no `encode` (no compression), `admin off`, metrics on a separate `:9180` site, `/healthz` → 200 even when the file is missing. Rejected: `go-pmtiles serve` (it serves z/x/y, so the client would have to change) and nginx (it needs an exporter sidecar for metrics).
- D2 **Routing: Traefik, same host, path `/tiles`**, added as a second path in the app's existing Ingress. That means the same certificate and the same origin, the CSP stays the same and no CORS is needed. Traefik prefers the longer path. The app does not proxy the tiles (that would keep Tomcat threads busy for nothing).
- D3 **Builder image `ghcr.io/epsuchti/foodsharing-tiles-builder:<n>`**, published from the app repo by its own workflow. Multi-stage build: Maven builds the pinned `protomaps/basemaps` jar; the runtime is a JRE plus bash, curl, coreutils, the `pmtiles` binary (copied from `protomaps/go-pmtiles:v1.31.2`) and our script, running as uid 10001. A K8s pod cannot run `docker run`, so the build logic moves out of `tiles.sh` into the image.
- D4 **The method follows the container limit**, not the host. `build-basemap.sh` reads `min(cgroup memory.max, MemAvailable)` and the CPUs from `cpu.max`, then applies the MT1 thresholds: ≥16 GiB in-memory, 8–16 GiB mmap, <8 GiB or an OOM/failure → `pmtiles extract`, with a `WARN` for every fallback. Changing `builder.resources.limits.memory` in the values therefore selects the method; `TILES_METHOD` forces one.
- D5 **Shared file: one PVC** `<release>-tiles` (local-path, RWO, 60Gi, `helm.sh/resource-policy: keep`). RWO is per node and there is one node, so the CronJob pod (read-write) and the tile server (subPath `basemap`, read-only) mount it at the same time. Layout: `basemap/` (current, previous and the new temporary file, so the rename stays atomic on one filesystem) and `work/` (pbf, sources, temp files). Swap, keeping the previous file and "failure keeps the old file" work as in MT1. The script also writes `work/last-build.json` (method, seconds, peak memory from `memory.peak`, file size, OSM timestamp) and logs the same values in one line, because Prometheus and Loki keep data for only 7 days.
- D6 **CronJob** `30 3 1 * *`, `timeZone: Europe/Zurich`, `concurrencyPolicy: Forbid`, `backoffLimit: 0`, `activeDeadlineSeconds: 14400`, `startingDeadlineSeconds: 21600`, history 3 succeeded and 3 failed. **Memory request = limit, 16Gi** (owner: the server has 32 GB or more, so the in-memory build; measure the first run, then fix the number), so the scheduler never overcommits the node and the app is never OOM-killed because of the build. If the node can't spare it, the pod stays Pending and fails at the deadline; lowering the limit then picks mmap or extract (D4). CPU request 500m, limit 4. **Bootstrap:** no Helm hook (it would block Helm for an hour). `scripts/deploy-tiles.sh` checks for the file in the tile pod and, if it is missing, starts `kubectl create job --from=cronjob/... <release>-tiles-initial-<date>`. The same command is the manual rebuild (documented).
- D7 **Tile server resources:** request 10m CPU and 64Mi, limit 500m and 256Mi. Page cache is charged to the pod, so the limit caps how many hot tiles stay in RAM. Reads come from NVMe, so that's fine; the dashboard shows it.
- D8 **Test shares prod's tiles** (owner, Q2): in `foodsharing-platform-test` the chart renders only an `ExternalName` Service pointing at the prod tile service (Traefik already has `allowExternalNameServices: true`). Test then has no PVC, no CronJob and no second 50 GB.
- D9 **Spring keeps `/tiles/**`** for local dev, e2e (Zurich fixture, `window.__fsTileUrl` stub) and compose. In K8s the values set `APP_MAP_TILES_DIR: ""`, so a misrouted request gives 404 instead of reading a folder that isn't there. No app code change.
- D10 **`tiles.sh` stays as a host wrapper** (compose deploy and `scripts/tiles-fixture.sh`). Same arguments; it runs the builder image (pulled from GHCR, or built locally with `--build-image`) with `--memory`/`--cpus` from `MemAvailable`/half the cores. Both paths therefore run the same script.

## Scope
In: builder image, workflow and script refactor (app repo); a new chart, values, deploy script, dashboard and docs (hosting repo).
Out: alerts of any kind (no PrometheusRule, owner: "No alerts for now"; `todo.md` gets an item "MT2: tile alerts (build failed or stale, OOM, tile server down, file missing)"), client and server code, the compose tile serving, a CDN or object storage, a second node or RWX storage, and changing the area.

## Changes
**App repo** (`/Users/eric/develop/foodsharing-platform-2.0`, branch off `main`)
- New `server/deploy/tiles/Dockerfile`, `server/deploy/tiles/build-basemap.sh` (logic moved from `tiles.sh`: area, bbox, maxzoom, md5, checks, swap, previous, lock; env and args `TILES_OUT`, `TILES_WORK`, `TILES_METHOD`; cgroup reading per D4; `last-build.json`), `server/deploy/tiles/version.txt` (`1`).
- `server/deploy/tiles.sh`: becomes the wrapper (D10); its help and arguments stay. `scripts/tiles-fixture.sh` stays unchanged and must still produce a fixture of 3 MB or less.
- New `.github/workflows/tiles-image.yml`: on push to `main` touching `server/deploy/tiles/**`, plus `workflow_dispatch`. buildx `linux/amd64,linux/arm64`, pushes `<version.txt>` and `latest`, using `GITHUB_TOKEN` (same pattern as the `release` job in `ci.yml`). It refuses to overwrite an existing `<version>` tag.
- Docs: `docs/runbook.md` §1 (topology line), new §10.4 "K3s: tile service" (manual rebuild, rollback with `kubectl exec` into a debug pod on the PVC, checks, needs), §10.3 (measured values from `last-build.json`). Also decision 163, ledger MT2, `todo.md` (alerts later), `docs/dev-environment.md` (builder image), and `todo-eric.md` (the MT1 check now runs on K3s; record the node's free RAM).

**Hosting repo** (`/Users/eric/develop/hosting-lifetour`). Work in its own worktree on branch `feat/foodsharing-tiles` off `main` (`git worktree add ../hosting-lifetour-tiles -b feat/foodsharing-tiles main`). Never touch the uncommitted `feat/mailserver` tree. It edits the same values files and docs, so the later merge has to resolve those by hand.
- New chart `charts/pmtiles-basemap/` (`Chart.yaml`, `values.yaml`, `README.md`, templates):
  - `mode: serve` (prod) renders: `deployment.yaml` (Caddy, uid 10001, read-only root, emptyDir for `/config` and `/data`, probes on `/healthz`), `service.yaml` (http 8080, metrics 9180), `configmap-caddyfile.yaml`, `pvc.yaml`, `cronjob-build.yaml`, `servicemonitor.yaml`.
  - `mode: external` (test) renders only the `ExternalName` Service to `<prod release>.<prod ns>.svc.cluster.local`.
  - Labels: `app.kubernetes.io/component: tiles-server|tiles-build`, so the existing Alloy relabeling makes them searchable in Loki.
- `charts/spring-boot-webapp`: generic `ingress.extraPaths: [{path, pathType, serviceName, servicePort}]` for the main host, default empty, so the other apps render the same as before (check with `helm template` diffs).
- `apps/foodsharing-platform/prod-values.yaml` and `test-values.yaml`: `ingress.extraPaths` `/tiles` → `foodsharing-platform-tiles`, `env.APP_MAP_TILES_DIR: ""`. New `tiles-prod-values.yaml` (serve, builder 16Gi, PVC 60Gi) and `tiles-test-values.yaml` (external).
- New `scripts/deploy-tiles.sh <app> <env> [builder-tag]`: `helm upgrade --install <app>-tiles-<env> ./charts/pmtiles-basemap -n <app>-<env> -f apps/<app>/tiles-<env>-values.yaml`, waits for the rollout, then the bootstrap from D6. `--rebuild` starts a manual Job. Also a `Makefile` target `deploy-tiles`.
- New `monitoring/grafana/dashboards/foodsharing-tiles.yaml` (ConfigMap pattern of `k8s-pod-resources.yaml`, variable namespace):
  - Builder: working set, RSS and limit (cAdvisor); CPU; duration (completion − start); age of the last success; last terminated reason (OOMKilled shows up here).
  - Tile server: memory and CPU; requests by status code; p50 and p95 latency; bytes sent.
  - Loki panel: `{app_kubernetes_io_component=~"tiles-.*"} |~ "WARN|ERROR|\\[tiles\\]"`.
- `install.sh` and `refresh-infra.sh` don't change: both already apply every file in the dashboards folder. That keeps install on a clean VM working.
- Docs: `docs/foodsharing-platform.md` (new section "Map tiles": deploy order, bootstrap, rebuild, rollback, needs, test uses prod), `docs/monitoring-and-logs.md` (the new dashboard; no alerts yet), `README.md` (helper `deploy-tiles.sh`).

## UI
None. Browsers keep the URL and the behaviour. No mockup.

## Tests
- App: `shellcheck` covers the new script (CI job). `scripts/tiles-fixture.sh` through the wrapper produces a fixture of 3 MB or less (manual, recorded in the commit). `docker build` of the builder in the workflow. Inside the container, `TILES_METHOD=extract` with a small bbox works, and `docker run --memory=4g` logs a fallback `WARN`. `StaticResourceTilesIT`, `@MT1` e2e and `smoke.sh` stay unchanged and green.
- Hosting: `helm lint charts/pmtiles-basemap charts/spring-boot-webapp`. `helm template` for both tile values files and for all `apps/*/*-values.yaml`; the diff against `main` may only add the `/tiles` path for foodsharing-platform. `kubeconform` if it is available. `bash -n` and shellcheck on `deploy-tiles.sh`.
- On the server (test first, then prod):
  - `smoke.sh` against `https://test.foodsharing.site`: 206 and the `PMTiles` v3 magic.
  - `curl -I` shows the ETag, the cache control and no `Content-Encoding`; `basemap.previous.pmtiles` gives 404.
  - Run a manual Job and watch memory in Grafana; after the swap, the ETag changes.

## Done when
**App repo**
- [ ] The builder image is in GHCR for amd64 and arm64. `tiles.sh` and `tiles-fixture.sh` work through it, and shellcheck is green.
- [ ] Runbook §1, §10.3 and §10.4, decision 163, ledger MT2, the `todo.md` alerts item, `dev-environment.md` and `todo-eric.md` are updated.
**Hosting repo** (branch `feat/foodsharing-tiles`, not merged without the owner)
- [ ] `helm lint` and `template` are clean, and the other apps render unchanged.
- [ ] Prod: the tile pod serves 206, the CronJob exists, and the initial Job built DACH (method, peak memory and duration recorded).
- [ ] Test serves the same file through the ExternalName.
- [ ] The dashboard shows the builder's memory against its limit, the duration and the tile requests and latency, and the Loki panel shows the build log. No PrometheusRule exists.
- [ ] Docs are updated.

## Risks
- 16 GiB is the owner's estimate (32 GB or more on the node) until the first run is measured; if the node is busier than expected, the build stays Pending until the deadline; lower the limit and get mmap or extract (D4).
- Without alerts, a failed or Pending monthly build is only noticed on the dashboard ("age of the last success"). Look at it after the 1st of each month until the alerts item is done.
- Prometheus and Loki keep 7 days, so the memory peak of a monthly run is only visible for a week. `last-build.json` and runbook §10.3 keep the number.
- The merge with `feat/mailserver` conflicts in the foodsharing values files and docs.
- Caddy's ETag format must stay strong. A Caddy upgrade has to be checked with `curl -I`.
- A second node later would need node affinity for the local-path PV (it pins itself) or RWX storage.
