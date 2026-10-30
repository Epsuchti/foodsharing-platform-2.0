# M7 design review (plan §18.5, plan-m7 F3–F5)

## Round 1 — function & flow (F3, 2026-10-30)

### Method
- AI persona walkthroughs (decision 3) against the packaged jar with the `e2e` profile (seed data, fixed clock
  2026-11-02 09:00), Playwright chromium, logged in through `POST /api/dev/login-as`, pending legal consents
  accepted through the API (except one run that walked the newcomer consent page in the UI).
- Every page of each journey on **desktop 1280×800** and **mobile Pixel 7** (412 px, touch), full-page screenshots
  reviewed by eye, plus an automated audit per page: horizontal overflow (`scrollWidth > clientWidth`), raw i18n
  keys in the visible text, failed API calls, DataState of every `fs-data-state`, and on mobile every interactive
  element below 40 px (inline text links in paragraphs exempt, WCAG 2.5.8).
- Screens in German (the persona's own language where the server setting wins: quiz → it, bot → gws).
  Screenshots are review material only (scratch folder), not baselines (decision 69/109).
- Checklist (§18.5): purpose within 5 s, one obvious primary action, works one-handed at phone width, text
  fits, designed loading/empty/error states, consistent shared components, nothing unexplained for a newcomer.

### Personas and journeys
| Persona | Seed | Journey walked |
|---|---|---|
| Visitor | — | landing, find region, public region page (`/r/zuerich-stadt`), public intro pickups, login, deep link to a store |
| Newcomer | `newbie`, `quiz` | consent page → dashboard → journey (`/onboarding`) → profile & region step → learning pages → quizzes → foodsaver quiz → intro pickup booking (`/intro-pickups`, `/r/…/intro-pickups`) → my bookings → messages |
| Foodsaver | `foodsaver` | dashboard, my stores, region stores, store page, pickups (`/pickups`, mine), messages, baskets (list, offer, mine), map, own profile, account profile, search, bells, my regions, region page |
| Store manager | `bieb` | dashboard, store overview, team, pickups/slots, automation (policy, adverts), wall, log, chat, edit store, new store |
| Ambassador | `bot` | dashboard, region tabs (overview, members, stores, FSPs, statistics, reports, achievements, resources, options, settings), verification queue, newsletters, newcomers funnel, region automation, region users |
| Orga | `orga` | dashboard, feature toggles, configuration, admins, uploads, OAuth clients, release notes, content + page editor, blog management, region admin, legal texts |

### Findings
Severity: **blocker** = a persona cannot complete the journey or the page is broken; **major** = clear friction
or a checklist failure on a main path; **minor** = polish, left for round 2 unless trivial.

| # | Persona | Page | Finding | Severity | Fix / status |
|---|---|---|---|---|---|
| 1 | Visitor, newcomer | `/find-region`, `/r/:slug`, `/r/:slug/intro-pickups` (deep link / first page) | Title, subtitle, cards and buttons show raw keys (`regions.find.title`, `introPickups.public.title`, …): `catalogGuard` treated these URLs as start pages, but their texts live in the regions / intro-pickups catalogs, which are not in the start-up catalog. | blocker | **Fixed**: removed `r` and `find-region` from `START_URL` (they wait for the complete catalog like every other route); `catalog.guard.spec.ts` updated. |
| 2 | Orga (any role with many nav items) | every page, desktop 1280 | Page scrolls horizontally by 220–350 px: the visually hidden labels of icon-only header links are `position: absolute` without a positioned ancestor inside the scrolling nav, so links scrolled out of the nav still widened the document. | blocker | **Fixed**: `.hdr__link { position: relative }`. |
| 3 | Orga | `/admin/feature-toggles`, configuration, donations, blocklist, IP blocks (mobile) | Settings tabs widen the page to 725 px instead of scrolling (host element without `min-width: 0`). | major | **Fixed**: `fs-settings-tabs` host `display: block; min-width: 0`. |
| 4 | Orga, ambassador | `/admin/uploads`, `/regions/:id/users` (mobile) | Page scrolls horizontally by 133–187 px although the tables sit in a scroll wrapper: hidden header labels escape the wrapper (same cause as #2). | major | **Fixed**: `.table-wrap { position: relative }` on both pages. |
| 5 | All (touch) | pickups "Eintragen"/"Austragen", store "Bewerben", journey action, admin toggles, OAuth actions, page-header back links | Buttons are 31–36 px tall on phones, back links 22 px (target ≥ 44 px). | major | **Fixed**: on `(pointer: coarse)` every `.p-button`/`.fs-btn` has `min-height: 2.75rem` (icon-only buttons also `min-width`); page-header back link 44 px tall without moving the title. Mobile baselines change in the next batch. |
| 6 | Newcomer | `/dashboard` | The "your next step" card shows the step title twice and the same CTA twice (compact stepper action + card action), plus the header journey bar: three identical "Profil vervollständigen" buttons on one screen. | major | **Fixed**: `fs-journey-stepper [progressOnly]` (meter + "step n of m" only) inside the card; unit test added. |
| 7 | Newcomer | `/consent` | The header journey bar offers "Profil vervollständigen", which leads to a page the consent guard bounces straight back from (loop next to the page's own primary action). | major | **Fixed**: journey bar hidden on `/consent` (`journeyBarAllowedOn`, unit test). |
| 8 | Foodsaver, store manager | store overview | Members read "Du kannst dich für das Team bewerben" in the requirements card right above "Team verlassen". | major | **Fixed**: members/coordinators see "Du bist im Team. Diese Voraussetzungen gelten für neue Mitglieder." (5 languages; requirements list unchanged); unit test for `isInTeam`. |
| 9 | Ambassador (also bells panel, newsletter unsubscribe) | verification queue | "Verlauf" renders as a second filled primary button next to "Verifizieren": the `fs-btn--text` variant used since M2 was never defined. | major | **Fixed**: global `.fs-btn--text` (transparent, primary text). |
| 10 | Foodsaver | `/map` (mobile) | Search, layer chips and four store filters fill the whole first screen; the map starts below the fold. | major | **Open → round 2**: needs collapsible filters on phones; the filter selects are driven by e2e specs on every project, so the change belongs with the visual round and its spec updates. |
| 11 | Newcomer | `/onboarding` | Same CTA three times (header bar, journey CTA, current step action). | minor | Open: D11.5.4 asserts the bar action on `/onboarding`; decide in round 2 whether the bar hides its action there. |
| 12 | Newcomer, all | header (desktop 1280) | With more than five items the main nav is icon-only (9 unlabelled icons for a newcomer; compass, pin and "?" are ambiguous); the brand logo shrinks to a dot. | minor | Round 2 (labels for the top items or a "more" menu). |
| 13 | All | terminology | "Foodsaver*in" (journey title, newcomers page) vs "Foodsaver:in" elsewhere; badge "Botschafter*in" vs "Botschafter:in"; nav "Mein Einstieg" vs page "Dein Weg zur Foodsaver*in" vs link "Ganze Reise ansehen"; store tab "Protokoll" vs pickups "Verlauf". | minor | Round 2 glossary pass (all five languages). |
| 14 | Newcomer (mobile) | journey bar | The current step title is truncated ("Profil & Bez…") next to the action. | minor | Round 2. |
| 15 | Store manager, foodsaver (mobile) | store page tabs | Eight tabs scroll horizontally without a fade or indicator; "Pinnwand", "Chat", "Automatisierung" are off-screen. | minor | Round 2. |
| 16 | Store manager | store pickups | Empty slots are bare "+" circles without a visible label; own sign-ups only as avatars. | minor | Round 2. |
| 17 | Ambassador | region members (mobile) | Icon-only search/remove actions per member; "Austreten" is prominent in the header of the ambassador's own home region. | minor | Round 2. |
| 18 | Ambassador | verification queue | "Verifizieren" is enabled while the identity is still unchecked; the expected order (identity first) is not explained. | minor | Round 2 (copy or disable with reason). |
| 19 | All (mobile) | dashboard activity filter chips (32 px), dashboard text links (25 px), basket form kind checkboxes (13 px, label clickable), region options/settings switches | Small targets that are not buttons. | minor | Round 2 (chips and switches 44 px on touch). |
| 20 | Newcomer | `/consent` | The petition banner appears on the blocking consent page and competes with "Zustimmen und weiter". | minor | Round 2 (no banners on `/consent`). |
| 21 | Foodsaver (mobile) | `/map` | Transient 10 px horizontal overflow while Leaflet animates markers. | minor | Round 2. |
| 22 | Ambassador | `/regions/:id/options` (desktop) | 7 px horizontal overflow. | minor | Round 2. |

Totals: 2 blockers, 8 majors (7 fixed, 1 open), 12 minors (open). By persona (a finding can count for several):
visitor 1, newcomer 7, foodsaver 6, store manager 4, ambassador 6, orga 4.

Not findings: map tiles and avatar images fail in the offline review environment (504) — the pages show their
designed "map could not be loaded" state with retry.

### Verification
Fixed findings re-checked on a rebuilt jar with the same scripts (overflow audit, raw-key scan, screenshots).
Checks (decision 106): `npm run lint`, `npm run i18n:check`, `ng test` for the touched specs, `ng build` (via the
jar build), e2e typecheck.

## Round 2 — visual polish (F4, 2026-10-30)

### Method
- Same set-up as round 1 (packaged jar, `e2e` profile, `POST /api/dev/login-as`, consents accepted through the API),
  personas visitor, newbie, foodsaver, store manager (`bieb`), ambassador (`bot`) on **desktop 1280×800** and
  **Pixel 7**, **light and dark** (dark through the emulated colour scheme — the seed accounts follow the system theme,
  so `localStorage['fs.theme']` alone left round-1-style dark runs light). Full-page and first-screen screenshots of
  every page reviewed by eye, before and after, plus the round-1 audit (horizontal overflow, raw keys, failed API
  calls, data states, touch targets below 40 px on the phone).
- Focus (§18.5 round 2): spacing rhythm, typography, card consistency, page headers and action placement, dark-mode
  surfaces, focus rings, icons, list density on phones; the most used pages first (landing, onboarding, dashboard,
  store page, pickups, messages, map, profile, region page). Screenshots are review material only (decision 69/109).

### Round-1 open items
| R1 # | Finding | Status |
|---|---|---|
| 10 | `/map` filters fill the first phone screen | **Fixed**: below 48rem search and "near me" share one row (icon-only buttons with names), the layer chips are one swipeable row, and the store / member filters fold behind a toggle (`fs-marker-filter-bar [collapseOnPhone]`, `<testId>.toggle` with `aria-expanded` and the count of active filters). The map now starts in the upper half of the Pixel 7 screen. `tests/map/m1-map.spec.ts` opens the toggle on phones before choosing (asserting the selects are hidden first); every assertion stays. |
| 11 | `/onboarding`: the same CTA three times | **Fixed** (intended UX change): on the journey page itself the header bar shows the step and progress but no action (`fs-journey-stepper [hideAction]`, `isJourneyPage`), and the full stepper leaves out the current step's action (`[omitActionFor]`) — the hero CTA is the one action. D11.5.4 now asserts exactly that on `/onboarding` and the bar action at 360 px on `/dashboard` (unchanged strength: action visible at 360 px in all five languages). |
| 12 | Newcomer header: unlabelled icons, logo shrinks to a dot | **Fixed**: the brand never shrinks (word hidden below 25rem, mark always shown); in the compact nav on ≥ 75rem the first two items keep their label (≤ 24 characters), the newcomer journey moved right after the dashboard with the journey page's flag icon; the nav scrolls with an edge fade (`fsScrollHint`). An overflow "more" menu is left for round 3 (e2e specs click `nav.<id>` on desktop). |
| 13 | Terminology | **Fixed (de)**: colon form, the dominant one in the catalog (60 vs 51), everywhere: `Foodsaver:in`, `Botschafter:innen`, `Nutzer:innen` … (52 strings); the journey is "Einstieg" everywhere (nav, page "Mein Einstieg" / "Dein Einstieg als Foodsaver:in", dashboard "Dein Einstieg" / "Alle Schritte ansehen", passport link); store tab "Protokoll" → "Verlauf" like the region tab. Other languages keep their own conventions (gws keeps `*`). |
| 14 | Journey bar cut off on phones | **Fixed**: count above the title, title up to two lines, the action may wrap. |
| 15 | Store tabs overflow without hint | **Fixed**: `ScrollHintDirective` (`fsScrollHint`) fades the edge behind which tabs wait (store, region and header nav); unit test for `updateScrollHint`. |
| 16 | Empty pickup slots unlabeled | **Fixed**: the first free slot reads "+ Eintragen" (or "n frei" when the viewer cannot sign up), the others stay compact; own slot gets a stronger ring; 44 px slots on touch. |
| 17 | Member actions icon-only, "Austreten" prominent | **Fixed**: "Rechte" and "Entfernen" are labelled buttons (accessible names unchanged, contain the label); leaving moved from the region header to a membership footer below the tab content ("Zürich Stadt ist dein Stammbezirk. · Austreten", danger text button, same test id). |
| 18 | "Verifizieren" enabled before the identity check | **Copy and order** (not UI-only: the server deliberately allows verifying without an identity confirmation, several journeys rely on it): while the identity is open the card says "Prüfe zuerst den Ausweis …", "Identität bestätigen" is the primary button and "Verifizieren" secondary. Making it a hard rule is a product decision (see below). |
| 19 | Small targets on touch | **Fixed**: `--fs-target-sm` (36 px, 44 px on coarse pointers) for chips, pills and segmented links (activity filters, map layers, store log presets, resources, automation nav, region picker, history timeline, profile regions, surveys); on coarse pointers fields are 44 px, the hit areas of switches/checkboxes/radios grow to 44 px around the unchanged control, native checkboxes 24 px, header buttons, user menu, brand, footer links, back/parent links and dashboard links 44 px. Remaining sub-40 px items on the audited pages: Leaflet zoom/attribution, inline links in definition lists, the select trigger inside a 44 px select, number-field spin buttons. |
| 20 | Petition banner on `/consent` | **Fixed**: `petitionBannerAllowedOn` keeps it off `/consent` and `/petition` (unit test). |
| 21 | Transient map overflow on phones | **Fixed** with #10 (the controls row no longer wraps wider than the card); 0 px in the re-run. |
| 22 | Region options 7 px overflow | **Fixed**: number fields shrink with their grid column (`.p-inputnumber { min-width: 0; max-width: 100% }`). |

### Round-2 findings
| # | Page | Finding | Severity | Fix / status |
|---|---|---|---|---|
| R2-1 | all (phones) | Page headers spend the first screen: 48 px icon tile centred next to a three-line block, 24 px gap below. | major | **Fixed**: compact header on phones (40 px icon top-aligned, smaller subtitle, actions on their own row). |
| R2-2 | dashboard (newcomer) | ~80 px empty band between greeting and next step: cards that render nothing (push opt-in, pickups card of newcomers) still took a grid gap. | minor | **Fixed**: empty grid children are not displayed. |
| R2-3 | `/onboarding` | Hero, "Alle Schritte" and the stepper stuck together (content of the data state had no rhythm). | minor | **Fixed**: one 1.25 rem stack. |
| R2-4 | pickups `/pickups` (phones) | Three rows per pickup (text, then a full-width "Eintragen") and a lone right-aligned "Meine Abholungen" toolbar. | minor | **Fixed**: the action stays beside the text (one row per pickup); "Meine Abholungen" moved into the page header actions. |
| R2-5 | store pickups (phones) | Toolbar actions right-aligned in two ragged rows, primary last. | minor | **Fixed**: actions start left, "Einmalige Abholung" first on phones. |
| R2-6 | store page header | "Apple Karten" was a text button next to outlined siblings. | minor | **Fixed**: same outlined secondary style as "Route planen". |
| R2-7 | profile (phones) | Six stat tiles in three rows of two filled a screen. | minor | **Fixed**: three compact tiles per row below 30rem. |
| R2-8 | dark mode | The warm hero glow (`#f2b84b` hard-coded in six places) turned dark surfaces olive (dashboard greeting, store/region heroes, login, landing). | minor | **Fixed**: token `--fs-accent` (muted `#9c7a3c` in dark), used everywhere. |
| R2-9 | header (desktop) | The journey bar was wider than the header and the page (80rem, 1rem padding). | minor | **Fixed**: aligned to `--fs-page-max` and the gutter. |
| R2-10 | header (desktop, many items) | Nav icons scrolled out of the header without a hint. | minor | **Fixed** with R1 #12 (edge fade); "more" menu open (round 3). |
| R2-11 | messages, dashboard | Relative times "in 3 Tagen" on past messages in the review set-up: the browser clock is real time, the server's e2e clock is 2026-11-02. | — | Not a finding (review artefact). |

Checked without findings: focus rings (global 3 px primary ring, component rings 2 px primary, visible in both
themes), PrimeIcons only, card radius/shadow tokens, typography scale (page title clamp 1.5–2.1 rem, card titles
1.05–1.1 rem), dark surfaces and borders, messages list density.

### Left for round 3
- Header "more" menu for roles with many items (needs the desktop nav specs to open it first).
- Store page hero on phones still fills most of the first screen for managers (four badges, four buttons): collapse
  the navigation links into one "Route" menu.
- Hard rule "verify only after the identity check" if the product wants it (server + journeys + e2e).
- Empty-state illustrations were reviewed only on the audited pages (they use the shared `fs-data-state` set);
  success/celebration states and the admin pages were not re-reviewed.

### Verification
Overflow / raw-key / failed-call audit clean on the re-run (light and dark, desktop and Pixel 7). Checks (decision
106): `npm run lint` (incl. theme tokens), `npm run i18n:check`, `ng test` for the touched specs, `ng build` (jar
build), e2e typecheck, chromium + mobile-chrome `tests/map/m1-map.spec.ts`, chromium `d11-journey`, `r2-membership`,
`ve2-verify`, `pu3-signup`, `pu7-options`, `c8-petition`, `d4-shell-navigation`.

## Round 3 — scripted persona walkthroughs (F5, D11.5.5, 2026-10-30)

### Method
- Plan §18.5 round 3 asks for a usability test with 3–5 people from Zürich; decision 3 replaces it during the build
  with AI personas. Round 3 makes the personas **scripted and repeatable**: one committed Playwright spec per persona
  in `e2e/tests/personas/` (tagged `@D11.5.5`, the feature IDs it walks and `@page` for the axe check) that walks the
  persona's main journey by hand — navigation through the real header / "More" menu / tab bar and drawer, the real
  forms and dialogs — and asserts the outcome (role, booking, confirmed slot, request, log entries, verified flag,
  report status, queued newsletter, feature off for members). Other people (the manager who confirms, the requester,
  the quiz answer key) act through their own API sessions and are not counted.
- Each spec counts **steps** (tasks as the persona would name them), **clicks/taps** and **filled fields** through a
  small `Walk` helper (`e2e/tests/personas/_persona.ts`), records them with the duration as test annotations
  (`test-results/results.json`) and fails when a journey gets longer than its round-3 budget (the counts below) — a
  longer journey has to be a deliberate decision. Every spec runs on `chromium` (1280×800) and `mobile-chrome`
  (Pixel 7).
- Friction is whatever made a walk longer, ambiguous or a dead end. The first runs showed it; it was fixed and the
  counts below are after the fixes. Admin pages and success/celebration states were re-reviewed by eye from
  screenshots (desktop and phone; review material only, decision 69/109).

### Persona results
| Persona (seed) | Journey | Steps | Clicks desktop / phone | Fields | Friction found | Status |
|---|---|---|---|---|---|---|
| Newcomer (`newbie`) | consent → "next step" → profile & region → 4 learning chapters → foodsaver quiz + agreement → first EinAb booking → "my EinAbs" | 8 | 52 / 51 (about 35 of them answer the quiz) | 5 | After the last chapter "Zur Übersicht" led to the chapter list instead of the quiz; the confirmed quiz ("Du bist jetzt Foodsaver:in …") was a dead end without the next step. | **Fixed** (R3-3, R3-4) |
| Foodsaver (`foodsaver`) | my stores → store → sign up for Wednesday → confirmation bell → confirmed slot → offer a basket → request bell → request on the basket | 5 | 14 / 17 | 1 | At 1280 px the header showed one or two links: the rest scrolled out of a 110 px wide nav (search, region switcher and the named user menu took the row), so "Betriebe" and "Essenskörbe" were not findable. | **Fixed** (R3-1) |
| Store manager (`bieb`) | my stores → store → confirm Sarah's sign-up → add a regular Tuesday pickup → store log shows both | 4 | 11 / 13 | 1 | Phone: the manager's hero (badges + four buttons) pushed the tabs below the first screen (round-2 list). | **Fixed** (R3-2); the spec asserts the tabs end inside the first Pixel 7 screen |
| Ambassador (`bot`) | verification queue → identity + verify → region reports → no-show report "in progress" with consequence → region newsletter written, saved, queued | 4 | 15 / 16 | 4 | Verification and newsletters sit behind "More" on a 1280 px laptop (one extra click each). | Accepted: the dashboard and the region page link both; from 90rem more links fit |
| Orga (`orga`) | feature toggles → baskets off (confirm) → the member loses nav entry and page → baskets on | 4 | 5 / 5 | 0 | — | Pass |

A whole walk takes 5–13 s in the e2e set-up (no think time); durations are recorded per run, not budgeted.

### Fixes
| # | Area | Change |
|---|---|---|
| R3-1 | Header, desktop (round-2 list) | The nav takes the free space of the header and wraps into a clipped second row; `HeaderComponent.measureNav` counts the links of the first row (ResizeObserver) and the rest move to a **"More" menu** (`nav.more`: a disclosure with a labelled link list, `aria-expanded`, Escape returns the focus, closes on navigation and on a click outside, active state when the current page is inside, red dot when a hidden item has unread messages/mails). Occasional items are `NavItem.secondary` (store chains/categories, events, polls, surveys, admin pages, quizzes of verified members) and move first (`orderHeaderNav` / `splitHeaderNav`, unit test). A link keeps its test id `nav.<id>` wherever it is reachable; specs open the menu when needed through `openNav(page, id)` (`e2e/fixtures/nav.ts`, also the phone drawer; `b1-baskets` uses it, `d4-shell-navigation` has a new "More" test). To leave room for links the user menu shows the avatar only between 48 and 90rem (the name stays in its accessible name), the search box is `15vw` (was `17vw`), and the first two items keep their label from 90rem (was 75rem: two labels left room for one link at 1280 px). Result at 1280 px: 5–7 icons + "More" for every role, no sideways scrolling. |
| R3-2 | Store page, phones (round-2 list) | "Route planen" and "Apple Karten" fold behind one "Route" button (`stores.page.route`, `aria-expanded`); desktop unchanged; test ids of both links unchanged (S15). |
| R3-3 | Learning chapter (success state) | Last chapter while the journey is open: primary "Weiter mit deinem Einstieg" (`content.chapter.toJourney`), "Zur Übersicht" becomes secondary. |
| R3-4 | Quiz results (celebration state) | Confirmed quiz while the journey is open: "Weiter mit deinem Einstieg" (`quiz.results.toJourney`). |
| R3-5 | D4.3 mobile spec | Timing cause found (below); the footer check scrolls to the end and measures until the layout has settled. |

### Re-review: admin pages and success states
- Admin pages (orga, desktop and phone: feature toggles, configuration, uploads, admins, release notes, bounced
  mails, achievements, OAuth clients, regions, content, jobs, mail outbox, integrations, store categories, blocklist,
  newsletters, store chains, quiz editor): no horizontal overflow, no raw keys, every data state designed (bounced
  mails shows its empty state). Open minors: page widths differ (jobs / integrations use the full width, toggles
  64rem, bounced mails 60rem); integrations show eight filled "Test senden" buttons on one screen.
- Success states: EinAb booking (private details, calendar, "my EinAbs"), basket created (empty-request hint), pickup
  sign-up (unconfirmed badge), verification (verified tag + toast) and newsletter queued (status badge) show the
  result and the next step; the two dead ends were the last chapter and the quiz confirmation (R3-3, R3-4).

### D4.3 (mobile tab bar vs footer)
Not reproduced in 12 repeated chromium runs, but the timing cause is real: measured on `/dashboard` at 390 px, the
footer version moves from 2835 px to 3006 px and then to 3666 px between 1.9 and 2.2 s after the page load — the
deferred petition banner and late dashboard content arrive after `pageShown` (+1.5 s). The spec scrolled to the
footer once and measured right after it; a layout shift between the two calls left the footer behind the tab bar. The
check now scrolls to the end and polls the same assertion (version bottom ≤ tab bar top) on the settled page.

### Launch report note (D11.5.5)
"D11.5.5 — usability test with 5 real newcomers: replaced by scripted AI-persona walkthroughs (decision 3); a real
usability test with 5 newcomers from Zürich remains recommended before launch."

### Verification
Persona specs green on `chromium` and `mobile-chrome`, plus chromium and mobile-chrome `d4-shell-navigation`,
`s15-navigation`, `b1-baskets`, and chromium `c11-feature-toggles`, `c1-content`, `mb2-folders`, `n1-conversations`,
`a11y/pages`, `q2-session`. Checks (decision 106): `npm run lint`, `npm run i18n:check`, `ng test` for the touched
specs, `ng build` (jar build), e2e typecheck. Screenshot baselines that change in the next batch: every desktop page
(header with icons + "More", avatar-only user menu), the store pages on phones ("Route"), the last learning chapter
and the confirmed quiz result.
