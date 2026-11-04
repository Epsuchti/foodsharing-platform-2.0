# Todo for Eric

Tasks only a person can do: decisions, accounts, money, other people. Everything an agent can do lives in `todo.md`. Details and references for most items: "Needs a human before launch" in `docs/launch-report.md`.

## Decide
- [ ] Should verification require a confirmed identity? The server allows it without one today; the UI only nudges reviewers. If yes, it is a small change (decision 124).
- [ ] Go through this list after the final gate and strike what no longer applies.

## Infrastructure and accounts
- [ ] Register the domain and set up DNS. This blocks mail DNS, OAuth redirect URIs, the passkey RP id, wallet pass identifiers and the Telegram bot name.
- [ ] Choose hosting: server, Postgres with backups, TLS reverse proxy, monitoring (Swiss hosting for nDSG?).
- [ ] Set up a staging environment for testers and the Zürich pilot team.
- [ ] Choose a mail provider and set up the regional mailboxes (`MAILBOX_*`).
- [ ] Generate the production secrets into `server/deploy/.env` (`docs/secrets.md`).
- [ ] Create the Google OAuth client with the production redirect URI.
- [ ] Create the Telegram bot with BotFather and set the token and webhook.
- [ ] Set the Alertmanager recipients and SMTP password (`server/deploy/monitoring/`).
- [ ] Optional: request an NVD API key and add it as the repository secret `NVD_API_KEY`. The weekly OWASP check works without it; the key only makes the database download faster.

## Once production exists
- [ ] Reverse proxy: send `X-Forwarded-For` from an address in `TRUSTED_PROXIES`, or the rate limit (decision 144) sees the proxy's IP for everyone.
- [ ] Database role: allow `CREATE EXTENSION unaccent` (search rework, decision 147).
- [ ] Gap rule migration (changelogs 181, 182): run the pre-deploy SQL check on a copy of production first. It has not been tested on PostgreSQL with real data.
- [ ] Have `smoke.sh`, `release.sh --check` and the k6 load test run against the real server.
- [ ] Uploads in the database (decision 158): the hosting repo no longer mounts `/data` (persistence, `UPLOADS_DIR` and fsGroup removed; prod Postgres 20Gi, local backup 10Gi). k3s was never deployed with files, so it needs no import: release an image built after decision 158 before the first deploy. A compose demo that still has `/data/uploads` runs the import once (runbook §6.5). `replicas` above 1 only after checking in-memory state and jobs. The import and the `storage_key` column are removed a release later (`todo.md`).

## Content and people
- [ ] Get the final legal texts from the association: privacy policy, terms, store-manager confidentiality agreement.
- [ ] Have the association confirm the retention periods against the privacy policy (`docs/privacy/retention.md`).
- [ ] Run a real usability test on staging with 3–5 Zürich newcomers, plus foodsavers, store managers and ambassadors.
- [ ] Have native speakers review the `gws`, `fr`, `it` and `en` texts, above all `gws` for the gap rule.
- [ ] Have the admins approve the passport layout (`docs/passport-layout.md`, `docs/passport-sample.png`).
- [ ] Have the ambassadors confirm or correct the Zürich regions and stores (`docs/data/zuerich/`).
- [ ] Name the first ambassadors and guides for staging.

## Quick check
- [ ] Open the map in a normal browser and say whether the OpenStreetMap tiles load. They did not load in the built-in browser pane, though `curl` gets them. If they fail for you too, an agent takes it from there (`todo.md`).

## Launch day
- [ ] Announce the switch-over (users re-register), import the EinAbs (`docs/runbook.md` §4) and switch off the old einab-tabelle.
