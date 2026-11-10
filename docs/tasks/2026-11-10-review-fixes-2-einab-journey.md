# Review fixes 2/5: EinAb booking, notifications, journey waiting step, funnel labels (A4, A5, B1, B2)

Package 2 of the 2026-11-10 review batch (order 1 → 2 → 3 → 4 → 5). Runs **before** `docs/tasks/2026-11-10-einab-hub.md` (EA23). That plan moves `newcomer-funnel-page` into a tab and removes the gap table, so changes here stay minimal and the gap table is not touched.

> **Q3, assumed, owner to confirm at plan approval:** the waiting-for-verification step shows **one** figure: the region's median days from the last counted EinAb to verification (at least 3 cases in the last 365 days). With fewer cases it shows the static "ca. 7 Tage". Never both.

## Goal
A newcomer sees only EinAbs they can book, with an empty state that tells them why nothing is listed. Notifications keep naming only bookable dates. The waiting step shows a single, meaningful time and lets them message the named contact. The funnel stops showing "In Einführung" twice.

## Decisions
- A4: today "ca. 7 Tage" is the fixed `P7D` (`JourneyCalculator.java:137`) and "40 Tage" is the median from registration to verification of the whole journey (`JourneyService.typicalVerificationDays`). That is why the numbers contradict each other in all languages: they come from data, not from the translations. The fix is Q3 above.
- A4: every contact in a `waitingFor` block gets a "Nachricht" button (find-or-create direct conversation, same as the profile page). This applies to all waiting blocks, not only verification, because it is one component.
- A5: the duplicate is `IN_PROGRESS` with `introPickupCount` 1 and 2 (one step per count), not FAIRTEILER_MISSING. Label: "In Einführung: {count} von {target}". Client only.
- B1: filter in the client (`bookable === true`). The contract is unchanged and the reasons stay available for the empty state. The list already loads 100 at once.
- B2: already true for new EinAbs (`IntroPickupNotifications.notifySubscribers` skips `notBookableReason`). Only a pinning test is added. No notification for freed slots (owner).

## Scope
In: A4, A5, B1, B2 as above. Out: freed-slot notifications, the gap table, funnel layout, EinAb hub (EA23), booking rules themselves.

## Changes
**Server** (no contract change)
- `onboarding/service/JourneyService.typicalVerificationDays`: median of `verifiedAt − lastIntroPickupAt` over region members verified within `TYPICAL_WINDOW`. Use `null` when fewer than 3 cases. Add a batched `IntroPickupProgressService.lastIntroPickupAt(Collection<UUID>)` (one query; the single-user method stays). Rename the constant/Javadoc accordingly.
- `onboarding/service/JourneyCalculator` (verification step, ~line 137): when `typicalVerificationDays != null`, `estimatedDuration` is `null`. Otherwise keep `P7D` and `typicalDays` stays `null`.

**Client**
- `shared/journey-stepper/journey-stepper.component.ts`: per contact in `waiting.contacts`, a small text button "Nachricht" (`shared.journey.message`, aria "Nachricht an {name}") next to the `fs-user-chip`. It uses `runMutation(() => messaging.lookupConversation({ conversationLookup: { memberIds: [id] } }))` and navigates to `/messages/:id`. `data-testid="<testId>.message"`. Hidden for the user themselves. `shared.journey.typicalDays` text: "dauert hier meist {days} Tage" (5 languages).
- `features/intro-pickups/pages/newcomer-funnel-page.component.ts`: label `IN_PROGRESS` via new key `introPickups.funnel.stage.IN_PROGRESS_COUNT` ("In Einführung: {count} von {target}", target from the funnel). Track by `step.stage + step.introPickupCount`. Nothing else on the page changes.
- `features/intro-pickups/pages/bookable-list-page.component.ts`: `visible = items.filter(i => i.bookable)`. Custom empty state by the reasons of the hidden items:
  - no items at all: today's empty title/text + "Benachrichtige mich"
  - any `USER_BOOKING_LIMIT_REACHED` / `USER_ALREADY_BOOKED_IN_INTRO_PICKUP` covering everything: `introPickups.bookable.emptyEnough` ("Du hast genug Termine gebucht") + link "Meine Termine"
  - `FAIRTEILER_VISIT_REQUIRED_FOR_THIRD_INTRO_PICKUP`: `emptyFairteiler` ("Buche zuerst einen Fairteiler-Besuch")
  - otherwise: `emptyNoMatch` ("Gerade passt kein Termin zu deinen bisherigen Buchungen – zum Beispiel brauchst du eine andere Begleitung oder Kategorie. Wir sagen dir Bescheid, sobald es passt.") + "Benachrichtige mich"
  - Put the choice in a pure helper `bookableEmptyKind(items)` in `intro-pickups.util.ts`. Remove the not-bookable reason branch in the template.
- All new keys in 5 languages.

## UI
Visible: EinAb list (fewer cards, new empty states), the journey waiting block (one time, message button), funnel labels. No mockup.

## Tests
- New: `JourneyService` / `JourneyCalculator` unit tests (median from the last EinAb, < 3 cases → null and P7D, never both). `IntroPickupNotificationsTest`: a subscriber with a `notBookableReason` gets no bell and no mail (pins B2). `bookableEmptyKind` spec. Funnel label spec. Stepper spec for the message button.
- Changed on purpose: `ea5-booking-rules.spec.ts` (blocked cards no longer listed; assert the empty state for Luca's situation), `intro-pickups-pages.spec.ts` (reason branch gone), journey stepper specs asserting duration plus typical days.
- E2E `@EA5` (Luca sees only bookable dates; empty state) and the onboarding waiting step (`@D11.5.5` persona newcomer if it covers it). Screenshot states: list with items, each empty kind, waiting step.

## Done when
- [ ] Luca's list shows only bookable dates; empty states by reason
- [ ] Waiting step shows one time figure; "Nachricht" opens the conversation
- [ ] Funnel shows "In Einführung: 1 von 3", "2 von 3"
- [ ] B2 pinned by test; i18n check, server and client tests green; ledger rows EA5/onboarding updated

## Risks
- Few verified members in a region: the median is often null, which is fine (fallback).
- The EA23 plan copies the funnel page body. Keep the label change in the template/i18n only so it carries over.
