# EinAb guide quiz: release needs a quiz pass (Q4)

## Goal
Giving EinAbs is tied to a new **EinAb guide quiz** instead of a permission that anyone can grant. The quiz unlocks after N confirmed store pickups (N is a region setting, default 25). Passing it does not release you. An ambassador (region admin) or ORGA still releases you, and only people with a pass can be released. Existing guides must pass the quiz too, within a grace period.

## Decisions (new decision 167, amends 40 and 74)
- `CAN_GIVE_INTRO_PICKUPS` stays as the internal "released in region X" flag. All existing checks keep working unchanged (guide pages, booking rules, funnel, nav).
- New `QuizKind.EINAB_GUIDE`, a global quiz. Try and wait rules match STORE_MANAGER (`waitDays`, disqualification). No pass confirmation. The pass never expires.
- Who may try: verified, at least FOODSAVER, and `MemberPickupStats.pickupCount(user)` (confirmed past store pickups only) ≥ the home region's `guideQuizMinPickups`. No home region means the global default of 25. People who currently hold `CAN_GIVE_INTRO_PICKUPS` anywhere skip the pickup threshold.
- Below the threshold the quiz does not appear in `listQuizzes` and `startQuizSession` answers 403 `QUIZ_NOT_ALLOWED`.
- New region setting `guideQuizMinPickups` (INT, 0–1000, default 25). Region admins write it, `CAN_MANAGE_USERS` holders read it. Changes are recorded in history like the other EinAb rules.
- **Release (enable):** region admins (`mayAdministrateRegion`: ambassadors, ORGA) only, and only with a valid EINAB_GUIDE pass. Otherwise 409 `GUIDE_QUIZ_NOT_PASSED`. Region membership is still required. **Disable:** still allowed for `CAN_MANAGE_USERS` holders and region admins.
- The generic region-permission grant (`RegionMembersService.grant`) refuses `CAN_GIVE_INTRO_PICKUPS` with 409 `USE_GUIDE_RELEASE`. Revoking through it stays allowed for region admins.
- The WELCOME function-group grant of `CAN_GIVE_INTRO_PICKUPS` is removed (`AppProperties` default and `application.properties`). WELCOME keeps no permission.
- When someone passes, the region admins of the home region get a bell (`GUIDE_QUIZ_PASSED`). The guide admin page gets a **Ready for release** list: active members of the region with a valid pass who are not released there yet.
- **Transition for existing guides** (decided by the planner on the owner's "build it right away"):
  - Grace deadline `app.intropickup.guide-quiz-deadline`, an ISO date, default `2027-01-15`, about 10 weeks.
  - Until then, existing holders keep the permission.
  - Bell and mail `GUIDE_QUIZ_REQUIRED` to every holder without a pass: on the first job run after deploy, and again 14 days before the deadline.
  - Starting on the deadline, a daily job `GuideQuizDeadlineJob` (08:15 Zurich, `RunnableJob`, `Clock`) revokes `CAN_GIVE_INTRO_PICKUPS` in every region from holders without a pass. It records `GUIDE_DISABLED` with reason "guide quiz not passed" and sends the guide `GUIDE_RELEASE_REVOKED`.
  - Upcoming EinAbs of a revoked guide stay as they are, and bookings are kept. Region admins and `CAN_MANAGE_USERS` holders of that region get one `GUIDE_EINABS_NEED_GUIDE` bell that links to the admin EinAb list, where they reassign through the existing EA12 assign.

## Scope
In: quiz kind, seed quiz and questions, gate, region setting, release rule, generic grant refusal, WELCOME grant removal, Ready-for-release list, bells, transition job, journey GUIDE track, i18n, seed, tests, decisions/ledger entries.
Out: a "request release" button for users (passing is the request), expiry or re-certification of the pass, the content of the real questions (placeholder questions now; the owner or the quiz AG replaces them with the editor Q3), and changes to EinAb booking rules.

## Changes
**Contract** (`server/src/main/resources/openapi/`)
- `schemas/quizzes.yaml`: add `EINAB_GUIDE` to the role enum.
- `schemas/regions.yaml` `IntroPickupRegionSettings`: add `guideQuizMinPickups` (int, min 0, max 1000, required).
- `schemas/intro-pickups.yaml`: new `GuideCandidate` (user summary, `quizPassedAt`, `pickupCount`).
- `paths/intro-pickup-admin.yaml`: `GET /regions/{regionId}/intro-pickups/guide-candidates` → `listGuideCandidates` (CAN_MANAGE_USERS reads). Document the new 403 and 409 codes on the existing enable operation.
- `schemas/common.yaml`: error codes `GUIDE_QUIZ_NOT_PASSED`, `USE_GUIDE_RELEASE`.
- Regenerate server and client.

**DB**
- `db/changelog/changes/188-guide-quiz.yaml`: `region.guide_quiz_min_pickups INT NOT NULL DEFAULT 25`, plus the `EINAB_GUIDE` quiz row (production data, not seed) with `max_failure_points 2`, `question_count_timed 4`, `question_count_untimed 5`, the same as the STORE_MANAGER row.
- Placeholder questions go in seed only. Production gets real questions through the editor, and an empty pool must make `startQuizSession` answer 409 (verify the existing behaviour and add a test if it is missing).
- Works on PostgreSQL and H2. No CLOB.

**Server**
- `quiz/domain/QuizKind`: add `EINAB_GUIDE`.
- `QuizRuleService.mayTry(kind, role, verified, editorGroupMember, guidePickupsReached)`: `EINAB_GUIDE -> verified && isAtLeast(role, FOODSAVER) && guidePickupsReached`. `roleOf(EINAB_GUIDE)` returns no role change; check how `roleOf` is used on pass and make the pass not touch `Role`.
- `QuizSessionService.mayTry`: compute `guidePickupsReached` through a new small port `GuideQuizEligibility` in `quiz/service`, implemented in `intropickup`. The implementation reuses `region/service/MemberPickupStats`, `RegionSettingsService.read(homeRegion)` and `PermissionResolver.permissionsByRegion` for the holder exemption. This keeps quiz free of intropickup imports.
- On pass, publish `GuideQuizPassedEvent(userId)`. `IntroPickupNotifications` (AFTER_COMMIT) sends the bell to the home region's admins.
- `RegionSettingsService`: read, write and history for `guideQuizMinPickups`. `Region` entity field. `RegionMapper`.
- `IntroPickupAdminService.setGuideEnabled`:
  - enable requires `permissions.mayAdministrateRegion` (else 403) and a valid pass (`QuizSessionService`/`tryStateOf(EINAB_GUIDE).passValid()`, else 409); disable keeps `requireAdmin`.
  - New `guideCandidates(regionId, page, size)` (read-only).
- `RegionMembersService.grant`/`mayGrant`: refuse `CAN_GIVE_INTRO_PICKUPS` (`USE_GUIDE_RELEASE`). Drop the CAN_MANAGE_USERS exception.
- `config/AppProperties`: remove the WELCOME default grant. Add `intropickup.guideQuizDeadline` (LocalDate). `application.properties`: remove line 129 and add the deadline.
- New `intropickup/service/GuideQuizDeadlineJob` + `GuideQuizTransitionService`:
  - `@Transactional` methods record history (`GUIDE_DISABLED`), with `@NoHistory` only on the reminder bookkeeping.
  - Reminders are de-duplicated by a `notification` dedupe key per user and phase (`initial`/`final`), so no new table.
- `notification/domain/NotificationType`: add `GUIDE_QUIZ_PASSED`, `GUIDE_QUIZ_REQUIRED`, `GUIDE_RELEASE_REVOKED`, `GUIDE_EINABS_NEED_GUIDE`, plus their mail and bell templates in five languages.
- `onboarding/service/JourneyService` + `JourneyCalculator`: `TrackInputs` gets `guideQuiz` (TryState), `pickupCount` and `guideMinPickups`. The GUIDE track becomes:
  - DONE when released; LOCKED when unverified;
  - LOCKED with progress `x/N` and blockedReason `onboarding.blocked.guidePickups` below the threshold;
  - AVAILABLE or IN_PROGRESS via `quizStatus(guideQuiz)` with action `/quizzes/EINAB_GUIDE`;
  - WAITING_FOR_OTHERS (`onboarding.waiting.guideRelease`, contacts = home-region ambassadors) after a pass.
- `GuideStatus` javadoc: mention the quiz.

**Client** (generated services only)
- `features/quiz/quiz-kind.ts`: add `EINAB_GUIDE` (label and icon).
- `features/intro-pickups/pages/admin-intro-pickups-page`, guides tab:
  - new "Ready for release" section above the guide list (`loadResource` + `<fs-data-state>`, `runMutation` release with optional reason);
  - the enable button only for region admins (`RegionCapabilities`), with tooltip text otherwise;
  - a banner shows the deadline to guides without a pass.
- Region EinAb settings form (EA22): number field `guideQuizMinPickups`.
- Region members permission UI: hide `CAN_GIVE_INTRO_PICKUPS` from the grant choices and point to the guide page.
- `data-testid`s: `intro-pickups.admin.candidate-release`, `intro-pickups.admin.candidates`, `regions.settings.guide-quiz-min-pickups`.
- i18n: `features/quiz`, `features/intro-pickups`, `features/regions`, `features/onboarding` and the server mail texts, in de/en/gws/fr/it. `npm run i18n:check` must pass.

**Seed** (`db/seed/`, fixed UUIDs, update `docs/seed-ids.md`)
- `quiz/quiz_i18n.csv`: EINAB_GUIDE name and description in five languages. (The quiz row itself comes from changelog 188 with id `00000013-0001-4000-8000-000000000008`; the seed must not duplicate it.)
- 8 questions with 3–4 answers each, in five languages (`quiz_question*`, `quiz_answer*`). Topics: guide's role, hygiene briefing, checking ID, counting and confirming attendance, Fairteiler visit, cancellation, safety, the comment etiquette.
- `quiz_session.csv`: passed EINAB_GUIDE session for `guide@local`. A passed session, not released, for `jumper@local`, who becomes the Ready-for-release candidate in Zürich. Make sure jumper has ≥ 25 confirmed past pickups or the session alone suffices; a candidate only needs the pass.
- `bot@local` and the other WELCOME holders keep giving EinAbs only if they have a direct `region_permission` row. Check `region_permission.csv` and the seed e2e personas and fix fallout.

**Docs**: decision 167 in `docs/decisions.md` (amends 40 and 74), the Q4 row in plan.md §Q, `docs/ledger.md`, and the plan §11 guide track text ("pickups → quiz → ambassador release").

## UI
Yes, a visible change: the candidate list on the guide admin page, the settings field, the journey GUIDE track states and a new quiz card. No mockup advised, because it reuses the existing list, card and settings patterns. Screenshot baselines are regenerated via CI (decision 161).

## Tests
- Unit:
  - `QuizRuleServiceTest` (EINAB_GUIDE mayTry matrix, wait rules);
  - `JourneyCalculatorTest` (all five GUIDE states);
  - `GuideQuizEligibility` (threshold, missing home region, holder exemption);
  - `GuideQuizTransitionService` (before deadline: reminders only, deduped; 14-day reminder; at deadline: revoke with history, bells to admins only when upcoming EinAbs exist; idempotent; passers untouched);
  - `RegionSettingsService` (field and history);
  - `IntroPickupAdminService` (enable needs admin + pass; guide admin may disable, not enable; candidates).
- Changed on purpose:
  - `RegionMembersServiceTest`, `PermissionServiceMatrixTest`, `RegionCapabilitiesTest`, `RegionPermissionResolverTest`, `PermissionServiceTest` (no WELCOME grant; CAN_MANAGE_USERS can no longer grant the guide permission);
  - `RegionApiIT` (grant answers 409).
- IT:
  - quiz list hides and shows by pickups;
  - pass → bell to ambassador;
  - release flow over HTTP;
  - deadline job with a fixed `Clock`;
  - `StoreIdentityLeakIT` mapping for the new GET.
- e2e `@Q4`:
  - `e2e/tests/quiz/q4-guide-quiz.spec.ts` (locked track with x/25, quiz visible above threshold, pass → waiting);
  - `e2e/tests/intro-pickups/ea17-guides.spec.ts` (bot releases jumper; guide admin sees no enable);
  - settings field in `ea22-settings.spec.ts`.
- Screenshot specs: candidates list (filled, empty), GUIDE track locked and waiting, the deadline banner.

## Done when
- [ ] Contract regenerated; server and client build; ArchUnit green (`@NoHistory`/history, one Api per controller).
- [ ] The quiz is visible only from N confirmed pickups (region setting, default 25); release only by ambassadors or ORGA with a pass.
- [ ] The generic grant and WELCOME no longer hand out `CAN_GIVE_INTRO_PICKUPS`.
- [ ] Existing guides get reminders; after `2027-01-15` those without a pass are revoked, their EinAbs flagged to admins; the job only revokes and is idempotent.
- [ ] Seed, five-language i18n, `npm run i18n:check`, unit, IT and e2e `@Q4` green; screenshots regenerated via CI and reviewed.
- [ ] Decision 167, the ledger and plan rows written.

## Risks
- Removing the WELCOME grant silently removes guides who only had it through the group. **Mitigation:** changelog 188 inserts direct `region_permission` rows for current WELCOME function-group members (SQL over the group membership tables; if that is not portable to H2, use a one-off idempotent startup migration in `GuideQuizTransitionService`, run before the property change takes effect), so they get the grace period like everyone else.
- Production has no EINAB_GUIDE questions until the quiz AG adds them, so nobody can pass before the deadline. The ledger and handover must say this. The deadline is a property, so ops can push it.
- Guides revoked at the deadline can no longer confirm attendance at their upcoming EinAbs. Admins have to reassign them, which is why `GUIDE_EINABS_NEED_GUIDE` exists.
- Cross-module port: keep `quiz` independent of `intropickup` (ArchUnit module rules).