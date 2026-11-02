# Pickup gap rule: shared store list (AU1 extension)

## Goal
Add an optional list of stores to a store's gap rule. A pending sign-up in any listed store is then also checked against the person's pickups in the other listed stores, using the one shared minimum gap. This closes the loophole of booking another store of the same chain. With an empty list, nothing changes.

## Decisions
Owner answers (2026-11-01). Record them as decision 151.
- D1 **One gap, one list.** Both are shared. Saving in one listed store writes the gap and "rule on" to all listed stores. A store is in at most one list. Max 10 stores, counting the store itself. With a list, the gap is 1–60.
- D2 **Gap and timing as today.** `calendarGapDays` counts free days in the store's zone. `rulesAreSkipped` ignores past pickups and pickups from two days before onwards, and skips sign-ups that close to the pickup.
- D3 **Same store exactly as today** (Q2). Every other own-store pickup counts, pending or confirmed, except one at the same time. No tie-break.
- D4 **Other listed stores follow new rules.** A pickup there blocks if it is confirmed, if either sign-up time is missing, or if it was signed up strictly earlier (equal times block both). A same-time pickup in another store counts. A member without an enabled policy blocks with its pickups; its own sign-ups aren't checked.
- D5 **Live data, no re-read** (Q1). A database error takes the existing path: `FAILED` audit, log, retry by the 5-minute `automation-evaluate` job.
- D6 **Who may change the list** (Q4).
  - Candidates are live stores in the store's region subtree, plus the current members.
  - Changing the gap or the members needs `mayUseAutomations` on every store whose gap or membership changes; otherwise 403 `ACCESS_DENIED` with arg `storeId`.
  - Leaving is always allowed: rule off, empty list, or policy deleted.
  - In the UI (owner, designer round 2026-11-01): no "Liste verlassen" button; leaving means removing the members or switching the rule off. A member the person may not change (no `mayUseAutomations` there) is shown locked and cannot be removed, so the list stays; members the person may change stay removable. With a locked member the only way out of the group is switching the rule off, which leaves (previous bullet) and keeps the gap as this store's own rule, switched off. The locked row says so.
  - A store left alone drops out of the group and keeps the gap as its own rule.
  - A member without a policy gets one, switched off and in dry run (as mirrors do).
- D7 **Linked-store conflict** (Q3). A link rule's reach is same-day = 0, otherwise `max(before, after) − 1`. It conflicts when its reach is less than the gap.
  - Saving is blocked with 400 `GAP_LINK_CONFLICT` (`storeId`, `otherStoreId`) only if every store of the rule and its owner are in the list. Otherwise: a warning.
  - Checked on both save paths (gap list and links).
- D8 **Rollout** (Q5). Per-store dry run, no extra flag. Keep listed stores in dry run, review `WOULD_DECLINE` GAP audits, then go live.
- D9 **Rule ID and texts.** Stays `PolicyRule.GAP`, so metrics and decision 142 don't change.
  - With a list, a new text: "Du hast bereits am {Datum} eine Abholung bei {Betrieb}. Zwischen zwei Abholungen bei {Betriebe} müssen mindestens {Abstand} liegen; hier wären es nur {tatsächlicher Abstand}."
  - Without a list: `automation.rule.gap`, unchanged.
  - Tag `@AU1`. Changelog and seed 181. Seed table code `0022-0016`.

**Request vs platform (findings).**
- Cache, re-read and "can't read another store" don't apply: pickups are native and read live (D5).
- The zone comes from the region, not a fixed Europe/Zurich.
- `signedUpAt` (`createdAt`) is NOT NULL, so the missing-time fallback is defensive only.
- "Stores they manage" maps to `mayUseAutomations`.
- A slow sign-up that commits after a later one can lose to it, because the later one is already confirmed. Accepted.

## Scope
- **In:** the shared list and gap; the cross-store check; D7 on both sides; the candidates endpoint; the gap card with live preview; history, seed, tests, docs.
- **Out:** a list-level dry run; stores outside the region subtree; changes to the same-store behaviour, request policy, adverts and metrics.

## Changes
Start only after the Fairteiler cleaning rewrite (`2026-11-01-fairteiler-cleaning-shifts.md`) is merged into `integration/2026-11-01`. It edits the same evaluator, data, service, test, `automation.yaml`, message bundles, seed 105 and cleaning card. The designer mockup must be approved first.

**Contract.**
- `schemas/automation.yaml`:
  - `PickupPolicy` gets `gapStoreIds` (uuid[], maxItems 9, the other members) and read-only `gapStores[]` (`store: StoreRef`, `automationActive`, `mayEdit`).
  - New `GapCandidate` (`store`, `automationActive`, `inOtherList`, `links[]` of `{storeIds, windowMode, windowDaysBefore, windowDaysAfter}`, own and mirrored).
- `paths/automation.yaml`: `GET /api/stores/{storeId}/automation/gap-candidates` (`listGapCandidates`, mayUseAutomations).
- `schemas/common.yaml` `ApiErrorCode`: `GAP_LIST_TOO_LARGE`, `GAP_LIST_STORE_TAKEN` (`storeId`), `GAP_LINK_CONFLICT`. A gap of 0 with a list fails with `VALIDATION_FAILED` (`minimumGapDays`).
- Regenerate both sides.

**Liquibase and seed.**
- `db/changelog/changes/181-pickup-gap-group.yaml`: `pickup_gap_group` (BaseEntity columns, `minimum_gap_days INT NOT NULL`) and `pickup_policy.gap_group_id UUID NULL` (FK `SET NULL`, indexed). Runs on H2 and PostgreSQL.
- `db/seed/changes/181-automation-gap-group-seed.yaml` + `db/seed/automation/pickup_gap_group.csv`, plus a `pickup_policy` loadUpdateData: S-01 + S-03 (policy `…-04`, disabled, which gives the "no automation" state), gap 2. Check that no M3/AU scenario changes outcome.
- Update `docs/seed-ids.md`.

**Server (automation).**
- `PickupGapGroup` entity, its repository, `PickupPolicy.gapGroupId`, `PickupPolicyRepository.findByGapGroupId`.
- `PickupPolicyEvaluator`:
  - `Spec` + `gapStoreIds`.
  - The gap block keeps the own-store path (D3) and adds the other-store path (D4). Read the own `signedUpAt` as at line 197.
  - The first blocking pickup by date gives the group text.
- `PolicyTexts`: `date(...)` and a conjunction helper `all(names)`.
- `PickupPolicyData.spec`: fill `gapStoreIds` (live members only). No new `Data` methods: `signupsOfUser` already takes several stores.
- `PickupPolicyService`:
  - `PolicyInput.gapStoreIds`. In `put`: validate D1/D6/D7, lock the group row (pessimistic), write every member's gap and `gapRuleEnabled`, create, merge or dissolve the group.
  - `delete` and rule off leave the group.
  - `validateLinks` adds D7. Put D7 in a static `GapLinkConflicts` helper.
  - Member version conflicts → 409 `CONCURRENT_MODIFICATION`.
- Read-only `listGapCandidates`, reusing `StoreService.liveStores`, region store listing and `StorePermissionService.mayUseAutomations`.
- History: `AutomationAction.GAP_LIST_CHANGED` on every affected policy (`storeIds`, `minimumGapDays`), labels in `features/history/i18n/*.json`.
- `AutomationMapper` and `AutomationController`: new fields, plus the generated method only.
- `automation.rule.gap.group` in all six `i18n/automation/messages*.properties`.

**Client** (`features/automation/sections/pickup-policy.section.ts`, gap card, and a new `components/gap-preview.component.ts`).
- "Regel aktiv" and "Min. Tage Abstand" stay (min 1 with a list).
- Searchable `p-multiselect` "Gilt auch für diese Betriebe (optional)":
  - fed by `loadResource(listGapCandidates)` in `<fs-data-state>`
  - automation badge; `inOtherList` options disabled; non-editable members locked
  - `selectionLimit` 9; reuse `releaseOpenGuard`
- The shared-list note, the "no automation" warning, and the D7 warnings: blocking ones disable save and name the store; the same warning shows in the links card.
- Preview: 11 cells centred on an example pickup ("Abholung" / "Nein" / "OK"), the next possible date (pickup + gap + 1), and the message. The logic is a pure function in `automation.util.ts`.
- `payload()` + `gapStoreIds`. Texts `errors.GAP_*` in `core/i18n`; UI texts in `automation/i18n/{de,en,gws,fr,it}.json`.
- Test IDs `automation.policy.gapStores|gapNote|gapWarning|gapPreview|gapPreview.day`.

## UI
Yes, a visible gap card change. A designer mockup is required before implementing (owner): multi-select with badges and locked members, note, both warnings, and the 11-cell preview on phone and desktop.

## Tests
- **`PickupPolicyEvaluatorTest`**, new cases:
  - Mon/Wed = 1; same and next day = 0
  - other-store pickups: confirmed blocks; earlier pending blocks, later doesn't; equal times; null `signedUpAt`; same instant counts
  - past and within-2-days pickups ignored; a non-automated member blocks
  - the group text
  - An empty list works exactly as today; existing gap cases unchanged.
- **New `PickupPolicyServiceGapTest`:** limits and minimum gap, taken store, 403 naming the store, leaving always allowed, a lone store keeps its gap, created policy off and in dry run, D7 matrix (same-day, `max − 1`, partial list only warns, mirrors, both save paths).
- **`PickupPolicyFlowIT`:** a cross-store `WOULD_DECLINE` in dry run, then `DECLINED` with history.
- **Client:** `automation.spec.ts` (preview function, payload, warnings); `npm run i18n:check`.
- **e2e:** `e2e/tests/automation/au1-gap-store-list.spec.ts @AU1`, and `automation.screen.ts` states (list, warnings, preview, phone). No baselines (decision 69).
- Keep green: `ModulesTest`, `ArchitectureTest`, `MetricKeyCoverageTest`, `StableOrderTest`, `SeedDateShiftsTest`, `LocalProfileSmokeIT`.

## Done when
- [ ] Contract regenerated; changelog 181 applies on H2 and PostgreSQL; seed loads; `docs/seed-ids.md` updated
- [ ] Empty list behaves as before (existing tests unchanged and green)
- [ ] Cross-store check follows D3/D4; group text in all languages
- [ ] Shared save, leave and dissolve work with history; 403, 400 and 409 tested
- [ ] D7 blocks from both sides and warns for partial lists
- [ ] Gap card matches the approved mockup; `i18n:check` passes
- [ ] `@AU1` e2e passes; screenshot specs exist; decision 151 and AU1 ledger note added
- [ ] Implementer lists what it couldn't test (the commit-order race, non-Zurich zones)

## Risks
- Merge conflicts with the cleaning rewrite: implement after it lands.
- Saving one store changes other stores' gap and switches their rule on. The note and history must make this visible.
- A later sign-up may lose to an earlier pending one that another rule later declines. Accepted (D4).
- Archived members: filter to live stores in `spec` and the view.
- The client message copy can drift from the server bundle. Edit both in the same commit.
