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
npm run e2e:screenshots -- --update-snapshots       # deliberately re-baseline (review the PNGs in the PR)
npm run e2e:coverage                                # ledger IDs vs @ID tags → docs/coverage.md
npm run e2e:typecheck                               # tsc --noEmit -p e2e
```

Environment variables: `E2E_BASE_URL` (test a running app instead of starting the jar, e.g. `http://localhost:8081`
started with `--spring.profiles.active=e2e`), `E2E_BUILD=1` (build the jar when missing), `E2E_JAR`, `E2E_PORT`,
`E2E_SCREENSHOTS=1` (enables the screenshot projects; set by `screenshots.sh`).

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
