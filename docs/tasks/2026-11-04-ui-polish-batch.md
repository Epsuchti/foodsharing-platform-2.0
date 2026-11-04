# UI polish batch (owner feedback 2026-11-04)

Status: approved by the owner in advance. **Designer first** for the Pickups page and the home screen (step 2 below), then implementer.
One branch, one PR. Owner screenshots 1–10 are described per item.

## Goal
Fix 15 owner points: bell icons, basket form, Pickups page redesign, home screen rework, stores list, pickup slots, chat mentions, menu visibility.

## Decisions (owner answers; record as decision 159 on merge)
1. Pickups: **one page** `/pickups`: "My pickups" on top (first 5, "show more"), "Where can I help?" below (first 10, "show more"). No tabs. History and agenda become header actions. `/pickups/mine` stays as a deep link (redirect to `/pickups#mine`).
2. Home: role subtitle removed for **all** roles; "Your next step" only when something is missing (profile: no avatar or no phone; foodsharer: passkey); ambassador "activity" step and the stale admin placeholder card removed; the whole quick-link block (devices, my activity, settings) removed. New cards (existing endpoints only) listed under Changes.
3. Menu: server-computed nav capabilities in `SessionInfo` (precedent: `mailboxes`, decision 149). Betriebsketten from STORE_MANAGER up.
4. My stores: no cooperation/team badges (incl. "Urgently looking for people"); with "show inactive" on, inactive stores keep their cooperation badge. Application withdraw and invitation accept/decline sit inside the card. One free-slot chip per pickup (overrides M7 R2 #16). Basket form: alignment fix + light polish without designer.
5. Chat mention: composer shows plain `@Name`; tokens built on send; edit mode converts tokens back.
6. Bell icons: client maps Lucide names to `@openng/icons` names (same approach as decisions 95/111).

## Scope
In: the 15 points below. Out: new server endpoints for the home screen; rich mention chips; changing what the server allows (only what the menu shows); forum composer.

## Changes

### 1. Bell icons (#1)
Cause: `NotificationType` icons are Lucide names; the font lacks ~half (`mail`, `calendar-check`, `calendar-x`, `store`, `badge-check`, `alarm-clock`, `message-square`, `map-pin`, `shopping-basket`, `vote`, `award`, `bot`, `newspaper`, `plug-zap`, `at-sign`, `eye-off`, `book-open`, `clipboard-list`, `scale`, `life-buoy`).
- New `client/src/app/core/icons/lucide-icons.ts`: `LUCIDE_TO_PI` map + `piIcon(name, fallback)`. Suggested: mail→envelope, calendar-check→calendar-clock (or check-circle), calendar-x→calendar-times, store→shop, badge-check→verified, alarm-clock→clock, bot→microchip-ai, newspaper→file, plug-zap→bolt, message-square→comment, at-sign→at, eye-off→eye-slash, vote→chart-bar, shopping-basket→shopping-cart, map-pin→map-marker, book-open→book, clipboard-list→list-check, scale→sliders-h, award→trophy, life-buoy→question-circle. Names that exist pass through; unknown → `bell`.
- `bells/components/bell-item.component.ts` `bellIconClass` uses it. Let `profiles.util.achievementIcon` reuse the map if it's trivial (keep its trophy fallback).
- Unit spec: every icon string the server sends (copy the list from `NotificationType.java` + explicit `.icon("…")` calls) resolves to a class that exists in the font list.

### 2. Basket form (#2)
`baskets/pages/basket-form-page.component.ts`: `.form-grid { align-items: start }`, `.field { align-content: start }`; weight input with a "kg" suffix (`p-inputgroup`), drop "kg" from the label in all 5 languages; food types as toggle chips (hide native checkbox visually, keep it focusable, visible focus ring); consistent card spacing.

### 3. Pickups page (#3, designer)
- `pickups/pages/pickup-options-page.component.ts` becomes the combined page (or a new `pickups-page.component.ts` that reuses the list markup of `my-pickups-page` and `pickup-options-page`, both moved to components `my-pickups-list` / `pickup-options-list` in `pickups/components/`). Two `loadResource`s, each with its own `<fs-data-state>`. Sign-out and sign-up flows stay as they are (`runMutation`, `PickupSignupDialogComponent`).
- "Show more" is client-side (`listMyPickups`, `listPickupOptions` return full lists).
- `pickups.routes.ts`: `pickups/mine` → `redirectTo: 'pickups'` with fragment `mine` (functional redirect). Update links in `my-next-pickups-card`, `pickup-history-page` back link, nav `activeMatch`.
- Keep the test ids `pickups.mine.*` / `pickups.options.*` on the parts.

### 4–9. Home screen (designer)
`dashboard/pages/dashboard-page.component.ts`:
- Remove `welcome__hint` (roleHint) and its keys `dashboard.page.roleHint.*`; remove the `.links` nav (sessions/activity/settings) and keys; remove `isAdmin` card.
- `nextStepFor` → `nextStepFor(user)`: foodsharer → passkey (as now); foodsaver+ → `profile` only when `!user.avatarUrl || !user.phone`; otherwise no card. Unit test the rule.
- `my-next-pickups-card.component.ts` (#8): date tiles (weekday + day, time, store, confirmed/waiting state, cleaning marker), per designer mockup.
- New cards in `features/dashboard/components/`, each `loadResource` + `<fs-data-state>`, hidden when empty unless the designer says otherwise:
  - **Help needed soon**: next 3 open pickups from `PickupsService.listPickupOptions` with Sign up (reuse signup dialog) and a link to `/pickups`. Verified foodsavers only.
  - **Waiting for you**: store invitations (`StoresService` my-stores list, invited group), unread conversations (`getConversationUnreadCount`), own unconfirmed slots (from `listMyPickups`), pending applications count. Each row links to its page.
  - **Upcoming events**: next 3 from `EventsService.listMyEvents`.
  - **Your stores** (planner's addition): store teams with free slots in the next 7 days, from the my-stores list (`freeSlotStatus`/`nextPickupAt`).
  - **Hygiene/quiz reminder** (planner's addition) only if the session/quiz status already gives an expiry date cheaply; otherwise skip and note it in the report.
- Keep nearby baskets, push opt-in, bounce banner, journey card, activity feed. Layout per mockup.

### 10–11. My stores (#10, #11)
`stores/pages/my-stores-page.component.ts`: `<li class="store-card">` gets the border; inside it the `<a class="store">` (main area) and the action row (withdraw / accept / decline). Remove `fs-cooperation-status-badge` / `fs-team-status-badge`, except the cooperation badge for inactive stores when "show inactive" is on.

### 12. Free slots (#12)
`shared/pickup-slot/pickup-slot.component.ts`: render one free chip: clickable → "+ Sign up" (aria label `shared.pickupSlot.free` + count); not clickable → "N free". Update `pickup-slot.component.spec.ts` (`keeps the free slot logic` expects one chip now, on purpose). Applies to cleaning shifts too.

### 13. Slot order (#13)
Already ordered by sign-up time (`SlotSchedule.merge`, `createdAt`). Add a tie-break by id (`thenComparing`) so ties are stable; extend `server/src/test/java/network/foodsharing/common/schedule/SlotScheduleTest.java`. Report to the owner that the order is sign-up time.

### 14. Chat mention (#14)
`messaging/message-body.ts`: `insertMention` inserts `@Name `; new `encodeMentions(text, picked: Map<name,id>)` (longest names first, only `@Name` followed by end/space/punctuation) and `decodeMentions(body)` → `{text, picked}`. `conversation-view.component.ts`: keep a `pickedMentions` signal, encode on send/edit save, decode when entering edit mode, reset on send/cancel. The edit "unchanged" check compares encoded text. Unit tests in `message-body.spec.ts`.

### 15. Menu visibility (#15)
Cause: `nav.config.ts` `isAnyAdmin` counts admins of any unit (Nina admins the working group "Betriebskoordination Zürich"), and `editsQuizzes` counts any working-group admin, while the server allows only admins of groups with a quiz kind.
- Contract (`openapi/schemas/auth.yaml`): `SessionInfo.capabilities: array of NavCapability` (enum: `QUIZ_EDIT`, `MANAGE_NEWCOMERS`, `VERIFY`, `MANAGE_MEMBERS`, `NEWSLETTERS`, `BLOG_MANAGE`, `CONTENT_EDIT`). Regenerate.
- Server: `SessionInfoBuilder.capability(...)`; one enricher per module (pattern: `mailbox/service/MailboxIntegrations.java`):
  - quiz: `QuizEditorService.mayEdit` for any `QuizKind`
  - region: `mayAdministrateRegion(home)` or CAN_MANAGE_USERS anywhere → newcomers, verification, members
  - newsletter: `NewsletterService` scope rule
  - content: blog write rule (`BlogRules`) → BLOG_MANAGE; PR function/ORGA → CONTENT_EDIT
  - Enrichers stay cheap (one query each).
- Client `nav.config.ts`: these items read `ctx.session?.capabilities`; `storeChains` visible from `Role.StoreManager` (+ verified). `isAnyAdmin` is no longer used by nav items; keep it only where other code needs it.
- Server tests: an enricher test per capability, with Nina (seed `…0009`) getting none of them; the controller/session test checks the field.

## UI
Visible changes everywhere above. **Designer mockups required before the implementer:** Pickups page (variant b only) and home screen (incl. new cards and next-pickups tiles) → `docs/design/pickups-page-mockup.html`, `docs/design/home-mockup.html`. Everything else: no mockup. All new text in de, en, gws, fr, it; `npm run i18n:check`.

### Design decisions (designer, 2026-11-04; spec and keys in `docs/design/ui-polish-batch-proposal.md`)
Pickups page (`/pickups`)
- One `fs-page-header`: title `pickups.page.title` "Pickups", subtitle `pickups.page.subtitle`, icon `pi pi-calendar`; actions History (`pi pi-history`, keeps `data-testid="pickups.mine.history"`) and Day overview (`pi pi-list`, keeps `pickups.mine.agenda`) as `p-button [text] severity="secondary"`.
- Two sections, each `<section id="mine|options" aria-labelledby>` with `h2` 1.2rem/700 + icon + muted count (`pickups.mine.count` / `pickups.options.count`), options subtitle below. Section gap 2rem. Each section: own `loadResource` + `<fs-data-state skeleton="list">`.
- Card markup is the existing `my-pickups` `.item` and `pickup-options` `.option` (moved to `pickups/components/my-pickups-list` and `pickup-options-list`), unchanged visually; the "My pickups" empty text changes to `pickups.mine.emptyText` "See below where slots are still free." with a `#options` link button.
- "Show N more": `p-button [text] icon="pi pi-angle-down"` label `pickups.page.showMore`, below the list, left-aligned (full width on phones), `aria-controls` on the `ul`; reveals the next 5 (mine) / 10 (options) and moves focus to the first revealed store link. No "show less".
- Fragments: `#mine`, `#options` (`scrollPositionRestoration`/`anchorScrolling` as configured; `/pickups/mine` → `/pickups#mine`). `my-next-pickups-card` "All my pickups" → `/pickups#mine`.
- Test ids: keep all `pickups.mine.*` / `pickups.options.*`; new `pickups.mine.showMore`, `pickups.options.showMore`, `*.count`; removed `pickups.options.mine`, `pickups.mine.options`.

Home (`dashboard-page`)
- Columns as today (`1.25fr / 1fr`). Left: welcome (no hint; role + region chips stay), push opt-in, next step (only when something is missing), `fs-my-next-pickups-card`, new `fs-help-needed-card`, activity feed. Right: new `fs-waiting-card`, new `fs-upcoming-events-card`, new `fs-your-stores-card`, nearby baskets. Phone: `.main, .side { display: contents }` and an `order` per card so the order is welcome → next step → Waiting for you → My next pickups → Help needed soon → Upcoming events → Your stores → Baskets → What's happening.
- New shared `shared/date-tile/date-tile.component.ts` (`fs-date-tile`, inputs `at`, `timeZone`): token `--fs-date-tile: 3.25rem`; weekday short / day / month short; `tile--today` filled `--fs-primary`/`--fs-on-primary`; `aria-hidden`, the row's `<time datetime>` carries a visually hidden full date. Used by the pickups card and the events card.
- `my-next-pickups-card`: tile rows (store link, time, "Cleaning shift" with `pi-sparkles`, state word + icon `pickups.tab.state.*`); only the "All my pickups" link remains; the options request and `pickups.dashboard.free` move to the Help card (`dashboard.help.all` → `/pickups#options`). e2e `a19-dashboard-pickups` changes accordingly.
- `fs-help-needed-card`: next 3 options with free slots, row = store link / `fsDate: 'dateTime'` / `pickups.options.free`, small primary Sign up (`PickupSignupDialogComponent`), footer link; verified foodsavers only; hidden when empty.
- `fs-waiting-card`: `forkJoin(listMyStores, getConversationUnreadCount, listMyPickups)`; rows only when count > 0, in this order: invitations (`membership === INVITED`) → `/stores/mine`; unread conversations → `/messages`; own unconfirmed pickups → `/pickups#mine`; own applications (`membership === APPLIED`) → `/stores/mine`. Row = icon tile, text, count pill, chevron; warning colour for the last two (plus the text). Hidden when all zero. `listMyPickups` may be shared with the pickups card through a page-level resource; two calls are acceptable.
- `fs-upcoming-events-card`: next 3 from `listMyEvents` with `startsAt >= now` (not WONT_JOIN), tile rows with time · location/online · `events.status.*` word + icon; link `/events/mine`; hidden when empty.
- `fs-your-stores-card`: `listMyStores` items with membership MEMBER/COORDINATOR and `freeSlotCount > 0`, sorted RED → GREEN, max 5; dot + "N free slots in the next 7 days", "Next {date}", link to `/stores/:id/pickups`; footer `/stores/mine`; hidden when empty.
- "Hidden when empty" cards: compact skeleton while loading, nothing when empty (the grid's `:empty` rule removes the gap), compact error + retry stays visible. Below-the-fold cards use `@defer (on viewport)`.
- `nextStepFor(user)`: foodsharer → passkey; otherwise profile only if `!avatarUrl || !phone`; else `null` and no card. Hygiene/quiz reminder: not in the mockup; add only if the session already carries the expiry (plan 4–9).
- Test ids: see proposal §7 (`dashboard.help.*`, `dashboard.waiting.*`, `dashboard.events.*`, `dashboard.stores.*`; removed `dashboard.pickups.free`, `dashboard.page.roleHint/adminCard/sessionsLink/activityLink/settingsLink`).

Free-slot chip (#12): one `.slot--free.slot--labelled` per pickup; clickable → `pi-plus` + "Sign up", accessible name `shared.pickupSlot.free` with the count ("Free slot – sign up, 3 free"); not clickable → `shared.pickupSlot.freeShort` "N free", `disabled`. Same for cleaning shifts.

## Tests
- New unit: icon mapping, `nextStepFor`, mention encode/decode, free-slot chip, nav capability predicates (`sidenav.component.spec.ts` / nav config spec), dashboard cards (DataState states).
- Server: `SlotScheduleTest` tie-break, enricher tests, session contract test.
- Changed on purpose: `pickup-slot.component.spec.ts` free chips; dashboard specs (removed links/hint/admin card); nav specs that assumed working-group admin = manager; e2e `pu7-options`, `pu8-agenda`, `pu6-history`, `fs2-cleaning-agenda` (route `/pickups/mine` now redirects); stores e2e (withdraw button location); a11y `routes.ts` / `csp.spec.ts` entries for `/pickups/mine`.
- New e2e tagged `@UI-POLISH` (+ feature tags `@PU7`, `@S6`, `@A19`): Nina sees no Verwalten items except Betriebsketten, and no ORGA; bell list has no empty icons; mention composer shows `@Name` and the sent message renders a chip; combined pickups page show more; home cards. Screenshot specs for the new states (baselines in batch later, decision 69).

## Done when
- [ ] Every bell type shows an icon
- [ ] Basket form inputs aligned, kg suffix, chip toggles
- [ ] `/pickups` combined page per mockup; `/pickups/mine` redirects
- [ ] Home per mockup; no role hint, no quick links, no false "Complete your profile"
- [ ] My stores: no badges (except inactive), actions inside cards
- [ ] One free-slot chip per pickup
- [ ] Slot order stable by sign-up time (tested)
- [ ] Composer shows `@Name`; sent/edited messages keep mention tokens
- [ ] Menu items driven by server capabilities; Nina sees only Betriebsketten under Verwalten
- [ ] Lint, i18n:check, unit, server tests, ArchUnit, targeted e2e green; decision 159 drafted

## Risks
- Capabilities enrichers add queries to every session bootstrap; keep each to one query and cover with `QueryCountIT`.
- Mention encoding: two picked people sharing a name prefix → match longest first; a hand-edited name loses the link (accepted).
- Redirecting `/pickups/mine` breaks e2e and links that aren't updated; grep `pickups/mine` across client and e2e.
- The home screen loads more requests; defer the cards below the fold (`@defer (on viewport)`) to keep the start-up budget (decision 84).
