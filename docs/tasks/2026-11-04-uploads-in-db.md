# Uploads in PostgreSQL (C10 / A34 / A41)

## Goal
Upload bytes (originals and `sm`/`md`/`lg` variants) live in PostgreSQL instead of `app.uploads.dir`. The nightly
`pg_dump` then backs them up, the RWO `/data` PVC goes away and the app can run more than one replica. Upload ids,
URLs (`/api/uploads/{id}?size=`) and stored references (`photo_upload_id`, `avatar_url`, `mail_attachment.upload_id` …)
stay exactly as they are.

## Findings (verified)
- All byte I/O already goes through one SPI: `upload/service/UploadStorage` (`write/open/delete/list` by storage key),
  only implementation `DiskUploadStorage` (the only `Files.write`/`Path.of(uploads.dir)`). `BasketService`,
  `MailAttachmentStore`, `MailboxDirectory`, `MailDeliveryService`, `DataExportService`, `AdminUploadsController`
  (→ `UploadAdminService`) etc. only call `UploadService` (`store/open/delete`). Variants are keys `<storage_key>_<size>`.
- `UploadService` writes bytes before the row and repairs with after-commit/on-rollback file deletes;
  `deleteOrphanVariants` lists the disk to remove variants without a row; `UploadSeedFiles` (local/e2e) writes
  `db/seed/upload/files/*` (31 files, 116 KB) under keys `seed/<file>` and renders variants.
- Served only by `UploadsController.downloadUpload` (`Cache-Control: public, max-age=86400` for public uploads, no ETag,
  no 304). No static resource handler for uploads. URLs in entities point at `/api/uploads/<id>`, never at a path.
- Limits: `spring.servlet.multipart.max-file-size=20MB`, `app.uploads.max-size-mb=20`, compose ≤ 20 MB attachments,
  inbound raw mail ≤ 25 MB (`IncomingMailProcessor.MAX_RAW_BYTES`). Hikari pool 20 (prod), Tomcat 50 threads.
  Pod limit 1536 Mi, heap 75 % ≈ 1.1 GB. Postgres PVC 10 Gi (chart default), local backup PVC 5 Gi, B2 retention 14.
- Bans: ArchUnit rule 7 (`ArchitectureRules`) rejects `@Lob` and `columnDefinition` with TEXT/CLOB; nothing bans
  BYTEA/`byte[]`. Conventions §7 says "no `@Lob`, no TEXT". Repositories already use `JdbcClient`/`JdbcTemplate`
  (`TelegramOffsetRepository`, `StatisticsReadModel`).
- Build: Spring Boot 4.1.2 → H2 2.4.240, Hibernate 7.x, pgjdbc 42.7.x. H2 2.x `BINARY VARYING` (alias `BYTEA`) holds up
  to 1 000 000 000 bytes and is not a LOB; PostgreSQL `bytea` ≤ 1 GB, TOASTed out of line above ~2 kB.
- The compose deploy (`server/deploy/backup.sh`, `restore.sh`, `verify-backup.sh`, `compose.yaml`, runbook §6.5/§7,
  decision 82) tars the uploads volume; k3s backup (hosting repo) only runs `pg_dump -Fc`.

## Decisions (proposed: decision 158, amends A34/A41 and decision 82)
- **BYTEA is allowed for binary content only**, in exactly one place: `upload_blob_chunk.bytes`, written and read with
  `JdbcClient` in the `upload` repository layer. No JPA entity gets a `byte[]` field (new ArchUnit rule), so bytes can
  never be loaded with an entity. Liquibase `type: BYTEA` (→ `bytea` on PostgreSQL, `BINARY VARYING` on H2); never
  `BLOB` (Liquibase maps it to an H2 LOB). Conventions §7 gets the sentence; A34/A41 say "bytes in `upload_blob_chunk`".
- **Chunked storage, 1 MiB per row** (default of Q2): bounded heap on reads, real streaming without large objects
  (pgjdbc reads a `bytea` value fully into memory, `java.sql.Blob`/`lo_*` would need LOBs and `lo_unlink` hygiene).
- Bytes are written in the same transaction as the `upload` row; FK `ON DELETE CASCADE` removes them with the row.
  That deletes the after-commit/rollback file hooks and the orphan-variant sweep.
- Content is immutable per (upload, variant) → ETag = sha256 of that blob.

## Scope
In: schema + storage implementation, serving with ETag/304, one-time import from disk, seed, config/doc/deploy-script
clean-up in this repo, tests. Out: hosting-repo changes (listed as follow-up), Range requests, S3 backend, dropping
the `storage_key` column (follow-up changelog once the import is gone), not storing `lg` when the original is small (Q5).

## Changes
**Liquibase** `db/changelog/changes/186-upload-blob.yaml` (context `schema`, include in master):
- `upload_blob(upload_id UUID FK upload(id) ON DELETE CASCADE, variant VARCHAR(8) /* orig|sm|md|lg */, mime_type
  VARCHAR(120), size BIGINT, sha256 VARCHAR(64), chunk_count INT, created_at TIMESTAMPTZ)`, PK `(upload_id, variant)`.
- `upload_blob_chunk(upload_id UUID, variant VARCHAR(8), chunk_no INT, bytes BYTEA NOT NULL)`, PK
  `(upload_id, variant, chunk_no)`, FK `(upload_id, variant)` → `upload_blob` ON DELETE CASCADE.
- changeSet `dbms: postgresql`: `ALTER TABLE upload_blob_chunk ALTER COLUMN bytes SET STORAGE EXTERNAL` (out of line,
  no pglz/lz4 attempt on JPEG/PDF that don't compress; saves CPU).
- New tables, no data change → production-safe on both DBs; no edit of `130-upload.yaml`.

**Server** (`network.foodsharing.upload`):
- `repository/UploadBlobRepository` (JdbcClient): `insert(uploadId, variant, mime, InputStream)` splits into 1 MiB
  chunks and computes sha256/size; `find(uploadId, variant)` → metadata; `chunk(uploadId, variant, n)`;
  `exists(uploadId, variant)`; `countMissingOriginals()`. Delete = cascade.
- `UploadStorage` SPI reshaped to `(UUID uploadId, String variant)`: `write`, `open` → `StoredBlob(mime, size, sha256,
  InputStream)`, `exists`; drop `list`/`delete`/`StoredFile`. `DbUploadStorage` replaces `DiskUploadStorage`; its
  stream (`ChunkedBlobInputStream`) fetches one chunk per query outside any transaction, so no connection is held
  while a slow client downloads.
- `UploadService`: `store` writes `orig` + variants via storage inside the transaction; remove `deleteBytesOnRollback`,
  `deleteBytesAfterCommit`, `deleteQuietly`, `deleteOrphanVariants`; `variantsStored` uses `exists`; `open` returns
  sha256 for the ETag. `UploadCleanup` drops the orphan sweep. `storage_key` is still filled (unique, NOT NULL) but
  only means "legacy disk path".
- `UploadsController.downloadUpload`: `ETag: "<sha256>"`; `If-None-Match` match → 304 without reading chunks;
  `Cache-Control` per Q4 (public: `public, max-age=604800, immutable`; private: `private, no-cache`).
- `UploadDiskImport` (`@Profile("prod")`, `app.uploads.import-dir` empty = off; `ApplicationReadyEvent`, ShedLock):
  for every `upload` row without an `orig` blob, read `<import-dir>/<storage_key>`, verify sha256 against
  `upload.sha256` (mismatch/missing → logged, row left alone), import variants `<key>_<size>` if present else render
  them; idempotent, batches of 50, one transaction per upload, `@NoHistory(reason="moves bytes of existing uploads")`.
  Logs `imported/missing/mismatched`. Removed in the release after the hosting cut-over (follow-up).
- `UploadSeedFiles`: writes seed bytes via `DbUploadStorage` for the seeded rows (looked up by `storage_key
  seed/<file>`), only when the blob is missing or its sha256 differs; still runs after every seed reset (the H2 SCRIPT
  snapshot is taken before it, so it holds no seed bytes).
- `AppProperties`/properties: drop `app.uploads.dir` (all profiles), add `app.uploads.import-dir` (prod `${UPLOADS_IMPORT_DIR:}`).
  Surefire/failsafe per-fork `app.uploads.dir` (A60) and `e2e/global-setup.ts` instance dir become unnecessary.
- `IncomingMailProcessor`/`UploadsController.createUpload`: unchanged (bytes already in memory, ≤ 25/20 MB).

**OpenAPI** `paths/uploads.yaml` `downloadUpload`: optional header parameter `If-None-Match`, response header `ETag`,
response `"304"`. Non-breaking for oasdiff. Regenerate; client unchanged (browser does the conditional GETs).

**Deploy/docs (this repo)**: `server/deploy/compose.yaml` (drop `uploads` volume, `UPLOADS_DIR`), `backup.sh`/
`restore.sh`/`verify-backup.sh` (drop the tar step), runbook §1/§6.5/§7 (size query `pg_total_relation_size
('upload_blob_chunk')`, cut-over SQL check), `docs/secrets.md` (`UPLOADS_DIR` → `UPLOADS_IMPORT_DIR`), `server/Dockerfile`
(`/data` volume no longer needed), conventions §7 + A34 + A41 + A60, decisions 158, `docs/seed-ids.md` module 0040,
`docs/privacy/retention.md` (blobs go with the `upload` row). No data-inventory row (no user column).

**Hosting repo follow-up (not edited here)**: after the import release is verified, remove `persistence` and
`UPLOADS_DIR` from `apps/foodsharing-platform/{prod,test}-values.yaml`, keep `podSecurityContext` only if still needed,
raise `postgres.storage.size` (prod 10 → 20 Gi) and `backup.local.size` (5 → 10 Gi), then `replicas` may go above 1.

## Size, heap, pool
- Writes: unchanged peak (the multipart/mail bytes are already a `byte[]`) plus 1 MiB chunk buffers.
- Reads: ≤ ~2–3 MiB per active download (one chunk + pgjdbc decode) instead of the file; 50 parallel downloads of 20 MB
  files ≈ 150 MB worst case, fine for a 1.1 GB heap. Each chunk borrows a connection for milliseconds; pool 20 stays.
- Statement time per 1 MiB chunk is small; no long transactions.

## Backup growth (pg_dump -Fc)
- Formula: `V = Σ_kind n_kind × (avg_orig + avg_variants)`; dump ≈ `1.05 × V` (COPY hex-encodes bytea, zlib packs it
  back to ≈ the binary size; JPEG/PNG/PDF/zip/docx don't compress further). B2 holds `14 × dump`; local PVC 1 dump.
- Per item: client crops photos to ≤ 1024/1280 px JPEG ≈ 150–300 kB; variants add sm 4 + md 25 + lg ≈ orig ⇒ ×2.
  Seed: 116 kB. Today on the demo PVCs: measure with `du -sh /data/uploads` before cut-over.
- Realistic year 1 (Zürich launch): 2 000 users × 60 % photo × 0.2 MB ≈ 0.25 GB; 1 500 baskets × 0.5 MB ≈ 0.75 GB;
  FSP/blog/content < 0.1 GB; mail attachments 20 mailboxes × 2/week × 0.5 MB ≈ 1 GB ⇒ **≈ 2 GB, +1.5–2.5 GB/yr**.
  B2: 14 × 2.1 GB ≈ 30 GB (≈ USD 0.2/month). Dump/restore time grows by roughly a minute per few GB.
- Revisit (move to object storage) when uploads exceed ~20 GB or the dump takes > 30 min.

## UI
No UI change, no mockup. The admin upload summary keeps its numbers (from `upload.size`).

## Tests
- Unit: chunk splitting/joining at 0, 1, 1 MiB−1, 1 MiB, 1 MiB+1, 20 MB bytes; sha256/size; ETag/304 decision;
  Cache-Control per public/private; import rules (missing, mismatch, already imported, variants present/absent).
- `UploadServiceTest` changes on purpose: no disk dir, rollback leaves no blob, delete cascades, orphan-variant tests
  removed with the feature. `IncomingMailProcessorTest`: no uploads dir.
- ArchUnit: entities have no `byte[]` fields; existing `@Lob`/TEXT rule unchanged; controller/layer/@NoHistory rules
  cover the new classes. `ArchitectureRuleFixtureTest` gets a fixture for the new rule.
- `LiquibasePostgresIT`: `upload_blob_chunk.bytes` is `bytea` with storage `e`; H2 test: column type `BINARY VARYING`
  (not BLOB). `BackupRestoreIT`: an upload's bytes survive dump/restore byte-identical. `UploadPublicAccessIT`: 304.
- e2e `@C10` (`e2e/tests/uploads/c10-upload-storage.spec.ts`): upload, download byte-identical, ETag + conditional GET
  → 304, private attachment `Cache-Control: private`; seeded avatar renders after a seed reset (existing
  `c10-seed-avatars` must stay green). No screenshot change (no UI).

## Done when
- [ ] Changelog 186 runs on H2 and PostgreSQL; no `app.uploads.dir` left in code, properties, compose, tests.
- [ ] Upload/download/delete/variants/mail attachments/data export work with bytes only in the DB.
- [ ] ETag + 304 + Cache-Control as decided; contract regenerated, oasdiff non-breaking.
- [ ] Import job imports a copied demo `/data/uploads` idempotently; SQL check shows 0 missing originals.
- [ ] Docs/decision/conventions/seed-ids/runbook/deploy scripts updated; `./mvnw verify`, `npm run i18n:check`, e2e `@C10` green.
- [ ] Hosting follow-up written to `todo-eric.md` (persistence, UPLOADS_DIR, PVC sizes, replicas).

## Risks
- Import before the PVC is gone: the release with the import must start once with the PVC still mounted
  (`UPLOADS_IMPORT_DIR=/data/uploads`); verify, wait for one good nightly backup, then remove the PVC.
- DB growth hits the 10 Gi Postgres PVC earlier than before (uploads + WAL + dumps): raise sizes in the follow-up.
- H2 file DB in `local` grows with uploads (`./data/foodsharing.mv.db`); harmless, reset with the seed reset.
- Two replicas + import: ShedLock prevents a double run; the import is idempotent anyway.

## Alternatives
- **S3-compatible (Backblaze B2 / MinIO):** scales and offloads traffic (presigned URLs), but adds credentials, an SDK,
  a second consistency domain (orphans between row and object), and a separate backup/versioning story; MinIO in the
  cluster needs its own PVC and backup — the same problem again. Worth it at tens of GB.
- **RWX volume (Longhorn/NFS):** allows replicas, but still outside `pg_dump`, extra storage infrastructure on k3s.
- **Recommendation:** PostgreSQL now (single backup, transactional, volume is small); the `UploadStorage` SPI keeps a
  later S3 backend a one-class change.

## Open questions for the owner
1. Keep files in PostgreSQL at all? **Recommendation: yes, now**; revisit S3/B2 at ~20 GB or 30-min dumps.
2. Chunked rows (1 MiB) or one `bytea` row per file? **Recommendation: chunked** (bounded heap, streaming, short connection use).
3. Import the existing demo files from `/data/uploads`, or start empty since the platform isn't live?
   **Recommendation: build the small idempotent import**, run it once on test and prod, remove it a release later.
4. Cache policy: public images `public, max-age=604800, immutable` (a deleted avatar may linger ≤ 7 days in browser
   caches), private files `private, no-cache`, both with ETag/304? **Recommendation: yes.**
5. Stop storing `lg` when the original is already ≤ 1280 px (saves ~40 % of image bytes)? **Recommendation: not in
   this task**; follow-up, since it changes A41.
6. Raise prod Postgres PVC 10 → 20 Gi and local backup PVC 5 → 10 Gi in the hosting follow-up? **Recommendation: yes.**
7. Remove the uploads volume and tar step from the compose deploy (`server/deploy/*`) too? **Recommendation: yes**,
   compose and k3s then back up the same way.
