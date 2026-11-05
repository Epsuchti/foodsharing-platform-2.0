# Movable store pickups, linked EinAbs move along (PU10 + EA16, phase 2 of 2)

Builds on `2026-11-05-einab-from-pickups.md`, which must be merged first because it introduces `intro_pickup.linked_pickup_at` and the cancel sync.

> Note from phase 1 (2026-11-05): phase 1 became decision 169, so this phase is decision **170**. The sync lives in the pickup module (`pickup.service.IntroPickupLinkSync`, pickup → intropickup, because pickup already depends on intropickup); `IntroPickupPickupSync` and `LinkedIntroPickupInfo` below mean that class and `IntroPickupGuideService.linkedAt`.

## Goal
Store managers can move one pickup, a one-time pickup or one occurrence of a regular pickup, to another date and time. Sign-ups move with it, everyone signed up is notified, and linked EinAbs move along, with booked newcomers notified.

## Decisions (new decision 169)
- **Who:** `mayEditPickups` (store managers, coordinators; the same as `editPickup`, PU2).
- **What:** one future pickup → a new future time in the **same store**. The new time must not already be a pickup of that store with slots > 0 (409 `PICKUP_TIME_TAKEN`). Old and new must both be in the future (400 `PICKUP_IN_PAST`), and new ≠ old (400 `VALIDATION_FAILED`).
- **Regular occurrence:** a `PickupDate` override with `slotCount 0` at the old time (cancels that one occurrence; the schedule already supports this), plus a new `PickupDate` at the new time with the slot count (including added extra slots) and description of the old one. The moved pickup is one-time from then on, so the ↻ marker disappears. The regular schedule itself is unchanged.
- **One-time pickup:** its `PickupDate.pickupAt` is changed (or replaced by delete + create if the unique key makes that simpler).
- **Sign-ups move with it:**
  - Every `PickupSignup` of the old time gets `pickupAt = new`.
  - `confirmed`/`confirmedBy`/`confirmedAt` are kept, and `remindedAt` is reset to null, so the reminder job reminds again.
  - `PickupExtraSlot` rows (audit) follow the pickup.
  - Eligibility and automation rules are **not** re-checked; people who can't make it sign out themselves.
  - The day chat (`pickup_day_conversation`, per store and day) is not touched; find-or-create adds the signers to the new day's chat when it is opened.
- **Optional message** (max 500) from the manager to the people signed up.
- **Notifications:** new `NotificationType.PICKUP_MOVED` (category PICKUPS, icon `calendar`), bell + mail to every signed-up user except the actor. Params: store name, old time, new time, message; link `/stores/{id}/pickups`. Bell texts and the mail template in de/en/gws/fr/it.
- **History:** new `StoreLogAction.PICKUP_MOVED` on the store (metadata `from`, `to`, `signups` count, `message` flag). The store log shows a label for it (`HistoryI18nCoverageTest`).
- **Event:** `PickupEvents.PickupMovedEvent(storeId, from, to, userIds, byUserId)`, published in the same transaction.
- **EinAb sync:** `IntroPickupPickupSync` listens to `PickupMovedEvent` (same transaction).
  - For every linked, not deleted, future EinAb with `storeId` + `linkedPickupAt = from` whose guide is in `userIds`: set `startAt` and `linkedPickupAt` to `to`, record history `IntroPickupAction.UPDATED` (metadata `fields=startAt`, `cause=STORE_PICKUP_MOVED`) and publish an event after commit.
  - Booked newcomers get a **dedicated** new `INTRO_PICKUP_MOVED` (bell + mail, old and new time prominently, five languages). The guide gets no extra notice; he got `PICKUP_MOVED`.
  - The EinAb's booking rules (lead time, minimum pickups) are not re-checked; bookings stay.
  - If the new time is less than 24 h ahead, the mail subject says "short notice" (same template, a parameter).
- **UI:** in the store pickups tab, the slot edit dialog (managers) gets a "Move" button → dialog with a date-time picker (store time zone), an optional message, and a summary: "N signed up will be notified; M EinAbs move along" (M from the pickup module's `LinkedIntroPickupInfo` from phase 1, extended with `countAt(storeId, pickupAt)`).

## Contract
- `paths/pickups.yaml`: `POST /api/stores/{storeId}/pickups/{pickupAt}/move`, operationId `movePickup`, x-permission `mayEditPickups`, x-history `StoreLogAction.PICKUP_MOVED`.
  - body `PickupMove {newPickupAt (date-time), message? (maxLength 500)}`
  - 200 the pickup slot model at the new time
  - errors: 400 PICKUP_IN_PAST / VALIDATION_FAILED, 403, 404, 409 PICKUP_TIME_TAKEN
- The pickup slot model gets `linkedIntroPickupCount?` for managers (move dialog summary).
- `schemas/common.yaml`: `PICKUP_TIME_TAKEN`.

## Server
- `PickupSignupService.move(storeId, pickupAt, newPickupAt, message)`: `stores.lock(storeId)`, rights, guards, override/date handling, sign-up and extra-slot updates, history, event, notifications (via `StoreNotifications`). Inject `Clock`.
- Repository: `PickupSignupRepository.findByStoreIdAndPickupAt`, a bulk update for `PickupExtraSlot`.
- `IntroPickupPickupSync.onMoved`; `IntroPickupNotifications.onMoved` (AFTER_COMMIT, REQUIRES_NEW, like the others).
- A pure rule class `PickupMoveRule` (guards) with unit tests.

## Liquibase and seed
- No schema change expected. If the `pickup_date` unique key `(store_id, pickup_at)` blocks the update, use delete + insert, not a new changelog.
- Seed: none needed. The e2e test uses the phase 1 linked EinAb (the guide's confirmed sign-up with a booked newcomer).

## Client
- `client/src/app/features/pickups/tabs/store-pickups.tab.ts` (+ dialog component): Move button `pickups.slot.move`; dialog testids `pickups.move.dialog`, `pickups.move.newTime`, `pickups.move.message`, `pickups.move.save`; `runMutation`; error mapping for PICKUP_TIME_TAKEN.
- i18n in de/en/gws/fr/it (dialog, summary, errors, store log label, bell texts); `npm run i18n:check`.

## Tests
- Unit:
  - `PickupMoveRule` (past, same time, taken, rights)
  - regular-occurrence override vs one-time
  - sign-ups keep confirmation and reset the reminder
  - the EinAb sync (only linked, only the guide's, past untouched)
- IT:
  - `movePickup` end to end
  - `PICKUP_MOVED` mail to the signed-up people (not to the actor)
  - linked EinAb moved + `INTRO_PICKUP_MOVED` to the booked newcomer
  - 409 taken
  - 403 for a team member without rights
- e2e `e2e/tests/pickups/pu10-move-pickup.spec.ts` `@PU10 @EA16`: a manager moves the guide's linked pickup → the slot appears at the new time with its sign-ups → the guide's EinAb shows the new time → the newcomer has a bell. Plus a 409 case.
- Screenshots: the move dialog (with summary) in `e2e/screenshots/pickups.screen.ts` `@PU10`; regenerate in CI with `-f grep=@PU10`, review the PNGs.

## Docs
- Ledger: new row PU10 "Move a pickup (one-time or one regular occurrence), sign-ups move along, PICKUP_MOVED"; EA16 notes "linked EinAbs move along". Decision 169. plan.md: a pickup section line under PU2.

## Open for the owner (planner's recommendation applied; confirm in the summary)
- Moving is within the same store only, with no limit on how far.
- Rules are not re-checked for moved sign-ups or bookings.
- A dedicated `INTRO_PICKUP_MOVED` instead of the generic "updated".
- The moved regular occurrence becomes a one-time pickup.
- No designer mockup (a small dialog).
