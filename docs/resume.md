# Resume notes (paused 2026-10-29)

Read first: `plan.md`, `docs/ledger.md`, `docs/decisions.md` (1–106), `docs/conventions.md`, `docs/plan-m1.md` … `docs/plan-m7.md`, `AGENTS.md`.

## State on `main`
- Backend: all features of M1–M6 and M7 B1–B10 (imports, prod profile/image/release, runbook/backups, monitoring, load test, security review, privacy review, CI hardening) are done.
- Frontend: M1–M5 done (incl. surveys, nav badges, rich mail editor), M4 automation done. Lighthouse project green (`/` 100, `/onboarding` ≥ 90).
- Last verified on main: full `./mvnw clean verify` green; full chromium e2e 407/408 before the latest frontend merges (the one failure was Lighthouse under machine load, since moved to its own `lighthouse` project).
- Coverage: `node e2e/scripts/feature-coverage.mjs` green (non-strict).

## Windows environment (2026-10-29, resumed session)
- JDK 21: portable Temurin in `~/.jdks/jdk-21.0.13+9` (owner-approved download). Prefix Java/Maven/Playwright commands with `export JAVA_HOME="$HOME/.jdks/jdk-21.0.13+9"; export PATH="$JAVA_HOME/bin:$PATH"`. The system JDK 17 is untouched.
- Node: the system Node 24.14.1 is too old for Angular CLI (≥ 24.15.0); use the Maven-installed `client/node` (`export PATH="$PWD/client/node:$PATH"`). No Python on the machine.
- **Blocker: no Docker.** The 13 Testcontainers ITs (Postgres) fail with "Failed to load ApplicationContext"; everything else in `./mvnw clean verify` is green (unit, client 296/296, 210 other ITs). Docker is also needed for the screenshot baselines (decision 69), the k6 load test and `smoke.sh`.
- Step 0 fixes on main: first test of `automation.spec.ts` timed out under load (lazy chunks now warmed in `beforeAll`); `feature-coverage.mjs` spawns Playwright through node (Windows EINVAL on `.cmd`); `fixtures/pdf.ts` font path with '/' (Windows). Coverage non-strict green, 112/150 features.
- Step 0 Playwright chromium (before the merges): 435/441; the 6 failures were Windows path bugs (pdf fixture: fixed; `scripts/import-csv.mjs` main check: with the backend worker as F1-R2).
- Lighthouse: the full Playwright `chrome.exe` cannot be spawned on this machine (spawn UNKNOWN; the headless shell works), so run the `lighthouse` project with `CHROME_PATH=<ms-playwright>/chromium_headless_shell-1248/chrome-headless-shell-win64/chrome-headless-shell.exe`. Under parallel worker builds `/` scored perf 95 / a11y 100, `/onboarding` perf 77 (runs 77, 82, 68) / a11y 100 → re-measure on an idle machine in F6 before touching anything; thresholds unchanged.
- Ledger: no Python, so `scripts/ledger.py` cannot run; rows are edited with a small node helper (same columns).
- Merged on main: F1 stores import page, Q1 scaffolding, backend open rows (N7, EA16, AU5, AU13, C15 done; AU11 partial; AD1 admins API).
- In flight: frontend worker on Q2 profiles (`build/m7-q2-profiles`); backend worker on `build/m7-b-gaps` (G1–G4, F1-R1 `RegionDetail.mayCoordinateStores`, F1-R2 import-csv.mjs on Windows, AU11 store-team request messages).
- Frontend queue after Q2: Q3…Q14 (`docs/plan-m7-queue-f.md`); Q13 also builds `/admin/admins` (`listAdmins`) and an @AD1 permission-matrix UI test; F1 page switches to `RegionDetail.mayCoordinateStores` once merged.
- Next backend task after the current batch (fresh worker): test-suite speed-up per decision 100 (parallel forks, fewer Spring contexts, shared ArchUnit/Modulith class import); same test count and results before/after.

## Team setup the owner asked for
One frontend worker + one backend worker (+ Fable planner for plans/reviews). Workers run in their own worktree, never write to the main checkout; the integrator merges (check `git diff --name-only --diff-filter=U` before committing a merge; union-merge i18n JSON conflicts). Screenshots: write `*.screen.ts` specs only; baselines + design rounds in one batch at the end (decision 69).

## Remaining queue, in order
1. Frontend: M7 F1 stores CSV import page + `e2e/tests/stores/s2-import.spec.ts @S2 @D16.2` (backend ready).
2. Frontend: M6 Queue F (`docs/plan-m6.md`): profiles/bananas/buddies/sleep; account (email change, photo crop, calendar feed, business card, data export, delete account, notes, bounce UI); baskets; food share points; map with all layers; activity feed/dashboard; reports & mediation; achievements; admin (feature toggles, configuration, blocklists, uploads); content/blog/release notes/team/petition/donations/support; statistics with animated counters; global search; resources; OAuth consent + client admin. Small leftover: mailbox pages use `sr-only` instead of `fs-visually-hidden`.
3. Backend: answer frontend requests from step 2; M6 B14/B15, M5 B10, M4 B9, M3 B8 close tasks are covered by M7 B10 except ledger rows the frontend closes.
4. End batch (`docs/plan-m7.md` Part C F2–F6): all screenshot baselines in Docker (two stable runs), design rounds 1–3 with AI personas, final axe sweep over every route, 5-language smoke, Lighthouse report.
5. Integrator final gate (`docs/plan-m7.md` Part D): full verify, Playwright on all 5 projects, screenshots, `feature-coverage.mjs --strict` (then enable strict in CI), release check, smoke, load test refresh, every ledger row done, tick plan.md checkboxes, Fable final review `docs/review-m7.md`, finalise `docs/launch-report.md`.

Helper: `scripts/ledger.py <ID> <status> [backend] [frontend] [e2e] [screenshot] [notes]` ('-' keeps a column; `--add` adds rows).
