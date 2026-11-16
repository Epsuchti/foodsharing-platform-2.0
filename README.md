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
`2026-11-02T09:00:00Z`, mails captured and readable through `/api/dev/mails`). `e2e/global-setup.ts` starts one jar
per worker (`E2E_WORKERS`, default 2) from the newest `server/target/foodsharing-platform-*.jar`, each on a free port
with its own database, and stops them afterwards. Decision 180: the browser projects run persona journeys
(`e2e/journeys/<persona>/<slug>.journey.ts`); the axe route sweep is the `a11y` project, Lighthouse the `lighthouse`
project.

```bash
cd server && ./mvnw -DskipTests package && cd ..   # jar with the Angular client
npm run e2e:install                                 # e2e dependencies + browsers (once)
npm run e2e                                         # all projects
npm run e2e:chromium                                # one project
npm run e2e -- journeys/store-manager --project=webkit   # one persona / file
npm run e2e:ui                                      # Playwright UI mode
npm run e2e:screenshots                             # chromium + mobile-chrome journeys with checkpoint screenshots, in Docker
npm run e2e:coverage                                # ledger IDs vs journey tags + e2e/coverage-map.json → docs/coverage.md
npm run e2e:typecheck                               # tsc --noEmit -p e2e
```

Environment variables: `E2E_WORKERS` (workers and jars, default 2), `E2E_BASE_URL` (test running apps instead of
starting jars: one URL per worker, space- or comma-separated), `E2E_BUILD=1` (build the jar when missing), `E2E_JAR`,
`E2E_PORT` (port of the first jar), `E2E_JAVA_OPTS`, `E2E_SCREENSHOTS=1` (compare checkpoint screenshots; set by
`screenshots.sh` inside the Playwright image), `E2E_PROJECTS` (projects `screenshots.sh` runs), `E2E_INSTANCE`.

Rules (see `docs/conventions.md` A8, `AGENTS.md` and decision 180):

- A journey is a persona doing a real multi-step process through the UI. It imports `journey as test`, `checkpoint`
  and `expect` from `e2e/fixtures/checkpoint.ts` and usually a `Walk` from `e2e/fixtures/persona.ts`. Rules and
  API-only checks are unit or integration tests, not journeys.
- The title names the process and carries every ledger ID it proves (`… @PU3 @S12`). Meta tags: `@chromium-only`
  (CDP features like the virtual passkey authenticator), `@infra`, `@gallery`. An ID proven only by a unit or
  integration test is listed in `e2e/coverage-map.json`. Unknown tags fail the coverage gate.
- Every journey starts from the seed (reset before each test), in German and the light theme. Log in with
  `test.use({ role: 'orga' })` or `const page = await as('bieb')`.
- `checkpoint(page, 'name')` waits until the page is at rest; on chromium and mobile-chrome it runs axe, and inside the
  Playwright image (`npm run e2e:screenshots`) it compares a screenshot with `e2e/__screenshots__/<project>/…`.
  Baselines are regenerated in CI (`screenshots-update.yml`, decision 161), never on the host.
- `--update-snapshots` takes an optional mode since Playwright 1.50 (`all|changed|missing|none`); `screenshots.sh`
  rewrites a bare `--update-snapshots`/`-u` to `--update-snapshots=changed`.
- `node e2e/scripts/feature-coverage.mjs` fails when a ledger row marked `done` has neither a passing tagged journey
  nor a coverage-map entry, when a tag is not a ledger ID, when a mapped file is missing, and with `--strict` when any
  feature row is uncovered. Where every removed test went: `docs/testing-migration.md`.
