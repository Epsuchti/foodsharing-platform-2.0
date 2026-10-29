# Resume notes (paused 2026-10-29)

Read first: `plan.md`, `docs/ledger.md`, `docs/decisions.md` (1–92), `docs/conventions.md`, `docs/plan-m1.md` … `docs/plan-m7.md`, `AGENTS.md`.

## State on `main`
- Backend: all features of M1–M6 and M7 B1–B10 (imports, prod profile/image/release, runbook/backups, monitoring, load test, security review, privacy review, CI hardening) are done.
- Frontend: M1–M5 done (incl. surveys, nav badges, rich mail editor), M4 automation done. Lighthouse project green (`/` 100, `/onboarding` ≥ 90).
- Last verified on main: full `./mvnw clean verify` green; full chromium e2e 407/408 before the latest frontend merges (the one failure was Lighthouse under machine load, since moved to its own `lighthouse` project).
- Coverage: `node e2e/scripts/feature-coverage.mjs` green (non-strict).

## Blocker (2026-10-29, resumed session on Windows)
- The machine only has JDK 17 (`java -version` → 17.0.12, no `JAVA_HOME`); the build needs JDK 21 (`release version 21 not supported`). Step 0 (`./mvnw clean verify`, Playwright against the jar) cannot run until JDK 21 is installed and `JAVA_HOME` points at it. Installing a JDK from the agent was refused by the permission system.
- e2e deps are installed (`e2e/node_modules`, Playwright chromium). In flight: frontend worker on M7 F1 (branch `build/m7-f1-stores-import`, Java-free checks only), backend worker analysing N7/EA16/AU5/AU11/AU13/C15/AD1 (branch `build/m7-bx-open-rows`, no unverified server commits), Fable planner splitting M6 Queue F into worker-sized tasks (to be saved as `docs/plan-m7-queue-f.md`).

## Team setup the owner asked for
One frontend worker + one backend worker (+ Fable planner for plans/reviews). Workers run in their own worktree, never write to the main checkout; the integrator merges (check `git diff --name-only --diff-filter=U` before committing a merge; union-merge i18n JSON conflicts). Screenshots: write `*.screen.ts` specs only; baselines + design rounds in one batch at the end (decision 69).

## Remaining queue, in order
1. Frontend: M7 F1 stores CSV import page + `e2e/tests/stores/s2-import.spec.ts @S2 @D16.2` (backend ready).
2. Frontend: M6 Queue F (`docs/plan-m6.md`): profiles/bananas/buddies/sleep; account (email change, photo crop, calendar feed, business card, data export, delete account, notes, bounce UI); baskets; food share points; map with all layers; activity feed/dashboard; reports & mediation; achievements; admin (feature toggles, configuration, blocklists, uploads); content/blog/release notes/team/petition/donations/support; statistics with animated counters; global search; resources; OAuth consent + client admin. Small leftover: mailbox pages use `sr-only` instead of `fs-visually-hidden`.
3. Backend: answer frontend requests from step 2; M6 B14/B15, M5 B10, M4 B9, M3 B8 close tasks are covered by M7 B10 except ledger rows the frontend closes.
4. End batch (`docs/plan-m7.md` Part C F2–F6): all screenshot baselines in Docker (two stable runs), design rounds 1–3 with AI personas, final axe sweep over every route, 5-language smoke, Lighthouse report.
5. Integrator final gate (`docs/plan-m7.md` Part D): full verify, Playwright on all 5 projects, screenshots, `feature-coverage.mjs --strict` (then enable strict in CI), release check, smoke, load test refresh, every ledger row done, tick plan.md checkboxes, Fable final review `docs/review-m7.md`, finalise `docs/launch-report.md`.

Helper: `scripts/ledger.py <ID> <status> [backend] [frontend] [e2e] [screenshot] [notes]` ('-' keeps a column; `--add` adds rows).
