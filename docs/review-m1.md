# M1 "Foundations" — planner review (Fable), condensed action list

Verdict: foundation solid (contract-first, DataState, history, 5 languages, ArchUnit verified). §20 definition of done **not yet met**.

## 1. Gaps (blocking DoD)
1. Screenshot baselines missing for `e2e/screenshots/auth.screen.ts` and `history.screen.ts` → generate in Docker, review, commit; CI must fail on missing baselines (`updateSnapshots: 'none'` when `CI`).
2. Design rounds 0/3 logged → do rounds below, re-baseline after each, log `M1 3/3` in ledger.
3. Ledger rows AU12, AD1, EA4, N6, C12 → `in-progress` with notes (done in commit after this review).
4. D15.1: run `npm run er-diagram`, commit `docs/er/` (PNG/SVG + summary).
5. D5.4: CI never ran (no remote) → stays in-progress until one green CI run.
6. `docs/launch-report.md` must exist with human-only items (decision #3).

## 2. Partial / unproven
- §10.2: security log must use `fs-history-timeline` (support page-only source without filters); remove feature-local list and `ACTION_STYLE` map.
- D10.3 notes: list owning modules of the other 10 views.
- PWA: e2e test that `/ngsw.json` and `/manifest.webmanifest` are served from the jar.
- Error-state injection missing for `/account/profile`, `/dashboard`, login page when `/api/public/app-info` fails.
- Login/magic-link pages: `expectNoMissingKeys` in all 5 languages.
- Gallery: hard-coded `Daily/Weekly/Never` → `dev.gallery.freq.*`; history demo data has a German reason in fr and a synthetic "future action" in the filled specimen.
- Tags: `d4-m2-fixtures.spec.ts` should not use `@D4.3` (use `@infra` meta tag); gallery screenshots need a tag (`@gallery` meta tag in coverage script).
- i18n: gender-inclusive role names consistent in fr/it (`common.role.FOODSAVER`).

## 3. Design round 1 — flow
- Dashboard "next step" card actionable per role (newcomer → add passkey; foodsaver → complete profile; orga → review activity); drop "Bald verfügbar".
- Forbidden page for anonymous users → "log in to see this page" + login button with returnUrl.
- 404/403 pages share one button set (primary home + secondary back).
- Move dev "Galerie" nav item out of main nav (user menu / footer, devMode only).

## 4. Design round 2 — visual polish (ranked)
1. Mobile tab bar only has 2 tabs → add activity + settings tabs for M1.
2. One nav label key per destination across header, tab bar and dashboard links.
3. Dashboard desktop top-heavy / short pages float → grid or pinned footer (`min-height` main).
4. Screenshot mask renders magenta → use surface-coloured mask or stable text swap.
5. Forbidden error-state layout (icon + askWho flex row), shorter fr copy.
6. Google button identical on landing and login (multi-colour G on neutral).
7. Dark-mode loading button looks disabled → loading keeps primary colour.
8. Timeline metadata chips wrap (2-line clamp) instead of ellipsis.
9. Role badge on dashboard hero: primary-soft background tokens.
10. Empty-state SVG illustrations (`public/images/empty/*.svg`) via `fs-empty-state [illustration]`.
11. Offline banner shorter copy on mobile.

## 5. Design round 3 — persona walkthroughs
- Newcomer: role hint under role chip; landing example card not clickable-looking (or real link); screenshot of passkey nudge; user menu groups account pages under one "Konto" entry.
- Foodsaver: "log out everywhere" as bottom secondary action on sessions page; "only logins" preset on `/me/activity`, security log links to it; theme hint "applies to all your devices".
- Ambassador/orga: dashboard shows home region + muted admin card for ambassadors; CSV export as icon button in page header; forbidden `askWho` parametrised by role.

Order: (1) baselines + docs → (2) round 1 + round 2 items 1–5, re-baseline → (3) timeline unification + round 2 items 6–11, re-baseline → (4) round 3, re-baseline, log 3/3.
