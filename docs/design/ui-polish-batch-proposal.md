# UI polish batch: Pickups page and home screen — design proposal

Status: design for `docs/tasks/2026-11-04-ui-polish-batch.md` (#3 Pickups page, #4–#9 home, #12 free-slot chip).
Owner pre-approved implementation; decisions below are the designer's unless marked as a question.
Mockups: `pickups-page-mockup.html`, `home-mockup.html` (toggles: theme, desktop/phone 412, role, state, today/proposal).

## 1. Problem
- `/pickups` and `/pickups/mine` are two pages that answer one question ("what am I doing, where am I needed?") and
  link to each other with buttons that look like primary actions.
- The home screen explains the role in a sentence nobody reads, nags every foodsaver to "complete your profile"
  even when it is complete, spends the right column on three settings links, and lists the next pickups as
  "04.11.2026, 18:00" text with an hourglass that has no label.
- A pickup with four free slots renders four dashed chips; only the first says anything.

## 2. States
| Piece | Loading | Empty | Error | Data |
|---|---|---|---|---|
| Pickups page, each section | `skeleton="list"` (3 / 4 rows) | full `fs-empty-state` (mine: "No pickups planned – See below where slots are still free." with a link to `#options`; options: "All covered") | `fs-error-state` + retry per section | first 5 / 10 cards, then "Show N more" |
| Home "My next pickups" | compact skeleton, 3 tile rows | compact empty "No pickups planned" + "All my pickups" link stays | compact error + retry | 3 tile rows |
| Home "Help needed soon", "Waiting for you", "Upcoming events", "Your stores" | compact skeleton (2–3 rows) inside the card | **card hidden** (`:empty` rule of the dashboard grid) | compact error + retry, card stays | rows |
| Next step | — | **card hidden** when nothing is missing | — | passkey (foodsharer) / profile (no avatar or no phone) |

## 3. Visual spec (tokens from `client/src/styles.scss`)
**Pickups page** (`:host` width `min(100%, 56rem)` as today)
- `fs-page-header` title "Pickups", subtitle, icon `pi pi-calendar`; actions: History (`pi pi-history`) and Day overview
  (`pi pi-list`) as `p-button [text] severity="secondary"`. On phones they wrap under the title (existing header rule).
- Sections: `h2` 1.2rem/700 with icon (`pi-calendar` / `pi-plus`), muted count (0.95rem/600, tabular) after the title,
  options subtitle 0.92rem `--fs-text-muted`. Section gap 2rem. `id="mine"` / `id="options"` for the fragments.
- Cards: exactly the existing `.item` / `.option` markup (`fs-card`, 0.9rem 1rem padding, grid `1fr auto`), cleaning
  shift keeps the 4px `--fs-primary` left border; state line `Confirmed` (`--fs-primary-strong`, check) / `Unconfirmed`
  (`--fs-warning-text`, hourglass); options keep "1 of 2 slots free" + `fs-pickup-summary`.
- "Show N more": `p-button [text] icon="pi pi-angle-down"`, left-aligned (full width on phones). Reveals the next
  5 / 10; focus moves to the first newly shown store link. No "show less".
- Today view in the mockup: old options page header with the "My pickups" button.

**Home**
- Grid: desktop `minmax(0,1.25fr) minmax(0,1fr)` as today. Left: welcome, next step, My next pickups, Help needed soon,
  What's happening. Right: Waiting for you, Upcoming events, Your stores, Food baskets near you. Push opt-in and bounce
  banner stay where they are. Phone: wrappers `display: contents`, cards ordered by `order` (see mockup note).
- Welcome: unchanged minus `welcome__hint`; role chip and region chip stay.
- New shared `fs-date-tile` (`shared/date-tile/`): width `--fs-date-tile: 3.25rem`, min-height 3.4rem,
  `--fs-radius-md`, bg `--fs-primary-soft`; weekday 0.66rem/700 uppercase `--fs-primary-strong`, day 1.35rem/800
  `--fs-text`, month 0.66rem/650. Today: bg `--fs-primary`, all text `--fs-on-primary`. `aria-hidden`; the row carries a
  `<time datetime>` whose visually hidden prefix reads the full date ("Wed 4 Nov, 18:00").
- Tile rows (`.row`): grid `var(--fs-date-tile) minmax(0,1fr)`, gap 0.8rem; title 700 ellipsis; meta 0.88rem muted
  with time, kind ("Cleaning shift" + `pi-sparkles`), state (check/hourglass + word).
- Waiting for you: rows 44px min, `wait__icon` 2.25rem on `--fs-primary-soft` (warning-soft for "still unconfirmed"
  / "application pending"), count pill `--fs-primary`/`--fs-on-primary` (warning variant `--fs-warning`), chevron.
  Rows (in this order, each only when count > 0): store invitations → `/stores/mine`; unread conversations →
  `/messages`; your unconfirmed pickups → `/pickups#mine`; your pending applications → `/stores/mine`.
- Help needed soon: 3 rows store / "Wed, 4 Nov, 18:30" / "2 of 2 slots free" + small primary Sign up (same dialog as
  the page); link "All {count} pickups with free slots" → `/pickups#options`. **The free-slot link leaves "My next
  pickups"**, which then only needs `listMyPickups` + cleaning shifts.
- Upcoming events: 3 tile rows, time · location or "Online" · status word (`events.status.*`, check for ACCEPTED/
  MAYBE, hourglass for INVITED); link "All my events" → `/events/mine`.
- Your stores: stores from `listMyStores` with `membership` MEMBER/COORDINATOR and `freeSlotCount > 0`, sorted
  RED → GREEN, max 5; row: name → `/stores/:id/pickups`, dot (`--fs-danger`/`--fs-warning`/`--fs-accent`/`--fs-primary`
  by `freeSlotStatus`) **plus** the words "N free slots in the next 7 days", "Next Thu 18:30"; link "All my stores".
- Dark mode: tokens only; the mockup's Dark toggle shows every card.

**Free-slot chip (#12, `fs-pickup-slot`)**: one `.slot--free.slot--labelled` per pickup. Clickable: `pi-plus` + "Sign
up", accessible name `shared.pickupSlot.free` + ", N free". Not clickable: "N free" (`shared.pickupSlot.freeShort`),
`disabled`. Nothing else in the row changes.

## 4. Labels and i18n keys (de / en; implementer adds gws, fr, it)
| Key | de | en |
|---|---|---|
| `pickups.page.title` | Abholungen | Pickups |
| `pickups.page.subtitle` | Deine kommenden Abholungen – und wo deine Betriebe noch Leute brauchen. | Your upcoming pickups, and where your stores still need people. |
| `pickups.page.showMore` | {count} weitere anzeigen | Show {count} more |
| `pickups.mine.emptyText` (changed) | Schau unten, wo noch Plätze frei sind. | See below where slots are still free. |
| `dashboard.help.title` | Bald Hilfe gesucht | Help needed soon |
| `dashboard.help.all` | Alle {count, plural, one {# Abholung} other {# Abholungen}} mit freien Plätzen | All {count, plural, one {# pickup} other {# pickups}} with free slots |
| `dashboard.waiting.title` | Wartet auf dich | Waiting for you |
| `dashboard.waiting.invitations` | {count, plural, one {Einladung in ein Betriebsteam} other {Einladungen in Betriebsteams}} | Store {count, plural, one {invitation} other {invitations}} |
| `dashboard.waiting.unread` | Ungelesene {count, plural, one {Unterhaltung} other {Unterhaltungen}} | Unread {count, plural, one {conversation} other {conversations}} |
| `dashboard.waiting.unconfirmed` | {count, plural, one {Deine Abholung ist} other {Deine Abholungen sind}} noch unbestätigt | Your {count, plural, one {pickup is} other {pickups are}} still unconfirmed |
| `dashboard.waiting.applications` | {count, plural, one {Deine Bewerbung ist} other {Deine Bewerbungen sind}} noch offen | Your store {count, plural, one {application is} other {applications are}} pending |
| `dashboard.events.title` / `.all` | Nächste Termine / Alle meine Termine | Upcoming events / All my events |
| `dashboard.stores.title` / `.all` | Deine Betriebe / Alle meine Betriebe | Your stores / All my stores |
| `dashboard.stores.freeSlots` | {count, plural, one {# freier Platz} other {# freie Plätze}} in den nächsten 7 Tagen | {count, plural, one {# free slot} other {# free slots}} in the next 7 days |
| `dashboard.stores.next` | Nächste {date} | Next {date} |
| `dashboard.stores.open` | Abholungen | Pickups |
| `shared.pickupSlot.free` (changed) | Freier Platz – eintragen, {count} frei | Free slot – sign up, {count} free |
| `shared.dateTile.today` (vh text) | Heute | Today |

Removed: `pickups.dashboard.free`, `dashboard.page.roleHint.*`, `dashboard.page.quickLinks`, `dashboard.page.sessionsText/activityText/settingsText`, `dashboard.page.admin.*`, `dashboard.page.step.activity.*`.
Reused: `pickups.mine.*`, `pickups.options.*`, `pickups.history.link`, `pickups.agenda.title`, `pickups.cleaning.label`, `pickups.tab.state.*`, `events.status.*`, `shared.pickupSlot.freeShort`.

## 5. Accessibility
- Every state has icon + word (Confirmed/Unconfirmed, status dots + text, warning pills + text); nothing is colour-only.
- Sign up / Sign out buttons get an `aria-label` with store and time ("Sign up: Hiltl, Wed, 4 Nov, 20:30").
- "Show N more": `aria-controls` on the list; focus moves into the revealed items.
- Date tiles are `aria-hidden`; the `<time>` reads the full date. Sections and cards use `aria-labelledby`.
- Count pills carry the number in text (no `aria-label` needed). Rows in "Waiting for you" are links, 44px on touch.

## 6. Mobile (412px, touch)
- Pickups: header actions wrap under the title; cards stay one row (text left, action right) as today; "Show N more"
  is full width, 44px. The fragment `#mine` lands on the first section (top of page).
- Home: single column, order welcome → next step → Waiting for you → My next pickups → Help needed soon →
  Upcoming events → Your stores → Baskets → What's happening (the open-ended feed is last). Tile rows wrap their meta
  line; store names ellipsise. No horizontal overflow (checked in the mockup frame).

## 7. Test ids
Keep: `pickups.mine.state/pickup/cleaning/signOut/cleaning.signOut/confirmed/unconfirmed/history/agenda`,
`pickups.options.state/option/free/summary/signUp`, `dashboard.pickups` + `.state/.pickup/.cleaning/.all`,
`dashboard.page.welcome/name/role/homeRegion/nextStep/nextStepAction`, `dashboard.activity.*`, `dashboard.baskets.*`.
New: `pickups.mine.count`, `pickups.mine.showMore`, `pickups.options.count`, `pickups.options.showMore`,
`dashboard.pickups.list/confirmed/unconfirmed`, `dashboard.help` + `.state/.list/.option/.signUp/.all`,
`dashboard.waiting` + `.state/.invitations/.unread/.unconfirmed/.applications`, `dashboard.events` + `.state/.list/.event/.all`,
`dashboard.stores` + `.state/.list/.store/.all`.
Removed: `pickups.options.mine`, `pickups.mine.options`, `dashboard.pickups.free` (→ `dashboard.help.all`),
`dashboard.page.roleHint`, `dashboard.page.adminCard`, `dashboard.page.sessionsLink/activityLink/settingsLink`.
`dashboard.page.nextStep` is now conditional: e2e that expect it for a complete seed user must use a user without
phone or assert absence.

## 8. Rejected alternatives
1. **Tabs "Mine | Where can I help?"** on one page: hides the second answer behind a click, and the owner chose (b).
2. **Interleaving Help needed soon into My next pickups** (one chronological list with "free" rows): compact, but
   mixes "I committed" with "I could", and the sign-out and sign-up actions would sit in the same list.
3. **Keeping the free-slot count link in My next pickups** next to the new Help card: the same number twice on one
   screen; moved into the Help card's footer instead.

## 9. Questions (none blocking; recommendation in bold)
1. "Waiting for you" for store managers: applications to review in *their* stores would be the truly actionable row,
   but there is no cheap endpoint (per-store team calls). **Ship without it; note as follow-up.**
2. "Your stores" and "Help needed soon" overlap in meaning. **Keep both as planned; Your stores is per store and only
   for RED/ORANGE/YELLOW/GREEN with free slots, max 5. Drop it later if it reads as noise.**
3. "Show N more" with no "show less". **Fine: the page is scrolled, not paged.**
4. Date tile with 3 lines (weekday / day / month) vs 2 (weekday / day). **3 lines: pickups and events cross month
   boundaries within the 14-day window.**
