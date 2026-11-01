# Pickup slots: confirmed vs. unconfirmed at a glance (resume item 11)

Source: `docs/design/pickup-slots-proposal.md` (sections 2–7) and `docs/design/pickup-slots-mockup.html`. Owner decisions:
section 10 of the proposal, plus the planner round on 2026-11-01 (below). Where this plan and the proposal disagree, this plan wins.

## Goal
On `/stores/:id/pickups`, anyone can tell confirmed, unconfirmed and own slots apart without relying on colour, and
without hovering. Managers can see and confirm unconfirmed sign-ups quickly. The options page `/pickups` shows the
same counts.

## Decisions
1. Each slot state gets two cues: ring style (solid/dashed) **and** a badge icon (`pi-check` / `pi-hourglass`). The unconfirmed ring and badge use the full `--fs-warning` colour.
2. Your own slot is a pill with the visible word "Du" (proposal 3.2). It keeps opening "Austragen?".
3. Managers (`mayConfirmPickups`) see each unconfirmed slot as a split pill: first name + ✓. Both the pill and the ✓ open the **existing** slot dialog. The ✓ focuses "Bestätigen" there. There is no one-click confirm.
4. No eligibility in the row: no lock pill, no hint line, no `eligibility` input. A free slot stays clickable unless you are already in the pickup (as today). The sign-up dialog is unchanged: it already shows the reasons and hides "Eintragen".
5. "Alle n bestätigen" appears per pickup when 2 or more slots are unconfirmed. It opens **one** confirm dialog (ConfirmationService) that names the people. On accept, `confirmSignup` runs once per person, one after another (`concat`). Then one toast `pickups.tab.confirmedAll` and a reload. If a call fails partway, the error toast shows and the list reloads; the people still unconfirmed stay visible.
6. Filter chip "Unbestätigt (n)", for `mayConfirmPickups` only, third in the range chip group, `aria-pressed`.
   - The count covers the currently selected range.
   - The chip is hidden when the count is 0, unless it is pressed.
   - When pressed and nothing is left, the list shows `pickups.tab.pendingEmpty`.
7. Wording: "unbestätigt" everywhere (de), and the other languages follow.
   - Summary: "✓ 2 von 3 bestätigt · ⧗ 1 unbestätigt". The proposal's "Du wartest" special case is dropped.
   - Own status line: "Dein Platz ist noch unbestätigt".
   - Accessible names: "{name}, unbestätigt" and "Du ({name}), unbestätigt – austragen".
   - `pickups.tab.waiting` becomes "Unbestätigt", which also changes `/pickups/mine`.
   - en/gws/fr/it: unconfirmed / unbestätigt / non confirmé·e / non confermato.
8. `/pickups` (nav "Abholungen") gets the summary counts. Contract: a new **required** `confirmedCount` (int32) on `PickupOption`.
9. No dark baselines for the pickups tab (decision 109). Baselines are taken in the end batch (decision 69).
10. No designer step: the proposal and mockup exist and the owner decided on them.

## Scope
In: the slot component, the store pickups tab (summary, confirm-all, filter chip, state line in the slot dialog, own
status line), slot tokens, `PickupOption.confirmedCount` + its mapper, summary on the options page, i18n, unit/e2e/screenshot specs.
Out:
- the lock pill and eligibility hint (decision 4)
- one-click confirm
- a store-wide confirm-all, and a server-side batch confirm endpoint
- dark baselines
- changes to the sign-up dialog, `my-next-pickups-card` and the agenda page
- seed changes (the e2e tests create a second unconfirmed sign-up through the API)

## Changes
**Contract**
- `server/src/main/resources/openapi/schemas/pickups.yaml`: in `PickupOption`, add `confirmedCount` (integer, int32, minimum 0) to the properties and to `required`. Then regenerate the server and client code.

**Server**
- `server/src/main/java/network/foodsharing/pickup/mapper/PickupMapper.java` `toOptions`: `setConfirmedCount`. Count only the sign-ups that `setOccupiedSlots` already keeps (`byId` contains the user) **and** that are `isConfirmed()`, so the count never exceeds `occupiedSlots.length`. The query does not change.

**Client: shared**
- `client/src/styles.scss`: add the tokens `--fs-on-warning` (#ffffff light / #1a201a dark), `--fs-slot-size` (2.4rem; 2.75rem under `pointer: coarse`), `--fs-slot-ring` (2px) and `--fs-slot-badge` (1.05rem).
- `client/src/app/shared/pickup-slot/pickup-slot.component.ts`:
  - New input `confirmable` (default false) and new output `confirmClick: PickupSlot`.
  - Badge `<span class="slot__state" aria-hidden="true">`. Own slot rendered as the "Du" pill ("Du" is `aria-hidden`). The split pill is `<div role="group">`: pill button (`slotClick`) + ✓ button (`confirmClick`, test id `<testId>.confirm`).
  - Own slot gets the extra test id `<testId>.mine`. Since `data-testid` holds a single value, use a separate attribute: `data-mine="true"` on the `.taken` button. **Keep `.taken`** so the existing specs still match.
  - Spec 3.1/3.2 styling through the tokens. Accessible names per proposal 4.1, with the wording from decision 7.
  - The free slot logic stays as it is.
- Pure helpers in `client/src/app/features/pickups/pickups.util.ts` (existing file and spec):
  - `slotState(slot, myId)`
  - `pickupSummary(slots, slotCount)` → `{confirmed, unconfirmed, total}`
  - `unconfirmedCount(pickups)`
  - `firstName(displayName)`
- New `client/src/app/features/pickups/components/pickup-summary.component.ts` (`fs-pickup-summary`, inputs `confirmed`/`unconfirmed`/`total`; icons `aria-hidden`; test id input). The tab and the options page both use it.

**Client: store pickups tab** (`features/pickups/tabs/store-pickups.tab.ts`)
- The summary goes in `.pickup__head` (pencil `margin-left: auto`; under 48rem the summary gets `flex-basis: 100%`). Test id `stores.pickups.summary`, attributes `data-confirmed` / `data-pending`.
- "Alle n bestätigen": `p-button [text] size="small"`, test id `stores.pickups.confirmAll`. Built with `runMutation` (decision 5).
- Chip: test id `stores.pickups.range.pending`; a `pendingOnly` signal filters `days()`.
- `[confirmable]="ctx.may('mayConfirmPickups')"` and `(confirmClick)="openSlot(pickup, $event, true)"`. When the third argument is true, focus `stores.pickups.confirmSlot` after the dialog opens (via `p-dialog` `(onShow)`).
- In the slot dialog, a state line under the date: `pickups.tab.state.confirmed` / `.unconfirmed`.
- Own status line text becomes `pickups.tab.waitingMine`. Its test id `stores.pickups.unconfirmed` stays.

**Client: options page** (`features/pickups/pages/pickup-options-page.component.ts`)
- `fs-pickup-summary` next to `pickups.options.free`, test id `pickups.options.summary`.
  - `confirmed` = `option.confirmedCount`
  - `unconfirmed` = `occupiedSlots.length - confirmedCount`
  - only when at least one slot is taken

**i18n** (all five languages)
- Files: `client/src/app/shared/i18n/*.json` and `client/src/app/features/pickups/i18n/*.json`.
- Keys:
  - from proposal 4.2: `shared.pickupSlot.you`, `mineConfirmed`, `mineUnconfirmed`, `confirmOne`; changed `shared.pickupSlot.unconfirmed`; `pickups.tab.summary.confirmed`, `pickups.tab.summary.unconfirmed`, `pickups.tab.waitingMine`, `pickups.tab.confirmAll`, `pickups.tab.confirmAllTitle`, `pickups.tab.confirmAllText` (with names), `pickups.tab.confirmedAll`, `pickups.tab.range.pending`, `pickups.tab.pendingEmpty`, `pickups.tab.state.confirmed`, `pickups.tab.state.unconfirmed`
  - changed: `pickups.tab.waiting`
  - not added: `freeLocked`, `summary.waitingYou`
- `npm run i18n:check` must pass.

## UI
- Yes, this is a visible UI change on the store pickups tab and on `/pickups`.
- No designer mockup is advised; the existing proposal and mockup are the reference. The mockup still shows "wartet" and the lock pill; follow this plan where they differ.

## Tests
- Server: `server/src/test/java/network/foodsharing/pickup/PickupUserApiIT.java`. Assert `confirmedCount` for an option with one confirmed and one unconfirmed sign-up.
- Unit: `pickups.util.spec.ts` covers the new helpers. Note `pickupSummary` takes slots, but the options page builds its counts from `confirmedCount`; test that arithmetic (unconfirmed = taken − confirmed, never below 0) too.
- New unit specs:
  - `shared/pickup-slot/pickup-slot.component.spec.ts`: every state, badge, accessible names, "Du" pill, split pill only when `confirmable`, `confirmClick`/`slotClick`.
  - `features/pickups/tabs/store-pickups.tab.spec.ts`: summary, chip count/visibility/filter, the confirm-all order, partial failure.
- e2e `@PU3` in `e2e/tests/pickups/pu3-signup.spec.ts`:
  - The manager uses `stores.pickups.slot.confirm`, the dialog opens with "Bestätigen" focused, and `summary[data-pending]` drops.
  - Confirm-all on a pickup with 2 unconfirmed sign-ups (the second is created through the API).
  - The chip filters, and shows the empty text afterwards.
- e2e `@PU4` in `pu4-eligibility.spec.ts`: in the UI, a jumper on S-02 clicks a free slot, sees the reasons, and no `pickups.signup.confirm` button is shown.
- e2e `@PU7` in `pu7-options.spec.ts`: `pickups.options.summary` shows the counts.
- a11y: every `stores.pickups.slot.*` button has an accessible name containing bestätigt, unbestätigt or frei.
- Existing specs that stay unchanged on purpose: `personas/store-manager.spec.ts` (pill → "Bestätigen", still 13 clicks) and `personas/foodsaver.spec.ts`.
- Screenshots, `e2e/screenshots/pickups.screen.ts`:
  - Existing captures that change: pickups-tab, booked, signup-*, options, agenda.
  - New light capture: `pickups-tab-manager` (bieb on S-01: split pill, chip, summary).
  - No baselines are updated in this task.

## Done when
- [ ] Contract regenerated; `confirmedCount` mapped and covered by the IT
- [ ] The slot states match decisions 1–3; the "Du" pill and split pill work; the ✓ focuses "Bestätigen"
- [ ] Summary on the tab and on `/pickups`; confirm-all from 2; the "Unbestätigt (n)" chip filters
- [ ] No lock pill or eligibility hint anywhere; the sign-up dialog is unchanged
- [ ] i18n in five languages, `i18n:check` green; no hard-coded colours (dark mode through tokens)
- [ ] Unit, PU3/PU4/PU7 e2e and the a11y sweep green; client lint and typecheck green
- [ ] New screenshot spec added; baselines left for the end batch

## Risks
- The split pills make a manager's 8-slot row wrap to three lines on phones (accepted in proposal 6).
- Confirm-all fires n sequential requests. A failure partway leaves a partly confirmed pickup; the reload shows it.
- The new required field makes clients generated before this change incompatible; only our own client uses it.
- Changing `pickups.tab.waiting` also changes `/pickups/mine` and its screenshots (intended).
- Focusing "Bestätigen" inside `p-dialog` can race the dialog animation. Use `onShow`, not a timeout.
