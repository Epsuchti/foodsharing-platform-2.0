# Runbook — Foodsharing Platform 2.0

Operations handbook for the production instance. Commands run on the host in `server/deploy` (checkout of this repository, `.env` next to `compose.yaml`) unless noted.

## 1. Topology

```text
Internet ──443──▶ reverse proxy (TLS, HSTS; Caddy/nginx on the host)
                     │ 127.0.0.1:8080
             ┌───────▼────────┐   backend network (compose project "foodsharing")
             │ app (1 replica)│──9090──▶ prometheus ─▶ alertmanager ─▶ IT mail
             │ prod profile   │                 └────▶ grafana (127.0.0.1:3000, SSH tunnel)
             └──┬──────────┬──┘
     postgres:17 (pgdata)  /data/uploads (uploads volume)
                │
   outbound: SMTP (ProtonMail), Web Push, Telegram, Photon, Google OIDC, IMAP (optional)
```

- One app container; scheduled jobs are guarded by ShedLock (`shedlock` table), so a second replica during an upgrade is harmless.
- State lives only in Postgres (`pgdata`) and the uploads volume; both are in the backup (§7). Sessions are server-side rows, so a restart logs nobody out.
- Management port 9090 (health, Prometheus) is never published; the proxy only forwards to 8080.

## 2. Build & release

Image: `ghcr.io/epsuchti/foodsharing-platform:<version>` (+ `:latest`), built from the packaged jar (Angular client inside) by `server/Dockerfile`: `eclipse-temurin:21-jre`, user `app` (uid 10001), `/data` volume (uploads), `SPRING_PROFILES_ACTIVE=prod`, `JAVA_OPTS` default `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`, `HEALTHCHECK` on `:9090/actuator/health/liveness`, `exec java` so SIGTERM reaches the JVM (graceful shutdown, 30 s). The version is the number in `server/deploy/version.txt`; it is baked into the image (`/app/version.txt`, `APP_VERSION`), shown by `/api/public/app-info` and written into every log line.

Release steps (maintainer machine, Docker with buildx):
1. Bump `server/deploy/version.txt`, commit, wait for green CI on `main`.
2. `server/deploy/release.sh --build --check` — builds the jar and the image for `linux/amd64` and `linux/arm64`, pushes nothing.
3. `server/deploy/release.sh --load && server/deploy/smoke.sh` — local image + smoke test against the compose stack (below).
4. `docker login ghcr.io`, then `server/deploy/release.sh --push` — refuses a dirty tree or an existing tag; pushes `<version>` and `latest`, creates the annotated tag `v<version>`; `git push origin v<version>` (CI's `release` job rebuilds the same tags from the tag build).
   Alternatively push only the tag: the CI `release` job (on `v*` tags, after contract, build, e2e, screenshots, ER check and shellcheck) checks that `server/deploy/version.txt` equals the tag and runs `server/deploy/release.sh --tagged` (tag must point at HEAD; pushes `<version>` and `latest` from the tested jar).
5. Upgrade the server (§5).

`smoke.sh` starts `compose.yaml` + `compose.local.yaml` (Postgres, Mailpit, app with throwaway secrets in `server/deploy/.env.local`, project `foodsharing-smoke`, ports 18080/19090 on 127.0.0.1) and checks readiness on the management port, `/` = 200 with the SPA, app-info version = `version.txt`, dev endpoints 404, actuator not on the public port, Prometheus on 9090, JSON log lines, non-root user. `--keep` leaves the stack running (used for the backup rehearsal in §7).

Verified output (2026-10-29, version 44):

```text
$ server/deploy/release.sh --build --check
==> Checking ghcr.io/epsuchti/foodsharing-platform:44 for linux/amd64,linux/arm64 (nothing is pushed)
#5 [linux/arm64 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:…
#6 [linux/amd64 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:…
#6 [linux/amd64 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:…
#11 [linux/arm64 5/5] COPY --chown=root:root deploy/version.txt /app/version.txt
#6 [linux/amd64 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:…
#6 [linux/amd64 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:…
#6 [linux/amd64 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:…
#6 [linux/amd64 1/5] FROM docker.io/library/eclipse-temurin:21-jre@sha256:…
#15 [linux/amd64 5/5] COPY --chown=root:root deploy/version.txt /app/version.txt
OK: image builds for linux/amd64,linux/arm64 (version 44, jar target/foodsharing-platform-0.0.1-SNAPSHOT.jar)
```

```text
$ server/deploy/release.sh --load && server/deploy/smoke.sh
==> Starting ghcr.io/epsuchti/foodsharing-platform:local (prod profile) with postgres and mailpit
==> Waiting for readiness on the management port
ok   readiness UP on :9090
ok   liveness UP on :9090
ok   / 200 (SPA)
ok   app-info version 44
ok   dev endpoints 404
ok   actuator not on the public port
ok   prometheus on :9090 (218 fs_* series)
ok   JSON log lines
ok   runs as non-root (uid 10001)
SMOKE OK (ghcr.io/epsuchti/foodsharing-platform:local, version 44)
```

## 3. Configuration

- **Profile** `prod` (set by the image). `application.properties` holds the defaults, `application-prod.properties` the production overrides; every environment variable, how to generate it, rotation and consequence of loss: [`docs/secrets.md`](secrets.md). Template: `server/deploy/.env.example` → `server/deploy/.env` on the host (`chmod 600`). `ProdSecretsInventoryTest` keeps the three in sync.
- **Ports**: 8080 application (bound to `127.0.0.1` via `HTTP_BIND`, TLS reverse proxy in front, `X-Forwarded-*` honoured); 9090 management (`/actuator/health/{liveness,readiness}`, `/actuator/health/version`, `/actuator/prometheus`), only on the compose network. Local and e2e profiles keep the actuator on 8080 (e2e global setup waits on `/actuator/health/readiness` of the app port).
- **Database**: PostgreSQL 17 (`pgdata` volume), Liquibase context `schema` only (never `seed`), Hikari pool `DB_POOL_SIZE` (20).
- **Shutdown**: `server.shutdown=graceful`, 30 s per phase; compose `stop_grace_period: 45s`.
- **Logs**: one JSON object per line on stdout (logstash layout: `@timestamp`, `level`, `logger_name`, `thread_name`, `message`, `traceId`, `app`, `version`, `stack_trace`); `docker compose logs app` / the host's log driver. `StructuredLoggingTest` guards the format.
- **Storage**: uploads in `/data/uploads` (`uploads` volume); container root filesystem read-only, `/tmp` tmpfs, all capabilities dropped.
- **External services**: SMTP (ProtonMail token), Photon geocoding (`PHOTON_URL`), Google OIDC, Web Push (VAPID), Telegram, optional IMAP mailbox.

## 4. Initial data

Production starts with an empty schema (Liquibase context `schema`, no `seed`). The initial data is imported over the normal admin API with `scripts/import-csv.mjs`, never through a dev endpoint. Every import is a **dry run by default** (prints the preview, changes nothing); `--confirm` imports. Imports are idempotent: a second run reports every row as `UNCHANGED` / skipped.

### 4.1 Session
1. Log in as an ORGA account in the browser via magic link.
2. Copy the value of the `fs_session` cookie (developer tools → Storage → Cookies).
3. `export FS_SESSION=<value>` and `export FS_URL=https://<host>`.

The session expires like any other login; log out in the browser after the import.

### 4.2 Regions (D16.1)
Data: `docs/data/zuerich/regions.csv` (Europa → Schweiz → Kanton Zürich → Zürich Stadt with Kreise 1–12, Winterthur and the sub-regions Limmattal, Zürcher Oberland, Zürcher Unterland, Zimmerberg, Glattal, Knonaueramt, Weinland, Pfannenstiel, Furttal with postcodes). **Model pending ambassador confirmation** — have the Zürich ambassadors confirm names, borders and postcodes before importing.

```sh
node scripts/import-csv.mjs regions docs/data/zuerich/regions.csv --base-url "$FS_URL" --session "$FS_SESSION"
node scripts/import-csv.mjs regions docs/data/zuerich/regions.csv --base-url "$FS_URL" --session "$FS_SESSION" --confirm
```

Expected output of the confirmed run on an empty database: one `CREATE` line per region and `regions: created=26 updated=0 errors=0 dryRun=false`; exit code 0. A repeated run prints `created=0 updated=0` (every row `UNCHANGED`). Exit code 2 means rows with errors (they were skipped; fix the file and rerun).

### 4.3 Stores (D16.2)
Data: `docs/data/zuerich/stores.csv` (anonymised model of ~40 Zürich stores; replace it with the real list from the store coordination before the import). Columns `name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,team_status,public_info,manager_foodsaver_numbers`; empty coordinates are geocoded from the postcode during the confirmed import.

Prerequisites: the regions are imported (4.2) and every category and chain named in the file exists (admin → store categories / store chains); unknown names are row errors `UNKNOWN_CATEGORY` / `UNKNOWN_CHAIN`. The session must belong to the store coordination of the target region (ORGA, admins of its store coordination group, else its ambassadors). Manager foodsaver numbers only resolve for users that already exist (`UNKNOWN_USER` warning otherwise; rerun the import after the managers registered — existing stores are matched by name + postcode and only gain the missing managers).

```sh
node scripts/import-csv.mjs stores docs/data/zuerich/stores.csv --region zuerich-stadt --base-url "$FS_URL" --session "$FS_SESSION"
node scripts/import-csv.mjs stores docs/data/zuerich/stores.csv --region zuerich-stadt --base-url "$FS_URL" --session "$FS_SESSION" --confirm
```

Expected output of the confirmed run: one line per store and `stores: created=40 updated=0 skipped=0 errors=0 dryRun=false`, exit code 0. Rows ending in `[GEOCODING_FAILED]` were imported without coordinates (set them in the store form). A rerun prints `created=0 updated=0 skipped=40`.

### 4.4 Past EinAbs (D16.3)
Source: the protocol sheet of the einab-tabelle (export as CSV, `;` or `,`, UTF-8; header `ID;Datum;No.;Betrieb;Kategorie;EinAb-Geber;FT-Besuch;Feedback`). Format sample: `docs/data/zuerich/einabs-sample.csv`. The real file contains personal data and feedback: keep it off the repository and delete it after the import. The server never stores or logs the file.

Order and prerequisites:
1. Regions (4.2) and stores (4.3) are imported.
2. The newcomers exist: users are matched by **foodsaver number** (column `ID`); unknown numbers are `UNKNOWN_USER` and are not imported (no placeholder accounts). Users must be members of the target region or a region below it (e.g. a Kreis of Zürich Stadt), otherwise `USER_OTHER_REGION`.
3. Store aliases map the sheet's spellings to the platform's store names (`docs/data/zuerich/store-aliases.csv`, model pending confirmation by the EinAb team). Case, spaces, punctuation and umlauts (`ä`/`ae`) are ignored when matching.
4. The session belongs to an admin of the target region with CAN_MANAGE_USERS (or ORGA); this admin becomes the guide of the imported EinAbs.

```sh
node scripts/import-csv.mjs aliases docs/data/zuerich/store-aliases.csv --region zuerich-stadt --base-url "$FS_URL" --session "$FS_SESSION" --confirm
node scripts/import-csv.mjs einabs einab-tabelle.csv --region zuerich-stadt --base-url "$FS_URL" --session "$FS_SESSION"
node scripts/import-csv.mjs einabs einab-tabelle.csv --region zuerich-stadt --base-url "$FS_URL" --session "$FS_SESSION" --confirm
```

Expected output:
- aliases: `aliases: created=2 skipped=0 errors=0 dryRun=false` (a rerun reports `created=0 skipped=2`).
- einabs dry run: every row that needs attention or is skipped is listed with its reasons (`UNKNOWN_USER`, `USER_OTHER_REGION`, `INVALID_DATE`, `FUTURE_DATE`, `MISSING_STORE`, `UNKNOWN_CATEGORY`, `DUPLICATE_IN_FILE`, `ALREADY_IMPORTED`; informational: `STORE_NAME_CORRECTED`, `CATEGORY_MAPPED`, `NOT_COUNTED`, `NO_FEEDBACK`), then e.g. `einabs: total=9 willCreate=6 willSkip=1 needsAttention=2 knownUsers=3 unknownUsers=1 dryRun=true` for the sample. Exit code 2 = rows need attention: fix the sheet (or create / move the users) and run the dry run again; rows needing attention are never imported, so a partial import is safe.
- einabs confirm: `einabs: created=6 skipped=1 needsAttention=2 dryRun=false`; files with more than 500 rows run as a job (`einabs: job <id> DONE` first; `FAILED` means nothing was imported — the transaction rolled back — check the server log by traceId and rerun).
- A rerun of the confirm creates nothing (`created=0`, all earlier rows `ALREADY_IMPORTED`); bookings are matched per user + day + store, also against EinAbs entered by hand.
- Each EinAb is dated at 12:00 Europe/Zurich on its day; counted bookings appear immediately in the newcomers' progress (`x` in `No.` = imported but not counted).

## 5. Start, stop, upgrade, rollback

| Task | Command |
|---|---|
| First start | `cp .env.example .env && chmod 600 .env`, fill it in (`docs/secrets.md`), `docker compose up -d`, wait for §6.1, then §4 (initial data) |
| Status | `docker compose ps` (app `healthy` = liveness on 9090) |
| Stop / start | `docker compose stop app` (graceful: running requests finish, max 30 s) / `docker compose start app` |
| Restart | `docker compose restart app` |
| Upgrade | `./backup.sh`, set `VERSION=<new>` in `.env`, `docker compose pull app && docker compose up -d app`; Liquibase migrates on start; check §6.1 and `curl -s https://<host>/api/public/app-info` |
| Rollback (no schema change in between) | set the previous `VERSION`, `docker compose up -d app` |
| Rollback (new version migrated the schema) | changesets are forward-only: restore the backup taken before the upgrade (§7.2) with the previous `VERSION`; data written since then is lost — prefer a fix-forward release when it is small |
| Logs | `docker compose logs -f --since 10m app` (JSON lines, §9.2) |

## 6. Health, jobs, outbox, breakers

### 6.1 Health
`docker compose exec app curl -s localhost:9090/actuator/health/readiness` → `{"status":"UP"}` (includes the database); `/liveness` only checks the JVM; `/actuator/health/version` shows the running version. Readiness DOWN with liveness UP = database unreachable: `docker compose ps postgres`, `docker compose logs postgres`.

### 6.2 Jobs and ShedLock
Admin UI → Operations → Jobs (`GET /api/admin/jobs`, ORGA) lists every job with schedule, last run and lock; "Run now" (`POST /api/admin/jobs/{name}/run`) runs it once (history `JOB_RUN_MANUAL`). Two failures in a row send the job-failure alert (bell + mail to ORGA) and `JobFailing`. A job that stays locked after a crash: the lock expires by itself (`lock_until`, at most `app.jobs.default-lock-at-most-for` = 30 min); only if it must run earlier:
```sh
docker compose exec postgres psql -U foodsharing -c "select * from shedlock where lock_until > now()"
docker compose exec postgres psql -U foodsharing -c "update shedlock set lock_until = now() where name = '<job>'"
```

### 6.3 Mail outbox stuck
Symptoms: `MailOutboxStuck`, magic links not arriving. Admin UI → Operations → Mail outbox (`GET /api/admin/mail-outbox`) shows pending/failed mails with the last error. Causes: SMTP breaker open (§6.4), expired Proton SMTP token (`535` in the error → rotate `SMTP_PASSWORD`, `docs/secrets.md`), per-user rate limits (`app.mail.rate-limit.*`, mails are delayed, not lost). After fixing the cause, failed mails can be retried individually (`POST /api/admin/mail-outbox/{mailId}/retry`); pending ones go out with the next run (every 10 s, backoff up to 8 attempts).

### 6.4 Circuit breakers
Admin UI → Operations → Integrations (`GET /api/admin/integrations`): state per integration (SMTP, WEB_PUSH, TELEGRAM, GEOCODING, HOLIDAY_CALENDAR, wallets, IMAP), last error and outages. An open breaker skips calls and probes every 5 min (`app.integrations.probe-interval`); "Probe" forces a probe, "Send test" sends a test message to yourself. While GEOCODING is open stores keep their coordinates and imports report `GEOCODING_FAILED`.

### 6.5 Disk, uploads, database
- `df -h /var/lib/docker`; uploads: `docker compose exec app du -sh /data/uploads`; database: `docker compose exec postgres psql -U foodsharing -c "select pg_size_pretty(pg_database_size('foodsharing'))"`.
- Unclaimed uploads are deleted after 24 h by the cleanup job; history retention and run retention are jobs too (§6.2).
- Pool exhausted (`DatabasePoolExhausted`): `select state, count(*) from pg_stat_activity group by 1`, look for long `active` queries (`select pid, now()-query_start, query from pg_stat_activity order by 2 desc limit 5`), `select pg_cancel_backend(<pid>)`; raise `DB_POOL_SIZE` only together with Postgres `max_connections`.

## 7. Backups and restore

### 7.1 Backup
`./backup.sh` (cron daily, e.g. `15 2 * * * cd /opt/foodsharing/server/deploy && ./backup.sh 2>&1 | mail -s backup it@…`) writes to `backups/`:
`foodsharing-<UTC>.dump` (pg_dump custom format, `--no-owner --no-privileges`), `.counts` (row count per table), `.uploads.tar.gz` (the uploads volume), `.sha256`; removes own backups older than `KEEP_DAYS` (14). Copy `backups/` off the host afterwards (e.g. `restic backup backups/` or `rsync` to the storage box; encrypted, 30 days). `.env` is backed up separately (password manager), never with the dumps.

### 7.2 Restore
1. `./verify-backup.sh backups/<name>.dump` — checksums, restore into a throwaway `postgres:17`, row counts compared with `.counts`.
2. `./restore.sh backups/<name>.dump --yes` — checksums, stops the app, drops and recreates the database, `pg_restore --exit-on-error`, restores uploads, starts the app and waits for readiness.
3. Check §6.1, log in, look at the newest data; note the restore in the incident log.

`BackupRestoreIT` runs the same pg_dump / pg_restore flags on every build (full schema + seed data, equal row counts, per-table content checksums and sequence positions). The script set is shellcheck-clean.

### 7.3 Point-in-time recovery (optional)
The daily dump loses at most 24 h. For PITR enable WAL archiving on the `postgres` service (`command: postgres -c wal_level=replica -c archive_mode=on -c archive_command='test ! -f /archive/%f && cp %p /archive/%f'` plus an `archive` volume) and take a weekly base backup (`docker compose exec postgres pg_basebackup -U foodsharing -D /archive/base-$(date +%F) -Ft -z`). Recovery: stop the stack, replace `pgdata` with the base backup, create `recovery.signal`, set `restore_command='cp /archive/%f %p'` and `recovery_target_time='<UTC timestamp>'`, start postgres, verify, then start the app. Not enabled by default (decision 82).

### 7.4 Rehearsal (2026-10-29, local compose stack)

```text
$ # COMPOSE_PROJECT_NAME=foodsharing-smoke COMPOSE_FILE=compose.yaml:compose.local.yaml COMPOSE_ENV_FILES=.env.local (stack from smoke.sh --keep)
$ # marker row + upload before the backup
$ ./backup.sh
==> Dumping foodsharing to backups/foodsharing-20261029T102251Z.dump
==> Archiving uploads
OK: foodsharing-20261029T102251Z (396K dump, 158 tables, 149 rows)
$ ./verify-backup.sh backups/foodsharing-20261029T102251Z.dump
==> Checksums
foodsharing-20261029T102251Z.dump: OK
foodsharing-20261029T102251Z.counts: OK
foodsharing-20261029T102251Z.uploads.tar.gz: OK
==> Restoring into a throwaway postgres:17
==> Comparing row counts
uploads archive readable (1 files)
VERIFIED: foodsharing-20261029T102251Z restores (158 tables, 149 rows, 145 changesets)
$ # simulate data loss
marker rows before restore: 0
$ ./restore.sh backups/foodsharing-20261029T102251Z.dump --yes
==> Checksums
foodsharing-20261029T102251Z.dump: OK
foodsharing-20261029T102251Z.counts: OK
foodsharing-20261029T102251Z.uploads.tar.gz: OK
==> Stopping app
==> Recreating database foodsharing
DROP DATABASE
CREATE DATABASE
==> Restoring uploads
==> Starting app
OK: restored foodsharing-20261029T102251Z; app ready
marker rows after restore: 1
upload after restore: hello
```

## 8. Monitoring and alerts
See [`docs/monitoring.md`](monitoring.md): metrics, alert rules with first steps, dashboard and how to start Prometheus/Alertmanager/Grafana (`compose.monitoring.yaml`).

## 9. Support requests, incidents

### 9.1 Support requests
Requests arrive in Admin → Support (`/api/admin/support`, rate limited to `app.support.max-per-hour`). Typical requests:
- **Data export (GDPR Art. 15/20)**: the user downloads it themselves (Settings → Privacy → Export, `GET /api/users/me/export`, zip of every module). There is no export on behalf of a user; if they cannot log in, verify their identity by mail from the registered address and help them log in (§9.3, email change in §9.1).
- **Account deletion (Art. 17)**: the user deletes in Settings → Account; ORGA can delete via `DELETE /api/admin/users/{userId}` (history `ACCOUNT_DELETED`). Personal rows are deleted, authored content is anonymised (decision 71). Confirm to the user by mail; backups age out after `KEEP_DAYS` / 30 days offsite (mention this in the answer).
- **What is kept after a deletion, and for how long**: `docs/privacy/data-inventory.md` (per column: deleted / anonymised / kept) and `docs/privacy/retention.md` (archive 2 years, history per action, cleanup tasks), decision 87.
- **Email change / bounced address**: Admin → Users → email (`PUT /api/admin/users/{userId}/email`); bounced list `GET /api/admin/users/bounced`.

### 9.2 Logs by traceId
Every response carries `X-Trace-Id`; error responses show it to the user (`traceId` in the error body / "error code" dialog). Query:
```sh
docker compose logs --no-log-prefix app | grep '"traceId":"<id>"' | jq -r '[."@timestamp", .level, .logger_name, .message] | @tsv'
docker compose logs --no-log-prefix --since 1h app | jq -c 'select(.level == "ERROR") | {t: ."@timestamp", traceId, logger_name, message}'
```
Client-side errors are logged by `/api/public/client-errors` with the same traceId field.

### 9.3 Magic links
Links are valid 30 min (`app.auth.login-token-validity-minutes`) and single-use; at most 3 requests per email and 10 per IP per 15 min (`429 TOO_MANY_LOGIN_REQUESTS`). "Link does not arrive": check the outbox (§6.3) for the address, the bounced list and the email blocklist. "Link invalid": already used / expired — request a new one; mail scanners that pre-open links are the usual cause (history `LOGIN_FAILED` with reason).

### 9.4 IP blocks and abuse
Admin → Settings → IP blocks (`/api/admin/ip-blocks`) blocks an IP hash (raw IPs are never stored; rotating `APP_IP_SALT` invalidates all blocks). Login-failure and rate-limit spikes (`LoginFailureSpike`, `RateLimitSpike`): find the source in the logs (§9.2, the proxy log has the IPs), block it, and keep the block time-limited.

### 9.5 Incident checklist
1. Acknowledge the alert; open an incident note (time, symptom, alert).
2. Check `docker compose ps`, §6.1, the dashboard and the last 15 min of ERROR logs (§9.2).
3. Mitigate: restart (§5), roll back (§5), open breakers are self-healing (§6.4), outbox retries (§6.3).
4. Data loss or corruption: stop the app, take a backup of the current state, then restore (§7.2) — never restore over the only copy.
5. Security incident (leaked secret, account takeover): rotate the secrets (`docs/secrets.md`), revoke sessions (Admin → Users → deactivate), inform the data protection team within 72 h if personal data is affected.
6. Write the post-mortem (cause, timeline, follow-up tasks) into the incident log.
