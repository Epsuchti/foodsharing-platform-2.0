# Implementation prompt: Foodsharing Platform 2.0

Start a Claude Code session in `/Users/eric/develop/foodsharing-platform-2.0` with **Opus 5.5** as the main
model, in auto mode or with bypass permissions, and paste everything below the line.

---

You are the lead engineer building Foodsharing Platform 2.0 from scratch in this repository. `plan.md` in
the repo root is the single source of truth. Read it completely before doing anything. The reference app
is at `/Users/eric/develop/foodsharing-einab-tabelle`. Copy its setup, conventions and features from there
(build, OpenAPI generation, Liquibase rules, AGENTS.md rules, i18n, PrimeNG/Optimus usage, services) rather
than reinventing them.

## Mission

Implement **every** feature in plan.md §13 and §14 and every deliverable checkbox in the plan, to the
plan's definition of done (§20). Nothing is optional, deferred or "out of scope". You will not get any
answers from me during this session. Never stop to ask a question. When something is ambiguous, pick the
option closest to foodsharing.network's behaviour (public repo: https://gitlab.com/foodsharing-dev/foodsharing,
clone it for reference), write the decision into `docs/decisions.md`, and keep going.

## Roles

- **Planner: use the Agent tool with `model: "fable"`** (subagent type `Plan`) for planning. Before each
  milestone in §20, give the planner plan.md, `docs/ledger.md` and the current code state. Ask it for
  an ordered task list for that milestone. Each task names the feature IDs it covers, the OpenAPI changes,
  the Liquibase changes, the backend classes, the frontend routes/components, the seed data, and the
  Playwright e2e and screenshot tests. Also use the planner to review each finished milestone against
  plan.md and list every gap.
- **Implementer: you (Opus 5.5).** You may run implementation subagents (Opus) in parallel for independent
  modules. Each one works in its own git worktree (`isolation: "worktree"`) and merges back when green.
  Give every subagent the relevant plan.md sections, AGENTS.md and the conventions below.

## Ledger: the session must be resumable

Create `docs/ledger.md` first. It has one row per feature ID from §13/§14, plus every deliverable checkbox
in plan.md. Columns: ID, feature, status (`todo / in-progress / done`), backend, frontend, e2e test name,
screenshot test, notes. Update it after every task and commit it. If the session or context restarts, read
plan.md, `docs/ledger.md` and `docs/decisions.md` first, then continue with the first item that isn't done.
A feature is `done` only when its tagged Playwright test passes.

## Order of work

Follow the milestones in plan.md §20 in order (1 → 7). Within a milestone:
1. Ask the planner (fable) for the task list.
2. For each task, change the OpenAPI spec first, regenerate, then do Liquibase + entities + services +
   controllers implementing the generated interfaces, then the Angular feature using only the generated
   client and `DataState`, then i18n in all 5 languages (de, en, gws, fr, it), then seed data, then the
   Playwright e2e and screenshot tests.
3. Run the full verification (below). Fix everything until it is green.
4. Do at least three design rework rounds (§18.5). Generate screenshots of every page and state, look at
   them yourself (read the PNGs) against the review checklist, improve the design, and re-run. Update the
   screenshot baselines after each round.
5. Ask the planner (fable) to review the milestone against plan.md, fix every gap it finds, update the
   ledger, and commit with a clear message.

## Non-negotiable conventions

- Controllers implement generated `*Api` interfaces, with no own mapping annotations (ArchUnit test).
- No raw `HttpClient` in Angular outside `core/`/`api/` (ESLint rule). Generated code is never committed.
- Liquibase changes must work on H2 (`MODE=PostgreSQL`) and Postgres. Use `VARCHAR(32600)` instead of
  TEXT/CLOB. Never edit an applied changelog.
- Every data-loading UI element goes through `DataState`, with skeleton, empty, error + retry, forbidden,
  not-found and offline states.
- Every mutating service method records an action-history entry (§10).
- Every new text is added in all five languages, and `i18n:check` must pass.
- The `local` profile runs fully offline with H2, seed data, Mailpit, a fake OIDC provider, and
  log-only Telegram/push.
- Tests are never skipped, disabled or weakened to get green. Fix the code.

## Verification before marking anything done

- `./mvnw clean verify` (backend tests incl. Testcontainers Postgres, client build, lint, i18n check).
- Playwright e2e against the packaged jar on all projects (desktop Chrome/Firefox/WebKit, mobile),
  including error injection and axe checks.
- Playwright screenshot tests (run inside the Playwright Docker image if Docker is available).
- Feature-coverage script: every ID in §13/§14 has a passing tagged e2e test.

## Git

Commit often on `main`, or on a `build/<milestone>` branch merged into `main` when green, with
descriptive messages. Commit the ledger with every task. Do not push unless the repo has a remote
configured and pushing is clearly expected. When the work is complete, leave everything committed locally.

## Finish

You are done only when all of these are true: every ledger row is `done`, every plan.md checkbox is
ticked, the full verification is green, and the final planner review reports no gaps. Then write
`docs/launch-report.md` with what was built, how to run it, the test results, the decisions taken, and
anything that still needs a human: domain, Google OAuth credentials, Telegram bot token, mail provider
and hosting.
