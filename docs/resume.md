# Handover (2026-10-31)

Foodsharing Platform 2.0 is being handed over to a new developer. Start here, then read `plan.md` (the single source of truth), `AGENTS.md` (binding rules), `docs/conventions.md`, `docs/decisions.md` (1–138) and `docs/ledger.md` (build state per feature).

The repository is `git@github.com:Epsuchti/foodsharing-platform-2.0.git` (`origin`). As of 2026-10-31, `origin/main` is at a08df6a, and local `main` is ahead with the sharding, stability and handover commits. CI (`.github/workflows/ci.yml`) runs on every push to `main`. Its run on a08df6a failed only in `./mvnw verify`, on `BellMessagesSyncTest`, which is fixed in 2de8a15. The other jobs (contract, audits, shellcheck) were green, and e2e and screenshots did not run because the build failed.

## Where the project stands
- **Features:** every feature row in `docs/ledger.md` is done except D11.5.3 and D18.3 (Lighthouse, see "Open" below). M1–M6 are done; M7 is in its final gate. Strict coverage: 150/150 features have a tagged test.
- **Desktop side menu** (decisions 133–135, `docs/design/sidenav-*`) replaced the desktop header. Phones keep the bottom tab bar.
- **Screenshot baselines:** 547 baselines (`e2e/__screenshots__`, light theme, German, desktop and mobile; decision 109) were generated in Docker. A compare run matched 544/547; the three differences were fixed and each re-compared 3×.
- **Test speed:** decision 137 added parallel local runs, 4 apps with their own databases: about 16 min for chromium and 14 min for screenshots, instead of about 43 and 40 serially.
- **Test stability (decision 138):**
  - every ordered query has an id tiebreaker, guarded by `StableOrderTest`; UUID v7 ids
  - the browser uses the seed clock
  - the seed is reset per repetition
  - the JVM runs in UTC
  - error states stay until the user retries
  - no clicks covered by toasts
  - screenshots wait for `html[data-page-shown]`, the network to go quiet, and animations to finish
  - business jobs don't fire on the wall clock in the e2e profile
  - automation test/run buttons are blocked while a form is unsaved

## Last verified results
| What | On | Result |
|---|---|---|
| Server unit tests + all 257 ITs incl. the 13 Postgres Testcontainers ITs | main 69f8b9e | green |
| Full chromium e2e (serial) | main a3a3be4, side menu merged | 1002/1007; the 5 failures were fixed afterwards and checked with repeat runs |
| Full mobile-chrome e2e (serial) | main a3a3be4 | 1006/1007; the one flake (AU1) was fixed afterwards |
| Full chromium e2e (sharded) | the sharding branch on a08df6a | 1006/1006 in 16:22 |
| Screenshots | main a08df6a | 547 baselines; 544/547 on compare; the three fixed and re-compared |
| Handover check after the stability merge | main de04b34 + bell sync | client typecheck, lint, i18n check, 92/92 unit test files and e2e typecheck green; server unit tests 1162/1168 (see below) |

**Handover check, server unit tests:** 6 failures in two classes.
- **`BellMessagesSyncTest` (5):** fixed. The side menu added the key `bell.ui.button.itemCount` without running `node scripts/sync-bell-messages.mjs`; it now passes 5/5.
- **`SpaForwardingControllerTest.languagePreferenceOrderIsCookieThenAcceptLanguageThenGerman` (1): still open.** It expects `/it` to fall back to the client shell because "a language without a prerendered page (client build skipped)". The current `server/target/classes/static/` contains a prerendered `it/index.html` from the full client build, so the controller serves it.
  - The test depends on the build state: in CI's clean build on a08df6a it passed. Locally, a stale `static/it` from an earlier build makes it fail. Run `./mvnw clean verify` before suspecting the code.
  - Next: find out why `static/it` now exists (stale output from another build, or a prerender change), then make the test independent of what is in `target/classes`.
  - Don't just change the expectation.

**Not verified yet:** the stability pass (decision 138) was merged when its worker was stopped, before its own full verification run. Its commits passed their targeted checks. A full run on today's main hasn't been done yet. That's the first job.

## First steps for the next developer
1. Set up the environment (Windows notes below), then build the jar:
   `cd server && ./mvnw -q -DskipTests package`
2. Run the full suites on main, twice. Both runs must be green before anything else:
   - `npm run e2e:sharded -- --project=chromium`
   - `npm run e2e:sharded -- --project=mobile-chrome`
   - `node e2e/scripts/feature-coverage.mjs --results e2e/test-results/results.json --strict`
3. Screenshots:
   - Compare first: `npm run e2e:screenshots -- --shards=4`.
   - Decision 138 (UUID v7 ids, seed clock in the browser, UTC) may change visible data in some screens. Look at each difference; retake only intended changes with `npm run e2e:screenshots -- --shards=4 --update-snapshots=changed`.
   - Never update baselines to make a failing test pass (AGENTS.md).
4. The final gate (plan-m7 Part D):
   - fill in the test results in `docs/launch-report.md`
   - regenerate `docs/coverage.md`
   - set M7 done in the ledger
   - tick `plan.md`
   - go through the "needs a human" table with the owner

## Open (base build)
- **Lighthouse D11.5.3 / D18.3:** `/onboarding` scores 82–86 under load against a hard threshold of 90; `/` scores 98–99. Measure on an idle machine or in CI. If it's still below 90, the parked branch `build/m7-perf-2` (decision 131: build-time theme, blocking first navigation) is the next step.
- **Flakes seen in the last runs,** addressed by decision 138 but not yet confirmed by a full run:
  - `mb2-folders.spec.ts:85` (list reloaded before the retry click)
  - `d10-history.spec.ts:299` (a toast covered the h1)
  - `events.screen.ts:26` (times; probably a stale baseline)
  - `history.screen.ts:20` (order)
  - focus and border differences in a few screenshots under load
- **Windows Playwright worker crash** (exit 3221226505 / 0xC0000409): hits a random test about once per full serial run and passes on rerun. It's infrastructure on this machine, not app code. Sharded runs turn video off, which lowers memory.
- **Needs a human** (`docs/launch-report.md`):
  - a green CI run on GitHub: push `main`, then fix what the e2e and screenshot jobs report (D5.4, D18.1)
  - a server, domain and mail provider; then `smoke.sh`, `release.sh --check` and the k6 load test against it
  - legal texts
  - a real usability test with newcomers
  - the product question whether verification requires a confirmed identity

## How the work was organised
The owner works with Claude Code:
- an integrator session on `main`
- Opus workers, each in its own git worktree (`.claude/worktrees/`, branches `worktree-agent-*` and `build/*`)
- a Sonnet test worker for full runs
- Fable for designs and reviews

Workers never write to the main checkout and merge `main` before committing; only the integrator merges into `main` and checks `git diff --name-only --diff-filter=U` before committing a merge. Worktrees and branches whose work is on `main` can be removed (`git branch --merged main`). The one unmerged branch worth keeping is `build/m7-perf-2`.

## Windows environment
- **JDK 21:** portable Temurin in `~/.jdks/jdk-21.0.13+9`. Every Git Bash shell, from the repo root, needs `export JAVA_HOME="$HOME/.jdks/jdk-21.0.13+9"; export PATH="$JAVA_HOME/bin:$PWD/client/node:$PATH"`.
- **Node:** use the Maven-installed `client/node` (24.15). The system Node is too old for the Angular CLI. There is no Python.
- **Docker Desktop:** installed; the CLI is in `/c/Program Files/Docker/Docker/resources/bin`. It's needed for the Postgres ITs and the screenshot container.
- **Generated client API:** `client/src/app/api` is generated and never committed. Run `npm run openapi:generate` in `client/` when it's missing; `npm start` and the Maven build regenerate it.
- **Maven build:** it runs `npm ci` in `client/`, which reinstalls `client/node_modules`. Lint and unit tests in `client/` fail while that runs.
- **Locked jar:** a running local app locks `server/target/*.jar` on Windows. Stop it before `./mvnw clean package`.
- **Local app:**
  - from `server/`: `java -jar target/foodsharing-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`
  - or: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
  - http://localhost:8080, with seed data and the test-person switcher on `/login`
  - the H2 database is in `server/data/`
  - Mailpit: `server/start_smtp_server.sh`
  - The app's built-in browser pane can't reach external hosts, so map tiles don't load there; check the map in a normal browser (resume item 17).
- **Lighthouse:** set `CHROME_PATH` to Playwright's headless shell (`…/chromium_headless_shell-1248/chrome-headless-shell-win64/chrome-headless-shell.exe`); the full chrome.exe can't be spawned on this machine.
- **Ledger:** `scripts/ledger.py` needs Python. Edit `docs/ledger.md` by hand, keeping the 8 columns of a feature row.

## After the base build: agreed with the owner, not started
1. Parallel shards: done (decision 137).
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
20. Search rework (owner, 2026-10-31; compared with foodsharing.network's `SearchGateway.php`). No Elasticsearch: it needs too much memory for a single-host setup, it's a second system to keep in sync including permissions, and Postgres covers the need. Revisit only with millions of entries; then look at Meilisearch or Typesense first.
   - Matching:
     - Postgres full-text (`tsvector`, German stemming) with `ts_rank` relevance.
     - `pg_trgm` for typo tolerance and an indexed "contains".
     - `unaccent` for umlauts.
     - Permissions stay in the same SQL query.
   - Order: your own things first, as foodsharing.network does:
     - regions and groups you're a member of or administer
     - stores where you're store manager, then member
     - buddies first among people
     - events and polls by time distance
     
     Then relevance, then title, then id.
   - People search becomes contact-based, as on foodsharing.network: people who share a region, group, store team or chat with you, plus buddies. Last names are hidden in region search. Email search only with permission; global people search only for orga.
   - A rate limit on the full search (ties in with item 5, bot protection).
   - Forum: search post contents too, not only thread titles, behind a feature switch. With several words, also search stores' street, postcode and town.
   - Tests: these Postgres features don't exist in H2, so the search queries get Testcontainers Postgres ITs. Keep `SearchService` unit tests for the permission scope.
