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
_(B2)_

### 4.4 Past EinAbs (D16.3)
_(B3)_

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
