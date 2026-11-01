# Search rework (resume item 20)

## Goal
Global search becomes relevant and personal: Postgres full-text + `pg_trgm` + `unaccent`, own things first, contact-based
people search, forum post contents behind a toggle, a per-user rate limit. Local/e2e/test keep running on H2.

## Decisions
- No Elasticsearch (owner, item 20). Permissions stay inside the same SQL query.
- **Two SQL paths, one ordering.** `SearchIndexRepository` picks the matching SQL by database (Postgres vs H2, detected
  once from `DatabaseMetaData`). Postgres: `search_vector @@ query` OR trigram `similarity`/`%` ≥ threshold, rank by
  `ts_rank` + similarity. H2: today's LIKE per term on the Java-normalised `search_text`, rank 0. Ownership ordering and
  permission filter are plain SQL shared by both paths. Relevance/typo behaviour is proven only by Postgres ITs.
- Order per kind: own-rank (see below), then relevance desc, then title, then id (decision 138).
  - REGION/GROUP: admin 0, active member 1, other 2. STORE: manager 0, team member/jumper 1, other 2.
  - USER: buddy 0, other contact 1. EVENT/POLL: `abs(starts_at − now)` from `Clock` (poll: `starts_at`), ascending.
- Store address rule: store name always matches; street, postcode, town only when the query has ≥ 2 terms (owner).
- **People search is contact-based** for `/api/search` (kind USER) and `/api/users/search` (picker): one shared SQL
  "contacts of X" = shares an active region/group membership, a store team (member/jumper), a conversation, or is a
  confirmed buddy. Global people search (all users) only for ORGA.
- **Last names are NOT hidden** (owner 2026-11-01: "Lets not hide it."). Deviates from the resume line "last names
  hidden in region search"; people results show full names everywhere, `/api/regions/{regionId}/members` unchanged.
  Record in `docs/decisions.md`.
- Email search: only when the query contains `@`; exact full email (case-insensitive), no partial match. Allowed for
  ORGA (all users) and holders of `CAN_MANAGE_USERS` (only users with an active membership in those regions).
  Everyone else: an `@` query matches nothing on email.
- Forum post contents: runtime `FeatureToggle.SEARCH_FORUM_POSTS`, default off. Queried live on `forum_post` (no copy
  into `search_index`); hits stay kind THREAD with a snippet subtitle; hidden posts and inactive/deleted threads never.
- Rate limit: 60 requests/min per user on `/api/search` (`SlidingWindowRateLimiter`, key = user id), 429 +
  `Retry-After`, `app.search.rate-limit-per-minute` (0 = off; 0 in test and e2e). Escalating blocks come with item 5.

## Scope
In: `/api/search`, `/api/users/search`, `search_index` schema, toggle, rate limit, client 429 message + thread snippet.
Out: forum's own search (`ForumSearchService`, keeps `searchBody`), map search, region search, `/api/search/index`,
escalating blocks / per-IP limit (item 5), Meilisearch/Typesense.

## Changes
### Phase A: matching and ordering
- Liquibase `server/src/main/resources/db/changelog/changes/178-search-fulltext.yaml` (never edit 173):
  - 178-1 (all DBs): `search_index` add `address_text VARCHAR(500)` (stores: street, postcode, city; normalised).
  - 178-2 `dbms: postgresql`: `CREATE EXTENSION IF NOT EXISTS unaccent`; text search config `fs_german`
    (`COPY = german`, mapping for word types `WITH unaccent, german_stem`); `search_vector tsvector` column filled by
    the indexer (`to_tsvector('fs_german', …)` in the upsert, Postgres path only) + GIN index; trigram GIN on `title`.
  - H2 never sees the tsvector column; `LiquibasePostgresIT` and the H2 tests both must pass.
- `SearchIndexRepository`: `Source` gets `addressText`; store source reads `postcode`; split `search(...)` into
  `PostgresMatch`/`H2Match` fragments + shared own-rank `case` expressions joined on `entity_id`
  (`region_membership`, region admins, `store_membership`, `buddy`, `event.starts_at`, `poll.starts_at`); `now` param.
  Store address column matched only when `terms.size() >= 2`.
- `SearchIndexer`: write `address_text`; trigger full rebuild once after 178 (index empty → `rebuildIfEmpty` already;
  add a one-off rebuild when `search_vector` is null on Postgres).
- `SearchService`: pass user id + `clock.instant()`; `SearchPermissions` unchanged (still unit-tested).
- `docs/conventions.md` search section: two-path rule.
### Phase B: people
- New `user/repository` native query `contactsOf` reused by `UserRepository.searchByNamePrefix` (replace the
  shared-region `exists`) and by the USER branch in `SearchIndexRepository` (read-only SQL, decision 77).
- USER branch: non-ORGA restricted to contacts (replaces REGION_MEMBERS visibility for users); ORGA unrestricted.
- Email branch in `SearchService` + `UserSearchService`: `q` contains `@` → exact `lower(email) = :email`, scope from
  `PermissionService.isOrga` / regions with `CAN_MANAGE_USERS` (`permissionsByRegion`).
### Phase C: forum posts, rate limit
- Contract first: `openapi/schemas/admin-settings.yaml` `FeatureToggleKey` += `SEARCH_FORUM_POSTS`;
  `openapi/paths/search.yaml` `/api/search` += 429 (`RATE_LIMITED`, `Retry-After` header). Regenerate client.
- `FeatureToggle.SEARCH_FORUM_POSTS(false)`; seed `db/seed/changes/169-admin-seed.yaml` style: add a new seed changelog
  (toggle off) if toggles are seeded rows; admin-settings i18n label in 5 languages.
- Changelog 178-3 `dbms: postgresql`: GIN index on `to_tsvector('fs_german', body)` of `forum_post`.
- `SearchIndexRepository.threadsByPostContent(...)`: same visibility as THREAD rows (region members; ambassador forum
  → admins), excludes hidden posts; merged into THREAD hits (dedupe by thread id), snippet via
  `ForumSearchService.snippet` logic copied into search (no forum dependency) or moved to `common`.
- Rate limit in `SearchService.search` (or controller-side via a small `SearchRateLimiter`), metric tag `search`,
  `RateLimitInventoryTest.LIMITED_OPERATIONS` += `search`; properties in `AppProperties.Search`,
  `application.properties` (60), `application-test/e2e.properties` (0).
- Client `features/search`: `global-search.component.ts` and `search-page.component.ts` map 429 to an error state with
  the rate-limit text (`fs-data-state`, retry); thread hits show subtitle snippet (existing subtitle slot).
  i18n keys in de/en/gws/fr/it, `npm run i18n:check`.

## UI
Small visible change: rate-limit error message, thread snippets in subtitles, new toggle row on admin settings. No new
layout; designer mockup not advised.

## Tests
- New `search/SearchPostgresIT` (Testcontainers, `PostgresContainerConfig`, seed context): stemming ("Bäckerei" finds
  "Baeckereien"/"bäckerei"), umlaut/accent, typo ("Zuerch" → Zürich), ownership order (admin region, manager store,
  buddy first), event time distance, id tiebreak, address only with 2+ words, permission filter, forum post toggle.
- New H2 `SearchFlowIT` cases: ordering + address rule + contacts + email on the LIKE path.
- `SearchServiceTest` (unit): scope, email permission (ORGA, CAN_MANAGE_USERS scoped, others none), rate limit 429.
- `UserSearchService` test: picker finds store-team/chat/buddy contacts, not strangers.
- `IndexUsagePostgresIT`: assert GIN indexes used for search. `RateLimitInventoryTest` updated on purpose.
- e2e `e2e/tests/search/c5-search.spec.ts` (`@C5`): own region first, store address with two words, 429 message
  (forced via a dev/test limit override), forum post hit with toggle on; screenshot spec for the 429 state in
  `e2e/screenshots/search.screen.ts` (baselines in batch, decision 69).

## Done when
- [ ] Phase A: 178 applies on Postgres and H2; Postgres ITs green; order rules hold; decision recorded.
- [ ] Phase B: contacts-only people search in both endpoints; email rule enforced; full names shown.
- [ ] Phase C: toggle + 429 in the contract; limiter in inventory; client messages in 5 languages; i18n check green.
- [ ] `docs/decisions.md` entry (two paths, ordering, no hidden last names, email, toggle, limit); ledger updated.
- [ ] Unit, IT (H2 + Postgres), e2e `@C5` green; nothing skipped.

## Risks
- H2 and Postgres results differ (ranking, typos): e2e must only assert things both paths share.
- `unaccent` is not immutable: only use it inside the `fs_german` config, never directly in an index expression.
- Production DB role needs `CREATE EXTENSION` (pg_trgm already worked in 173-2; verify unaccent on the host).
- Contacts query can be slow for big regions: check with `LoadPathsPostgresIT`/`IndexUsagePostgresIT`.
- Email exact-match still lets permitted users confirm an address exists; scoped and accepted by the owner.
