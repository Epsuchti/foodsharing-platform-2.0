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
