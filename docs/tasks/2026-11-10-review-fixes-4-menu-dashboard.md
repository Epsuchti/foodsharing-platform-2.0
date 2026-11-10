# Review fixes 4/5: one "Mitglieder", newcomer menu and dashboard, "Wartet auf dich", store dots (D1, D3, D4, D5)

Package 4 of the 2026-11-10 review batch (order 1 → 2 → 3 → 4 → 5).

> **Q1, assumed, owner to confirm at plan approval:** the region tab "Mitglieder" stays. The nav entry "Mitglieder" (`users`, page "Benutzer in …") is removed. Its extras move into the tab for people with MANAGE_MEMBERS, and `/regions/:id/users` redirects to `/regions/:id/members`.
>
> **Q5, assumed, owner to confirm at plan approval:**
> - (a) The journey page hides additional tracks that are LOCKED for the user, and shows the hygiene track only once the foodsaver quiz step is done. The quiz rule itself is unchanged.
> - (b) "Wartet auf dich" also drops "Deine Bewerbung ist noch offen".
> - (c) Forum, Abstimmungen and Termine stay in a newcomer's menu: the server lets region members use them (`ForumRules.mayAccess`, `PollRules.maySee`).

## Goal
One members list. Newcomers see no menu items or dashboard elements they can't use, and no menu behind the consent wall. "Wartet auf dich" lists only things the user can act on. Store status dots on `/stores/mine` are readable.

## Decisions
- D1: the tab is visible to every member and already has the admin actions, so it is the one that stays (Q1). Only this menu case changes; no other menu restructuring.
- D3: the menu itself already follows the server rules for newcomers (checked: `pickups`/`stores` need verified foodsaver, `mailboxes` the server flag, `passport` verified). What remains:
  - the "Was läuft" chips for STORE_WALL (only when `pickupsVisible`) and MAILBOX (only when `session.mailboxes`)
  - the journey tracks (Q5a)
  - `/consent` with minimal chrome (`data: { chrome: 'minimal' }`, the existing `chromeOf` mechanism)
- D4: rows the user can't act on are removed: `unconfirmed` and `applications`. `invitations` and `unread` stay.
- D5 (no mockup, owner): the dot is replaced by a text chip on its own line under "Nächste Abholung": tone from the status (YELLOW info, ORANGE warn, RED danger) with an icon and the existing `shared.store.freeSlots.<STATUS>` text. GREEN shows nothing.

## Scope
In: D1, D3 (the items above), D4, D5. Out: other menu restructuring, region tab grouping, mobile dashboard length, inbox naming, other dashboard cards.

## Changes
**Server**
- `onboarding/service/JourneyCalculator.tracks`: leave out tracks whose status is LOCKED. HYGIENE is left out while the QUIZ step is not done (pass `quizDone` in). No contract change (`tracks` is a list).

**Client**
- `layout/nav.config.ts`: delete the `users` item. `features/users-admin/users-admin.routes.ts`: `regions/:regionId/users` → redirect to `regions/:regionId/members` (keep `home` resolution). Check the users-admin feature for other pages; user admin pages stay.
- `features/regions/tabs/region-members.tab.{ts,html}`: for MANAGE_MEMBERS viewers (same check as the users page: ambassador, CAN_MANAGE_USERS, ORGA; reuse `hasPermission`/`isOrga` from `core/auth/permission.util`), add the users page's admin filter, the "letzte Abholung" column/line and the link to `/users/:id` (admin view). Reuse the `listRegionMembers` parameters the users page already sends. Then delete `pages/region-users-page.component.ts` and move its i18n keys that are still needed.
- `features/dashboard/components/activity-feed-card.component.ts`: `types` filtered by `SessionService` (STORE_WALL ↔ verified foodsaver, MAILBOX ↔ `session.mailboxes`).
- `features/legal/legal.routes.ts`: consent route `data: { chrome: 'minimal' }`.
- `features/dashboard/dashboard.util.ts` `waitingRows`: remove `unconfirmed` and `applications` (and the i18n keys `dashboard.waiting.unconfirmed/applications`). Check `waiting-card.component.ts` for now-unused inputs (pickups).
- `features/stores/pages/my-stores-page.component.ts` and `shared/store-badges/store-badges.component.ts`: new `fs-free-slot-chip` (in store-badges, `CHIP_STYLES`, tone map) replacing `fs-free-slot-dot` on `/stores/mine`. Keep the dot component if the gallery is its only other user, otherwise remove it and its gallery section.

## UI
Visible: menu (one item fewer for managers), members tab extras, newcomer journey tracks, activity chips, consent page without the menu, waiting card, my-stores chips. No mockup (owner, D5).

## Tests
- New: `JourneyCalculator` test (locked tracks out, hygiene after QUIZ done). `dashboard.spec.ts` / `home-cards.spec.ts`: `waitingRows` without the two rows, activity types per session. Members tab spec for the admin extras. Free-slot chip spec in `m3-shared.spec.ts`. Shell/route spec: consent minimal chrome.
- Changed on purpose: `sidenav.component.spec.ts` / nav specs listing `users`; `regions-pages.spec.ts`; users-admin page specs (deleted page); e2e using `nav.users` or `usersAdmin.list.*` (move to `regions.members.*`); `a19-home-cards.spec.ts` (waiting rows); `m3-shared.spec.ts` dot test; the onboarding journey spec counting tracks.
- E2E: `@R8` members tab admin extras plus the `/users` redirect; newcomer persona: no Betriebe/Postfächer chips, consent without menu; `@A19` waiting card. Screenshot states for each.

## Done when
- [ ] One "Mitglieder"; old URL redirects; managers keep the filter, last pickup and admin link
- [ ] Newcomer: no locked tracks, no early hygiene track, no unusable chips, no menu on /consent
- [ ] "Wartet auf dich" without pure waiting rows; `/stores/mine` chips with text
- [ ] i18n, tests green; ledger and a decision row (D1/D3/D4) updated

## Risks
- The members tab is used by every member, so the admin extras must stay hidden for normal members (test both).
- Removing tracks changes the onboarding page layout for verified users too. Check the persona journeys' step budgets.
