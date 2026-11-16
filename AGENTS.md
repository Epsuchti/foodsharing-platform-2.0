# Agent Notes

Single source of truth: `plan.md`. Open points: `todo.md`; ones that need a person: `todo-eric.md`. Build state: `docs/ledger.md`. Decisions: `docs/decisions.md`. Detailed conventions: `docs/conventions.md`.
Environment: start every shell with `source scripts/env.sh`; requirements, commands and OS notes are in `docs/dev-environment.md`.
Big docs (`plan.md`, `docs/conventions.md`, `docs/decisions.md`, `docs/ledger.md`): never read them in full. Use their contents section or search for the ID (`A3.`, `| 133 |`, `| S6 |`) and read only those parts.

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
- Every data-loading UI element uses `DataState` (`loadResource`) and `<fs-data-state>` with skeleton, empty, error + retry, forbidden, not-found and offline states. Mutations use `runMutation`. The states are tested once in the shared `fs-data-state` spec, not per page (decision 180).
- New UI text in all five languages (`de`, `en`, `gws`, `fr`, `it`); `npm run i18n:check` must pass.
- Seed data updated with every new entity (`db/seed/`, context `seed`, fixed UUIDs, see `docs/seed-ids.md`).
- Tests per kind (decision 180): **unit** (plain JUnit / Vitest, no Spring context) for every rule, permission, state machine, validation and mapper edge case; **guardrails** (`ArchitectureTest`, `ModulesTest`, `OpenApiContractTest`, `StableOrderTest`, inventory tests, `check-data-state`); **integration** only for Liquibase/queries on PostgreSQL, H2 seed boot, IDOR/identity-leak/privacy and the `ApiSmokeIT` row of each controller; **journey** (`e2e/journeys/<persona>/<slug>.journey.ts`) for a persona doing a real multi-step process through the UI, not for rule permutations or API-only checks. A feature extends the journey of its persona (or adds one) with its ledger ID in the title (`@S6`) and `checkpoint()`s at the states worth looking at; an ID proven only below e2e goes into `e2e/coverage-map.json`. Screenshot baselines are only updated deliberately, never to make a failing test pass by accident.
- Screenshot baselines are regenerated in CI, not locally (decision 161): when a task that changes visible UI is done, push its branch and run `gh workflow run screenshots-update.yml --ref <branch>` (optional `-f grep=@S6`, `-f mode=all`; it regenerates the journey checkpoints of chromium and mobile-chrome), wait with `gh run watch`, then `git pull` the bot commit. Review every changed PNG (`git show --stat HEAD`, open them with Read) against the intended change and report unexpected diffs instead of accepting them. Never run it on `main`.
- Tests are never skipped, disabled or weakened to get green. Fix the code. Removing a test under an owner-approved redesign, with a row in `docs/testing-migration.md`, is redesign, not weakening (decision 180).
- `data-testid="<module>.<page>.<element>"` on interactive elements.

## Choosing the workflow (agents from the `workflow` plugin, github.com/Epsuchti/claude-agents; details in `docs/tasks/README.md`)
| Task | Who |
|---|---|
| A question, a lookup, a one-line change | the main session directly |
| Clear and small: about 3 files or fewer, one layer, no contract, Liquibase or permission change | `fixer` (Sonnet); then `tester` if shared components, the shell or many pages are touched |
| Needs a decision, spans layers (contract + server + client), changes the database, permissions or data protection, a visible UI change, or "done" can't be said in one sentence | `planner` → owner approves the plan (→ `designer` mockup if the owner wants one) → `implementer` → `tester` → fixes by the same `implementer` |

- When in doubt, use the planner: building in the wrong direction costs more than a question round.
- When the `designer` reports a mockup, the main session opens it for the owner right away in the built-in browser (`navigate` to `file:///…/docs/design/<slug>-mockup.html`), before relaying the designer's questions. After the designer revises it, open it again.
- Agents can't ask the owner directly. The main session relays the planner's questions and the answers.
- The main session merges into `main` and pushes only when the owner says so.
