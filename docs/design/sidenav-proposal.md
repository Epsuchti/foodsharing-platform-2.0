# Desktop side navigation — design proposal

Status: proposal for the desktop redesign phase (decision 132). No code changed. Mockup:
`sidenav-mockup.html` next to this file.

What it replaces: the desktop top header for **signed-in users** (brand, measured nav with the "More" menu,
search box, region switcher, "what's new", language, theme, bell, user menu). What stays as it is: the phone
layout below 48rem (top header with search/language/theme/avatar + bottom tab bar + menu drawer), and the
top header for **visitors** (landing, find region, public region pages, login — marketing pages with one nav
item; a rail next to a hero would look like an app frame around a brochure).

Reading order of this document: concept → widths → tokens → states → implementation → alternatives → open
questions.

---

## 1. Concept

### 1.1 Why a side menu

Round 3 (decision 127) got the header to "5–7 icons + More" at 1280 px. That is the ceiling of a horizontal
nav: an ambassador has 13 main items, an orga 25, and the persona walk left verification and newsletters one
click behind "More" on a laptop. A vertical list has room for labels at every width from 80rem, groups items
by purpose, and never needs to measure what fits. The phone keeps its tab bar: the side menu is a desktop
pattern only.

### 1.2 Anatomy (expanded, top to bottom)

```
┌──────────────────────┐
│ ● foodsharing      ‹ │  brand (link to /dashboard) + collapse toggle
│ ⌖ Zürich Stadt     ▾ │  context switcher: regions + working groups (home region first)
│ 🔍 Suchen…           │  global search (the existing fs-global-search, full width)
│                      │
│ ⌂ Übersicht          │  ── group 1, no heading: the daily tasks
│ ⚑ Mein Einstieg      │     (newcomers only, with a small progress meter)
│ 📅 Abholungen        │
│ 🛍 Betriebe          │
│ 💬 Nachrichten    ③  │     badge = unread conversations
│ ✉ Postfächer      ①  │     store managers and above
│ 🔔 Glocke        ⑫  │     opens the bell panel next to the item (no page change)
│ 🛒 Essenskörbe       │
│                      │
│ COMMUNITY         ▾  │  ── group 2, collapsible, open by default
│ 🗺 Karte             │
│ ⌖ Bezirke & Gruppen  │
│ 📅 Termine           │
│ 📊 Abstimmungen      │
│ ? Quiz               │
│ ⏱ Meine Aktivität    │
│                      │
│ VERWALTEN         ▾  │  ── group 3, ambassadors / managers / guides; open by default
│ … Neue Foodsaver     │
│ ✓ Verifizierung      │
│ …                    │
│ ORGA              ▸  │  ── group 4, orga only; closed by default, opens when a child is active
│                      │
│ 🎁 Was ist neu     • │  bottom block, pinned
│ (avatar) Bea Bot     │  user menu: profile, account, passport, notifications, support,
│          Botschafter:in   language, theme, logout
└──────────────────────┘
```

The nav list between the search and the bottom block scrolls on its own when it is taller than the space
left (measured in the mockup at 800 px: 535 px of list for every role but the newcomer, see section 4);
brand, switcher, search and the user block never scroll away, the current item is scrolled into view on
navigation, and the list fades at the edge behind which more items wait (a vertical variant of the existing
`ScrollHintDirective`).

### 1.3 Information architecture

Items come from `nav.config.ts` as today. Each main item gets a `section`; the order inside a section is the
declared order, with `secondary` items last (the existing `orderHeaderNav` rule, reused). Sections appear only
when they contain at least one visible item, so the groups per role fall out of the existing `visible` rules:

| Section | i18n key (de / gws / en / fr / it) | Items in declared order | Who sees it |
|---|---|---|---|
| *(none)* — daily | — | dashboard, onboarding, pickups, stores, messages, mailboxes, **bells** (panel), baskets | everybody signed in (newcomers: dashboard, onboarding, messages, bells) |
| Community | `nav.section.community`: Community / Community / Community / Communauté / Community | map, regions, introPickups, guide, events, polls, quizzes (secondary when verified), activity | everybody |
| Verwalten | `nav.section.manage`: Verwalten / Verwalte / Manage / Gérer / Gestire | newcomers, verification, users, newsletters, blogManage, surveys, storeChains | ambassadors, group admins, users with CAN_MANAGE_USERS / CAN_MANAGE_SURVEYS; store chains: foodsavers (secondary) |
| Orga | `nav.section.orga`: Orga / Orga / Orga / Orga / Orga | adminRegions, adminQuizzes, adminContent, storeCategories, adminIntegrations, adminJobs, adminMailOutbox, adminSettings, adminOauth, adminUploads, adminAchievements, adminAdmins, adminReleaseNotes, adminBounced | orga; adminQuizzes also working-group admins, adminContent also PR admins (then "Orga" shows with one or two items — see open question 6) |

Per role this gives:

| Role | Groups shown | Items (de) |
|---|---|---|
| Newcomer (unverified) | daily, Community | Übersicht, Mein Einstieg, Nachrichten, Glocke · Karte, Bezirke & Gruppen, Einführungsabholungen, Quiz, Termine, Abstimmungen, Meine Aktivität |
| Foodsaver | daily, Community | Übersicht, Abholungen, Betriebe, Nachrichten, Glocke, Essenskörbe · Karte, Bezirke & Gruppen, Termine, Abstimmungen, Quiz, Meine Aktivität (+ Betriebsketten in "Verwalten", secondary — see question 6) |
| Store manager | + Postfächer in daily | as foodsaver + Postfächer |
| Ambassador | + Verwalten | + Neue Foodsaver, Verifizierung, Mitglieder, Newsletter, Blogbeiträge |
| Orga | + Orga | + the 14 admin pages behind one closed heading |

Primary vs secondary: in the side menu every visible item is listed; `secondary` only decides the order
inside its section (last) and, in the rail, which items fold into the section flyout (section 1.5). The
"More" menu disappears for signed-in desktop users.

### 1.4 Where the other header pieces go

| Piece | Today (desktop header) | Proposal |
|---|---|---|
| Brand | left | top of the sidebar, links to `/dashboard`; rail shows the mark only (the brand never shrinks, R1 #12) |
| Region switcher | icon + name + chevron, popover of member regions | second row of the sidebar, full width, shows the active region; popover lists **regions and working groups** (`SessionInfo.units`, home region first, groups under their own heading); a filter field appears from 8 entries |
| Search | inline box `15vw`, phone toggle | third row, full width field; in the rail an icon that opens the same `fs-global-search` as a floating panel to the right of the rail |
| Bells | `fs-bell-button` with panel below the header | nav item "Glocke" in the daily group with the unread count; click opens the existing panel anchored to the item (expanded: below the item, rail: to the right). No route change; `aria-haspopup="dialog"`, `aria-expanded` as today. The `/bells` page item stays `notYet` |
| Messages / mailboxes badges | `fs-nav-badge` inline | same component, trailing in the row; overlay on the icon in the rail (`[overlay]` exists) |
| "What's new" dot | gift icon button | small item at the top of the bottom block ("Was ist neu" with the dot); keeps `shell.whatsNew` |
| Language, theme | two icon buttons | two rows inside the user menu ("Sprache · Deutsch", "Darstellung · Hell") that expand to the radio lists that `fs-language-menu` / `fs-theme-menu` already render; phones keep the drawer chips |
| User menu | avatar pill, popover | bottom block: avatar + display name + role (two lines), opens the same popover upwards; rail shows the avatar only (name stays in the accessible name) |
| Journey bar (newcomers) | below the header, page-wide | top of the content column, sticky, same component; the "Mein Einstieg" item additionally shows "Schritt 3 von 6" as a meter under its label |
| Login button | right | unchanged: visitors keep the top header |

### 1.5 Nested context: store and region pages

The side menu is **global**; a store or region page keeps its own horizontal tab strip under the hero
(`/stores/:id/{overview,team,pickups,…}`, `/regions/:id/{overview,members,…}`), exactly as today. Reasons:
members belong to several stores and regions and jump between them from lists, the tab strips are already
built and tested (S15, R2), and a second-level tree in the sidebar would duplicate them.

What the sidebar does on such a page:

- The parent item is active by **route prefix**, not exact route: on `/stores/…` "Betriebe" is current, on
  `/regions/…` "Bezirke & Gruppen", on `/me/intro-pickups` and `/intro-pickups/…` "Einführungsabholungen".
  `NavItem` gets `activeMatch?: string` (a path prefix) for the cases where the item route (`/stores/mine`)
  is not the prefix of its pages (`/stores/:id`).
- The region switcher does not follow the page: pages under `/regions/:id` use their own region (unchanged
  rule of `RegionContextService`). When the page's region differs from the active one, the switcher row shows
  a small "Seite: Winterthur" hint under the active region name (muted, 0.75rem) so the two are never confused.
- The hero's parent link (`stores.page.region`) and the page header back link stay the way back up.

---

## 2. Behaviour across widths

### 2.1 Modes

| Viewport | Mode | Width | Notes |
|---|---|---|---|
| ≥ 80rem (1280 px) | **sidebar**, expanded by default | `--fs-sidenav-w` 16.5rem (264 px) | the user can collapse it to the rail; the choice is remembered (2.3) |
| 48rem – 79.99rem (768–1279 px) | **rail** | `--fs-sidenav-rail-w` 4.5rem (72 px) | the expand button opens the full sidebar as a temporary overlay over the content (shadow `--fs-shadow-lg`, no scrim); it closes on navigation, Escape, click outside and focus leaving it. Nothing is remembered here |
| < 48rem | **phone** (unchanged) | — | top header (brand, search toggle, language, theme, avatar) + bottom tab bar + drawer |

The 48rem boundary is the existing one (`PHONE_MAX` in `e2e/fixtures/nav.ts`, header and tab bar media
queries). 80rem is new: it is the Playwright `chromium` viewport (1280×800) and the laptop case that round 3
measured; from there 264 px of sidebar leave a content column of 1016 px minus gutters, enough for every page
that was designed for `--fs-page-max` 76rem (admin tables scroll inside their `.table-wrap` as they do on
phones today). Content stays centred within the column (`width: min(100%, var(--fs-page-max))`), so on a
1920 px screen nothing changes but the empty margins.

Rail content: every item of the daily group as a 40 px tile (44 px on touch, `--fs-sidenav-tile`); the
collapsible sections either as tiles too or **folded into one tile each** that opens a flyout (a disclosure
with the labelled link list of the section, the same markup as today's "More" list). Folding is measured, the
way the header measures its links today (`ResizeObserver`): sections fold from the bottom up (Orga, then
Verwalten, then Community) until the rail fits the viewport height, so the rail never scrolls; the daily group
never folds. Whole sections fold, never single items, so a tile never changes its place while the user is on
the same screen. Measured in the mockup at 800 px: newcomer and foodsaver need no fold (12 and 15 tiles),
an ambassador gets Community and Verwalten folded (daily tiles + two section tiles), an orga all three. On
a 1366×768 laptop (about 660 px of viewport) foodsavers get Community folded as well.

### 2.2 Tablet and phone

- 48–80rem (tablets in landscape, small laptops): rail + overlay. The overlay sidebar is the same component in
  its expanded state positioned `fixed` over the content; opening it moves focus to the first item, closing it
  returns focus to the expand button.
- Tablets in portrait (768–1023 px) are in the rail range too; on a coarse pointer every rail target is
  44 px (`--fs-target-sm`).
- Below 48rem nothing changes: the tab bar, the drawer with all items, the phone header. The sidebar is not
  rendered there (not hidden with CSS — not rendered, so no duplicate landmarks and no duplicate test ids).

### 2.3 Persistence

- Key `fs.nav` in `localStorage` through `core/storage.ts` (never throws), next to `fs.theme`, `fs.lang`,
  `fs.region`. Values: `open` | `rail`. Written only when the user presses the toggle; absent = width decides.
  Read once at boot, applied before the first paint of the shell (the shell sets `data-nav="rail"` on its
  host from the signal; no transition on the first render, so there is no flash).
- Section open/closed state: `fs.nav.sections` = JSON `{ "orga": false, "manage": true }`, written on toggle.
  Defaults: Community open, Verwalten open, Orga closed. The section containing the current page is always
  open on navigation (forced, not written).
- Per device, not per account (a shared volunteer laptop in a store back office should keep the rail the
  previous person chose for that screen). If an account-level preference is wanted later, the user settings
  page can mirror the key.

### 2.4 Keyboard and screen reader

- Landmarks: one, not two. The root is a plain `<div class="sidenav">` (no `<aside>`: an extra complementary
  landmark around a navigation landmark only adds a stop in the landmark list); inside it one
  `<nav aria-label="Hauptnavigation">` (existing key `layout.header.mainNav`) holds the section lists. Each section is a `<section>` with its heading as the
  disclosure button (`<h2><button aria-expanded aria-controls>Verwalten</button></h2>`) and a `<ul>` of links.
  The daily group has a visually hidden `<h2>` ("Täglich" / new key `nav.section.daily`, needed for the
  heading outline even though it is not shown).
- Tab order (DOM order): skip link → request bar → brand → collapse toggle → region switcher → search → nav
  links (closed sections contribute only their heading button) → "Was ist neu" → user menu → main content.
  For an orga with Orga closed this is ~22 stops; the skip link ("Zum Inhalt springen", unchanged
  `shell.skipLink`) jumps over all of them. Add a second skip link at the end of the sidebar? Not needed: Tab
  from the user menu lands in `<main tabindex="-1">`'s first control.
- Links: `aria-current="page"` on the active link (the `#rla` pattern of the drawer and the "More" list); the
  class `sidenav__link--active` is for styling only. Tests assert `aria-current`, not the class (today's D4.3
  asserts `hdr__link--active`).
- Rail: the label is a visually hidden `<span>` inside the link (accessible name unchanged) plus a tooltip
  shown on hover and `:focus-visible` (CSS, `aria-hidden`, reads the label from `data-label`); `title` is not
  used (it is announced twice).
- Collapse toggle: `<button aria-expanded="true|false" aria-controls="sidenav-nav">` named "Navigation
  einklappen" / "Navigation ausklappen" (new keys `layout.sidenav.collapse` / `layout.sidenav.expand`). Focus
  stays on the button; its name change is enough, no live region.
- Section flyouts in the rail and the overlay sidebar: disclosure pattern like `nav.more` today —
  `aria-expanded`, Escape closes and returns focus to the button, click outside closes, Tab out closes.
  No `role="menu"` (plain link lists, no arrow-key handling needed, no popover code in the start-up bundle,
  D11.5.3).
- Bells item: `aria-haspopup="dialog"` + `aria-expanded` as the current `bells.button`; the panel is the
  existing one.
- Badges: `fs-nav-badge` already renders the count `aria-hidden` plus a hidden "(3 ungelesen)"; unchanged.
- Reduced motion: no width transition (`transition-duration` is already 0.01 ms globally).
- Shortcut (optional, open question 7): `[` toggles the sidebar, `Ctrl/⌘ K` focuses the search.

---

## 3. Visual spec

All values use existing `--fs-*` tokens; new tokens are listed at the end.

### 3.1 Frame

| Property | Value |
|---|---|
| Sidebar width | `--fs-sidenav-w: 16.5rem` (264 px) |
| Rail width | `--fs-sidenav-rail-w: 4.5rem` (72 px) |
| Background | `--fs-surface`; the page background stays `--fs-bg` with its radial glows, so the sidebar reads as a panel |
| Right edge | `1px solid var(--fs-border)` |
| Position | `position: sticky; top: 0; height: 100dvh` (the content scrolls, the sidebar does not); overlay mode: `position: fixed; box-shadow: var(--fs-shadow-lg)` |
| Padding | `0.75rem` sides (expanded), `0.75rem` sides (rail, items centred) |
| Brand row | height `--fs-header-h` (4rem) so the content's first line aligns with today's header baseline; mark 2rem, wordmark as in `fs-brand` |
| Content column | `grid-template-columns: var(--fs-sidenav-x) minmax(0, 1fr)`; `--fs-sidenav-x` is the current sidebar width (0 on phones), set by the shell |

### 3.2 Items

| Property | Value |
|---|---|
| Height | `min-height: var(--fs-target-sm)` → 36 px mouse / 44 px touch (decision 124); the rail uses a square tile of `--fs-sidenav-tile` (40 px mouse / 44 px touch — 40 instead of 36 because an icon without a label needs the larger hit area, 44 everywhere would not fit 800 px for store managers) |
| Gap between items | `2px` (as `.menu-list`) |
| Padding | `0 0.75rem`; icon column `1.25rem` wide, `gap: 0.7rem` |
| Radius | `--fs-radius-sm` |
| Typography | `0.92rem` / weight 600 / `--fs-text-muted` (header link values); labels may **wrap to two lines**, never ellipsis (the overflow audit flags clipped text; wrapped is readable) |
| Icon | `.pi` 1.05rem, same colour as the text |
| Hover | background `--fs-surface-muted`, text `--fs-text` |
| Active (`aria-current="page"`) | background `--fs-primary-soft`, text and icon `--fs-primary-strong`, weight 700, plus a 3 px bar on the left edge in `--fs-primary` (`::before`, inset 0.4rem top/bottom, radius 999px) — the bar survives the rail, where the pill is only 44 px wide |
| Focus | the global `:focus-visible` ring (3 px, primary 45 %, offset 2 px); the list has `padding: 0.25rem` so the ring is never clipped by the scrolling list (the header comment on `.hdr__nav` applies) |
| Disabled | none — items that are not allowed are not rendered |

### 3.3 Badges

- Count badges: `fs-nav-badge` as is (`--fs-danger` pill, 1.15rem, white 0.7rem bold, `99+`). Expanded:
  trailing, `margin-inline-start: auto`, so the label never moves when the count arrives. Rail: `[overlay]`,
  top-right of the icon tile (`top: 0.3rem; inset-inline-start: calc(50% + 0.35rem)`).
- Bells: same pill on the bell item (today `bells.badge`).
- "What's new": the existing 0.55rem dot in `--fs-danger` at the trailing end of the row.
- Section headings with a hidden badge: when a closed section contains an item with a count (e.g. Postfächer
  never — it is in the daily group — but a future one), the heading shows the dot from `.hdr__more-dot`.

### 3.4 Section headings

`0.72rem`, weight 700, uppercase, `letter-spacing: 0.06em`, `--fs-text-muted`, padding `1rem 0.75rem 0.35rem`
(the drawer's `.mm__heading`). Collapsible headings are buttons with a `pi-chevron-down` (0.7rem) that rotates
−90° when closed; the whole row is the target (36/44 px). In the rail the section becomes one 44 px tile with
the section icon (`pi-users` Community, `pi-briefcase` Verwalten, `pi-cog` Orga) and a small chevron-right.

### 3.5 Context switcher, search, user block

- Switcher: a full-width row styled like an item (36/44 px, `pi-map-marker`, region name with ellipsis —
  deliberate truncation, full name in `aria-label` "Bezirk: Zürich Stadt – wechseln"), chevron right-aligned.
  Popover (Optimus `p-popover`, lazily loaded as today): list `role="menu"` with `menuitemradio`, home region
  first with the "Stammbezirk" hint, then regions, a separator and the heading "Arbeitsgruppen" with the groups;
  from 8 entries a filter field (`p-inputtext`, `shell.regionSwitcher.filter`); `max-height: 60vh`.
- Search: the existing `fs-global-search` input, full width, `--fs-surface-muted` background, 36/44 px.
- User block: 3rem avatar (`size="md"`), name 0.92rem 600, role 0.78rem `--fs-text-muted`; the row is a button
  with the popover opening upwards (`p-popover` positions itself). Rail: avatar only (2rem), centred.

### 3.6 Dark mode

Nothing is hard-coded: `--fs-surface` #1a201a sidebar on `--fs-bg` #111611, border `--fs-border` #2c352b,
active pill `--fs-primary-soft` (14 % green) with `--fs-primary-strong` #82d59b text. One correction to the
existing badge: `fs-nav-badge` and the tab-bar badge use white text on `--fs-danger`, which in dark mode is
#f08a80 — 3.3:1 for an 11 px bold number. The number is `aria-hidden` (the count is duplicated for screen
readers), but for readability the dark badge gets `#1a201a` text through a new token `--fs-on-danger`
(white in light mode). The mockup shows both themes.

### 3.7 New tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `--fs-sidenav-w` | `16.5rem` | same | expanded width |
| `--fs-sidenav-rail-w` | `4.5rem` | same | rail width |
| `--fs-sidenav-tile` | `2.5rem` (`2.75rem` on `pointer: coarse`) | same | rail tile size |
| `--fs-sidenav-x` | set by the shell: `0px` / rail / full | same | the column the content layout and fixed overlays (toasts, realtime chip, bell panel) offset by |
| `--fs-on-danger` | `#ffffff` | `#1a201a` | text on `--fs-danger` badges |

`--fs-header-h` stays defined (4rem) but the shell sets it to `0px` in sidebar mode, so the ten sticky
consumers (`sticky-offset.directive`, bell panel, map splits, editors) keep working without edits. The brand
row of the sidebar uses the literal 4rem.

---

## 4. States

| State | Behaviour |
|---|---|
| Badges loading | `NavBadgesService` loads its stores after the first page (decision 84); until then counts are 0 and no badge renders. The trailing slot is reserved by layout (label `flex: 1`), so the row never shifts when a count arrives; no skeleton — a skeleton pill would suggest a count that may well be 0 |
| Feature switched off | the item is not rendered (`visible` is false); a section that loses its last item disappears with its heading; if the user is on the page that got switched off, the page shows its 404 `FEATURE_DISABLED` state and no sidebar item is current (the C11 orga walk: "baskets off → the member loses nav entry and page" still holds) |
| Role without admin items | only the daily group and Community render; no empty headings, no "Verwalten"; the rail is daily icons + Community icons |
| List taller than the viewport (expanded) | measured in the mockup at 1280×800 with a mouse: 535 px of list between search and bottom block (511 px on touch). Newcomer 535 px (fits exactly), foodsaver 592 px, ambassador 858 px, orga 902 px with "Orga" closed. So every role but the newcomer scrolls the list a little at 800 px, and everyone does on a 1366×768 laptop. Design for it: top and bottom blocks pinned, the list scrolls with a fade at the hidden edge (vertical `ScrollHintDirective`), the current item is scrolled into view (`scrollIntoView({ block: 'nearest' })`) on navigation; the daily group is always at the top of the list, so the daily tasks never need a scroll. Not chosen: closing Community by default for foodsavers (would fit 800 px but hides "Karte" and "Bezirke & Gruppen" behind a click — open question 9) |
| Long labels | expanded label room = 264 − 2×12 (padding) − 2×12 (item padding) − 20 (icon) − 11 (gap) − 24 (badge slot) ≈ 173 px. Longest strings: de "Einstellungen & Schalter" (23 chars), "Unzustellbare E-Mails" (21), "Einführungsabholungen" (21); gws "Iifüehrigsabholige" (18), "Unzuestellbari E-Mails" (22); fr "Collectes d'introduction" (24), "Catégories de commerces" (23); it "Impostazioni e opzioni" (22). Measured in the mockup (system font, 0.92rem): every de and gws label fits on one line; in fr "Catégories de commerces" wraps to a second line, the others fit. Wrapping is the designed behaviour (no `white-space: nowrap`, no ellipsis; the row grows), so nothing is cut and the overflow audit stays clean. The mockup has a "Längste (fr)" toggle that swaps in the fr strings |
| Many regions | the switcher row shows the active region only; the popover lists all units with the home region first and groups in their own section; a filter field from 8 entries, 60 vh scroll. A member of 30 units sees a searchable list, never 30 sidebar rows |
| Region of the page ≠ active region | hint line under the switcher ("Seite: Winterthur") |
| Overlay open (48–80rem) | the content stays in place (no shift), the sidebar casts `--fs-shadow-lg`; Escape/outside/navigation close it |
| Offline | the offline banner stays at the top of the content column; the sidebar is static markup and keeps working |
| `chrome: minimal` / `none` | no sidebar (login, surveys, consent keep their minimal top header as today) |
| Journey open (newcomer) | "Mein Einstieg" shows a 3 px meter under its label (`--fs-primary` on `--fs-border`) and "3/6" trailing; the journey bar stays at the top of the content column |

---

## 5. Mockup

`sidenav-mockup.html` (inline CSS/JS, no CDN, inline SVG icons standing in for PrimeIcons). Toggles on the
page: role (Foodsaver:in / Botschafter:in), state (ausgeklappt / Rail / Rail + Flyout), theme (hell / dunkel),
labels (de / längste fr). The content area shows the store page "Bäckerei Sonnenberg" (fictional) with its tab
strip, to show the nested context next to the global sidebar. Popovers for the region switcher, the user menu
and the Verwalten flyout open on click. German labels are the real `nav.*` strings from `core/i18n/de.json`.

---

## 6. Implementation sketch (later phase)

### 6.1 Components and services

| Piece | Change |
|---|---|
| `layout/nav.config.ts` | `NavItem.section?: NavSection` (`'daily' \| 'community' \| 'manage' \| 'orga'`, default `daily`), `NavItem.activeMatch?: string`, `NAV_SECTIONS` (order, labelKey, icon), `sidenavSections(ctx)` → `{ section, items }[]` (visible, declared order, secondary last via `orderHeaderNav`). `orderHeaderNav`/`splitHeaderNav`/`measureNav` stay for the visitor header until it is simplified |
| `layout/sidenav.component.ts` (new, `fs-sidenav`) | brand, toggle, `fs-region-switcher [variant]="'sidenav'"`, `fs-global-search`, sections, flyouts, "Was ist neu", `fs-user-menu [variant]="'sidenav'"`. OnPush, no popover in the start-up bundle (disclosures only; the switcher/user popovers stay `@defer`red as today) |
| `layout/sidenav-state.service.ts` (new) | signals `mode: 'phone' \| 'rail' \| 'sidebar'` (from `matchMedia('(min-width: 48rem)')`, `'(min-width: 80rem)'`), `collapsed` (user choice, `fs.nav`), `overlayOpen`, `sections` open map (`fs.nav.sections`), `railFolded` (how many sections fold, measured by a `ResizeObserver` on the rail like `HeaderComponent.measureNav`), `effectiveWidth`; pure functions `resolveMode(width, choice)` and `foldSections(sections, available, tile)` unit-tested |
| `shared/scroll-hint/scroll-hint.directive.ts` | vertical axis (`data-more-start`/`data-more-end` on the scrolling list, mask top/bottom); the horizontal behaviour is unchanged |
| `layout/shell.component.ts` | grid with the sidebar column when `chrome === 'full' && isAuthenticated && mode !== 'phone'`; otherwise the current `fs-header`; sets `--fs-sidenav-x` and `--fs-header-h: 0px` on the host in sidebar mode; the `fsJourneyBar` slot moves from the header to the top of the content column |
| `layout/header.component.ts` | renders only for visitors on desktop and for everybody on phones; the measured "More" logic can be removed once visitors have one item (follow-up, not required) |
| `layout/region-switcher.component.ts` | `variant` input (`header` \| `sidenav`), groups from `session.units` (`type === 'WORKING_GROUP' && member`), filter field from 8 entries, page-region hint |
| `layout/user-menu.component.ts` | `variant` input; language and theme rows reuse `LanguageMenuComponent`/`ThemeMenuComponent` content as nested radio lists |
| `features/bells/components/bell-button.component.ts` | `variant="sidenav"` (labelled row, panel anchored to the item); `bells.button`/`bells.badge` test ids unchanged |
| `features/search/components/global-search.component.ts` | `inline` (field) vs `icon` (rail) presentation; the search index loads on first focus as today |
| `nav-badge.component.ts` | `--fs-on-danger` text colour |
| `layout/i18n/*.json` ×5 | `layout.sidenav.collapse`, `layout.sidenav.expand`, `layout.sidenav.overlay` (name of the overlay, "Navigation"), `layout.header.groups` ("Arbeitsgruppen"), `layout.header.pageRegion` ("Seite: {name}"), `layout.header.regionFilter`; `core/i18n/*.json`: `nav.section.daily`, `nav.section.community`, `nav.section.manage`, `nav.section.orga` |
| Reused unchanged | `NavContextService` (`main()`, `user()`, `context()`), `NavBadgesService`, `fs-nav-badge`, `fs-brand`, `RegionContextService`, `ThemeService`, `I18nService`, `LazyPopover`, `core/storage.ts`, `StartupService.pageShown` gating |

### 6.2 Test ids (`nav.*`, `shell.*`)

Unchanged: `nav.<id>` on every main link (reachable in the sidebar, the flyout or the overlay), `nav.badge.<id>`,
`nav.user.<id>`, `nav.tab.<id>`, `nav.drawer.<id>`, `nav.footer.<id>`, `shell.brand`, `shell.regionSwitcher`,
`shell.region.<slug>`, `shell.userMenu`, `shell.userMenu.logout`, `shell.userMenu.role`, `shell.whatsNew`,
`shell.skipLink`, `bells.button`, `bells.badge`.

New:

| Test id | Element |
|---|---|
| `nav.sidenav` | sidebar root; `data-mode="sidebar\|rail\|overlay"` |
| `nav.sidenav.toggle` | collapse/expand button (`aria-expanded`) |
| `nav.sidenav.close` | close button of the overlay (48–80rem) |
| `nav.section.<section>` | section heading button (`aria-expanded`), `<section>` ∈ daily (hidden heading, no button), community, manage, orga |
| `nav.section.<section>.list` | the `<ul>` |
| `nav.flyout.<section>` | rail tile that opens the section flyout (`aria-expanded`); `nav.flyout.<section>.list` |
| `nav.section.<section>.unread` | dot on a closed heading with hidden unread counts |
| `shell.group.<slug>` | working group entries of the switcher |
| `shell.regionSwitcher.filter` | filter field (from 8 units) |
| `shell.regionSwitcher.pageRegion` | the "Seite: …" hint |
| `shell.userMenu.language.<lang>`, `shell.userMenu.theme.<mode>` | radios inside the user menu (the header's `shell.language.<lang>` / `shell.theme.<mode>` keep working on phones and for visitors) |
| `nav.onboarding.progress` | the meter under "Mein Einstieg" |

Removed on signed-in desktop: `nav.more`, `nav.more.list`, `nav.more.unread`, `shell.searchToggle` (phone only
now), `shell.languageMenu`/`shell.themeMenu` (phone and visitors only).

### 6.3 e2e helpers and specs

- `e2e/fixtures/nav.ts` `openNav(page, id)`: desktop branch becomes: link visible → click (1); else read
  `data-nav-section` from the link; in sidebar mode click `nav.section.<s>` when `aria-expanded="false"` then
  the link (2); in rail mode click `nav.flyout.<s>` then the link (2). Below 80rem (no project today) the
  helper opens the overlay first. Return value (clicks) unchanged in meaning. `PHONE_MAX` unchanged.
- New helper `setSidenav(page, 'rail' | 'open')` (writes `fs.nav` before `goto`, like `THEME_STORAGE_KEY`
  in `fixtures/i18n.ts`) for the screenshot specs and D4.
- `tests/shell/d4-shell-navigation.spec.ts`: "desktop header navigation" → "desktop sidebar": asserts
  `aria-current` instead of `hdr__link--active`; the "More" test becomes "Orga section closed by default, opens
  when its page is current, remembered after toggle"; a new rail test at 1024×768 (toggle, flyout, Escape
  returns focus, overlay closes on navigation); skip link test unchanged.
- Persona specs (`tests/personas/*`): click budgets of the ambassador drop by one for verification and
  newsletters (Verwalten is open); the budgets are lowered deliberately in the same change (they only fail
  when a walk gets longer).
- `a11y/pages` axe sweep: covers the sidebar automatically; add the open overlay and an open flyout as states.
- Screenshot specs: every desktop baseline changes (decision 132 regenerates them at the end of the phase,
  never per task). New `screenshots/layout.screen.ts`: sidebar expanded and rail × newcomer / foodsaver /
  ambassador / orga, dark variant for foodsaver and orga, switcher open with the seed's 8 units (bot: kanton,
  Stadt Zürich, PR group, WELCOME…), user menu open, Verwalten flyout open in the rail, fr labels. The
  `screenshots-mobile` project is untouched.
- Unit specs: `nav.config.spec` (`sidenavSections` order, sections per seed role, `activeMatch`),
  `sidenav-state.service.spec` (mode resolution, persistence, forced-open section), `sidenav.component.spec`
  (toggle names, `aria-current`, flyout Escape), `shell.component.spec` (header vs sidebar per chrome/mode).

### 6.4 Risks

| Risk | Mitigation |
|---|---|
| `--fs-header-h` is the sticky offset in ten places (bell panel, map splits, editors, `StickyOffsetDirective`) | the shell sets it to `0px` in sidebar mode; one pass over the consumers with the overflow audit (`expectNoOverflow`) |
| Content column at 1280 px is 1016 px: admin tables (users, uploads, jobs) and the baskets/FSP map splits get narrower | tables already scroll in `.table-wrap`; the split layouts switch to stacked below a column width, not a viewport width (container query on the content column) |
| Persona click budgets and `openNav` change together | one commit: helper, budgets, D4; run chromium + mobile-chrome personas before and after |
| Start-up bundle (D11.5.3): the sidebar is in the shell | disclosures only, popovers stay `@defer`red; measure with the Lighthouse script (`e2e/scripts/lighthouse.mjs`) |
| Duplicate chrome (header and sidebar both in the DOM) would break Playwright strict mode on shared test ids | render one or the other from the `mode` signal (`matchMedia` at boot; signed-in pages are not prerendered) |
| Focus stops before the content grow for orga | sections closed by default for Orga; skip link; optional `[` shortcut |
| First paint with the wrong width (flash) when `fs.nav` says rail | read storage in the service constructor, apply `data-nav` before the first render, no transition on the first frame |
| Phone header and sidebar diverge over time (two places for search/language/theme) | both read `nav.config` and the same child components; a shell spec asserts the item set of header drawer and sidebar is identical per role |
| Dark badge text contrast | `--fs-on-danger` |

---

## 7. Alternatives considered

1. **Keep the top header, improve "More" into a grouped mega-menu.** Labelled, sectioned, keyboard-friendly.
   Rejected: it does not change the ceiling of 5–7 inline items at 1280 px; verification and newsletters stay
   one click away for ambassadors (round-3 friction); orga's 25 items remain a long list in a popover; the
   header still measures and reshuffles on every resize and language change.
2. **Sidebar plus a permanent top app bar** (search, region, bells, user in a 56 px bar; sidebar for links only,
   the GitLab/Jira layout). Rejected for now: two chromes cost 56 px of an 800 px laptop screen and split the
   "where is my stuff" question in two; the region switcher next to the links is where the context belongs.
   This is the fallback if the owner wants a page title bar or breadcrumbs across the top; everything in
   section 1.4 then moves up without changing the sidebar.
3. **Sidebar with contextual subtrees** (the current store or region expands under "Betriebe" / "Bezirke &
   Gruppen" with its tabs, Notion-style). Rejected: members use several stores and regions, so the tree would
   grow or jump around on every page change; the tab strips exist, are tested (S15, R2) and work on phones;
   `openNav` and the screenshot matrix would double.
4. **Sidebar for visitors too.** Rejected: the landing and public region pages are brochure pages with one nav
   item; a rail would frame them like an app and the hero layouts assume the full width.

---

## 8. Open questions for the owner

1. **Default at 1280 px (80rem):** expanded (proposed, content 1016 px) or rail with expand-on-demand? The
   Playwright `chromium` project runs at 1280×800, so this decides what every desktop baseline shows.
2. **Visitors keep the top header** (proposed) — or should the public pages get the sidebar as well?
3. **Journey bar on desktop:** top of the content column (proposed) or only the meter in the sidebar item?
4. **Section names** "Community / Verwalten / Orga": glossary check in the five languages (decision 124 style);
   "Verwalten" vs "Bezirk verwalten"; is "Orga" fine in fr/it?
5. **Bells:** panel anchored to the sidebar item (proposed, no page) or make `/bells` a page now and drop the
   panel on desktop?
6. **Small "Verwalten"/"Orga" for non-orga roles:** store chains sit in Verwalten for every foodsaver, quiz
   editing in Orga for working-group admins. Fine as is, or move `storeChains` to Community and give
   `adminQuizzes`/`adminContent` the Verwalten section when the user is not orga?
7. **Shortcuts:** `[` for the toggle and `Ctrl/⌘ K` for the search — wanted, or leave out (no shortcuts exist
   in the app today)?
8. **Persist per device** (proposed, `localStorage`) or per account (user setting, synced across devices)?
9. **Community open by default?** Open (proposed) means the expanded list scrolls by about 60 px for a
   foodsaver at 1280×800 (the daily group is always visible); closed would fit without scrolling but puts
   "Karte" and "Bezirke & Gruppen" one click away. The rail is unaffected (it folds by measurement).
