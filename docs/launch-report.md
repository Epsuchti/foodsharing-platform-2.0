# Launch report — Foodsharing Platform 2.0

Status: **in progress** (written incrementally; finalised after milestone 7). Build state per feature: `docs/ledger.md`; coverage: `docs/coverage.md`; decisions: `docs/decisions.md`.

## What is built (by milestone)

| Milestone | State | Notes |
|---|---|---|
| M1 Foundations | in review (design rounds 1/3) | Single jar (Spring Boot 4 + Angular 22/Optimus UI), split OpenAPI contract, history module, cookie sessions + CSRF, Google (OIDC) / magic link / passkey login, mail outbox, seed data + dev user switcher, fake OIDC provider, DataState/error handling, 5 languages, Playwright e2e + screenshot infrastructure. |
| M2 Regions & onboarding | in progress | Contract and region/user foundations merged; services and pages in progress. |
| M3–M7 | todo | |

## How to run

```bash
cd server && ./mvnw clean package          # builds backend + Angular client into one jar
java -jar server/target/foodsharing-platform-*.jar --spring.profiles.active=local
```

- `local` profile: H2 file DB with seed data, dev user switcher on `/login`, fake Google provider, Mailpit (`server/start_smtp_server.sh`, UI on :8025), Telegram/push log-only.
- Frontend dev server: `cd client && npm start` (proxy to :8080).
- E2E: `npm run e2e:install` once, then `npm run e2e`; screenshots in Docker: `npm run e2e:screenshots`; coverage: `npm run e2e:coverage`.
- ER diagram: `npm run er-diagram` (summary committed in `docs/er/diagrams/summary/`).

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
