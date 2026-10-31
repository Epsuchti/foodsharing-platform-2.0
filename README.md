# Foodsharing Platform 2.0

One Spring Boot 4 jar that bundles the Angular 22 client. Plan: [`plan.md`](plan.md) · conventions:
[`docs/conventions.md`](docs/conventions.md) · build state: [`docs/ledger.md`](docs/ledger.md) · decisions:
[`docs/decisions.md`](docs/decisions.md) · agent rules: [`AGENTS.md`](AGENTS.md).

Requirements: JDK 21, Node 24, Docker (Testcontainers, screenshots, ER diagram, contract diff).

## Local development (plan §4.2)

| Command | What it does |
|---|---|
| `cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` | Backend on :8080 with H2 + seed data, dev user switcher, fake Google login |
| `cd client && npm start` | Regenerates the API client, `ng serve` on :4200 with proxy to :8080 |
| `server/start_smtp_server.sh` | Mailpit for login links and notifications (SMTP :2525, UI http://localhost:8025) |
| `cd server && ./mvnw clean package && java -jar target/foodsharing-platform-*.jar` | Production-like single jar (client included) |
| `cd server && ./mvnw -Pskip-client -DskipTests package` | Fast backend-only jar (no Angular) |
| `cd server && ./mvnw verify` | Backend tests incl. ArchUnit, Modulith and Postgres ITs (Docker) |

## Repo tooling (root `package.json`)

Run `npm ci` in the repository root once.

| Command | What it does |
|---|---|
| `npm run check:openapi` | Validates the split spec (`openapi-generator-cli validate`, generator 7.15.0) and the contract rules of A5: every path referenced from `openapi.yaml`, tag == file, `operationId`, `x-permission`, `x-history` on mutating operations, error responses use `ApiError` |
| `npm run oasdiff [-- <base-ref>]` | Breaking-change check against `origin/main` (or the given ref) with `tufin/oasdiff` in Docker |
| `npm run er-diagram` | Liquibase on Postgres 17 (Docker) via the jar, then SchemaSpy → `docs/er/` |

## End-to-end tests (Playwright, `e2e/`)

Playwright runs against the **packaged jar** with profile `e2e` (in-memory H2 with seed data, server clock fixed at
`2026-11-02T09:00:00Z`, mails captured and readable through `/api/dev/mails`). `e2e/global-setup.ts` starts the newest
`server/target/foodsharing-platform-*.jar` on a free port and stops it afterwards.

```bash
cd server && ./mvnw -DskipTests package && cd ..   # jar with the Angular client
npm run e2e:install                                 # e2e dependencies + browsers (once)
npm run e2e                                         # all browser projects (chromium, firefox, webkit, mobile-chrome, mobile-safari)
npm run e2e:chromium                                # one project
npm run e2e -- tests/auth --project=chromium        # a folder / file
npm run e2e:ui                                      # Playwright UI mode
npm run e2e:screenshots                             # screenshot tests in the official Playwright Docker image
npm run e2e:screenshots -- --update-snapshots=changed   # deliberately re-baseline (review the PNGs in the PR)
npm run e2e:coverage                                # ledger IDs vs @ID tags → docs/coverage.md
npm run e2e:typecheck                               # tsc --noEmit -p e2e
```

Environment variables: `E2E_BASE_URL` (test a running app instead of starting the jar, e.g. `http://localhost:8081`
started with `--spring.profiles.active=e2e`), `E2E_BUILD=1` (build the jar when missing), `E2E_JAR`, `E2E_PORT`,
`E2E_JAVA_OPTS`, `E2E_SCREENSHOTS=1` (enables the screenshot projects; set by `screenshots.sh`), `E2E_INSTANCE`
(set by the sharded runners below: own state file, auth states, `test-results/instances/<name>/` and upload folder).

### Full runs, sharded locally (decision 137)

One jar holds one in-memory database, so a single run is serial (`workers: 1`). For full runs, shard on this machine:
`run-sharded.mjs` starts N jars (slots; own port, database, upload folder, log), cuts the suite into 3×N chunks
(`--shard=k/K`) and lets the slots work through them, each chunk against its slot's jar like a serial run (every spec
file still starts with a seed reset). The blob reports are merged into one HTML report and one `results.json`.
Sharded runs don't record videos (`E2E_VIDEO=off`, ~20 % faster; `--video` keeps them).

```bash
npm run e2e:sharded -- --project=chromium                 # slots: min(4, free GB / 2.5); --shards=N to override
npm run e2e:sharded -- --project=mobile-chrome --shards=4 tests/stores
node e2e/scripts/feature-coverage.mjs --results e2e/test-results/results.json --strict   # works on the merged JSON
npm run e2e:screenshots -- --shards=4                     # N jars on the host, N shards in one container
npm run e2e:screenshots -- --shards=4 --update-snapshots=changed   # re-baseline, sharded (or =all)
```

- Live output: failures and a progress line per minute (tests done, running chunks, CPU, free memory). At the end:
  passed / failed / flaky / skipped (the `setup` project counts once per chunk), every failed test with `file:line`,
  wall time, peak CPU, lowest free memory. Exit code 1 on any failure or flaky test, a crashed chunk or jar, or a
  failed merge.
- Files: `e2e/test-results/instances/slot-<i>/server.log`, `instances/chunk-<k>/{output.log,artifacts/}`
  (screenshots: `instances/screenshots-<i>/output.log`, `test-results/server-screenshots-<i>.log`). Merged:
  `e2e/playwright-report/` and `e2e/test-results/results.json`.
- Measured on the 16-CPU dev machine (decision 137): chromium 43 min serial → 16:22 with 4 slots.
- A spec file is one scheduling unit unless it opts into `test.describe.configure({ mode: 'parallel' })`; only files
  whose tests are independent of each other may do that (the axe sweep `tests/a11y/pages.spec.ts` does).
- `--update-snapshots` takes an optional mode since Playwright 1.50 (`all|changed|missing|none`), so a bare
  `--update-snapshots screenshots/auth.screen.ts` would take the file as the mode. `screenshots.sh` rewrites a bare
  `--update-snapshots`/`-u` to `--update-snapshots=changed`; with plain `npx playwright test` always write the `=mode`.
- `E2E_BASE_URL` and `E2E_PORT` don't combine with more than one shard (every shard needs its own database).

Rules (see `docs/conventions.md` A8 and `AGENTS.md`):

- Specs live in `e2e/tests/<module>/<id-lowercase>-<slug>.spec.ts` and import `test`/`expect` from `e2e/fixtures`.
- Every test title carries the ledger ID(s) it proves: `test('A2 magic link login and logout @A2 @page', …)`. Allowed
  extra tags: `@page` (runs axe after the test), `@screenshot`, `@chromium-only` (CDP features like the virtual passkey
  authenticator; excluded from Firefox/WebKit by the config), `@smoke`, `@slow`, `@journey`. Unknown tags fail the
  coverage gate.
- The database is reset to the seed state before each spec file (`test.use({ seedReset: 'test' | 'off' })` to change).
  Log in with `test.use({ role: 'orga' })` or `const page = await as('bieb')`.
- Screenshot tests live in `e2e/screenshots/<area>.screen.ts`, use `screenshotTest` from `e2e/screenshots/_setup.ts`,
  and are tagged `@screenshot` plus the feature ID. Baselines (`e2e/__screenshots__/`) are only produced by
  `npm run e2e:screenshots`, never on the host.
- `node e2e/scripts/feature-coverage.mjs` fails when a ledger row marked `done` has no passing tagged test, when a tag
  is not a ledger ID, and with `--strict` (milestone 7) when any feature row has no test.
