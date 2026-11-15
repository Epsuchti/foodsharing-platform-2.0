# Testing rework: lean unit/integration tests, persona journeys in Playwright

## Goal
Replace the per-feature test suite with thin unit and integration layers plus about 46 persona journeys that take
screenshots on the way. PR runs drop from 53–100 min to about 25 min, and a feature change touches one journey instead
of four specs. Today (run 43465186721): 425 job-minutes, 88 % e2e (1116 tests x 5 browsers, 44 % axe sweep), 31 jobs
against GitHub's limit of 20 at once, build 21 min (integration 12.6, unit 5.6), 1130 PNGs.

## Decisions (owner, 2026-11-15; record as decision 180)
1. All 5 browsers stay on PRs, running journeys only. One job per browser, 2 jar workers per job.
2. Screenshots only at journey checkpoints: desktop + phone, light, German, about 250–300 PNGs. Gallery about 25 per
   project. The separate screenshot suite (`e2e/screenshots/`, `screenshots-*` projects) is removed.
3. Every ledger ID is covered by a journey tag **or** a named unit/integration test. The coverage gate stays.
4. Integration tests 80 → about 25 classes. Kept: PostgreSQL, security/IDOR/privacy, permission matrices, one smoke per controller.
5. One shared `fs-data-state` test and one "things go wrong" journey replace per-page error-state and component state specs.
6. Big-bang on one branch `rework/testing`, phased commits, required checks updated once at the end.
7. Axe: at every checkpoint on chromium and mobile-chrome; the full route sweep (light + dark) runs on chromium only, in its own PR job.
8. Removing a test under this plan with a mapping entry is redesign. "Never skipped or weakened to get green" stays in force.
9. Unchanged: decision 140 (3 retries on CI), decision 161 (baselines updated in CI), Lighthouse job, load tests, ArchUnit, `i18n:check`.

## Scope
- **In:** `e2e/`, `server/src/test/`, client `*.spec.ts`, `ci.yml`, `screenshots-update.yml`, the docs listed below.
- **Out:** product behaviour, contract, Liquibase, load tests, `security.yml`. Product code changes only for missing
  `data-testid`s or to make a rule unit-testable (extract, no behaviour change).

## Target rules (what gets which test)
| Kind | Use it for | Not for |
|---|---|---|
| Unit (plain JUnit / Vitest, no Spring context) | every rule, permission, state machine, validation, mapper edge case | wiring, happy paths |
| Guardrails | `ArchitectureTest`, `ModulesTest`, `OpenApiContractTest`, `StableOrderTest`, inventory tests, `check-data-state` | |
| Integration | Liquibase/queries on PostgreSQL + H2 seed boot, IDOR/identity-leak/privacy, one smoke per controller (auth + one 2xx + one 403) | flows a journey walks |
| Journey (Playwright) | a persona doing a real multi-step process through the UI | rule permutations, API-only checks |

## Changes

### E2E infrastructure (`e2e/`)
- `global-setup.ts`: start `E2E_WORKERS` jars (default 2; reuse the slot/port/`E2E_INSTANCE` logic of
  `scripts/run-sharded.mjs`). `fixtures/index.ts`: `baseURL` picks the jar by `testInfo.parallelIndex`; `seedReset`
  default becomes `'test'` (0.35–1.5 s), so journeys never depend on each other. Login stays `role` / `as(role)`.
- `playwright.config.ts`: `workers: E2E_WORKERS`, file-level parallelism; `testMatch` `journeys/**/*.journey.ts`;
  projects `chromium` (1280x800) and `mobile-chrome` take screenshots, `firefox`, `webkit`, `mobile-safari` do not;
  new project `a11y` (chromium, `tests/a11y/pages.spec.ts`); drop `screenshots-*` and `E2E_SCREENSHOTS`.
- New `fixtures/checkpoint.ts`: `checkpoint(page, name)` = wait for data states and animations, then (screenshot
  projects only) axe via `expectNoSeriousViolations` and `toHaveScreenshot`. Move the Inter font, mask and stability
  code from `screenshots/_setup.ts` into it. Other browsers only wait for stability.
- Journeys in `e2e/journeys/<persona>/<slug>.journey.ts`, built on `tests/personas/_persona.ts` (`Walk`, step budgets).
  Title carries all IDs it proves. About 46 files, 4–8 checkpoints each:
  newcomer 3; auth and account 5; foodsaver pickups 4; store manager 6; EinAb guide/admin 4; ambassador
  (verification, reports, region) 4; community (forum, polls, surveys, events, baskets, FSP) 6; messaging, mailboxes,
  notifications 4; orga/admin (content, legal, automation, OAuth, jobs) 5; public, map, search, five-language smoke 3;
  shell/phone width 1; things go wrong (500, 403, 404, offline, retry) 1.
- Gallery: one journey, about 25 captures per project (one per shared component group, incl. all `fs-data-state` states).
- Delete `e2e/tests/**` except `a11y/`, `e2e/screenshots/`, all old baselines; keep fixtures that journeys still use.
- `scripts/feature-coverage.mjs`: an ID passes with a passing journey tag or an entry in new `e2e/coverage-map.json`
  (`"EA20": "server/src/test/java/.../IntroProgressJobTest.java"`); the script fails if the file is missing. Still `--strict`.
- Chromium and mobile-chrome run inside the pinned Playwright image (reuse `scripts/screenshots.sh` /
  `screenshots-container.sh`, pointed at the two projects); `screenshots-update.yml` keeps its inputs.

### Server tests (`server/src/test/`)
- Keep: all `*PostgresIT`, `LocalProfileSmokeIT`, `BackupRestoreIT`, `IdorSpotCheckIT`, `StoreIdentityLeakIT`,
  `QueryCountIT`, permission matrix tests, guardrails, `*ControllerTest` slices.
- Add one parameterised `ApiSmokeIT` (one shared context) covering every controller; delete flow/API ITs whose
  behaviour a journey or unit test now proves (target about 25 IT classes, failsafe 4 min or less).
- The 27 Spring-context "unit" classes over 10 s (950 fork-seconds, e.g. `NotificationServiceTest`, `BellServiceTest`,
  `BasketServiceTest`): convert to plain tests with fakes, merge onto `SharedJpaSliceConfig`, or delete if duplicated.
- API-only e2e specs (`c15-maintenance-jobs`, `d8-csrf`, `ad1-ops-endpoints`, `s2-import-api`) become server tests.

### Client tests (`client/src/app/`)
- Keep specs for utils, rules, pinned rule cases (decision 128), core services, shared components.
- One full state spec for `fs-data-state`/`loadResource`; delete per-component loading/empty/error specs.

### CI and docs
- `ci.yml`, 16 jobs: `e2e` matrix of 5 browsers, no shards, 2 workers; new `a11y` job; `screenshots` job removed.
- `AGENTS.md` platform rules: replace the per-feature `@ID` + "screenshot specs for its states" bullet with the target
  rules above; DataState bullet keeps the component rule but drops per-page state tests; add decision 8's wording.
- `plan.md` §18.1–18.4 and 18.6, `docs/conventions.md` A8 and A60, `docs/decisions.md` 180 (amends 69, 109, 120, 126,
  127, 137), `docs/tasks/README.md` tester step, `docs/ledger.md` note.
- New `docs/testing-migration.md`: one row per old e2e spec, screen file, deleted IT and deleted client spec →
  journey + checkpoint, unit test, `ApiSmokeIT`, or "dropped: reason".

### Order of work (one commit or more per step, branch `rework/testing`)
1. Mapping table skeleton generated from the file lists (207 specs, 40 screen files, 80 ITs, 27 slow classes).
2. E2E infrastructure + `checkpoint` + the store-manager journey as the pattern; old suite still runs.
3. Per area (order of the journey list): write journeys, add unit tests for rules only e2e asserted, fill the table,
   run old and new specs of the area green on chromium, then delete the old specs and screen files.
4. Gallery, things-go-wrong journey, a11y project, shared `fs-data-state` spec, client spec trim.
5. Server trim (`ApiSmokeIT` first, then deletions module by module, table rows as you go).
6. Coverage script + map, CI rewrite, docs and rules.
7. Push, run `screenshots-update.yml -f mode=all`, review every PNG, full CI green three times in a row.

## UI
No visible UI change; no mockup. Only `data-testid` additions if a journey needs them.
## Tests
- New: about 46 journeys, gallery journey, `ApiSmokeIT`, `fs-data-state` spec, unit tests for rules moved out of e2e, a coverage-map self-test.
- Removed on purpose, each with a row in the mapping table: `e2e/tests/**` except `a11y/`, `e2e/screenshots/**`,
  about 55 IT classes, per-component state specs, all 1130 baselines (replaced by about 300).

## Done when
- [ ] PR run is 25 min or less wall clock (build 10 or less, slowest e2e job 12 or less), 16 jobs, none queued.
- [ ] `feature-coverage.mjs --strict` passes: every ledger ID has a journey tag or a map entry.
- [ ] `docs/testing-migration.md` has no empty row; every "dropped" has a reason.
- [ ] Every rule previously asserted only in e2e or a deleted IT has a unit test.
- [ ] Baselines number 250–300 plus about 50 gallery; each reviewed.
- [ ] Three consecutive green CI runs without a failed (not merely flaky) test; flaky list reported.
- [ ] AGENTS.md, plan §18, conventions, decision 180 updated.
- [ ] Owner sets required checks on `main` to: `OpenAPI contract`, `Dependency review`, `shellcheck`, `npm audit .`,
      `npm audit client`, `npm audit e2e`, `Build and verify`, `E2E chromium`, `E2E firefox`, `E2E webkit`,
      `E2E mobile-chrome`, `E2E mobile-safari`, `Accessibility sweep`, `Lighthouse`, `Feature coverage`, `ER diagram`
      (add to `todo-eric.md`).

## Risks
- **Logic bugs found later and less precisely.** Counter: the unit-test-per-rule rule stays; step 3 moves e2e-only rule assertions into unit tests.
- **Lost coverage in a big-bang.** Counter: mapping table, old and new green side by side before each deletion, cut-list exclusions.
- **Long journeys are flakier and fail late.** Counter: seed reset per test, `Walk` steps name the failing step, traces, flaky list (decision 140).
- **Time target with 5 browsers.** 46 journeys at 15–20 s (webkit persona specs today: 13–20 s) is about 15 min serial, 8 min with
  2 workers, plus 2 min setup. If 2 jars do not fit a 4-CPU runner: 2 shards for webkit and mobile-safari (18 jobs).
- **Long-lived branch conflicts with feature work.** Counter: rebase daily; tests merged meanwhile are converted on the branch.
- **Page states no journey visits are not screenshotted.** Accepted by the owner (decision 2); the gallery shows each state once.
