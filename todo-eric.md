# Todo for Eric

Tasks only a person can do: decisions, accounts, money, other people. Everything an agent can do lives in `todo.md`. Details and references for most items: "Needs a human before launch" in `docs/launch-report.md`.

## Decide
- [x] ~~Should verification require a confirmed identity? The server allows it without one today; the UI only nudges reviewers. If yes, it is a small change (decision 124).~~ Yes, no ORGA exception (decision 174).
- [ ] Go through this list after the final gate and strike what no longer applies.

## Refactor (owner)
- [ ] Refactor the intro pickups (Einführungsabholungen): where they are managed and who may give them.
- [ ] Refactor the store automations (Automatisierung tab).

## Infrastructure and accounts
- [ ] Register the domain and set up DNS. This blocks mail DNS, OAuth redirect URIs, the passkey RP id, wallet pass identifiers and the Telegram bot name.
- [ ] Choose hosting: server, Postgres with backups, TLS reverse proxy, monitoring (Swiss hosting for nDSG?).
- [ ] Set up a staging environment for testers and the Zürich pilot team. The app side is ready (decision 173, runbook §11): in the hosting repo set `SPRING_PROFILES_ACTIVE=prod,staging` and `PERSONA_PANEL_PASSWORD` for test only, reset the test database (runbook §11.1), deploy, then share the panel password with the testers.
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
- [ ] Have native speakers check the new landing page texts (C17, decision 160) in `gws`, `fr` and `it`: `client/src/app/features/public/i18n/` and `content.support.subjectPreset.partner`.
- [ ] Have the admins approve the passport layout (`docs/passport-layout.md`, `docs/passport-sample.png`).
- [ ] Have the ambassadors confirm or correct the Zürich regions and stores (`docs/data/zuerich/`).
- [ ] Name the first ambassadors and guides for staging.

## Quick check
- [ ] Map tile service on K3s (MT2, decision 163, runbook §10.4; hosting branch `feat/foodsharing-tiles`, not merged). The MT1 check now runs on K3s:
  1. Free RAM of the node, recorded in runbook §10.3: `free -g` and `sudo kubectl describe node | sed -n '/Allocated resources/,/Events/p'`. The build asks for 16Gi (request = limit).
  2. Publish the builder image after this branch is on `main`: `gh workflow run tiles-image.yml --ref main`, then `gh run watch "$(gh run list -w tiles-image.yml -L 1 --json databaseId -q '.[0].databaseId')"`. Make the package `foodsharing-tiles-builder` public, or check that the account in `.ghcr-pull-secret.env` can read it.
  3. Merge `feat/foodsharing-tiles` in the hosting repo (conflicts with other work in the foodsharing values files and docs are resolved by hand), then on the server: `sudo ./scripts/deploy-app.sh foodsharing-platform prod <version>` and `sudo ./scripts/deploy-tiles.sh foodsharing-platform prod 1`. It starts `foodsharing-platform-tiles-initial-<date>`; follow it with `sudo kubectl logs -n foodsharing-platform-prod -f job/<job>` and watch "Map Tiles" in Grafana (builder memory against the limit).
  4. After the job: `sudo kubectl exec -n foodsharing-platform-prod deploy/foodsharing-platform-tiles -- cat /srv/work/last-build.json` and put method, peakMemoryMb, seconds and sizeBytes into runbook §10.3. Then set `builder.resources` in `apps/foodsharing-platform/tiles-prod-values.yaml` to the measured peak plus about 25 % (request = limit; under 16384 Mi it switches to mmap), commit, and run `deploy-tiles.sh` again.
  5. Test: `sudo ./scripts/deploy-app.sh foodsharing-platform test <version>` and `sudo ./scripts/deploy-tiles.sh foodsharing-platform test`.
  6. Checks for both hosts: `curl -s -o /dev/null -w '%{http_code}\n' -r 0-126 https://test.foodsharing.site/tiles/basemap.pmtiles` gives 206 and `curl -s -r 0-7 … | od -c` starts with `P M T i l e s 003`; `curl -sI` shows the ETag, `Cache-Control: public, max-age=86400` and no `Content-Encoding`; `…/tiles/basemap.previous.pmtiles` gives 404. Then a manual rebuild (`sudo ./scripts/deploy-tiles.sh --rebuild foodsharing-platform prod`) and the ETag changes after the swap.
  7. Open `/map` and a store page in light and dark mode on both hosts and say whether the map background shows.

## Launch day
- [ ] Announce the switch-over (users re-register), import the EinAbs (`docs/runbook.md` §4) and switch off the old einab-tabelle.
