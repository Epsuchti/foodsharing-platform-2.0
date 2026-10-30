# Final review — Milestone 7 and the whole build (plan-m7 Part D step 9)

Reviewer: Fable planner, 2026-10-30, `main` at `7821d14` (417 commits; 189 commits / 67 merges since 2026-10-29). Scope: plan.md §17–§21 and the §20 definition of done, AGENTS.md rules, `docs/plan-m7.md` Part D, decisions 1–127, conventions A1–A60. Read-only; evidence is quoted by path, test name and decision number.

## 1. Verdict

The platform is **launch-ready in code and documentation, but not yet launch-proven**. Every one of the 150 feature rows of §13/§14 has a backend, a frontend and a tagged Playwright test (decision 126; the `ci.yml` coverage job runs `feature-coverage.mjs --strict`). The architecture rules hold: ArchUnit rules 1–9 in `server/src/test/java/network/foodsharing/ArchitectureTest.java` cover generated-API controllers, layering, history on every mutation, Clock-only time, portable entities and locked jobs. i18n is complete in five languages: all 44 catalogs have identical key counts across de/en/gws/fr/it. The three design rounds ran app-wide with concrete fixes (decisions 121/124/127). Security, privacy, runbook, backups, monitoring and load test are documented with guarding tests.

What is **not** proven on this machine:
- (a) the screenshot suite: only 138 baselines per project exist, for 8 of 37 `*.screen.ts` specs, while the specs now call `visual.expectScreen` 301 times and `expectElement` 13 times; the 138 that exist predate the design rounds;
- (b) the 13 Postgres Testcontainers ITs, never run in this build phase;
- (c) CI on a remote, never run;
- (d) `/onboarding` Lighthouse ≥ 90 on an idle machine (last measured 69 under load; decision 123 defers the decision to the gate);
- (e) a real usability test and native-speaker translations (decision 3).

Launch should wait for items (a)–(d) to run once on a Docker/CI machine, plus the human items of the launch report. None of them needs new product code.

## 2. Definition of done (§20) — per item

| §20 item | Status | Evidence | Gaps |
|---|---|---|---|
| Passing e2e test per feature ID | met, except AD1 ledger row | `docs/ledger.md` 182 rows done, 1 in progress (AD1); `e2e/tests/admin/ad1-admins-ui.spec.ts` (3 `@AD1` tests incl. permission matrix) exists; 186 spec files; `feature-coverage.mjs --strict` 150/150 (decision 126) | `docs/coverage.md` is stale (last regenerated at `1ad9bb0`: "149/150, C13 missing, gate FAILED"); regenerate at the gate. AD1 waits for the test worker (decision 106). A full five-project Playwright run on the gate commit is still outstanding (launch report "Test results" all TBD). |
| Screenshot tests for all states, mobile + desktop | specs met, baselines not | 37 `e2e/screenshots/*.screen.ts`, ~314 screenshots × 2 projects (`screenshots-desktop` 1280×800, `screenshots-mobile` Pixel 7; decision 109); `screenshots.sh` + CI job `screenshots` with `updateSnapshots: 'none'` on CI | `e2e/__screenshots__` holds 138 PNGs per project, only for auth/content/gallery/history/legal/pages/quiz/regions; 29 specs have **no baseline**, and the 138 existing ones predate the design rounds. In CI the `screenshots` job will fail on the first run (missing snapshots), and `release` `needs: screenshots`, so no tag release is possible until baselines are generated in Docker (D18.2 todo). |
| No axe violations | met (local) | `e2e/tests/a11y/pages.spec.ts` sweeps `SWEEP_ROUTES` (190 routes) in light and dark + Pixel 7 top-level pages; `a11y-route-coverage.mjs` runs in the CI `build` job and fails when an app route is missing from `routes.ts`/`SWEEP_EXCLUDED`; `expectNoSeriousViolations` waits for animations (decision 125); Lighthouse a11y 100 on all 11 measured pages | Not yet run on firefox/webkit/mobile-safari on the gate commit. |
| Five languages | met | identical key counts per module in all five catalogs (e.g. regions 311, stores 304, history 372, core 429); `check-i18n.mjs` in `mvn verify`; `e2e/tests/shell/i18n-smoke.spec.ts` (`expectNoMissingKeys` per role/language); `c1-pages.spec.ts` five-language loop | Machine translations (decision 3). §18.3 also asks for "no overflowing text" in the language smoke, but the smoke checks missing keys only. Overflow is checked in `d11-journey`/`d4-shell-navigation` (German) and in the design-round audit scripts, not per language. |
| Three design rounds | met (AI stand-in) | `docs/design-review-m7.md`: round 1 with 22 findings (decision 121), round 2 (decision 124), round 3 scripted personas `e2e/tests/personas/*.spec.ts` with step/click/field budgets (`_persona.ts` `finish(budget)`, decision 127) | Round 3 "real users" replaced per decision 3; re-baselines after the rounds pending (same Docker gap). "Verify only after identity check" is an open product question. |
| M7 content: parity sign-off, security, load, a11y, privacy, translations, Zürich data, deploy/monitoring/backups/runbook, usability | met in code | D16.1–D16.3 (`RegionsCsvImportZuerichIT`, `StoresCsvImportZuerichIT`, `PastImportZuerichIT`, `scripts/import-csv.mjs`), D17.1 (`docs/load-test.md` 2026-10-29: 59 703 requests, p95 pass on every endpoint, 99.99 % checks), D17.2 (`docs/security-review.md`, `RateLimitInventoryTest`, `IdorSpotCheckIT`, `csp.spec.ts`), D17.3 (`DataInventoryTest`, `RetentionTableTest`, `AccountDeletionIT`), D19.1–D19.3 (`ProdSecretsInventoryTest`, `StructuredLoggingTest`, `MetricsContractTest`, `BackupRestoreIT`, runbook §1–§9) | The load test is from `5ba5be3` on a Mac, before the Q5–Q14 frontend and the last backend batches; it needs a refresh. The Zürich region/store files are models pending ambassador confirmation. Human items are listed in the launch report. |
| plan.md checkboxes | 22/24 ticked | D5.4 and D11.5.5 unticked with gate notes (plan-m7 Part D 8 asks for D5.4 and D11.5.5 "with notes"; the notes are there, but the boxes are left open) | Decide whether to tick with the note (the plan says tick all) or leave open. The ledger says D11.5.5 is done while plan.md shows it open; make them agree. |

## 3. Risks, ranked

| # | Risk | Severity | Likelihood | Mitigation |
|---|---|---|---|---|
| 1 | **Screenshot baselines absent for 29/37 specs; the existing 138 are stale.** The first Docker run will report ~490 missing/changed snapshots, and a careless `--update-snapshots` accepts regressions unseen. CI `release` is blocked behind `screenshots`. | high | certain | One deliberate `screenshots.sh --update-snapshots` run in Docker, reviewed as a contact sheet against `docs/design-review-m7.md` (the rounds list what must have changed: 44 px targets, "More" menu, "Route" button, compact headers); a second plain run with 0 diffs; commit as its own PR. |
| 2 | **13 Testcontainers ITs never run in this build phase** (`LiquibasePostgresIT`, `IndexUsagePostgresIT`, `LoadPathsPostgresIT`, `MessageSeqPostgresIT`, `BackupRestoreIT`, `RegionsCsvImportZuerichIT`, `StoresCsvImportZuerichIT`, `PastImportZuerichIT`, `GoogleLoginIT`, `MagicLinkFlowIT`, `PasskeyRepositoriesIT`, `ShedLockIT`, `WebAuthnEndpointsIT`). Changelogs 175–177 (oauth, calendar, inactivity-warning) and the M6/M7 index work were only exercised on H2 `MODE=PostgreSQL`. | high | medium (Postgres-only syntax/ordering differences are the classic failure; `ActivityFeedService` already comments on byte-order differences) | Run `./mvnw clean verify` on a Docker machine, or let CI's `build` job do it, before anything else; fix forward with tests. |
| 3 | **CI never executed on a remote.** `ci.yml` (contract, dependency-review, shellcheck, npm-audit, build, e2e 5×4 shards, lighthouse, coverage, screenshots, er-diagram, release), `security.yml` and `load.yml` are unproven. Typical first-run failures: runner memory for 4 shards × jar, `E2E_SHARDS`, artifact names, `NVD_API_KEY`. | high | high | Push, enable Actions, iterate on the first run; keep D5.4/D18.1 in progress until green. Budget half a day. |
| 4 | **`/onboarding` Lighthouse performance** (D11.5.3 hard threshold 90): idle measurements 93–94 (decision 84), loaded 69–82 (decisions 122/123, `docs/lighthouse.md`). The margin is thin and CPU-bound (bootstrap ~465 ms, Optimus runtime theme 90–140 ms). | medium | medium on CI runners (2 vCPU) | Run the `lighthouse` CI job first. If it is below 90 on an idle runner, use the known levers in decision 123 (theme at idle, defer the page-header subtitle work). Do not lower the threshold. |
| 5 | **Client copies of server rules** (parallel workers). Decisions 92 and 104 are now **server flags** (`RegionDetail.mayCoordinateStores`, `mayHandleReports` in `openapi/schemas/regions.yaml:213,216`; `stores.util.ts:52-59` and `reports.util.ts:142-149` read them, the copies are removed). Still **copies**: decision 99 basket location/contact validation (`baskets.util.ts:88`, mirrors `BasketRules`), decision 115 resource unit types (`resources.util.ts:7` `RESOURCE_UNIT_TYPES` vs `ResourceService` 403 `RESOURCES_NOT_AVAILABLE`), decision 116 OAuth redirect-URI/client-id validation (`oauth.util.ts:30`, mirrors `OAuthClientService.validRedirectUri`). All three are pre-validation only: the server still answers 400/403 and the copies are unit-tested, but they can drift silently. | low | medium over time | Backend: expose `ResourcePermissions.available`/a region flag and the basket/OAuth validation messages in the contract, or add a contract test that pins the copied lists (e.g. compare `RESOURCE_UNIT_TYPES` with an `x-resource-unit-types` enum). |
| 6 | **The test-budget process (decisions 106–108) leaves gaps.** Workers only ran the tests they touched, and the test worker's last recorded full runs are on `f960077`/`1a810ce`, i.e. before F3–F6 and the design rounds (18 ledger notes say "verified by the test worker"). Rounds 2–3 changed global CSS (44 px targets, header measurement, "More" menu), which affects every spec on every project, but only chromium/mobile-chrome subsets were run (design review "Verification" sections). The 4-fork test split (decision 108) changed resource isolation, and identical results were measured only once. | medium | medium | The gate's full `./mvnw clean verify` plus Playwright on all five projects on the gate commit is mandatory, not optional; record the counts in the launch report. |
| 7 | **Load test stale** (`5ba5be3`, Mac M4, before the M6 frontend and later backend batches; `load.yml` never ran). | medium | low | `e2e/load/run.sh` on Docker, then on production hardware (launch report). |
| 8 | **Activity feed and similar paging.** One e2e was temporarily weakened (`095591f`, A19 "load more asserts growth"); then the server got stable tie-breaking (`6d1771f`) and the exact `toHaveCount(before + 10)` returned (`a19-dashboard.spec.ts:48`). A good outcome, but it shows the pattern "weaken now, fix later" happened under the 5-minute budget. | low | — | Keep the rule: a weakened assertion needs a ledger/decision note with the fix commit; grep new specs for `expect.poll(...toBeGreaterThan` at review time. |
| 9 | **Stale documents**: `docs/coverage.md` (149/150 FAILED), launch report "C5 in progress" (the ledger says done), plan.md vs ledger on D11.5.5, and `docs/resume.md` still lists in-flight workers that have merged. | low | certain | Regenerate/align at Part D step 10. |
| 10 | In-memory rate limiters and a single instance (security review accepted gap, decision 81); retention periods and legal texts are seed texts. | low | — | Documented; revisit before a second instance; the association confirms the texts. |

## 4. Spot checks performed

1. **History on mutations, `StoresCsvImportService.importCsv`** (`@Transactional`, line 112): records `StoreLogAction.STORES_IMPORTED` (line 132) and `STORE_CREATED` per row (344). Pass.
2. **`BasketService`**: all 10 `@Transactional` methods record history (`CREATED`, `UPDATED`, `DELETED`, `REQUESTED`, `REQUEST_WITHDRAWN`, `REQUEST_STATUS_CHANGED`, `EXPIRED`, account-deletion scrub with reason). Pass.
3. **`ReportService`**: `create`, `update`, `delete`, `post`, `deletePost` and `sendDueReminders` all record `ReportAction.*`; read-only methods are correctly `readOnly = true`. Pass. Contract: 313 `x-history` annotations for 312 mutating operations in `openapi/paths/*.yaml`.
4. **`@NoHistory` usage**: 87 annotations in 42 files, each with a reason naming the recording delegate (e.g. "syncMembers records MEMBERS_SYNCED", "bells are transient hints"). No empty reasons. Pass.
5. **Clock rule**: zero `*.now()` / `System.currentTimeMillis` in `server/src/main/java`; ArchUnit `rule5OnlyClockConfigReadsTheSystemClock`. Pass.
6. **DataState / testids on three pages**:
   - `blog-list-page.component.ts`: 2 `fs-data-state`, 2 `loadResource`, 6 testids.
   - `oauth-clients-page.component.ts`: 4 data states, 3 loaders, 6 `runMutation`, 34 testids.
   - `region-achievements.tab.ts` delegates to `achievements-board.component.ts`, which uses `fs-data-state`.

   Raw `HttpClient` appears only in `app.config.ts` (provider), and `check-data-state.mjs` enforces loaders inside `loadResource`/`runMutation`. Pass.
7. **i18n**: key counts identical across the five languages in all 44 catalogs (see §2). Pass.
8. **Weakened e2e assertions since 2026-10-29**: 13 removed `expect(` lines vs 1 304 added across `e2e/tests`. Each removal inspected:
   - FSP marker count +1 (new seed FSP).
   - Basket status REQUESTED → READ: the owner view marks requests read, a behaviour change; the assertion is still exact.
   - Intro-pickup form values changed to the new seed times; still exact.
   - A19 temporarily relaxed and restored (see risk 8).

   No `test.skip`/`fixme`/`.only`; one `test.slow()` (`d7-dev-switcher`). Pass.
9. **Persona budgets**: `newcomer.spec.ts` `walk.finish({ steps: 8, clicks: 56, fields: 5 })`, with the round-3 counts 52/51 documented; `_persona.ts` asserts `toBeLessThanOrEqual(budget.steps)`. The budgets are real assertions, not just annotations. Pass.
10. **Axe route coverage**: `e2e/scripts/a11y-route-coverage.mjs` bundles `app.routes.ts` with esbuild, walks the lazy `*.routes` files and fails on an unlisted route; it is wired into CI `build`. `routes.ts` has 190 route cases + `SWEEP_EXCLUDED`, and `pages.spec.ts` loops light and dark. Not executed by me (it writes a temp bundle). Design: pass.
11. **Strict coverage gate**: `ci.yml:255-257` runs `feature-coverage.mjs --results … --strict` unconditionally (decision 126); the dispatch input is removed. Pass — but `docs/coverage.md` is stale (see risk 9).
12. **Screenshot baselines**: counted with `find e2e/__screenshots__`: 138 per project, covering 8 specs, against 301 `expectScreen` + 13 `expectElement` calls over 37 specs. Fail (known, D18.2 todo).
13. **Seed data per new entity**: changelogs `175-oauth`, `176-calendar`, `177-inactivity-warning` ↔ seeds `175-oauth-seed`, `176-calendar-seed`, `178-notification-outbox-seed`; `docs/seed-ids.md` present. Pass (177 has no seed by nature: it adds a warning column).
14. **Rule unit tests exist**: `BasketRulesTest`, `OAuthRulesTest`, `StoresCsvImportServiceTest`, `ReportHandlerRuleTest`, `HistoryQueryServiceTest`, `RateLimitInventoryTest`, `MetricsContractTest`, `DataInventoryTest`, `RetentionTableTest`, `ProdSecretsInventoryTest`, `StructuredLoggingTest`, `IdorSpotCheckIT` — all found (201 `*Test`, 64 `*IT`, 90 client specs). No `ResourceRulesTest` exists under that name: the resource limit rule's test either lives elsewhere or is missing, so check.
15. **Test forks**: `server/pom.xml` `test.forks=4`, `reuseForks=true` for surefire and failsafe (decision 108, A60). Pass.
16. **Lighthouse project isolation**: the `playwright.config.ts` `lighthouse` project uses `grep: LIGHTHOUSE` and is excluded from the five device projects; `d11-lighthouse.spec.ts` takes the median of 3 against `THRESHOLDS = {90, 95}`. Pass.

## 5. Recommendations before launch (ordered)

1. **ops** — Run the full gate on a Docker/CI machine: `./mvnw clean verify` (all 13 Postgres ITs), Playwright on all five projects + `lighthouse`, `feature-coverage --strict`, `er-diagram.sh --check`, `release.sh --check`, `smoke.sh`. Fill the launch report table with real numbers and the gate commit.
2. **frontend + human** — Generate the screenshot baselines once in Docker (`screenshots.sh --update-snapshots`), review the ~630 images against the design-review change list, and commit; a second plain run must show 0 diffs. Only then can the CI `release` job pass. Set D18.2 done.
3. **ops** — Push to the remote, enable Actions, add `NVD_API_KEY`, get `ci.yml`/`security.yml`/`load.yml` green once; close D5.4 and D18.1.
4. **frontend** — Measure `/onboarding` on the idle CI runner; if it is below 90, apply decision 123's levers. Keep the threshold.
5. **backend** — Replace the remaining client rule copies (decisions 99, 115, 116) with contract data, or pin them with a contract test; add the `ResourceRules` unit test if it does not exist under another name.
6. **backend/ops** — Refresh the k6 load test on the gate commit (`docs/load-test.md`), then on production hardware.
7. **frontend** — Extend `i18n-smoke.spec.ts` with the horizontal-overflow audit used in the design rounds (§18.3 "no overflowing text"), per language.
8. **human** — Launch-report human items, in this order:
   1. domain, hosting and secrets (blocks everything)
   2. Zürich ambassadors confirm `regions.csv`/`stores.csv`
   3. staging deploy
   4. usability test with 5 newcomers (D11.5.5)
   5. native-speaker review of gws/fr/it/en
   6. legal texts and retention confirmation
   7. passport layout approval
   8. product decision on "verify only after identity check"
9. **integrator** — Align the documents: regenerate `docs/coverage.md`, fix the launch report's "C5 in progress" and the persona round-3 TBD, reconcile the plan.md D11.5.5/D5.4 boxes with the ledger, close AD1 after the test worker's run, and prune the `docs/resume.md` in-flight list.
10. **ops** — Before a second app instance: move the rate limiters out of memory (security review accepted gap).
