# Fairteiler cleaning shifts and the cleaning rule (resume item 13, decision 136)

## Goal
A Fairteiler (FS1) can switch on cleaning shifts ("Putzeinsätze"): regular and one-time slots, sign-up confirmed by its managers, a derived team, history and statistics. Each region picks which of its own Fairteiler count for the cleaning rule (AU1). The rule reads those Fairteiler instead of the single `region.cleaning_store_id`, which is dropped.

## Decisions
Owner answers (2026-11-01):
- D1 Any verified foodsaver who isn't sleeping may sign up. The team is derived (the Fairteiler managers plus everyone with a cleaning sign-up, with shift count and last shift); there is no apply or join step.
- D2 Separate cleaning tables in the `fsp` module. The pure schedule arithmetic moves from `pickup.service.PickupSchedule` into a generic helper in `common`, used by pickup and fsp. `fsp` still may not depend on `pickup` or `store`.
- D3 A new changelog creates the link table and drops `region.cleaning_store_id` without moving data: store ids can't become Fairteiler ids, and the platform isn't live. As a deliberate exception to conventions §Seeds (record it as a new decision), remove the column from `db/seed/region/region.csv` and delete changeset `seed-105-7`. Zürich Stadt links Fairteiler Wiedikon, which has cleaning shifts.
- D4 Core scope only (see Scope: out).

Assumed, confirm at plan review:
- D5 An upcoming cleaning sign-up counts for the rule even while unconfirmed. This is the current behaviour (`signupsOfUser` ignores `confirmed`).
- D6 The decline and reminder texts list the next three free slots across all cleaning Fairteiler, as "date – Fairteiler name". The link goes to the cleaning tab of the Fairteiler with the earliest free slot, or else the first one configured.
- D7 Only Fairteiler in the region itself that are ACTIVE, not deleted and have cleaning switched on can be linked. Switching cleaning off, or deactivating or deleting a Fairteiler, removes its links (with history).
- D8 The region list is edited by whoever may edit cleaning exemptions today (ambassador or `CAN_USE_AUTOMATIONS` in the region, matching the `cleaning-exemptions` section). Others in the region only see it on the policy page.
- D9 The new feature ID is `FS2` (ledger row), and e2e is tagged `@FS2 @AU1`.

## Scope
In:
- Fairteiler cleaning switch.
- A cleaning tab on `/food-share-points/:fspId`:
  - slot list, sign-up and sign-out
  - managers confirm, decline or remove sign-ups
  - editors for regular slots and for one-time shifts and overrides
  - team list and simple statistics (shifts per person, last shift, total in the last 12 months)
  - history entries
- Bells: an unconfirmed sign-up to the managers; confirmed or removed to the person.
- The region "Putzen" (cleaning Fairteiler) section, the rule rewrite, and the policy page showing the region's cleaning Fairteiler.

Out (follow-ups):
- Cleaning shifts in "Meine Abholungen", the agenda, the dashboard card and the iCal feed.
- Reminder mails and jobs.
- Automatic confirmation by the pickup policy.
- Profile and region pickup counts.
- Account export and deletion contributors beyond the minimum (see Risks).
- Waiting for item 11: the slot redesign. Reuse `fs-pickup-slot` as it is.

## Changes
### Phase 1: contract, Liquibase, seed
- **Contract.** New tag `fsp-cleaning`: `openapi/paths/fsp-cleaning.yaml` and `openapi/schemas/fsp-cleaning.yaml`, mirroring the `pickups.yaml` shapes.
  - `GET/PUT /api/food-share-points/{fspId}/cleaning/regular-shifts`
  - `GET /api/food-share-points/{fspId}/cleaning/shifts?from&to`
  - `PATCH /api/food-share-points/{fspId}/cleaning/shifts/{shiftAt}` (slot count or description; 0 cancels)
  - `POST /api/food-share-points/{fspId}/cleaning/shifts/{shiftAt}/signups`
  - `DELETE /…/signups/{userId}`
  - `POST /…/signups/{userId}/confirm`
  - `GET /api/food-share-points/{fspId}/cleaning/team` (with stats)
  - `GET/PUT /api/regions/{regionId}/cleaning-food-share-points` (`{fspIds[]}`, plus a candidate list)
- **Contract, existing models.**
  - Add `cleaningEnabled` to the FSP detail and create/update models in `schemas/food-share-points.yaml`.
  - Remove `IntroPickupRegionSettings.cleaningStoreId` (`schemas/regions.yaml`).
  - Replace `StoreAutomation.cleaningStore` with `cleaningFoodSharePoints: FspRef[]` (`schemas/automation.yaml`).
  - Rename the enum value `NO_CLEANING_STORE` to `NO_CLEANING_FAIRTEILER` in `automation.yaml`.
  - Error codes reuse the `PICKUP_NOT_ELIGIBLE` pattern with a new `CLEANING_NOT_ELIGIBLE` (args.reason), or add whatever is missing.
- **Liquibase.** `db/changelog/changes/179-fsp-cleaning.yaml`:
  - `food_share_point.cleaning_enabled BOOLEAN NOT NULL DEFAULT false`
  - `fsp_cleaning_regular` (fsp_id FK cascade, weekday, start_time, slot_count, description VARCHAR(100); unique fsp_id+weekday+start_time)
  - `fsp_cleaning_shift` (fsp_id, shift_at, slot_count, description; unique fsp_id+shift_at)
  - `fsp_cleaning_signup` (fsp_id, user_id FK user_account cascade, shift_at, confirmed, confirmed_by, confirmed_at; unique fsp_id+user_id+shift_at; index fsp_id+shift_at and user_id+shift_at)
  - `region_cleaning_fsp` (region_id FK region cascade, fsp_id FK food_share_point cascade, BaseEntity columns; unique region_id+fsp_id)
  - `dropColumn region.cleaning_store_id`
  - Must run on PostgreSQL and H2.
- **Seed** (D3):
  - Edit `db/seed/region/region.csv` (remove the column) and `db/seed/changes/105-automation-pickup-policy-seed.yaml` (delete `seed-105-7`, fix its header comment).
  - New `db/seed/changes/179-fsp-cleaning-seed.yaml` with CSVs under `db/seed/fsp/`:
    - Wiedikon `00000049-0001-…01` gets `cleaning_enabled` (loadUpdateData).
    - A weekly regular slot plus one-time shifts.
    - Confirmed past sign-ups (foodsaver within 12 months, jumper older), an unconfirmed upcoming one, a free slot.
    - A zuerich-stadt link to Wiedikon.
  - Table codes 0004–0008 under module 0049. Add the new tables to `FspSeedDateShift`.
- **Docs.** Update `docs/seed-ids.md` (fsp row; remove the S-13 cleaning-store note at line ~213), and add a decision row for the D3 seed exception and the FS2 ID.

### Phase 2: server
- **Shared schedule helper.** `common/schedule/SlotSchedule` (generic over a slot key and a signup record), extracted from `PickupSchedule`. `PickupSchedule` delegates to it, and `PickupScheduleTest` must stay green unchanged.
- **fsp module.**
  - Entities in `fsp/domain`, repositories, and `FspCleaningScheduleService`.
  - `FspCleaningSignupService`, with sign-ups serialised by a row lock on `food_share_point` (as stores do).
  - Pure `FspCleaningRules`: eligibility = verified, not sleeping, not past, free slot, not already signed up. Managers sign up pre-confirmed. Manage = `FspRules.mayEdit`.
  - `FspCleaningTeamService` (derived team and stats).
  - `FspCleaningRegionService`, which validates D7.
  - `FspAction` gets new values (CLEANING_ENABLED/DISABLED, CLEANING_SCHEDULE_CHANGED, CLEANING_SIGNED_UP/OUT, CLEANING_CONFIRMED/REMOVED, REGION_CLEANING_FSPS_CHANGED). Every mutation calls `HistoryService`.
  - New `NotificationType`s for the bells.
  - `FspService` update or delete/status handles `cleaningEnabled` and D7 link removal.
- **Web.** `fsp/web/FspCleaningController` implements the generated `FspCleaningApi`. Mapping goes in `FspMapper` or a new `FspCleaningMapper`.
- **Region.** Remove `Region.cleaningStoreId`, its handling in `RegionSettingsService` (lines ~43, 100, 171) and in `RegionMapper` (290, 319).
- **Automation.**
  - `PickupPolicyEvaluator.RegionInfo(id, name, List<UUID> cleaningFspIds)`.
  - `Data` changes:
    - Replace the store-based calls in `cleaningRule` with `lastConfirmedCleaning(fspIds, user, before)`, `cleaningSignupsOfUser(user, fspIds, from, to)`, `freeCleaningSlots(fspIds, from, to)` (returning at, fspId, fspName) and `cleaningLink(fspId)`.
    - Implement them in `PickupPolicyData` through an fsp read service (`fsp.service.FspCleaningQueries`).
  - D5 and D6 apply here.
  - Rename `PolicyRule.NO_CLEANING_STORE`, and the `no-cleaning-store` message key in the five policy message bundles.
  - `PickupPolicyService.cleaningStoreOf` becomes `cleaningFoodSharePointsOf`, and `AutomationController` line 162 changes with it.
  - Add `fsp` to nothing else. Check that `automation → fsp` is allowed in `ModulesTest`: automation has no allow-list today, and fsp's own list is unchanged.

### Phase 3: client
- Run `npm run generate:api` (or the project's regenerate script) after Phase 1.
- `features/food-share-points`:
  - the `cleaningEnabled` switch in `fsp-form-page.component.ts`
  - a new `tabs/fsp-cleaning.tab.ts` (child route) using `shared/pickup-slot`, `shared/weekday-time-picker` and `shared/eligibility-reasons`, with `loadResource` and `<fs-data-state>` for slots and team, and `runMutation` for actions
  - the team and statistics card
  - test ids `fsp.cleaning.*`
- `features/automation`:
  - a new region section `cleaning-fsps` ("Putzen") in `pages/region-sections.ts` and `region-automation-page.component.ts` (same gate as `cleaning-exemptions`), plus a route in `automation.routes.ts`
  - the cleaning card in `sections/pickup-policy.section.ts` lists the region's cleaning Fairteiler as links, or a "none set" hint linking to the section
- Remove `cleaningStoreId` from `regions/tabs/region-settings.tab.ts` (comment at line 116) and from `regions-pages.spec.ts`.
- i18n: the fsp and automation `i18n/{de,en,gws,fr,it}.json`, with "Putzeinsatz" wording. `npm run i18n:check` must pass.

### Phase 4: e2e and screenshots
- `e2e/tests/food-share-points/fs2-cleaning-shifts.spec.ts @FS2 @AU1`:
  - a manager enables cleaning and adds a slot
  - a foodsaver signs up; the manager confirms
  - a region admin links the Fairteiler
  - a pickup policy with the cleaning rule declines a user without a shift and confirms one with a shift
- Update `e2e/tests/intro-pickups/_intro.ts` (drop `cleaningStoreId`).
- Screenshot specs for the cleaning tab (list, empty, error, forbidden, manager view, phone) and the "Putzen" section. Baselines are not generated now (decision 69).
- Add the ledger row FS2.

## UI
Yes. There is a new Fairteiler tab, a form switch, a region automation section and a policy card change. A designer mockup is advised for the cleaning tab only if the owner wants it before item 11 lands. Otherwise it reuses the existing pickup-slot look.

## Tests
- New unit tests:
  - `FspCleaningRulesTest` (eligibility matrix, manager pre-confirm)
  - `SlotScheduleTest` (moved from `PickupScheduleTest` where generic)
  - `FspCleaningRegionServiceTest` (D7: other region, inactive, disabled, deleted rejected)
  - `FspCleaningControllerTest` (every documented error)
- New ITs: `FspCleaningFlowIT` (sign-up, confirm, history rows, bells), and a migration check in `LocalProfileSmokeIT` (seed loads, Zürich linked).
- Changed on purpose:
  - `PickupPolicyEvaluatorTest`: cases for several Fairteiler, free slots counted across them, D5, D6, rename.
  - `AdvertFlowIT`: no cleaning store.
  - `regions-pages.spec.ts`, and the automation section specs.
  - `QueryCountIT` gets a budget for the shifts list. Extend `IndexUsagePostgresIT` for the signup indexes if hot.
- Keep green: `ModulesTest`, `ArchitectureTest` (one Api per controller, history on mutations), `StableOrderTest` (id tiebreakers), `SeedDateShiftsTest`.

## Done when
- [ ] Contract regenerated; no `cleaningStore*` left in server, client, e2e or docs outside the ER docs and `docs/er`
- [ ] Changelog 179 applies on H2 and Postgres; seed loads from scratch; `docs/seed-ids.md` and decisions updated
- [ ] Rule reads the region's cleaning Fairteiler; all evaluator tests pass
- [ ] Cleaning tab, switch, "Putzen" section and policy card work in all five languages; `i18n:check` passes
- [ ] Every mutation writes history; bells sent
- [ ] `@FS2` e2e passes; screenshot specs exist; ledger row FS2 added
- [ ] Follow-ups (Scope: out) listed in `docs/resume.md`

## Risks
- Extracting `PickupSchedule` could change DST or override behaviour for store pickups. Keep its tests unchanged and green.
- Editing seed files breaks existing local databases. A dev seed reset or a fresh database is needed; note this in the commit.
- Item 11 restyles `fs-pickup-slot`; the cleaning tab inherits that. Coordinate the order of the two.
- Account deletion and export (A40/A49): cleaning sign-ups have user FKs with cascade. Decide whether `fsp` needs an `AccountDeletionContributor` for future sign-ups (minimum: the cascade covers deletion).
- Several Fairteiler make the decline text longer; check the Telegram and mail lengths.
