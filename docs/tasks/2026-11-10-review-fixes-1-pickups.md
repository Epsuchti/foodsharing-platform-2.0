# Review fixes 1/5: pickup lists, rule wording, store page (A1, A2, A3, A7, A8)

Batch from the owner's UI review of 2026-11-10 (jar of 6 Nov, all points verified against main 0a4a510c, none already fixed).
Order of the batch: **1 → 2 → 3 → 4 → 5**, then one screenshot run in CI (decision 161). Only the listed points; nothing else changes.

## Goal
"Wo kann ich helfen?" (`/pickups`) and "Bald Hilfe gesucht" (dashboard) offer only pickups the user can actually take and show their own sign-ups as a state. The rule text is plain and correct. The automation header and the rule cards agree. On a phone, a blocked slot says "Gesperrt" and "Navigieren" looks like a button.

## Decisions
- Default (owner did not object): A1 hides **every** pickup the user is not eligible for: the region pickup rule, the store rules (already done, decision 167 S2) and personal reasons (hygiene, phone, verification). This matches 167 S2. *Assumed, owner to confirm at plan approval.*
- A2: a pickup the user is signed up for stays in the list with an "Eingetragen" chip (confirmed: "Eingetragen · bestätigt"; unconfirmed: "Eingetragen · noch unbestätigt") instead of "Eintragen". No contract change: `PickupOption.isConfirmed` is set only when the user is in.
- A3: the counters are the user's **other** sign-ups dated within ±`timespanDays` of the pickup's date (`PickupScheduleService.EligibilityContext.ruleSignups`). The text says exactly that. No rule line when the reasons contain `ALREADY_SIGNED_UP`. A limit ≤ 0 is left out.
- A7: rules take effect only while `pickupPolicy.enabled` is on (`PickupRuleGateImpl`). A card's "Aktiv" chip therefore requires the master switch too.
- A8: the phone layout of the blocked chip shows lock + "Gesperrt" (reverses the "lock only" phone rule in `pickup-slot.component.ts`).

## Scope
In: A1, A2, A3, A7, A8 as above. Out: everything else, including store-tab chips other than the blocked one, automation refactor, the store page default tab.

## Changes
**Server** (no contract change)
- `pickup/service/PickupUserService.options()`: per store, build `schedule.context(store, userId)` once and evaluate the free candidates (`EligibilityContext.evaluate(pickupAt, slot)`). Drop slots where `!result.eligible()` and the user is not signed up. Store managers keep their exemption only for store rules (as today). Update the Javadoc ("none that the store rules or the eligibility chain block").
- Reuse `EligibilityContext` (it already caches the rule sign-ups per store). Do not add a query per pickup.

**Client**
- `features/pickups/components/pickup-options-list.component.ts` and `help-needed-card.component.ts`: `option.isConfirmed != null` → chip (`data-testid="pickups.options.signedUp"` / `dashboard.help.signedUp`) instead of the button. Shared helper `isSignedUp(option)` in `pickups.util.ts`.
- `shared/eligibility-reasons/eligibility-reasons.component.ts`: replace `shared.eligibility.rule` with two optional lines `shared.eligibility.ruleTimespan` ("Bezirksregel: höchstens {limit} Abholungen innerhalb von {days} Tagen vor oder nach diesem Termin. Du hast in dieser Zeit schon {count} andere.") and `shared.eligibility.ruleDay` ("Höchstens {dayLimit} pro Tag. An diesem Tag hast du schon {sameDay} andere."). Show each only when its limit > 0. Show none when `ALREADY_SIGNED_UP`. Use ICU plurals. Fill in all 5 languages and delete the old key.
- `features/automation/sections/pickup-policy.section.ts`: `gapActive()` and every other rule-card status chip in the section include `draft.enabled`. When the master is off: chip "Aus" plus `automation.policy.inactiveHint` ("Wirkt erst, wenn die Regeln eingeschaltet sind"). Header (`tabs/store-automation.tab.ts`, `policy-sentences.ts activeRuleCount`) unchanged.
- `shared/pickup-slot/pickup-slot.component.ts`: remove the phone media rule that hides `.slot__label` of `.slot--blocked`, so the chip keeps its label and grows in width.
- `shared/navigate-button/navigate-button.component.ts`: make the trigger a `pButton` (ButtonDirective, `severity="secondary"`, `[outlined]="true"`, `size` from `small()`). Reason to verify first on Pixel 7 (`stores/:id/pickups`): the button styles are only injected when a PrimeNG button is instantiated. If the cause turns out to be different, fix that cause and note it.

## UI
Visible changes: the chip in two lists, the dialog text, the automation cards, the phone slot chip and the navigate button. No mockup.

## Tests
- New: server unit test for `options()` filtering (region rule over the limit, day limit, hygiene missing, signed-up slot kept). Put it next to `PickupEligibilityRulesTest` or extend `PickupUserApiIT` (seed persona Sarah: 2 per 7 days).
- New: `eligibility-reasons` component spec (texts, hidden on `ALREADY_SIGNED_UP`, limit 0 hidden). `pickups.util.spec.ts` gets `isSignedUp`. `automation.spec.ts` gets chip off while master off.
- Changed on purpose: `pickup-slot.component.spec.ts` (label on phones), `navigate.spec.ts` if it checks classes, `pu7-options.spec.ts` / `a19-home-cards.spec.ts` (signed-up chip, blocked pickups gone), any spec asserting `shared.eligibility.rule`.
- E2E `@PU7`: Sarah does not see region-rule-blocked pickups; a signed-up pickup shows the chip. Screenshot states for both lists and the phone store pickups tab.

## Done when
- [ ] No "Eintragen" on a pickup the sign-up would refuse or the user is already in (both lists)
- [ ] Dialog rule text plain, correct, in 5 languages; `npm run i18n:check` green
- [ ] Automation chips agree with the header when rules are off
- [ ] Phone: "Gesperrt" visible, "Navigieren" styled
- [ ] Server and client tests green; ledger row updated

## Risks
- Query budget of `listPickupOptions` (one context per store). Check the existing query-count test, if there is one.
- Sub-package A7 + A8 is fixer-sized (client only, about 3 files) and can run in parallel with A1–A3.
