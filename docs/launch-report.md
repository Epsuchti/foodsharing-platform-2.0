# Launch report — Foodsharing Platform 2.0

Status: **final gate run** (plan-m7 Part D), 2026-11-03. What still stands before launch is under "Needs a human". Build state per feature: `docs/ledger.md`; coverage: `docs/coverage.md`; decisions: `docs/decisions.md`;
design review: `docs/design-review-m7.md`; Lighthouse: `docs/lighthouse.md`; history views: `docs/d10-3-audit.md`.

## What is built (by milestone)

Every feature of plan §13 and §14 has a backend, a frontend and at least one Playwright test tagged with its ID
(`feature-coverage.mjs --strict`: 152 / 152 features, decision 126). Rows still open in the ledger are listed under
"Open rows" below.

| Milestone | State | Backend | Frontend |
|---|---|---|---|
| M1 Foundations | done | Single jar (Spring Boot 4 + Angular 22 / Optimus UI), split OpenAPI contract with ArchUnit rules, history module with `@Tracked` listener and the "every mutation records history" rule, cookie sessions + CSRF, Google (OIDC) / magic link / passkey login, mail outbox, seed data + dev user switcher, fake OIDC provider. | Shell, `DataState`/`fs-data-state` and error handling, 5 languages, theme switcher, login/sign-up, account security (sessions, security log), "my activity" timeline; Playwright e2e + screenshot infrastructure. |
| M2 Regions & onboarding | done | Region tree with closure table, memberships, ambassadors/admins, functions and permissions, working groups with applications, onboarding journey (explainer, chapters, region steps, nudges), quizzes + editor, legal agreements, verification and passports (PDF, batch, wallets), EinAb intro pickups with guides, iCal import, past-EinAb CSV import, newcomer funnel. | Explainer `/`, journey `/onboarding` with progress, region pages (tabs, settings, history), my regions, working groups, quizzes (sessions, history, editor), verification queue, passports, EinAb booking / my bookings / guide dashboard / admin / funnel / import, user administration. |
| M3 Stores & pickups | done | Stores with all §15.2 fields, chains, KAMs, categories with merge, teams (members, jumpers, invitations, applications), regular and one-time pickups, sign-up with confirmation and eligibility reasons, region pickup rule, wall with milestones, store log (27 actions), statistics, ICS feed. | Store list / my stores, store page (overview, team, pickups, wall, log, statistics, edit), chains and categories admin, pickups (options, mine, agenda, history), eligibility explanations. |
| M4 Automations & notifications | done | Pickup and request policies (gap, cleaning, experience, holiday rules, dry run), open-slot adverts, sign-out alerts, extra slots, audit archive with anonymous daily metrics; bells, mail outbox with retry, web push, Telegram, newsletters / info mail, settings matrix, ShedLock jobs with admin job and integration pages, outage circuit breaker. | Store automation pages (policy, auto-accept, adverts, sign-out alerts, decisions), region automation (decisions, metrics, fairness, Telegram, message accounts), bells panel, notification settings, push opt-in, Telegram linking, newsletters, admin jobs/integrations, offline banner. |
| M5 Communication | done | Messaging (direct, group, store, region chats over WebSocket), forums with moderation and moderation log, events with RSVP and ICS, polls (all types and scopes), surveys, regional mailboxes (IMAP/webhook, sanitised HTML, bounces), activity feed. | Messages with realtime delivery and unread badges, forum (threads, posts, reactions, moderation, follow, search), events, polls, surveys (incl. fill page without menu), mailboxes with rich-text editor (Quill in its own chunk), nav unread badges. |
| M6 Community & governance | done | Account lifecycle (email change, photo, export zip, deletion with anonymisation and archive), baskets, food share points, map layers, reports and mediation, achievements, feature toggles, content pages / blog / release notes / team page, donations / petition, support tickets, statistics, global search, resources, OAuth2/OIDC provider. | Built as queue F tasks Q1–Q14 (`docs/plan-m7-queue-f.md`, decision 91): Q1 scaffolding + shared components, Q2 public profile + buddies + bananas, Q3/Q4 account completion (photo crop, sleep mode, email change, calendar feed, business card, data export, deletion, bounce banner, admin user page), Q5 baskets, Q6 food share points, Q7 map + dashboard feed + region statistics, Q8 reports / mediation / report log, Q9 achievements, Q10 public content pages / FAQ / team / statistics / release notes / petition, Q11 blog / donations / support, Q12 global search + resources, Q13 admin settings (toggles, configuration, blocklists, IP blocks, uploads, admins list), Q14 OAuth consent / connections / client admin. |
| M7 Launch readiness | done; automated gate passed, launch still needs the items under "Needs a human" | Zürich data files + CSV imports (regions, stores, past EinAbs) over the admin API (`scripts/import-csv.mjs`); production profile, image, secrets inventory, release script; runbook, backups with verified restore; Prometheus / Alertmanager / Grafana; k6 load test; security review (OWASP Top 10 + ASVS L2); privacy review (data inventory, retention, deletion walk); CI hardening (strict coverage, oasdiff vs last tag, ER check, shellcheck, dependency review, tag release). | F1 stores CSV import page (dry-run preview, confirm); F2 screenshot specs for every route (baselines need Docker, decision 69/109); F3 design round 1 — function & flow (decision 121); F4 design round 2 — visual polish (decision 124); F5 design round 3 — scripted persona walkthroughs (`e2e/tests/personas`, in progress); F6 full-route axe sweep in both themes + Pixel 7, five-language smoke, M6 translation sweep, Lighthouse report (decision 120); start-up performance (decisions 122, 123). |

### Design rounds (plan §18.5, §20)

Rounds ran once over the whole app in M7 (decision 69: baselines and design rounds in one end batch), not per
milestone. Round 3 ("real users") is replaced by AI persona walkthroughs (decision 3); the real usability test is
listed under "Needs a human".

| Round | Scope | Result |
|---|---|---|
| 1 — function & flow (F3) | five personas (newcomer, foodsaver, store manager, ambassador, orga) + visitor, desktop and Pixel 7 | 22 findings (2 blockers, 8 majors, 12 minors); both blockers and 7 majors fixed (decision 121) |
| 2 — visual polish (F4) | same journeys, light and dark | fixed per `docs/design-review-m7.md`; left for round 3 listed there (decision 124) |
| 3 — persona walkthroughs (F5) | scripted persona e2e specs `e2e/tests/personas` | five personas walk their main journeys on chromium and mobile-chrome with step, click and field budgets (decision 127); green in the gate run |

### Open rows (ledger)

No ledger row is open. D11.5.5 is done through the persona walkthroughs (decision 3); the real usability test is still owed under "Needs a human", so its `plan.md` box stays unticked.

## How to run

```bash
cd server && ./mvnw clean package          # builds backend + Angular client into one jar
java -jar server/target/foodsharing-platform-*.jar --spring.profiles.active=local
```

- `local` profile: H2 file DB with seed data, dev user switcher on `/login`, fake Google provider, Mailpit (`server/start_smtp_server.sh`, UI on :8025), Telegram/push log-only.
- Frontend dev server: `cd client && npm start` (proxy to :8080).
- E2E: `npm run e2e:install` once, then `npm run e2e` (all projects) or `npm run e2e:chromium`; the jar is started by `e2e/global-setup.ts` on a free port (`E2E_BASE_URL` reuses a running app). Screenshots in Docker: `npm run e2e:screenshots`; coverage: `npm run e2e:coverage` (`--strict` in CI); route table check for the axe sweep: `npm --prefix e2e run a11y:routes`.
- Lighthouse: `E2E_BASE_URL=http://localhost:8080 node e2e/scripts/lighthouse.mjs [--report]` or the Playwright `lighthouse` project (`docs/lighthouse.md`).
- ER diagram: `npm run er-diagram` (summary committed in `docs/er/diagrams/summary/`); `scripts/er-diagram.sh --check` fails when it is stale (CI).

### Windows developer machine (as used for the build, `todo.md`)

- JDK 21: portable Temurin in `~/.jdks/jdk-21.0.13+9`; prefix Java/Maven/Playwright commands with `export JAVA_HOME="$HOME/.jdks/jdk-21.0.13+9"; export PATH="$JAVA_HOME/bin:$PWD/client/node:$PATH"` (Git Bash). The system JDK 17 is not enough.
- Node: use the Maven-installed `client/node` (system Node 24.14 is older than Angular CLI needs, ≥ 24.15).
- No Docker: the 13 Postgres Testcontainers ITs fail with "Failed to load ApplicationContext"; screenshot baselines, the k6 load test, `smoke.sh` and `release.sh` need Docker. Everything else in `./mvnw clean verify` runs.
- No Python: `scripts/ledger.py` cannot run; ledger rows are edited by hand with the same columns.
- Lighthouse: the full Playwright `chrome.exe` cannot be spawned (spawn UNKNOWN); set `CHROME_PATH=<ms-playwright>/chromium_headless_shell-1248/chrome-headless-shell-win64/chrome-headless-shell.exe`. Measure on an idle machine: under parallel builds performance drops by 10–25 points (`docs/lighthouse.md`).

### Production-like (Docker Compose, `server/deploy/`, details in `docs/runbook.md`)

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

Final gate (plan-m7 Part D) on 2026-11-03. Everything marked CI comes from [run 41538613759](https://github.com/Epsuchti/foodsharing-platform-2.0/actions/runs/41538613759) on `main` at `86cafd04`: 30 jobs passed, 2 did not apply (Dependency review runs on PRs, Release image on tags). The other checks ran on `fix/load-and-security-workflows` at `56c556b1`, which adds the workflow fixes and removes `async-http-client` (decision 157).

| Check | Command | Where | Result |
|---|---|---|---|
| OpenAPI validation | `node scripts/check-openapi.mjs` | CI | no validation issues |
| oasdiff | `oasdiff.sh` | CI | not compared on `main`: there is no `v*` tag yet |
| Server unit tests | `./mvnw clean verify` (surefire) | CI | 1353 tests, 0 failures, 0 skipped |
| Server ITs (H2 and Postgres) | `./mvnw clean verify` (failsafe) | CI | 304 ITs, 0 failures, 0 skipped; includes the 9 Testcontainers classes on Postgres |
| Client unit tests | `ng test` (in `mvn verify`) | CI | 784 tests in 102 files passed |
| Lint, i18n check, theme tokens | `npm run lint`, `npm run i18n:check` (in `mvn verify`) | CI | passed; 5654 keys in 5 languages |
| E2E typecheck | `npm run e2e:typecheck` | CI | passed |
| Playwright chromium | `--project=chromium` | CI | 1066 passed |
| Playwright firefox | `--project=firefox` | CI | 1064 passed, 1 flaky (`r7-region-admin.spec.ts:10`, passed on retry) |
| Playwright webkit | `--project=webkit` | CI | 1065 passed |
| Playwright mobile-chrome | `--project=mobile-chrome` | CI | 1066 passed |
| Playwright mobile-safari | `--project=mobile-safari` | CI | 1065 passed |
| Playwright lighthouse (D11.5.3) | `--project=lighthouse` | CI | `/` perf 93 / a11y 100; `/onboarding` perf 75 / a11y 100 (goal 70 and 95, decision 153) |
| Screenshots (desktop + mobile, decision 109) | `screenshots.sh` | CI | 609 passed, 0 failed, 1 flaky (`pages.screen.ts:71`, offline shell light-de, passed on retry) |
| Feature coverage | `feature-coverage.mjs --strict` | CI | 152 / 152 features, 0 untagged tests; 27 of 37 deliverables have a tagged test |
| Axe route sweep | `e2e/tests/a11y`, `a11y:routes` | CI | passed |
| Five-language smoke | `e2e/tests/shell/i18n-smoke.spec.ts` | CI | passed |
| ER diagram check | `scripts/er-diagram.sh --check` | CI | up to date |
| Release check | `server/deploy/release.sh --build --check` | this Mac | builds for linux/amd64 and linux/arm64 |
| Smoke test | `server/deploy/smoke.sh` | this Mac | all 9 checks OK on image version 44 |
| Security workflow | `security.yml` | GitHub, `56c556b1` | passed; OWASP findings dealt with in decision 157 |
| Load test | `load.yml` (`e2e/load/run.sh`) | GitHub, `3a463f39` and `56c556b1` | passed twice; latest 60,636 requests, p95 17.1 ms, 0.00 % failed, 100 VUs |
| Load test | `e2e/load/run.sh` | this Mac | **failed three times**: 3, 9 and (on a quiet machine) 4 connection timeouts between k6 and the app broke the failure threshold of low-volume endpoints (`docs/load-test.md`, `todo.md`) |

## Needs a human before launch

The checklist to work through is `todo-eric.md`; this table holds the details.

| Item | Why | What to do | Reference |
|---|---|---|---|
| Usability test with 5 Zürich newcomers | Replaced during the build by AI persona walkthroughs (decision 3, three design rounds in `docs/design-review-m7.md`). | Run a short moderated test (3–5 newcomers, plus foodsavers, store managers, ambassadors) on staging; fix the top findings. | D11.5.5, §11.5, §18.5, §20 M7 |
| Product question: verification requires a confirmed identity? | The server allows verification without the identity check; the UI only nudges reviewers. | Decide; if yes: server rule + journey + e2e (small change). | decision 124, `docs/design-review-m7.md` |
| Zürich regions and stores data | `docs/data/zuerich/regions.csv` is a model (sub-regions after foodsharing.network) and `stores.csv` an anonymised model, both pending ambassador confirmation. | Ambassadors confirm or correct the files; import with `scripts/import-csv.mjs` (runbook §4). | D16.1, D16.2, plan §16, §21 |
| First ambassadors / guides for staging | Needed to onboard the Zürich pilot team. | Name them; grant roles after import. | plan §21 |
| Staging environment | Plan §19: staging with seed data for testers and the Zürich pilot team. | Provision like production (compose), `local`-like seed only on staging. | §19 |
| Domain name | Blocks mail DNS (MX/SPF/DKIM/DMARC), OAuth redirect URIs, passkey RP id, wallet pass identifiers, Telegram bot name. Everything is configuration (`APP_DOMAIN`, `APP_BASE_URL`). | Decide and register the domain; set DNS. | §21, `docs/secrets.md` |
| Hosting, Postgres with backups, TLS reverse proxy, monitoring | Production deploy is not possible without infrastructure (Swiss hosting for nDSG?). | Choose the host; follow `docs/runbook.md` (topology, backups, monitoring). | §19, D19.1–D19.3 |
| Production secrets | `SPRING_DATASOURCE_PASSWORD`, `APP_SIGNING_SECRET`, `APP_IP_SALT`, `NEWSLETTER_SECRET`, `OAUTH_SIGNING_KEY`, VAPID keys, SMTP token, IMAP password, Telegram token/webhook secret, wallet signing keys. | Generate per `docs/secrets.md` into `server/deploy/.env` (never committed). | `docs/secrets.md`, D19.1 |
| Google OAuth credentials | `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` for the production domain. | Create the OAuth client with the production redirect URI. | §8.1, D8.5 |
| Telegram bot | Bot creation via BotFather; token stored encrypted. | Create the bot, set `TELEGRAM_BOT_TOKEN` and the webhook. | N5, AU10 |
| Mail provider + regional mailboxes | SMTP/IMAP (or inbound webhook) for `<region>@<domain>`. | Choose the provider, configure `MAILBOX_*`. | §13.6 |
| Alertmanager recipients and SMTP password | `server/deploy/monitoring/alertmanager.yml` sends to placeholder addresses. | Set the on-call addresses; put the SMTP password into `server/deploy/monitoring/smtp_password` (not committed). | D19.3 |
| `NVD_API_KEY` CI secret | The weekly OWASP dependency-check downloads the NVD database; without a key the first download takes 40 to 100 minutes (the job's timeout allows for it). | Optional: request a key at NVD, add it as a repository secret. | D17.2 |
| Native-speaker review of gws, fr, it (and en) | Translations are machine-made during the build. | Native speakers review the catalogs (`client/src/**/i18n`). | §17, §20 M7 |
| Passport layout approval | Plan §21 #5: layout checked and approved by our admins. | Admins review `docs/passport-layout.md` / `docs/passport-sample.png`. | Ve3 |
| Retention periods | `docs/privacy/retention.md` (decision 87) should be confirmed against the privacy policy text. | The association confirms or adjusts the periods. | §17, D17.3 |
| Legal texts | Privacy policy, terms, store-manager confidentiality agreement are seed texts. | The association provides the final texts (versioned, re-consent on change). | Ve1 |
| Launch switch-over | Users re-register; the old einab-tabelle is switched off. | Announce, import EinAbs (runbook §4), switch off the old app. | §20 Launch |
