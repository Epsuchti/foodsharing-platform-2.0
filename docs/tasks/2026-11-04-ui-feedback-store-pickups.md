# UI feedback batch 2, group A: store pickups (owner feedback 2026-11-04)

Status: approved by the owner. **Designer first** (one shared run with the store-team planner: compact team card + mention pill), then implementer.
Branch `feat/ui-feedback-batch-2`, one PR with group B. Steps marked **[mockup]** wait for the approved mockup; all others can start right away.

## Goal
Simplify and enrich the store pickups tab: remove the own "delete all future pickups" button, give the agenda a back button, show slot details (rich for managers and co-pickups, short for the rest of the team), a named day chat per store and day, a configurable number of extra slots, and compact day cards.

## Decisions (owner answers; record as decision 165 on merge)
1. Remove only the own button "Aus allen meinen künftigen Abholungen austragen" (`/me/pickups/history`); the admin variant on `/users/:id/pickups` and the endpoint stay.
2. The Tagesübersicht (PU8 agenda page + `getAgenda`) stays; it gets a back link to `/pickups`.
3. Slot click: **rich dialog** for managers (`mayConfirmPickups`/`mayEditPickups`) **and** people signed up for the same pickup; **short dialog** (avatar, name, profile link) for every other team member. Phone only under the existing S9 rule (`maySeePhoneNumbers`). "Slots hier"/"Letzter Termin hier" = confirmed past pickups at this store; "Aktuell belegte Slots" = the person's future slots at this store (expandable). Bestätigen/Austragen only for managers. Own slot keeps the sign-out confirm.
4. Day chat: one **named** chat per (store, calendar day in the store's time zone), persisted mapping. A find-or-create operation adds everyone currently signed up that day who isn't a member yet; nobody is removed automatically. Callers: team members of the store or people signed up that day. Name is set once on creation (e.g. "Abholung Coop Bahnhofbrücke, Do 5.11.").
5. Extra slots: store setting `maxExtraSlots` (int, 0 = off) replaces `extraSlotAllowed`; the first signer may add up to that many, still capped by `maxSlotsPerPickup`. Migration: true → 1, false → 0.
6. No extra-slot hint in the pickups tab.

## Scope
In: the 6 decisions above. Out: the store team tab, translations review, mentions, profile links, user cards, ambassadors (group B planner); removing anybody from day chats; changing who may add an extra slot (still only the earliest sign-up).

## Changes

### Contract (`server/src/main/resources/openapi/`, then regenerate)
- `paths/pickups.yaml` `/api/stores/{storeId}/pickups/{pickupAt}/signups/{userId}`: add `get` → `getPickupSignupDetail` (`x-permission: 'mayConfirmPickups or mayEditPickups or signed up for the same pickup'`; 403 ACCESS_DENIED, 404 SIGNUP_NOT_FOUND/STORE_NOT_FOUND).
- `schemas/pickups.yaml`: `PickupSignupDetail {user: UserSummary, confirmed, signedUpAt, phone?, pickupCount, lastPickupAt?, upcomingSlots: [date-time]}` (`phone` only with S9). `Pickup.extraSlot` → `extraSlots: [PickupExtraSlot]` (description AU6 updated).
- New path `/api/stores/{storeId}/pickup-days/{date}/conversation` `post` → `openPickupDayConversation` (tag pickups, `x-history`), body `PickupDayConversationOpen {name}` (1–200, used only on creation), returns `messaging.yaml#/Conversation`; 400 NO_SIGNUPS (nobody signed up that day), 403, 404.
- `schemas/automation.yaml`: `ExtraSlotSetting {maxExtraSlots: int32, min 0, max 50}` replaces `allowed`; `StoreAutomation.extraSlotAllowed` → `maxExtraSlots`. Error code rename `EXTRA_SLOT_ALREADY_ADDED` → `EXTRA_SLOT_LIMIT_REACHED` (`ApiErrorCode`, client `errors.*` keys).

### Database
- `db/changelog/changes/187-pickup-day-conversation-extra-slots.yaml` (check the next free number against group B before committing):
  - `createTable pickup_day_conversation` (BaseEntity columns, `store_id` FK store cascade, `pickup_date DATE`, `conversation_id` FK conversation cascade), unique `(store_id, pickup_date)`.
  - `store.max_extra_slots INT default 0 not null`; `update` via `sql`: `UPDATE store SET max_extra_slots = CASE WHEN extra_slot_allowed THEN 1 ELSE 0 END` (PG + H2); `dropColumn extra_slot_allowed`.
  - `dropUniqueConstraint uq_pickup_extra_slot_pickup`; `createIndex ix_pickup_extra_slot_store_pickup (store_id, pickup_at)`.
- Seed `db/seed/changes/187-pickup-day-conversation-seed.yaml`: one mapping + GROUP conversation + members for a seeded store/day with signups (fixed UUIDs, add the range to `docs/seed-ids.md`); seed store from 109 gets `max_extra_slots: 2`.

### Server
- `pickup/domain/PickupDayConversation.java` + repository (`findByStoreIdAndPickupDate`). `Store.extraSlotAllowed` → `maxExtraSlots`; `StoreService.setMaxExtraSlots`.
- `PickupSignupService.addExtraSlot`: rule extracted to a pure static `ExtraSlotRule.mayAdd(maxExtra, addedCount, slotCount, maxSlotsPerPickup, isFirst, inFuture)`; reuse in `PickupUserService.mayAddExtraSlot` and `PickupScheduleService:168`. `PickupExtraSlotRepository.findByStoreIdAndPickupAt` → `List`; `findByStoreIdAndPickupAtIn` grouping becomes a multimap; `PickupMapper:73-86/168` maps a list. `ExtraSlotService.setAllowed` → `setMaxExtraSlots` (history FIELD_CHANGED `maxExtraSlots`); `AutomationController:162/273`.
- `PickupSignupDetailService` (read-only): permission rule as a pure static method `SlotDetailRule.mayView(mayConfirm, mayEdit, callerSignedUpForPickup)` + unit test. Reuses `StoreTeamService`'s pickupCount/lastPickupAt computation (extract a shared query if private) and `PickupSignup.createdAt`; upcoming = the user's future signups at this store.
- `PickupDayConversationService.open(storeId, date, name)` (`@Transactional`, records history `PickupAction.DAY_CONVERSATION_OPENED` with members added): lock store, check `TEAM.contains(status)` or signed up that day (rule `DayChatRule.mayOpen` + unit test), signups of that day via `PickupScheduleService` in the store's zone, find-or-create (`MessagingService.createGroup` for creation; `addMember` for missing ones, through a messaging service method, not the repository), return the conversation.
- `PickupsController` implements the two new operations; `ApiErrorCode` additions.

### Client
- `pickups/pages/pickup-history-page.component.ts`: button only when `!own()`; remove key `pickups.past.deleteFutureOwn` (5 languages).
- `pickups/pages/agenda-page.component.ts`: `fs-page-header` gets `backLink="/pickups"` + `backLabel`.
- **[mockup]** `pickups/components/slot-detail-dialog.component.ts` (new): rich mode = `loadResource(getPickupSignupDetail)` + `<fs-data-state>`; header date/time; avatar, name, phone; icon buttons profile (`/users/:id`), message (`lookupConversation`, as in `profile-page.component.ts:234`), `tel:`, copy; state; counts; expandable upcoming list; footer Schließen, Bestätigen/Austragen for managers (existing `confirmSlot`/`askRemove` flows move here). Short mode (no request): avatar, name, profile link, Schließen.
- `tabs/store-pickups.tab.ts`: `slotsClickable` true for team members; `onSlot` picks rich mode when manager or `isSignedUp(pickup)`, else short; `extraSlots` list rendering; **[mockup]** compact day cards (styles ~line 373 ff.) and a day-chat icon button in each day header when anyone other than me is signed up that day → `runMutation(openPickupDayConversation)` with a localized name (`pickups.dayChat.name`) → navigate to the chat.
- `automation/sections/extra-slot.section.ts`: switch → number input (0–`maxSlotsPerPickup`), `runMutation`, hint "0 = aus".
- i18n (all 5): `pickups.slotDetail.*`, `pickups.dayChat.*`, `automation.extraSlot.max*`, `errors.EXTRA_SLOT_LIMIT_REACHED`, `errors.NO_SIGNUPS`; update `automation.extraSlot.allowed`. `npm run i18n:check`.

## UI
Yes. Designer mockup `docs/design/ui-feedback-batch-2-mockup.html` (shared run) covering compact day cards, rich + short slot dialogs, the day-chat button. Steps marked **[mockup]** wait for it.

## Tests
- Unit (server): `ExtraSlotRuleTest` (0 = off, limit reached, cap by maxSlotsPerPickup, only first signer, past), `SlotDetailRuleTest`, `DayChatRuleTest`.
- IT: `PickupsApiIT` signup detail (manager, co-pickup, other member 403, phone S9); day conversation (create, idempotent, adds new signer, keeps people who signed out, non-member 403); `AutomationApiIT`/AU6 with `maxExtraSlots = 2`; Liquibase migration on H2 (true→1, false→0).
- Client specs: `store-pickups.tab` (clickable for members, mode choice), `slot-detail-dialog` states, `pickup-history-page` (no button when own), extra-slot section.
- E2E: `e2e/tests/pickups/pu3-slot-details.spec.ts` `@PU3` (manager rich, co-pickup rich without remove, member short with profile link), `pu3-day-chat.spec.ts` `@PU3 @M5`, `automation/au6-extra-slot.spec.ts` updated for 2 slots `@AU6`, `pu6-history.spec.ts` (own button gone), `pu8-agenda.spec.ts` back link. Screenshot specs in `pickups.screen.ts`/store pickups for both dialogs and the compact cards; baselines via `screenshots-update.yml` in CI.

## Done when
- [ ] Own delete-future button gone, admin variant works; agenda has a back link.
- [ ] Rich/short slot dialogs per decision 3, phone under S9, actions only for managers.
- [ ] Day chat find-or-create is named, idempotent per store+day, adds new signers, never removes.
- [ ] `maxExtraSlots` setting migrated and enforced; AU6 shows all extra slots.
- [ ] Compact cards match the approved mockup.
- [ ] Contract regenerated, ArchUnit/history rules green, seed + `docs/seed-ids.md` updated, i18n check green, e2e + screenshots updated in CI and reviewed; ledger PU3/AU6 and decision 165 updated.

## Risks
- Changelog number clash with group B (same branch) — agree on 187/188 before committing.
- Dropping `extra_slot_allowed` and the unique constraint: H2 and PG names must match (`uq_pickup_extra_slot_pickup`); test on both.
- `Pickup.extraSlot` → `extraSlots` is a breaking model change; all callers (`PickupMapper` both paths, my-pickups list) must follow.
- The day's signups depend on the store time zone (`dayOf`); use the same zone server- and client-side.
- Co-pickup detail exposes counts and upcoming slots to peers; phone stays S9-gated.
