# Agent Notes

Single source of truth: `plan.md`. Build state: `docs/ledger.md`. Decisions: `docs/decisions.md`. Detailed conventions: `docs/conventions.md`.

## Carried over from foodsharing-einab-tabelle
- Prefer production-safe migrations that work on PostgreSQL **and** H2 (`MODE=PostgreSQL`).
- No `CLOB`/`@Lob`/`TEXT`. Long text is `VARCHAR(32600)` in both the entity and the Liquibase changelog.
- Never edit an applied changelog; add a new numbered file under `db/changelog/changes/`.
- ProtonMail SMTP defaults outside `local`; `local` points at Mailpit (`server/start_smtp_server.sh`).
- Angular uses only the generated OpenAPI services/models from `client/src/app/api`. No raw `HttpClient` for app endpoints (ESLint enforces it outside `core/` and `api/`). If an endpoint or model is missing, change `server/src/main/resources/openapi/` and regenerate.
- Skip full builds for trivial edits; build what the change actually touches.

## Platform rules
- Contract first: change `openapi/paths/<tag>.yaml` + `openapi/schemas/<tag>.yaml`, regenerate, then implement.
- Every `@RestController` implements exactly one generated `network.foodsharing.api.*Api` interface and has no mapping annotations of its own (ArchUnit).
- Layering: `web` → `service` → `repository`. Controllers never touch repositories. Generated models stay in `web`/`mapper`.
- Every mutating (`@Transactional`, not read-only) service method records history via `HistoryService` or is annotated `@NoHistory(reason=…)` (ArchUnit).
- No `Instant.now()`/`LocalDate.now()` etc.; inject `java.time.Clock`.
- Every rule change comes with a unit test.
- Every data-loading UI element uses `DataState` (`loadResource`) and `<fs-data-state>` with skeleton, empty, error + retry, forbidden, not-found and offline states. Mutations use `runMutation`.
- New UI text in all five languages (`de`, `en`, `gws`, `fr`, `it`); `npm run i18n:check` must pass.
- Seed data updated with every new entity (`db/seed/`, context `seed`, fixed UUIDs, see `docs/seed-ids.md`).
- Every feature gets a Playwright e2e test tagged with its feature ID (`@S6`) and screenshot tests for its states. Screenshot baselines are only updated deliberately (`e2e/scripts/screenshots.sh --update-snapshots`), never to make a failing test pass by accident.
- Tests are never skipped, disabled or weakened to get green. Fix the code.
- `data-testid="<module>.<page>.<element>"` on interactive elements.
