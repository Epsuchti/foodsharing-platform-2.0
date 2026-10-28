# Milestone 7 "Launch readiness" + final integration — ordered task plan (planner)

Governing text: plan.md §17, §18, §19, §20 (M7), §21; decisions 3 (human steps replaced by AI personas / automated checks) and 69 (screenshot baselines and design rounds in one end batch). Conventions in `docs/conventions.md` stay binding.

## Part A — Starting point

Exists and is reused: `importRegionsCsv` (`/api/admin/regions/import`, `region/service/RegionsCsvImportService.java`, columns `parent_slug,slug,name,type,time_zone,country,postcodes`), EinAb past import (`/api/regions/{id}/admin/past-import/{preview,confirm}`, `intropickup/service/PastImportService.java`), `config/ContentSecurityPolicy.java` + tests, `ProdLikeProfileTest`, `QueryCountIT`, `IndexUsagePostgresIT`, `LiquibasePostgresIT`, `AccountDeletionIT`, `history/RetentionPolicy` + `HistoryRetentionJob`, `common/jobs/CleanupTask`, Prometheus registry, structured logstash logs in prod, `e2e/scripts/feature-coverage.mjs --strict`, `e2e/scripts/screenshots.sh`, `e2e/scripts/lighthouse.mjs`, `scripts/er-diagram.sh`, `scripts/ledger.py`, `.github/workflows/ci.yml`.

Missing: stores CSV import; Zürich data files; `server/deploy/release.sh` still targets the reference image; no `docs/runbook.md`, backup/restore scripts, Prometheus/Grafana/alert files, load test, security/privacy review docs, secrets inventory, dependency scanning; a11y sweep lists only M1 routes; ledger backend columns and Milestones table stale; launch report stops at M2; plan.md checkboxes unticked.

Rules: Queue B owns `server/`, `scripts/`, `server/deploy/`, `docs/`, `.github/`, plus API-level e2e specs (no UI). Queue F owns `client/`, `e2e/screenshots/`, `e2e/__screenshots__/`, UI e2e specs. Decisions in `docs/decisions.md` (next number 78); ledger via `scripts/ledger.py`. New deliverable IDs (add in B1): `D16.1` regions import, `D16.2` stores import, `D16.3` EinAb import end-to-end, `D19.1` prod profile/secrets/image, `D19.2` runbook+backups, `D19.3` monitoring, `D17.1` load test, `D17.2` security review, `D17.3` privacy review, `D18.1` CI hardening, `D18.2` baselines + design rounds, `D18.3` axe/i18n/Lighthouse final.

## Part B — Queue B (backend worker), one run per task, in order

### B1 Zürich regions data (D16.1)
- `docs/data/zuerich/regions.csv` in the `importRegionsCsv` format: `kanton-zuerich` → `zuerich-stadt` (CITY, decision 26) with its Kreise as PART_OF_TOWN, sub-regions modelled on foodsharing.network (Winterthur, Limmattal, Zürcher Oberland, Zürcher Unterland, Zimmerberg, Glattal, Knonaueramt, Weinland, Pfannenstiel, Furttal) with postcodes; header comment: model pending ambassador confirmation.
- `scripts/import-csv.mjs regions|stores|einabs <file> --base-url --session <fs_session> [--region <slug>] [--confirm]`: posts the CSV to the existing endpoints (dry run by default, prints preview, `--confirm` imports); session from an ORGA magic-link login, never a dev endpoint.
- `RegionsCsvImportZuerichIT` (Postgres, schema only): dry-run then live, every row CREATED, closure rows to `europa`, postcode rows, second run UNCHANGED, history `REGIONS_IMPORTED`. Unit test: every seed region slug also appears in the Zürich file.
- Ledger rows D16.1–D18.3 added; `docs/runbook.md` skeleton with "Initial data" section.

### B2 Stores CSV import (D16.2, backend)
- Contract: new tag `store-admin`, `POST /api/regions/{regionId}/stores/import` with `StoresCsvImport {csv, dryRun}` → `StoresImportPreview {rows[], created, updated, skipped, errors}`; permission store coordination of the region (decision 50); `x-history: StoreAction.STORES_IMPORTED`. Columns `name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,team_status,public_info,manager_foodsaver_numbers`. Dedupe key normalised name + postcode within the region; missing coordinates geocoded; unknown category/chain → row error `UNKNOWN_CATEGORY`/`UNKNOWN_CHAIN`; unknown manager number → `UNKNOWN_USER` warning; size limit `IMPORT_TOO_LARGE`.
- `StoresCsvImportService`, `StoreAdminController`, unit test + Postgres IT with `docs/data/zuerich/stores.csv` (anonymised model, ~40 stores).
- API-level e2e `e2e/tests/stores/s2-import-api.spec.ts @S2 @D16.2`; i18n for new error codes; decision row.

### B3 EinAb CSV import end-to-end (D16.3)
- `docs/data/zuerich/einabs-sample.csv` (einab-tabelle export format) + `store-aliases.csv`; `PastImportZuerichIT` (Postgres): regions (B1) + stores (B2) imported, users created via the service layer, aliases, preview → confirm; asserts dedupe by user+day+store, UNKNOWN_USER/USER_OTHER_REGION reported not imported, counted bookings visible in newcomer progress, rerun imports nothing, job tracker finishes.
- Fix what the run reveals (umlaut/case alias matching, day boundary time zone, large files), each with a unit test. `scripts/import-csv.mjs einabs`; runbook "Initial data" finished with exact commands and expected output.

### B4 Production profile, image, secrets, release script (D19.1)
- `application-prod.properties`: Hikari pool size, graceful shutdown, management port 9090 (health/Prometheus off the public port; e2e/local keep 8080), Photon geocoding URL, storage settings, `app.mailbox.inbound`, structured JSON log fields (app, version). `StructuredLoggingTest`: one JSON object per line with traceId, level, logger.
- `docs/secrets.md`: every `${VAR}` in prod properties with generation command, rotation, consequence of loss; `ProdSecretsInventoryTest`; `server/deploy/.env.example`.
- `server/Dockerfile`: non-root, HEALTHCHECK on 9090 liveness, JAVA_OPTS defaults, `/data` volume. `server/deploy/compose.yaml` (+ `compose.local.yaml` with Mailpit).
- `server/deploy/release.sh` rewritten for this repo (image `ghcr.io/<owner>/foodsharing-platform`, version.txt, `--build`, buildx amd64+arm64 `--push`/`--check`, tags, git tag `v<version>`, refuses dirty tree). `server/deploy/smoke.sh` (compose up, readiness, `/` 200, app-info version, dev endpoints 404).
- Decision row; runbook "Build & release", "Configuration".

### B5 Runbook, backups, restore (D19.2)
- `docs/runbook.md` complete: topology, start/stop/upgrade/rollback, health, jobs/ShedLock, outbox stuck, breakers, disk/uploads, secret rotation, magic links, IP blocks, support requests (export/deletion), log queries by traceId, incident checklist.
- `server/deploy/backup.sh`, `restore.sh`, `verify-backup.sh`; PITR documented. `BackupRestoreIT` (dump container A, restore container B, equal checksums/row counts). shellcheck on deploy scripts.

### B6 Monitoring and alerting (D19.3)
- Metrics audit (outbox pending/failed, push/Telegram failures, breaker state, job duration + last success, WebSocket sessions, rate-limit rejections, login failures, job queue depth) → `common/metrics/OperationsMetrics`; `docs/monitoring.md`.
- `server/deploy/monitoring/{prometheus.yml,alerts.yml,grafana-dashboard.json}`, `compose.monitoring.yaml`; `MetricsContractTest` (every metric in alerts/dashboard exists).

### B7 Automated load test (D17.1)
- `e2e/load/k6/main.js` via `grafana/k6` Docker, against the jar with Postgres (compose); scenarios: landing/public region/explainer, dashboard, store list, pickups, sign-up churn, messaging, forum, map bubbles, search, bells; thresholds p95 < 200 ms, failures < 0.1 %; ramp 10→100 VUs, 5 min. `e2e/load/run.sh` → `docs/load-test.md`. Fix failing endpoints (indexes, N+1, caching), each with a test. CI `load` job on dispatch/schedule.

### B8 Security review (D17.2)
- `docs/security-review.md` (OWASP Top 10 2021 + ASVS L2, evidence per row). CSP strictness tests, rate-limit inventory test, upload sniffing, sanitising, SSRF allow-lists, WebSocket origin, OAuth PKCE/redirect exact match, actuator on management port, IDOR spot checks.
- `e2e/tests/a11y/csp.spec.ts @D17.2` (no CSP violations on any route).
- `npm audit --omit=dev --audit-level=high` in CI; OWASP dependency-check Maven profile (scheduled); Dependabot. Fix findings with tests.

### B9 Privacy review (D17.3)
- `docs/privacy/data-inventory.md` + `DataInventoryTest`; `docs/privacy/retention.md` + `RetentionTableTest`; review retention values; every expiring personal-data table has a `CleanupTask`.
- `AccountDeletionIT` walks every FK to `user_account` (each table classified deleted/anonymised/kept); export zip contains every personal-data module.

### B10 CI hardening, ER diagram, close tasks (D18.1)
- `ci.yml`: strict coverage behind an input, oasdiff on main against last tag, `er-diagram.sh --check`, npm audit, dependency-check, load, shellcheck, release version check, dependency-review on PRs.
- Ledger backend columns filled; launch report "What is built" M2–M7 backend and "How to run" extended; open decision rows recorded.

## Part C — Queue F (frontend worker), after its feature queue
- **F1** Stores import page (after B2): region admin → Stores → Import CSV with dry-run preview and confirm; `e2e/tests/stores/s2-import.spec.ts @S2 @D16.2`.
- **F2** Screenshot spec completeness for every route + generate all baselines in Docker; a second plain run must show 0 diffs.
- **F3** Design round 1 (function & flow) as five AI personas (newcomer, foodsaver, store manager, ambassador, orga) on mobile and desktop; findings in `docs/design-review-m7.md`; fix; re-baseline.
- **F4** Design round 2 (visual polish); fix; re-baseline.
- **F5** Design round 3 (scripted persona walkthroughs, decision 3); fix; re-baseline; D11.5.5 done with launch-report note.
- **F6** Final axe sweep over every route (both themes, mobile), 5-language smoke `e2e/tests/shell/i18n-smoke.spec.ts`, Lighthouse hard thresholds on `/` and `/onboarding` plus report-only pages in `docs/lighthouse.md`.

## Part D — Integrator final gate
1. Merge; `node scripts/check-openapi.mjs`; oasdiff.
2. `./mvnw clean verify`.
3. Playwright on all 5 projects + e2e typecheck.
4. Screenshot suite in Docker, zero diffs.
5. `feature-coverage.mjs --strict` green → enable strict coverage in CI.
6. ER diagram check, `release.sh --check`, `smoke.sh`, load test refresh.
7. Every ledger row done (human-dependent rows reference the launch report), Milestones M2–M7 done with 3/3 rounds.
8. Tick all plan.md checkboxes (D5.4 and D11.5.5 with notes).
9. Fable final review `docs/review-m7.md`.
10. `docs/launch-report.md` finalised.

## Part E — Ordering and risks
B1→B2→B3, B4→B5→B6 sequential; B7 needs B4's compose; B8/B9 independent; B10 last. F1 waits for B2; F2–F6 in order after the frontend feature queue. Strict coverage only after F6. Load test on Postgres, not H2. Dependency-check off the PR path. Management port change must not affect e2e/local health waits.
