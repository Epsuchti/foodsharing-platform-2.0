# Launch report — Foodsharing Platform 2.0

Status: **in progress** (written incrementally; finalised after milestone 7). Build state per feature: `docs/ledger.md`; coverage: `docs/coverage.md`; decisions: `docs/decisions.md`.

## What is built (by milestone)

| Milestone | State | Notes |
|---|---|---|
| M1 Foundations | in review (design rounds 1/3) | Single jar (Spring Boot 4 + Angular 22/Optimus UI), split OpenAPI contract, history module, cookie sessions + CSRF, Google (OIDC) / magic link / passkey login, mail outbox, seed data + dev user switcher, fake OIDC provider, DataState/error handling, 5 languages, Playwright e2e + screenshot infrastructure. |
| M2 Regions & onboarding | backend done; frontend per ledger | Region tree with closure table, memberships, ambassadors/admins, permissions and functions, working groups with applications, onboarding journey (explainer, chapters, region steps, quizzes, nudges), verification and passports, EinAb intro pickups with guides, iCal import and past-EinAb import, profile fields with `@Tracked` history. |
| M3 Stores & pickups | backend done; frontend per ledger | Stores with teams, managers, jumpers, applications, wall, categories/chains/KAM, regular and single pickups, sign-ups with confirmation, weights, store log, calendar (ICS) feed. |
| M4 Automation & notifications | backend done; frontend per ledger | Pickup and request policies, cleaning rules, open-slot adverts, sign-out alerts, audit archive (anonymous daily counts); bells, mail outbox with retry, web push, Telegram, newsletters/infomail, settings matrix, ShedLock jobs with admin job/integration pages. |
| M5 Community | backend done; frontend per ledger | Messaging (direct, group, store, region chats over WebSocket), forums with moderation, events with RSVP, polls, surveys, regional mailboxes (IMAP fetch, sanitised HTML, bounces), activity feed. |
| M6 Platform | backend done; frontend per ledger | Account lifecycle (email change, photo, export zip, deletion with anonymisation and archive), baskets, food share points, map layers, reports and mediation, achievements, feature toggles, content pages/blog/release notes/team page, donations/petition, support tickets, statistics, global search, resources, OAuth2/OIDC provider. |
| M7 Launch readiness | backend done; design rounds and final sweeps pending (F2–F6) | Zürich data files + CSV imports (regions, stores, past EinAbs) over the admin API; production profile, image, secrets inventory, release script; runbook, backups with verified restore; Prometheus/Alertmanager/Grafana; k6 load test; security review (OWASP Top 10 + ASVS L2); privacy review (data inventory, retention, deletion walk); CI hardening (oasdiff vs last tag, ER check, shellcheck, dependency review, tag release). |

## How to run

```bash
cd server && ./mvnw clean package          # builds backend + Angular client into one jar
java -jar server/target/foodsharing-platform-*.jar --spring.profiles.active=local
```

- `local` profile: H2 file DB with seed data, dev user switcher on `/login`, fake Google provider, Mailpit (`server/start_smtp_server.sh`, UI on :8025), Telegram/push log-only.
- Frontend dev server: `cd client && npm start` (proxy to :8080).
- E2E: `npm run e2e:install` once, then `npm run e2e`; screenshots in Docker: `npm run e2e:screenshots`; coverage: `npm run e2e:coverage`.
- ER diagram: `npm run er-diagram` (summary committed in `docs/er/diagrams/summary/`); `scripts/er-diagram.sh --check` fails when it is stale (CI).

Production-like (Docker Compose, `server/deploy/`, details in `docs/runbook.md`):

```bash
cp server/deploy/.env.example server/deploy/.env       # fill every secret (docs/secrets.md)
cd server/deploy && docker compose up -d                # Postgres 17 + app (prod profile), app on 127.0.0.1:8080, management on 9090
server/deploy/release.sh --build --check                # image for amd64 + arm64; --load + smoke.sh for a local smoke test
```

- Initial data (runbook §4): `node scripts/import-csv.mjs regions|stores|aliases|einabs <file> --base-url <url> --session <fs_session> [--region <slug>]` — dry run by default, `--confirm` imports; Zürich files in `docs/data/zuerich/`.
- Backups (runbook §7): `server/deploy/backup.sh` daily (pg_dump + uploads + checksums), `verify-backup.sh` restores into a throwaway container, `restore.sh` for a real restore.
- Monitoring (`docs/monitoring.md`): `docker compose -f compose.yaml -f compose.monitoring.yaml up -d` adds Prometheus, Alertmanager (mail) and Grafana; UIs via SSH tunnel.
- Load test (`docs/load-test.md`): `e2e/load/run.sh` (Postgres 17 + jar + k6 in Docker, p95 < 200 ms, failures < 0.1 %); CI `load.yml` weekly / on demand.
- Security and privacy: `docs/security-review.md`, `docs/privacy/data-inventory.md`, `docs/privacy/retention.md`; dependency-check weekly in `security.yml`.

## Test results

Updated at each milestone. Latest (M1 integration): `./mvnw clean verify` green; Playwright chromium 94/94; all five browser projects green per worker runs; screenshot suite stable in Docker (pages + gallery).

## Needs a human before launch

| Item | Why | Plan ref |
|---|---|---|
| Domain name | Blocks mail DNS (MX/SPF/DKIM/DMARC), OAuth redirect URIs, wallet pass identifiers, Telegram bot name. Everything is configuration (`app.domain`, `app.base-url`). | §21 |
| Google OAuth credentials | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` for the production domain. | §8.1 |
| Telegram bot token | Bot creation via BotFather; token stored encrypted (`app.crypto.key`). | N5 |
| Mail provider + regional mailboxes | SMTP/IMAP (or inbound webhook) for `<region>@<domain>`. | §13.6 |
| Hosting (Swiss hosting for nDSG?), Postgres with backups, TLS reverse proxy, monitoring | Production deploy is not possible without infrastructure. | §19 |
| `APP_SIGNING_SECRET`, `APP_IP_SALT`, VAPID keys, wallet signing keys | Production secrets. | §19 |
| CI on a Git remote | `.github/workflows/ci.yml` is complete but has never run (no remote configured). | D5.4 |
| Usability tests with Zürich newcomers / foodsavers / ambassadors | Replaced during the build by AI persona walkthroughs (decision #3). | §11.5, §18.5 |
| Native-speaker review of gws, fr, it (and en) translations | Translations are machine-made during the build. | §17, §20 M7 |
| Load test on production hardware | Only a local load test is possible during the build. | §20 M7 |
| Passport layout approval by admins | Plan §21 #5. | Ve3 |
| Exact Zürich sub-region list, first ambassadors/guides | Seed uses a modelled list. | §16 |
| Alertmanager recipients and SMTP password | `server/deploy/monitoring/alertmanager.yml` sends to placeholder addresses (`it@foodsharing.network`); set the real on-call addresses and put the SMTP password into `server/deploy/monitoring/smtp_password` (not committed). | D19.3 |
| `NVD_API_KEY` CI secret | The weekly OWASP dependency-check (`security.yml`) downloads the NVD database; without an API key it is very slow and may time out. | D17.2 |
| Strict feature coverage in CI | `ci.yml` runs `feature-coverage.mjs --strict` only with the `strict-coverage` dispatch input; make it the default once every feature row has a tagged test (after F6). | D18.1 |
| Retention periods confirmed by the association | `docs/privacy/retention.md` (decision 87: archive 2 years, sessions 30 days, sign-up attempts 90 days, history per action) should be confirmed against the privacy policy text. | §17 |
