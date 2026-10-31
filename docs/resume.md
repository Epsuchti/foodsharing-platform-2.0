# Resume notes (paused 2026-10-29)

Read first: `plan.md`, `docs/ledger.md`, `docs/decisions.md` (1–131), `docs/conventions.md`, `docs/plan-m1.md` … `docs/plan-m7.md`, `AGENTS.md`.

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
- Merged on main (2026-10-30): F1 stores import; Q1–Q10 and Q13 of `docs/plan-m7-queue-f.md`; backend open rows, contract gaps G1–G4, follow-ups (decisions 97, 102, 108); fast seed reset (107); test speed-up, 4 forks (108); screenshot matrix light/German only (109).
- Ledger rows still waiting for the test worker to see their tagged tests pass on main (decision 106): A19, AC1, C1, C3, C4, C8, C16, C10, C11, C12, C14, AD1. D10.3 needs an audit of its remaining views.
- Workers in flight: Q11 blog/donations/support (`build/m7-q11-blog`), Q12 search/resources (`build/m7-q12-search`), Q14 OAuth (`build/m7-q14-oauth`), backend product fixes (`build/m7-b-fixes`: release-note translations, PETITION_INACTIVE, achievement base text + clears, ConfigurationEntry.type, updateConfiguration audit fields), Sonnet test worker running full checks on main in its own worktree.
- Open optional backend requests (no blocker): OAuth `ConsentRequest.requiredRegions` and `OAuthClient.requiredRegions` as RegionRef (names); PATCH public→confidential should issue a secret or document the 400; resource limit in `ResourcePermissions` and file names on `ResourceImage`; blog `removePicture` and content page image removal (backend worker 2 in progress).
- Performance: `/onboarding` Lighthouse (hard threshold 90) is ~80 under load. Branch `build/m7-perf-2` (build-time theme, blocking first navigation) is parked until it can be measured on an idle machine/CI runner (decision 131); also the `fsScrollHint` forced layout follow-up.
- After Q14: plan-m6 F9 leftovers (a11y sweep completeness, translated-pages sweep), then the end batch (plan-m7 Part C F2–F6) — needs Docker for the baselines — and the final gate (Part D).

## Team setup the owner asked for
One frontend worker + one backend worker (+ Fable planner for plans/reviews). Workers run in their own worktree, never write to the main checkout; the integrator merges (check `git diff --name-only --diff-filter=U` before committing a merge; union-merge i18n JSON conflicts). Screenshots: write `*.screen.ts` specs only; baselines + design rounds in one batch at the end (decision 69).

## Remaining queue, in order
1. Frontend: M7 F1 stores CSV import page + `e2e/tests/stores/s2-import.spec.ts @S2 @D16.2` (backend ready).
2. Frontend: M6 Queue F (`docs/plan-m6.md`): profiles/bananas/buddies/sleep; account (email change, photo crop, calendar feed, business card, data export, delete account, notes, bounce UI); baskets; food share points; map with all layers; activity feed/dashboard; reports & mediation; achievements; admin (feature toggles, configuration, blocklists, uploads); content/blog/release notes/team/petition/donations/support; statistics with animated counters; global search; resources; OAuth consent + client admin. Small leftover: mailbox pages use `sr-only` instead of `fs-visually-hidden`.
3. Backend: answer frontend requests from step 2; M6 B14/B15, M5 B10, M4 B9, M3 B8 close tasks are covered by M7 B10 except ledger rows the frontend closes.
4. End batch (`docs/plan-m7.md` Part C F2–F6): all screenshot baselines in Docker (two stable runs), design rounds 1–3 with AI personas, final axe sweep over every route, 5-language smoke, Lighthouse report.
5. Integrator final gate (`docs/plan-m7.md` Part D): full verify, Playwright on all 5 projects, screenshots, `feature-coverage.mjs --strict` (then enable strict in CI), release check, smoke, load test refresh, every ledger row done, tick plan.md checkboxes, Fable final review `docs/review-m7.md`, finalise `docs/launch-report.md`.

Helper: `scripts/ledger.py <ID> <status> [backend] [frontend] [e2e] [screenshot] [notes]` ('-' keeps a column; `--add` adds rows).

## After the base build (owner, 2026-10-30)
1. Parallel shards for the local e2e and screenshot scripts: N jars with their own database and port, `--shard=i/N`, a separate output folder and server log per shard (about 3–4× faster).
2. Region automation metrics (`/regions/<id>/automation/metrics`): many metric labels are raw keys (`SLOT_APPROVAL.WOULD_CONFIRM`, `CANCELLATION.REPLACED`, `REQUEST.WOULD_DECLINE`, `SLOT_DECLINE_RULE.*`, `SLOT_SKIP_RULE.*`, `INTRO_PICKUP.DONE`, …). Give every metric key a label in all five languages and add a guard, so an unknown key fails the i18n check or a unit test instead of showing up raw.
3. Same page: the chart with ~30 series as spiky daily lines is hard to read; consider grouping or bars and fewer default series.
4. Native date inputs show `mm/dd/yyyy` in a German or Swiss-German UI, because they follow the browser locale. Check this against the app language.
5. Bot protection: a per-IP request limit for the whole API, not only the anonymous part.
   - What exists today:
     - `/api/public/**` is limited to 300/min per IP (`PublicApiRateLimitFilter`, decision 86).
     - The magic link has its own per-email and per-IP limit (`LoginRateLimiter`).
     - A few actions have per-user limits.
   - Missing: signed-in requests (`/api/**` outside public) have no per-IP or per-session ceiling.
   - Decide the limits: a general per-IP limit, plus a per-user limit for signed-in sessions so shared IPs (offices, mobile carriers) don't block each other.
   - Decide the client IP behind the reverse proxy (`X-Forwarded-For` only from the trusted proxy).
   - The 429 answer and the UI message.
   - Metrics and alerting.
   - Keep the e2e/test profiles at 0, and extend `RateLimitInventoryTest`.
   - Owner decisions (2026-10-30):
     - Both a per-IP and a per-user-account limit.
     - The real client IP from `X-Forwarded-For`, trusted only from our own reverse proxy.
     - Hitting the limit blocks that IP or account for a while, not just the requests of that minute. Every answer during the block is 429 with `Retry-After`.
     - The block grows with repeat offences: for example 1 min, then 5 min, then 30 min, reset after a quiet day.
     - Admins can see and lift active blocks; this reuses the existing IP blocklist admin page if possible.
     - Every block is recorded in the history.
     - The values are configurable.
6. Done (decision 138). Store automation: the pickup-policy and request-policy sections have the same save/run race that AU4 had. Their "test" and "run" buttons stay enabled while the form has unsaved changes. Apply the adverts pattern: disable them while the draft is dirty or a save is in flight, show the "Erst speichern" hint, and add a unit test.
7. Side menu search: remove the Ctrl/⌘ K shortcut completely (owner, decision 133 amended). That means the key handler, the "Ctrl K" hint in the search field, `aria-keyshortcuts`, and its line in the user menu's shortcuts block, plus the unit and e2e checks for it. The hint was also cutting off the placeholder "Personen, Betriebe, Grupp…"; check that the placeholder fits afterwards.
8. Seen on the same screenshot (event page `/events/<id>`):
   - The date range repeats the date in another format: "Samstag, 17. Oktober 2026 um 08:00 – 17.10.2026, 11:00". On the same day it should read "… 08:00 – 11:00".
   - There is a stray space before the comma: "Fairteiler Wiedikon , Schlossgasse 10".
   - In full-page screenshots the fixed side menu ends at the first viewport height. That's a capture artefact, like the modal backdrop. Decide whether the screenshot helper should capture with a viewport as tall as the page.
   - The "Verwalten" heading sits faded under the scroll fade at the bottom of the menu list. Check that the fade doesn't make a section heading look disabled.
9. Menu priorities (decision 135):
   - Rework `nav.config.ts` (sections, order, `mobileTab`) and the side menu and user menu: Meine Aktivität and Quiz move into the user menu.
   - Phone tabs become Übersicht/Mein Einstieg, Abholungen, Betriebe, Nachrichten, plus Menu.
   - Mein Einstieg is shown only while the journey is unfinished.
   - Postfächer only shows for people with a mailbox.
   - Update the D4/D4.3 specs, `openNav`, the personas' click budgets and the screenshot specs.
   - Baselines change deliberately.
10. Opening `/login` directly briefly shows the visitor side menu with an empty content area, then switches to the minimal login layout (seen in the local profile, 2026-10-30). The minimal-chrome decision should apply before the first render, so the layout doesn't flash.
11. Pickup slots: confirmed vs unconfirmed must be obvious. The design and mockup are in `docs/design/pickup-slots-*`; the owner decisions are in section 10 of the proposal. Implement it.
12. Broken toggle switches (owner, 2026-10-30): in a flex row next to a long description, `p-toggleswitch` shrinks (`flex-shrink: 1`, `min-width: auto`). On the store pickup policy page the six switches measured 40, 40, 26, 28, 20 and 33 px wide instead of 40, so only the knob was left. The app uses `p-toggleswitch` 51 times in 31 files.
   - Fix globally in `styles.scss` (`.p-toggleswitch { flex-shrink: 0; }`), and give checkboxes, radios, avatars and icon buttons the same treatment where they sit in flex rows.
   - Add a guard: an e2e helper in the a11y sweep, or a check in the i18n smoke, that fails when a switch, checkbox or radio is rendered narrower than its design size, across every route at desktop and phone width. That way the whole app gets checked, not just this page.
13. Fairteiler cleaning shifts and the cleaning rule (owner, 2026-10-30, decision 136). This replaces the "cleaning store" field.
   - The existing food share points (FS1, `/food-share-points`) are upgraded; there is no new store type. A Fairteiler can switch on cleaning shifts:
     - regular and one-time slots
     - sign-up with confirmation by the Fairteiler's managers
     - a team
     - history and statistics
     The labels say "Putzeinsatz" instead of "Abholung". Reuse the store pickup building blocks (slots, sign-up, confirmation, eligibility) where possible.
   - Region setting: each region picks which Fairteiler count for the cleaning rule (one or several). Only Fairteiler in that region itself can be picked; there is no inheritance, so sub-regions configure their own.
   - Cleaning rule (`PickupPolicyEvaluator.cleaningRule`):
     - Fulfilled by a confirmed cleaning shift in the back-check period (12 months) or an upcoming one, at any of the region's cleaning Fairteiler.
     - Free shifts for the reminder threshold are counted across all of them.
     - Exemptions, the new-foodsaver grace period and `NO_CLEANING_STORE` (renamed to "no cleaning Fairteiler") stay.
   - Data:
     - A new changelog replaces `region.cleaning_store_id` with a region↔Fairteiler link table.
     - Migrate existing values.
     - Update the seed: Zürich Stadt gets a real Fairteiler with cleaning shifts instead of S-13 "Migros City".
     - Update `docs/seed-ids.md`.
   - Contract first (OpenAPI), history on every mutation, i18n in five languages, and an e2e test plus screenshot specs with the feature ID. Update the automation policy page ("Putzen" shows the region's cleaning Fairteiler).
14. Store team (owner, 2026-10-31):
   - Sort by pickup count and by last pickup.
   - A filter (name, role; members/jumpers).
   - Page members and jumpers on the server instead of loading the whole team (contract first; stable sort with an id tiebreaker).
   - Applies to the team tab and to "Abholungen pro Mitglied" in the store statistics.
15. "Navigate" button for addresses (stores, Fairteiler, baskets, events): one button with a dropdown offering Google Maps and Apple Maps (deep links with the coordinates or address). It replaces the current "Route" button.
16. Edit and delete your own messages in every chat (direct, group, store, region, event, basket chats; check which walls count as chats). Every edit and delete is recorded in the history with the original text, so moderators can still see it. Others see "bearbeitet" / "gelöscht". Decide on a time limit and on moderator access with the owner.
17. The map doesn't load OpenStreetMap tiles (owner, local profile). First finding (2026-10-31): in the app's built-in browser pane every external request fails (OSM, Carto, Wikimedia; the tiles answer 504 via the service worker), while `curl` from the same machine gets the tile (200). So it is probably the browser pane's network, not the app. Confirm in a normal browser before changing code. Check the tile URL, the CSP `img-src`, the service worker and the attribution; e2e and screenshots stub the tile hosts, so no test noticed. Add a check that at least one real tile request succeeds outside the stubbed tests (smoke).
18. Done (decision 138, `StableOrderTest`). Unstable sorting: 27 repository methods order only by a timestamp (`…OrderByCreatedAtDesc`). Rows with the same time come back in random order, and with paging an entry can appear on two pages or on none. Uploads are fixed (`…CreatedAtDescIdDesc`, 2026-10-31); found because the uploads screenshot changed between runs. Add the id tiebreaker everywhere, and guard it with an ArchUnit or unit check: every paged or sorted repository query ends with a unique key.
19. Toasts on phones stick out past the left edge of the screen: at 412px, "…rden übernommen" is cut off (seen in the gallery toast screenshot, 2026-10-31). Check `toastBreakpoints` in `shell.component.ts` and the toast width/position so it stays inside a 16px gutter. Add a phone check to the overflow audit.
