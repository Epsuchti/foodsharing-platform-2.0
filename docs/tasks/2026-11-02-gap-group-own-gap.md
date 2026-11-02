# Gap rule: separate group gap (AU1, follow-up to decision 151)

## Goal
A gap group gets its own minimum gap, separate from each store's own gap. The own gap checks pickups in that store only. The group gap checks pickups in all stores of the group. The two checks are independent, and either can reject a sign-up. The group check also runs when the store's own rule is off. Everything else from decision 151 stays as it is: D2–D5, D7 semantics, who loses a conflict, live reads and the failure path.

## Decisions
Owner answers (2026-11-02). Record them as decision 152.
- D1 **Group gap.** `pickup_gap_group.minimum_gap_days` (the column already exists) is the group gap: 1–60, shared by all members. Saving the list writes only the group row, never a member's own gap or switch. A store that newly joins (or gets a policy created for it) keeps its own rule as it was; a created policy stays off, in dry run, with its own rule off.
- D2 **Two independent checks.**
  - Own check: the current single-store path. It runs if `gapRuleEnabled` and uses the own value.
  - Group check: the current `groupGap` (D3/D4 of decision 151) with the group value. It runs whenever the store has at least one other live member, whatever the own switch says.
- D3 **One GAP reason** (owner Q2). If only one check fails, use its text. If both fail:
  - When own gap ≤ group gap, drop the own sentence (the group check already covers own-store pickups).
  - Otherwise both sentences, joined by `\n`, in one `Reason(GAP, …)`.
  - So `SLOT_DECLINE_RULE.GAP` is still counted once, and texts on deploy match today's.
- D4 **Membership.** Switching the own rule off no longer leaves the group. Leaving means sending an empty list or deleting the policy, and is always allowed (D6 of 151). A store left alone still drops out.
- D5 **Leave action (assumed, confirm at plan review).** The owner wrote: "if you turn off your own gap rule for a store and it's in a group we can remove the store from the group". Read as follows:
  - There is no automatic leave.
  - The group section shows an explicit "Diesen Betrieb aus der Gruppe entfernen" action whenever this store is in a saved group and (its own rule is off, or a member is locked).
  - The action clears the list in the draft; saving then sends an empty list.
  - This replaces the decision-151 D6 UI bullet ("no leave button; switching the rule off is the only way out").
- D6 **D7 conflict check** uses the group gap, on both save paths, whenever the list is not empty. It is independent of the own switch.
- D7 **Migration** (owner Q3): each group gets its value explicitly from the gap its members use today; each store keeps its own gap and switch. **The store's own value applies again on its own.** On deploy it equals the group value, so nothing is rejected differently and the texts stay the same (D3). From the first later edit, the own value can reject differently:
  - Lowering the group gap does not lower members' own gaps. Their own-store pickups are still rejected at the old value.
  - A store's own gap set above the group gap rejects its own-store pickups at that higher value.
  The migration comment, decision 152 and the AU1 ledger note say this plainly. The UI says it too: "Der eigene Abstand jedes Betriebs gilt zusätzlich."
- D8 **UI** (owner Q4): an "Aktiv"/"Aus" header badge (own rule on or list not empty); when the list goes from empty to one store, prefill the group gap with `max(1, own gap)`. No mockup update (owner declined).
- Tag `@AU1`. Changelog and seed 182.

## Scope
- **In:** contract field, evaluator, service, migration, seed, mapper, gap card (two parts, two previews, badge, leave action), texts in all languages, tests, docs.
- **Out:** changes to D2–D5/D7 semantics of 151, request policy, metrics keys, history actions (only metadata values change), and a list-level dry run.

## Changes
Start after commit 4c18182 (picker overlay fix, worktree-agent-ac4cda05f7531c618) is merged into `integration/2026-11-01`. It touches the same e2e spec and screenshot file.

**Contract** (`openapi/schemas/automation.yaml`, `PickupPolicy`). Regenerate both sides.
- New `groupMinimumGapDays` (int32, 1–60, nullable): required with a non-empty `gapStoreIds`, null in responses without a group.
- Reword `gapStoreIds`: only the group gap is shared; an empty list leaves.
- Errors: `VALIDATION_FAILED` with field `groupMinimumGapDays`.

**Liquibase** (`db/changelog/changes/182-gap-group-own-gap.yaml`). One `sql` changeSet that runs on H2 and PostgreSQL:
`UPDATE pickup_gap_group g SET minimum_gap_days = GREATEST(1, (SELECT MAX(p.minimum_gap_days) FROM pickup_policy p WHERE p.gap_group_id = g.id)) WHERE EXISTS (SELECT 1 FROM pickup_policy p WHERE p.gap_group_id = g.id)`.
The comment carries the D7 text. Members' rows are untouched.

**Seed** (`db/seed/changes/182-gap-group-own-gap-seed.yaml`, context `seed`, `runOnChange`).
- S-03 (policy `00000022-0001-…-04`) goes back to `gap_rule_enabled=false`, `minimum_gap_days=0` and stays in group `0022-0016-…-01` (gap 2). This gives the "own rule off, group on" case.
- S-01 keeps own 2, rule on. S-03's policy is off, so no seeded outcome changes.
- No new IDs; add a note in `docs/seed-ids.md`.

**Server (automation)**
- `PickupPolicyEvaluator`:
  - `Spec` + `groupMinimumGapDays` (keep the short constructor; 0 without a group).
  - Replace the `if/else` at lines 199–211 with the two checks and the D3 merge.
  - `groupGap` takes the minimum as a parameter.
- `PickupPolicyData.spec`: read the group row via `PickupGapGroupRepository`.
- `PickupPolicyService`:
  - `PolicyInput` + `groupMinimumGapDays`.
  - In `put`: `grouped = !wantedGap.isEmpty()`; own gap 0–60 always; group gap 1–60 when grouped (move that check out of `validateGapList`).
  - In `validateGapList`, `changes` = new member or group gap changed.
  - `GapLinkConflicts.firstBlocking(list, groupGap, …)`.
  - `gapChanged` compares the group gap.
  - In `writeGapList`, drop the writes of `setGapRuleEnabled(true)` and `setMinimumGapDays`.
  - `recordGapList` metadata `minimumGapDays` = group gap.
  - Fix the Javadoc of `writeGapList` and `leaveGapList`.
  - `PolicyView` exposes the group gap.
- `AutomationMapper`: map the new field both ways. `PickupGapGroup` Javadoc: the row now holds the group gap.
- No new message keys: `automation.rule.gap` and `automation.rule.gap.group` stay.

**Client** (`features/automation/sections/pickup-policy.section.ts`, `components/gap-preview.component.ts`, `automation.util.ts`)
- The card gets a header badge `automation.policy.gapStatus` (on/off) and two parts:
  - **"In diesem Betrieb":** switch (now "Eigener Abstand"), own gap field (0–60, never locked by members), own `fs-gap-preview` with single-store text.
  - **"Betriebsgruppe (optional)":**
    - the existing picker and members, shown even when the own rule is off
    - group gap field `automation.policy.groupGapDays` (shown with a list; locked when a member is locked; prefill per D8)
    - note with the additional-own-gap sentence, warnings, the D5 leave action (`automation.policy.gapLeave`), and the group preview with group text
- `gap-preview.component.ts`: add a `testId` input (own `automation.policy.gapPreview`, group `automation.policy.groupGapPreview`) and a unique heading id per instance.
- `gapWarnings`, `gapBlocked` and the link-card warnings use the group gap and no longer depend on `gapRuleEnabled`.
- `payload()` always sends `gapStoreIds`, plus `groupMinimumGapDays` when the list is not empty.
- Texts in `automation/i18n/{de,en,gws,fr,it}.json`: reword `gapHint`, `gapNote`, `gapLockedGap`, `gapLockedLeave`, `gapMinWithList` (now for the group field); new `gapStatus.on|off`, `gapOwnTitle`, `gapGroupTitle`, `groupGapDays`, `gapOwnAlso`, `gapLeave`; the field error in `core/i18n` if needed.
- Test IDs `automation.policy.gapStatus|groupGapDays|gapLeave|groupGapPreview`.

**Docs:** decision 152 (including the D6 bullet it replaces), the AU1 ledger note, and the reworded description of 151 in `docs/decisions.md` (search `| 151 |`).

## UI
Yes, a visible gap card change (two parts, two previews, badge, group field, leave action). No designer mockup: the owner declined. Build from this plan and keep the styles of the existing card.

## Tests
- **`PickupPolicyEvaluatorTest`**, new cases: own gap only; group gap only (own rule off) blocking an other-store and an own-store pickup; own 3 / group 1 and own 1 / group 3; both fail with own ≤ group → only the group sentence, own > group → both sentences in one GAP reason; a group without live members → own check only.
- **`PickupPolicyServiceGapTest`**, changed on purpose: "rule off leaves" becomes "rule off keeps the group"; "save writes gap and rule on to members" becomes "only the group row changes". New cases:
  - group gap required and 1–60 with a list
  - 403 only when the group gap or membership changes
  - D7 with group gap 2 and own gap 0 blocks; own 5 with group 1 does not block
  - empty list with a locked member leaves
- **`PickupPolicyFlowIT`:** own rule off, group on → cross-store `DECLINED` with history.
- **Migration:** a test (H2) that the group value equals the members' gap after 182. Run on PostgreSQL in CI.
- **Client `automation.spec.ts`:** payload always carries the list and group gap (the old "rule off drops the list" case changes on purpose), prefill, badge, the leave action's visibility, and `gapBlocked` on the group gap.
- **e2e `au1-gap-store-list.spec.ts @AU1`:** reworked.
  - Test 1 sets the group gap.
  - Test 2: rule off keeps the list (line 91 changes); the leave action empties it.
  - New: own-off/group-on, and different numbers via the preview API.
  - `automation.screen.ts`: states with both previews, the badge and the leave action. No baselines (decision 69); the untracked `automation-gap-*` PNGs go stale.
- Keep green: `ArchitectureTest`, `ModulesTest`, `MetricKeyCoverageTest`, `LocalProfileSmokeIT`, `npm run i18n:check`.

## Done when
- [ ] Contract regenerated; 182 applies on H2 and PostgreSQL; the seed loads with S-03 own rule off and in the group
- [ ] Own and group checks independent; D3 single reason; the four owner scenarios covered by unit, IT and e2e tests
- [ ] Rule off keeps the group; the leave action works; D7 uses the group gap
- [ ] Card shows the badge, two parts, two previews and the prefill; `i18n:check` passes
- [ ] Decision 152, ledger note and migration comment state that own values apply again
- [ ] Implementer lists what it couldn't test

## Risks
- **Own values apply again after the first edit (D7):** a lower group gap leaves members' own gaps stricter. Visible only on each store's own page; mitigated by the note.
- **Pre-deploy SQL check:** members with `gap_rule_enabled=false` and a `gap_group_id`, and groups with fewer than 2 members. The current code never produces them; if any exist, such a member starts being group-checked on deploy.
- **D5 is an interpretation;** confirm at plan review.
- **e2e and screenshot file overlap** with the merged overlay fix: rebase first.
