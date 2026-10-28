# Runbook — Foodsharing Platform 2.0

Operations handbook for the production instance. Sections marked _(B4/B5/B6)_ are filled by the milestone 7 tasks of the same name (`docs/plan-m7.md`).

## 1. Topology
_(B5)_

## 2. Build & release
_(B4)_

## 3. Configuration
_(B4)_

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
_(B5)_

## 6. Health, jobs, outbox, breakers
_(B5)_

## 7. Backups and restore
_(B5)_

## 8. Monitoring and alerts
_(B6)_

## 9. Support requests, incidents
_(B5)_
