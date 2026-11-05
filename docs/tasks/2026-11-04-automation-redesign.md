# Automatisierung redesign (AU1–AU10, owner-approved design 2026-11-04)

Status: design approved (`docs/design/automation-redesign-proposal.md`, Q1–Q10 decided, all recommendations accepted; mockup
`docs/design/automation-redesign-mockup.html`, screens `rules`, `pickups`, `blocked`, `linked`, `picker`, `channels`, `region`).
**Four slices, one branch + PR each, merged in order S1 → S2 → S3; S4 may run in parallel with S3** (S4 needs S2's sentence util).
Longer than the usual 120 lines on purpose: four independently mergeable slices. Record as **one decision (next free; recorded as 167, since main took 165 and 166
during the merge)** amending 59/60, and update ledger AU1/AU2/AU3/AU4/AU7/AU8/AU10, `docs/conventions.md` l.215 (STORES_COORDINATION grants).

## Goal
Rules that block instead of "declining later", visible to the members they affect, with the linked store booked in the same step,
configured by every store manager without extra permissions.

## Decisions
1. No observe mode anywhere (Q2): `dryRun` leaves policies, request policy, adverts, run results, region overview and
   `app.automation.dry-run`. Existing dry-run rows become **disabled** (Q9); seed S-01 off, S-06 stays live (Q10).
2. No automation permissions: `CAN_USE_AUTOMATIONS`, `CAN_SEE_ALL_AUTOMATION_DECISIONS`, `CAN_SEE_USER_PICKUP_COUNT_GROUPING` go.
   Store automation + store Telegram chats = `mayEditStore`; region pages = `mayAdministrateRegion` or ORGA; audits: store manager all
   of the store, ambassador/ORGA all of the region, everyone else own.
3. Dry-run-only endpoints go (Q6): `runPickupPolicy`, `runRequestPolicy`, `previewPickupPolicy`. `runAdverts` stays, no `dryRun`.
4. `WOULD_*` / `DRY_RUN_SUMMARY` / `*_WOULD_*` stay as read-only enum values (Q8); not written, not in filters or metric groups.
   Audit tables keep their `dry_run` column (always false) until that cleanup; `AuditEntry.dryRun` stays read-only.
5. **Config `dry_run` columns stay physically** (unmapped, default switched to false): the seed changesets 105–108 `loadData` them
   and run after all schema changesets, so dropping them would break a fresh build; editing applied seed changesets is forbidden.
   Dropping them is a `todo.md` item together with Q8's cleanup.
6. Rules are checked **before** the sign-up is stored: a violated rule → 409 `PICKUP_NOT_ELIGIBLE` reason `AUTOMATION_RULE`;
   excluded date / holiday → stored unconfirmed for the managers; all rules pass → confirmed by the existing after-commit runner.
7. Links (Q3): one-way needs `mayEditStore(X)` + `StoreVisibility.mayKnowStores`, across regions; `bidirectional` needs
   `mayEditStore(Y)` for every linked store, else 403 `ACCESS_DENIED` `args.storeId`. Any-of within a rule, all rules required (Q5).
8. Sign-out of the linked Y pickup (Q4): X gets "Verknüpfung fehlt", the person has `graceMinutes` (*Frist nach Austragung*,
   default 120) to book another Y pickup, then X is freed with the rule's message. `graceMinutes` keeps its column, new meaning.
9. Member summary = card on the Pickups tab (Q1); default section strip + Regeln (Q7). `/pickups` options and the home
   help card hide rule-blocked pickups (same gate, no chip there).

## Scope
In: the four slices below. Out: gap-list picker (`listGapCandidates`, decision 151/152 unchanged), CSV import `dryRun`
(`StoresCsvImportService`, `RegionsCsvImportService`, m7-launch/s2 e2e: unrelated), removing the `WOULD_*` values, dropping columns.

## Changes

### S1 Remove dry run and automation permissions (@AU1 @AU2 @AU3 @AU4 @AU7 @AU8)
- Contract `openapi/paths/automation.yaml`: delete `/pickup-policy/run`, `/pickup-policy/preview`, `/request-policy/run`, the `dryRun`
  query param of `/adverts/run`; every store `x-permission` → `mayEditStore(store)`; region paths → `mayAdministrateRegion or ORGA`
  (audits: `…; else onlyMine`). `schemas/automation.yaml`: drop `dryRun` from `PickupPolicy`, `RequestPolicy`, `OpenSlotAdvert`,
  `AutomationRunResult`, `RegionAutomationStore`. `paths/telegram.yaml` store chats → `mayEditStore(store)`. `schemas/common.yaml`
  l.431 `Permission` enum minus the three. `schemas/stores.yaml` l.423: drop `StorePermissions.mayUseAutomations`. Regenerate.
- Liquibase `changes/188-automation-no-dry-run.yaml` (187 is taken by the store-pickups batch; recheck before commit): for
  `pickup_policy`, `request_policy`, `open_slot_advert`: `UPDATE … SET enabled = false, dry_run = false WHERE dry_run = true`,
  `addDefaultValue dry_run false`; `DELETE FROM region_permission WHERE permission IN (…three…)`. H2 + PostgreSQL.
- Seed `seed/changes/188-automation-no-dry-run-seed.yaml`: same UPDATEs (seed loads after 188) + delete region_permission seed
  rows `00000010-0009-4000-8000-00000000000{4,5,6}`. No new ids.
- Server: `security/Permission.java`, `config/AppProperties.java` l.194 + `application.properties` l.130 (STORES_COORDINATION grants
  nothing automation-related; drop the key if empty) and l.219 `app.automation.dry-run`. `StorePermissionService.mayUseAutomations`
  removed, callers → `mayEditStore` (`PickupPolicyService` l.148/292/297/440/487/680, `OpenSlotAdvertService`, `SignoutAlertService`,
  `AutomationRunService`, `TelegramChatService`, `StoreAlertAccessAdapter`, `StoreQueryService`, `StoreMapper`, `StoresController`).
  `AutomationAuditService` + `AutomationHistoryAccessRule`, `PickupCountGroupingService`, `CleaningExemptionService`,
  `MetricArchiveService`, `FspCleaningRegionService`: ambassador/ORGA branch only. Domain `PickupPolicy`, `RequestPolicy`,
  `OpenSlotAdvert` lose the `dryRun` field; `PickupPolicyRunner` (`effectiveDryRun`, `RunResult.dryRun`, `runStore(…, forced)`),
  `RequestPolicyRunner`, `OpenSlotAdvertRunner`, `PickupPolicyEvaluator.Spec` lose it; the 5-min job calls the runner directly.
  Mirror policies are created disabled. Audits write `dry_run = false`. `AutomationMapper`, `AutomationController` follow.
- Client: `features/automation`: one switch **Regeln anwenden** (`automation.policy.enabled`) in `pickup-policy.section.ts`,
  An/Aus in `request-policy.section.ts`; remove Testmodus, run/test buttons, result banners, `components/preview-dialog.component.ts`;
  `adverts.section.ts` keeps run now; `automation.util.ts`, `audit-table.component.ts`, `metric-groups.ts`: no WOULD_* in filters/groups.
  `stores/pages/store-page.component.ts` tab → `p.mayEditStore`; `tabs/store-automation.tab.ts`, `pages/region-automation-page.component.ts`,
  `pages/region-sections.ts`, `regions/pages/region-page.component.ts` l.60 → `isAmbassador || isOrga`; askWho texts per §3.7.
  i18n: remove the keys in proposal §5 "Removed keys" + `regions.permission.<three>.*`; add `enabledHint`, `disabledHint`, askWho (5 languages).
- History: unchanged (policy saves already record; the migration has none).

### S2 Up-front blocking, member summary, blocked slot + reason dialog (@AU1 @PU3 @PU4)
- Contract: `schemas/pickups.yaml` `PickupEligibilityReason` + `AUTOMATION_RULE`; `PickupEligibility.ruleBlocks: PolicyBlock[]`
  (`PolicyBlock {rule: PolicyRule, params: PolicyBlockParams}`, params per proposal §8.3; `PolicyRule` ref from automation.yaml).
  New `GET /api/stores/{storeId}/automation/rules` (`getStoreRules`, `maySeePickups`, tag automation) → `StoreRuleSummary` (enabled,
  gapDays, group {days, stores: StoreRef[]}, links [{stores, windowMode, before, after}], experience, cleaningMonths, manualDates,
  holidays). `signUp` 409 documents `args.reason=AUTOMATION_RULE`.
- Server: SPI `pickup/service/PickupRuleGate.java` (interface; pickup must not depend on automation, `ModulesTest`
  l.97) with `Map<Instant, List<RuleBlock>> blocks(storeId, userId, Collection<Instant>)`; no-op default. Impl
  `automation/service/PickupRuleGateImpl.java` reuses `PickupPolicyEvaluator` (no audit) with batched `Data` (one query set per
  list, `QueryCountIT` budget for `listPickups`). `Reason` gains a structured `params` record next to the text (`PolicyTexts` still
  renders mails/audits). Store managers (`mayConfirmPickups`) get no blocks. `PickupEligibilityRules`/`listPickups` mapper fill
  `ruleBlocks`; `PickupSignupService.signUp` refuses before insert and the gate writes a `DECLINED` audit (`@NoHistory(reason="attempt
  audit, no state change")` or audit only). `listPickupOptions` filters blocked pickups. Runner: after-commit path only confirms;
  job keeps the decline path (policy switched on later). New `AutomationController.getStoreRules` via `PickupPolicyService`.
- Client: `shared/pickup-slot/pickup-slot.component.ts` new free state `slot--blocked` (inputs `blocked`, `blockedRuleCount`, output
  `blockedClick`, `<button aria-haspopup="dialog">`, spec §3.3/§4). New `pickups/rule-sentences.ts` (pure: summary lines + reason
  sentences from `StoreRuleSummary`/`PolicyBlock`, never server text), `pickups/components/rule-summary-card.component.ts`
  (`<details>`, `localStorage` open state in try/catch, `loadResource` + `<fs-data-state>`), `pickups/components/blocked-reason-dialog
  .component.ts` (actions: jump to next day, cleaning link, linked → opens S3 step, until then the store link). Wire in
  `pickups/tabs/store-pickups.tab.ts`; eligibility reasons (`pickups.eligibility.*`) use the same chip and dialog. Audit label
  `DECLINED` → "Gesperrt". Keys `pickups.rules.*`, `pickups.blocked.*`, `shared.pickupSlot.blocked*`.

### S3 Linked-store booking (@AU1 @PU3)
- Contract: `signUp` optional body `SignupRequest {linkedPickups: [{storeId, pickupAt}] (max 5)}`; `ApiErrorCode` +
  `LINKED_PICKUP_REQUIRED` (args.rule), `LINKED_PICKUP_FULL`, `LINKED_PICKUP_BLOCKED`; new `GET /api/stores/{storeId}/automation/
  linked-options?pickupAt=` (`getLinkedOptions`, `maySeePickups`) → per rule {stores, window, from, to, satisfiedBy?, options[{store,
  pickupAt, freeCount, member, ruleBlocks}]}. `PickupSlot.linkMissingUntil` (own slot and managers only).
- Liquibase `changes/189-pickup-link-pending.yaml`: table `pickup_link_pending` (std columns, store_id, pickup_at, user_id,
  linked_store_id, deadline_at; `uq_pickup_link_pending_store_id_pickup_at_user_id`, FKs, `ix_…_deadline_at`); retention entry
  (`RetentionTableTest`). Seed `189-pickup-link-pending-seed.yaml` one row (S-04 → S-01), new range in `docs/seed-ids.md`.
- Server: `PickupRuleGate` gets `check(storeId, userId, pickupAt, plannedLinked)`; `PickupSignupService.signUp` books X and every
  linked pickup in one transaction (each through the normal eligibility + gate of its store; any failure rolls back all), history
  `SIGN_UP_SLOT` per slot as today. Automation listens to `PickupSignedOutEvent` (beside `SignoutAlertService.onSignedOut`): unsatisfied
  X → `pickup_link_pending` row (HistoryService event, system actor) + bell/push (new `NotificationType.PICKUP_LINK_MISSING`, icon
  in the client icon map); a new Y sign-up deletes it; the 5-min job frees X after `deadline_at` with `renderLinkedStoreMessage`
  (replaces the signedUpAt-based grace in `PickupPolicyEvaluator` l.225).
- Client: `pickups/components/signup-dialog.component.ts` step 2..n (`satisfied`, `choose`, `none free`, `not member`, cancel) per
  §3.4, one `runMutation` for all; "Verknüpfung fehlt" pill on the own slot opens the same dialog in repair mode. Keys `pickups.linked.*`.

### S4 Cross-region picker, Kanäle, tab redesign (@AU1 @AU8 @AU10)
- Contract: `GET /api/stores/{storeId}/automation/link-candidates?q=&size=20` (`searchLinkCandidates`, `mayEditStore`; q ≥ 2) →
  `LinkCandidate {store: StoreRef, regionPath: string[], automationEnabled, mayEdit}`; `StoreAutomation.telegramChatCount`,
  `RegionAutomationStore.telegramChatCount`. Name search in the store module (`StoreQueryService`, bounded by
  `StoreVisibility.mayKnowStores`, paged). `PickupPolicyService` link validation per decision 7 (`GapLinkConflicts` unchanged).
- Client: extract `RegionTelegramSection` (`region-sections.ts` l.440–595) into `automation/components/telegram-chats.component.ts`
  (`fs-telegram-chats`, input `scope: {regionId}|{storeId}`), reused by the region page and new `sections/channels.section.ts`
  (route `channels` in `automation.routes.ts`, "Wer sendet hierhin?" card); "Chats verwalten" link in adverts/sign-out sections.
  `store-automation.tab.ts` status strip `automation.store.summary`; `pickup-policy.section.ts` rule cards with the member sentence from
  `rule-sentences.ts` (split the 908-line file into card components), bidirectional checkbox locked per `mayEdit`, *Erweitert* with
  `graceMinutes` + message; new `components/store-picker-dialog.component.ts`; section label Protokoll; region overview columns Regeln
  An/Aus + Kanäle. Keys `automation.picker.*`, `automation.channels.*`, `automation.store.summary.*`, `automation.policy.*` (§5).

## UI
Design done: proposal §3–§7 and the mockup are the spec (states table §3.7, tokens §4, a11y/412 px §6, test ids §7).
Every new data element `loadResource` + `<fs-data-state>`, mutations `runMutation`, all text in de/en/gws/fr/it, `npm run i18n:check`.

## Tests
- Unit/server: `PickupPolicyEvaluatorTest` params per rule; `PickupRuleGateImplTest`; signUp refusal + linked atomic booking +
  rollback (`PickupPolicyFlowIT`); pending/grace job; link rule permissions (one-way across regions, bidi lock); migration on H2 +
  `LiquibasePostgresIT`; `QueryCountIT` for `listPickups`/`linked-options`/`link-candidates`; client `rule-sentences.spec.ts`,
  slot blocked state, dialogs, picker states.
- Changed on purpose: `PickupPolicyRunnerTest`, `RequestPolicyRunnerTest`, `OpenSlotAdvertRunnerTest`, `PickupCountGroupingAndMetricsTest`,
  `PickupPolicyServiceGapTest`, `AdvertFlowIT`, `PickupPolicyFlowIT`, `FspCleaningRegionServiceTest`, `PermissionServiceMatrixTest`,
  `RegionPermissionResolverTest`, `RegionCapabilitiesTest`; client `automation.spec.ts`, `metric-groups.spec.ts`,
  `pickup-slot.component.spec.ts`, region-page spec; e2e `automation/_automation.ts`, `au1-pickup-policy`, `au1-gap-store-list`,
  `au2-ownership` (the CAN_USE_AUTOMATIONS user 0011 is now forbidden; a store manager passes), `au3-auto-accept`, `au4-adverts`,
  `au8-audits`, `au11-region-sender`, `pu4-eligibility`; screenshots `automation.screen.ts`, `pickups.screen.ts`.
- New e2e per slice with the tags above; screenshot specs for every state in §3.7; baselines via CI (decision 161) after each slice.

## Done when
S1
- [ ] No `dryRun` in automation contract/entities/UI; three endpoints gone; three permissions gone everywhere (`grep` clean except Q8 values)
- [ ] 188 migrates dry-run rows to disabled on H2 + PostgreSQL; fresh seed build green; S-01 off, S-06 live
- [ ] Store manager without any permission configures automation; ex-permission holder gets forbidden
S2
- [ ] Violating sign-up refused up front; members see the summary card, blocked chip and reason dialog with own dates
- [ ] `listPickups` within its `QueryCountIT` budget; managers never see a blocked chip
S3
- [ ] X + Y booked atomically from the dialog; all five step states; Y sign-out → pending, bell, grace, then X freed
S4
- [ ] Picker finds stores across regions; bidi locked without `mayEditStore(Y)` (server enforces); Kanäle section live; strip + rule cards per mockup
All: lint, i18n:check, unit, server, ArchUnit, `ModulesTest`, targeted e2e green; decision + ledger + `todo.md` (drop columns / WOULD_*) updated

## Risks
- Per-pickup evaluation in `listPickups` is the hot path: batch `Data`, cache per request, keep evaluation without audit.
- Moving from "decline after commit" to "refuse before insert" changes PICKUP_NOT_ELIGIBLE flows and the race with the after-commit
  runner; keep the job's decline path for policies switched on with existing sign-ups.
- Atomic multi-store sign-up: lock order by (storeId, pickupAt) to avoid deadlocks; full slot in Y between options and submit → `LINKED_PICKUP_FULL`.
- Unmapped `dry_run` columns with a DB default: any native SQL insert must not rely on it; note in `todo.md`.
- S2/S4 both edit `automation.yaml` and the five i18n files: merge S2 first, rebase S4.
