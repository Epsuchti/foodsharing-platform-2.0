# Testing migration (decision 180)

Where every test that the testing rework (`docs/tasks/2026-11-15-testing-rework.md`) removed went. One row per old
e2e spec, screen file, integration test class, slow Spring-context unit class and deleted client spec. The last
column names exactly one of:

- **journey** `e2e/journeys/<persona>/<slug>.journey.ts` (+ the checkpoint that shows the state),
- **unit** the unit test class that now asserts the rule,
- **ApiSmokeIT** the controller row in `server/src/test/java/network/foodsharing/ApiSmokeIT.java`,
- **kept** the test stays (guardrail, PostgreSQL, security/IDOR/privacy, permission matrix),
- **dropped: reason** nothing replaces it, and why that is fine.

A removed test without a row here is a bug in the rework.

## E2E specs (`e2e/tests/**`)

| Old spec | Tests | IDs | Now covered by |
|---|---|---|---|
| `a11y/csp.spec.ts` | 1 | D17.2 | kept (a11y project) |
| `a11y/pages.spec.ts` | 4 | AD1 | kept (a11y project) |
| `account/a11-calendar.spec.ts` | 2 | A11 E3 | journey `journeys/account/profile-and-settings.journey.ts` (checkpoint `calendar-card`; filters, regenerate, delete, create again); unit `CalendarFeedServiceTest` (pickups, intro pickups, events, alarms, filters); error state dropped: per-page data states, decision 180 #5 (shared `fs-data-state` spec + things-go-wrong journey) |
| `account/a12-language.spec.ts` | 1 | A12 | journey `journeys/account/profile-and-settings.journey.ts` (checkpoint `settings`; all five languages, saved with the account) |
| `account/a13-delete.spec.ts` | 2 | A13 | journey `journeys/account/privacy-and-sessions.journey.ts` (checkpoint `delete-dialog`); ORGA-only deletion of others: unit `AccountAdminRulesTest`, anonymising: unit `AccountDeletionServiceTest`, the ORGA delete dialog: client `users-admin-pages.spec` |
| `account/a14-business-card.spec.ts` | 2 | A14 | journey `journeys/account/profile-and-settings.journey.ts` (PDF download); refusals and unverified users: unit `BusinessCardServiceTest`, client `account-q4.spec` |
| `account/a17-theme.spec.ts` | 1 | A17 | journey `journeys/account/profile-and-settings.journey.ts` (dark, light, system); unit client `theme.service.spec` |
| `account/a18-bounce.spec.ts` | 2 | A18 MB5 | journey `journeys/account/privacy-and-sessions.journey.ts` (checkpoints `bounce-banner`, `bounced-list`); access: unit `AccountAdminRulesTest`; error state dropped: per-page data states, decision 180 #5 (shared `fs-data-state` spec + things-go-wrong journey) |
| `account/a20-info-mail.spec.ts` | 1 | A20 N7 | journey `journeys/account/profile-and-settings.journey.ts` (mail switches); recipients of an info mail: unit `NewsletterServiceTest` (+ derived query `findByActiveTrueAndInfoMailMessageTrueAndDeletedAtIsNull`), client `account-q3.spec`; composing/sending a newsletter is the newsletter (N7) area's |
| `account/a21-export.spec.ts` | 2 | A21 | journey `journeys/account/privacy-and-sessions.journey.ts` (zip sections, 10-minute limit); unit `DataExportServiceTest`; failing start: client `account-q4.spec` |
| `account/a22-sessions.spec.ts` | 4 | A22 D8.4 D10.3 | journey `journeys/account/privacy-and-sessions.journey.ts` (checkpoints `sessions`, `session-expired`, `security-log`) |
| `account/a3-email-change.spec.ts` | 3 | A3 N6 | journey `journeys/account/privacy-and-sessions.journey.ts` (checkpoint `email-confirmed`; taken address, abort link, confirm link); taken address: unit `EmailChangeServiceTest`; cancel in the settings: client `account-q3.spec`, unit `EmailChangeServiceTest`; error states dropped: per-page data states, decision 180 #5 (shared `fs-data-state` spec + things-go-wrong journey) |
| `account/a4-profile.spec.ts` | 4 | A4 | journey `journeys/account/profile-and-settings.journey.ts` (checkpoints `profile-form`, `profile-mobile-required`); display name and mobile required: client `profile-page.component.spec`, unit `ProfilePhoneRulesTest`, `PhoneNumberValidatorTest` |
| `account/a5-photo.spec.ts` | 2 | A5 C10 | journey `journeys/account/profile-and-settings.journey.ts` (checkpoint `photo-crop`; JPEG ≤ 1 MB, public sm/md/lg); unit `ImageVariantsTest`; failed upload keeps the dialog: client `account-q3.spec`; error state dropped: per-page data states, decision 180 #5 (shared `fs-data-state` spec + things-go-wrong journey) |
| `account/a7-notes.spec.ts` | 2 | A7 | journey `journeys/account/achievements-and-notes.journey.ts` (checkpoint `member-notes`); who reads notes: unit `AccountAdminRulesTest`, `ProfilePermissionMatrixTest`; error state dropped: per-page data states, decision 180 #5 (shared `fs-data-state` spec + things-go-wrong journey) |
| `account/a9-sleep.spec.ts` | 2 | A9 | journey `journeys/account/profile-and-settings.journey.ts` (checkpoint `sleep-mode-temporary`; moon on profile, member list, store team); unit `SleepModeTest`; full sleep and a failed save: client `account-q3.spec` |
| `account/d17-3-privacy-api.spec.ts` | 1 | D17.3 | server `AccountDeletionIT` (`exportContainsEverySection`: every export file of the data inventory is in the zip) |
| `achievements/ac1-achievements.spec.ts` | 3 | AC1 Q2 S3 | journey `journeys/account/achievements-and-notes.journey.ts` (checkpoints `achievements-board`, `define-dialog`, `award-dialog`, `profile-with-award`, `awards-list`); visibility for members and recipients: unit `AchievementVisibilityTest`, `AchievementServiceTest`; translations: unit `AchievementMapperTest`; automatic awards (Q2, S3): unit `AutoAwardListenerTest`; error states dropped: per-page data states, decision 180 #5 (shared `fs-data-state` spec + things-go-wrong journey) |
| `admin-ops/ad1-admins.spec.ts` | 1 | AD1 |  |
| `admin-ops/au12-integrations.spec.ts` | 3 | AU12 D6.3 N6 |  |
| `admin-ops/c15-maintenance-jobs.spec.ts` | 2 | C15 |  |
| `admin-ops/d63-jobs.spec.ts` | 1 | D6.3 |  |
| `admin-ops/n6-mail.spec.ts` | 1 | N6 |  |
| `admin/ad1-admins-ui.spec.ts` | 3 | AD1 R8 |  |
| `admin/c10-uploads-admin.spec.ts` | 2 | C10 |  |
| `admin/c11-feature-toggles.spec.ts` | 3 | C11 |  |
| `admin/c12-blocklist.spec.ts` | 2 | A1 C12 |  |
| `admin/c14-configuration.spec.ts` | 3 | C7 C14 |  |
| `admin/d17-2-rate-limit-blocks.spec.ts` | 1 | D17.2 |  |
| `auth/a1-signup.spec.ts` | 7 | A1 D8.1 D8.2 | journey `journeys/account/sign-up-and-log-in.journey.ts` (checkpoints `login`, `magic-link-sent`, `consent`, `login-methods`; link sign-up, Google linking); account creation via Google, blocked domain and the recorded attempt: unit `AccountServiceTest`; unverified Google address: unit `GoogleOidcUserServiceTest`; rate limit: unit `LoginRateLimiterTest`; their messages: client `login-page.component.spec` |
| `auth/a2-login-translations.spec.ts` | 1 | A2 | dropped: `npm run i18n:check` fails on a key used in code that is missing in any of the five languages; the five-language smoke journey (public area) renders the pages |
| `auth/a2-login.spec.ts` | 6 | A2 D8.3 | journey `journeys/account/sign-up-and-log-in.journey.ts` (checkpoints `dashboard-passkey-nudge`, `link-used`; link login, used link, logout), journey `journeys/account/passkey-login.journey.ts` (passkey registration and both logins), journey `journeys/account/privacy-and-sessions.journey.ts` (checkpoint `session-expired`); session refresh after a role change: client `auth-facade.service.spec`; open redirect: unit `ReturnUrlValidatorTest`, client `return-url.spec`, `login-page.component.spec`; tokens: unit `MagicLinkServiceTest` |
| `auth/auth-error-states.spec.ts` | 5 | A2 A22 D4.3 D7.3 |  |
| `auth/d7-dev-switcher.spec.ts` | 1 | D7.3 | journey `journeys/account/privacy-and-sessions.journey.ts` (dev switcher login); every seed user in the catalog: unit `DevUserCatalogTest`; every seed role logs in: `e2e/auth.setup.ts` |
| `automation/au1-au10-link-picker-channels.spec.ts` | 4 | AU1 AU10 N5 | journey `journeys/store-manager/set-pickup-rules.journey.ts` (checkpoints `store-picker`, `linked-rule-one-way`); picker idle/empty: unit `channels-picker.spec.ts`; Kanäle: journey `journeys/store-manager/advertise-open-slots.journey.ts` (checkpoint `store-chats`); region overview: journey `journeys/store-manager/review-region-automation.journey.ts` (checkpoint `region-overview`) |
| `automation/au1-blocked-signup.spec.ts` | 3 | AU1 PU3 PU4 | journey `journeys/store-manager/set-pickup-rules.journey.ts` (checkpoints `blocked-reason`, `protocol`); managers never blocked: unit `PickupEligibilityRulesTest`; rules off, /pickups options: unit `PickupRuleGateImplTest` |
| `automation/au1-gap-store-list.spec.ts` | 5 | AU1 | journey `journeys/store-manager/set-pickup-rules.journey.ts` (checkpoint `gap-rule-with-store-list`); save, locks, leave, conflict: unit `PickupPolicyServiceGapTest`, `automation.spec.ts`; own vs group gap decisions: unit `PickupPolicyEvaluatorTest` |
| `automation/au1-linked-signup.spec.ts` | 2 | AU1 PU3 | journey `journeys/store-manager/set-pickup-rules.journey.ts` (member test, checkpoints `linked-step`, `link-repair`); LINKED_PICKUP_REQUIRED, deadline job: unit `PickupRuleGateImplTest`, `PickupPolicyEvaluatorTest` |
| `automation/au1-pickup-policy.spec.ts` | 6 | AU1 D10.3 | journey `journeys/store-manager/set-pickup-rules.journey.ts` (checkpoints `pickup-policy`, `protocol`); rules off, excluded date, cleaning: unit `PickupPolicyRunnerTest`, `PickupPolicyEvaluatorTest`; bidirectional rule + mirror: unit `PickupPolicyServiceGapTest`, `AutomationRulesTest`; one switch: unit `automation.spec.ts` |
| `automation/au11-region-sender.spec.ts` | 2 | AU11 | unit `RegionSenderTelegramTest`, `MailOutboxJobTest`, `MailTemplateServiceTest`, `StoreTeamServiceTest` (automatedRequestDecisionsAreSentOnBehalfOfTheStoresRegion) |
| `automation/au13-audit-retention.spec.ts` | 1 | AU13 | unit `PickupCountGroupingAndMetricsTest` (archiveCountsThenDeletesAndAddsToExistingRows, seriesCountsDoneIntroPickupsAndMergesTheArchive) |
| `automation/au2-ownership.spec.ts` | 3 | AU2 | journey `journeys/store-manager/set-pickup-rules.journey.ts` (checkpoint `pickup-policy`); access: unit `PickupPolicyServiceGapTest` (theAutomationOfAStoreNeedsItsEditRight), `AutomationAuditServiceTest`, `StorePermissionMatrixTest`; error + retry, rendered error code: dropped: generic DataState/error rendering (decision 180 §5, shared fs-data-state spec and things-go-wrong journey) |
| `automation/au3-auto-accept.spec.ts` | 1 | AU3 | journey `journeys/store-manager/advertise-open-slots.journey.ts` (checkpoint `auto-accept`); accept/decline by distance: unit `RequestPolicyRunnerTest` |
| `automation/au4-adverts.spec.ts` | 4 | AU4 AU9 | journey `journeys/store-manager/advertise-open-slots.journey.ts` (checkpoint `advert-unsaved`); filled-after-advert, late sign-out: unit `OpenSlotAdvertRunnerTest` |
| `automation/au5-signout-alerts.spec.ts` | 2 | AU5 | journey `journeys/store-manager/advertise-open-slots.journey.ts` (checkpoint `signout-alerts`); daily NOT_REPLACED once per day: unit `SignoutAlertServiceTest` |
| `automation/au6-extra-slot.spec.ts` | 2 | AU6 | journey `journeys/store-manager/advertise-open-slots.journey.ts` (checkpoint `extra-places`); limits: unit `ExtraSlotRuleTest`, `MayAddExtraSlotTest`, `extra-slot.section.spec.ts` |
| `automation/au7-au9-au10-region.spec.ts` | 3 | AU7 AU9 AU10 N5 | journey `journeys/store-manager/review-region-automation.journey.ts` (checkpoints `grouping`, `metrics`, `region-telegram`); member forbidden: unit `PickupCountGroupingAndMetricsTest` (groupingIsForRegionAdministratorsAndOrgaOnly) |
| `automation/au8-audits.spec.ts` | 1 | AU8 D10.3 | journey `journeys/store-manager/review-region-automation.journey.ts` (checkpoint `region-decisions`); member sees only own: unit `AutomationAuditServiceTest` |
| `baskets/b1-baskets.spec.ts` | 4 | B1 |  |
| `baskets/b2-requests.spec.ts` | 4 | B2 N1 N3 |  |
| `content/c1-content.spec.ts` | 4 | C1 |  |
| `content/c1-pages.spec.ts` | 4 | C1 C16 |  |
| `content/c2-blog.spec.ts` | 4 | C2 C11 N3 |  |
| `content/c3-statistics.spec.ts` | 3 | C3 |  |
| `content/c4-release-notes.spec.ts` | 2 | C4 |  |
| `content/c7-donations.spec.ts` | 2 | C7 C14 |  |
| `content/c8-petition.spec.ts` | 1 | C8 C11 |  |
| `content/c9-support.spec.ts` | 2 | C9 N6 |  |
| `content/content-error-states.spec.ts` | 6 | C1 |  |
| `dashboard/a19-dashboard.spec.ts` | 3 | A19 | journey `journeys/account/dashboard-feed.journey.ts` (checkpoints `dashboard`, `feed-filtered`, `nearby-baskets`, `baskets-without-address`); disjoint pages and mentions as names: unit `ActivityFeedServiceTest`; error state dropped: per-page data states, decision 180 #5 (shared `fs-data-state` spec + things-go-wrong journey) |
| `events/e1-events.spec.ts` | 5 | E1 |  |
| `events/e2-rsvp.spec.ts` | 1 | E2 N3 |  |
| `events/e3-ics.spec.ts` | 1 | E3 |  |
| `forum/f1-forum-access.spec.ts` | 2 | F1 |  |
| `forum/f2-threads.spec.ts` | 2 | F2 G3 |  |
| `forum/f3-posts.spec.ts` | 3 | D10.3 F3 G3 |  |
| `forum/f4-follow.spec.ts` | 1 | F4 N3 N6 |  |
| `forum/f5-search.spec.ts` | 1 | F5 |  |
| `foundation/ad1-ops-endpoints.spec.ts` | 3 | AD1 |  |
| `foundation/au12-pwa.spec.ts` | 1 | AU12 |  |
| `foundation/d4-m3-fixtures.spec.ts` | 1 | D4.2 |  |
| `foundation/d4-smoke.spec.ts` | 5 | D4.1 D4.2 D4.3 D5.1 D7.1 D7.2 D7.3 |  |
| `foundation/d8-csrf.spec.ts` | 2 | D8.4 |  |
| `foundation/infra-fixtures.spec.ts` | 2 |  |  |
| `foundation/m7-launch-api.spec.ts` | 4 | D16.1 D16.3 D19.1 D19.3 |  |
| `fsp/fs1-food-share-points.spec.ts` | 4 | FS1 N3 N6 R3 |  |
| `fsp/fs2-cleaning-agenda.spec.ts` | 3 | A11 A19 FS2 PU8 |  |
| `fsp/fs2-cleaning-shifts.spec.ts` | 3 | AU1 FS2 |  |
| `history/d10-history.spec.ts` | 9 | A22 D10.1 D10.3 | journey `journeys/account/my-activity.journey.ts` (checkpoints `my-activity`, `activity-details`, `activity-date-range`; D10.1 old/new values), journey `journeys/account/privacy-and-sessions.journey.ts` (security log → logins); access to other subjects: unit `HistoryAccessPolicyTest`; labels: unit `HistoryI18nCoverageTest` + `i18n:check`; ORGA CSV export: server `HistoryControllerTest`, client `my-activity-page.spec`; ORGA reads another user's logins: unit `HistoryAccessPolicyTest`; loading/error/offline and the filter overlay error dropped: per-page data states, decision 180 #5 (shared `fs-data-state` spec + things-go-wrong journey) |
| `intro-pickups/ea1-landing.spec.ts` | 1 | EA1 | journey `journeys/einab/newcomer-books-intro-pickups.journey.ts` (checkpoints `bookable-list`, `my-bookings`); journey `journeys/einab/newcomer-becomes-foodsaver.journey.ts` (checkpoint `no-region`); dropped: the API count of 15 dates is a seed count |
| `intro-pickups/ea10-guide.spec.ts` | 1 | EA10 | journey `journeys/einab/guide-offers-intro-pickups.journey.ts` (checkpoints `online-form-error`, `take-away-form`, `upcoming-einabs`); unit `IntroPickupValidationTest` (call link, address); unit `IntroPickupGuideServiceRulesTest` (past EinAbs) |
| `intro-pickups/ea11-templates.spec.ts` | 1 | EA11 | journey `journeys/einab/guide-offers-intro-pickups.journey.ts` (checkpoints `take-away-form`, `guide-settings`) |
| `intro-pickups/ea12-assign.spec.ts` | 1 | EA12 | journey `journeys/einab/guide-hosts-a-newcomer.journey.ts` (checkpoints `book-for-dialog`, `booked-for-chiara`, `assign-dialog`) |
| `intro-pickups/ea13-comments.spec.ts` | 1 | EA13 | journey `journeys/einab/guide-hosts-a-newcomer.journey.ts` (checkpoint `comment-written`) |
| `intro-pickups/ea14-done.spec.ts` | 1 | EA14 | journey `journeys/einab/guide-hosts-a-newcomer.journey.ts` (checkpoint `marked-done`); unit `IntroPickupGuideServiceRulesTest` (done only after the start) |
| `intro-pickups/ea15-ics.spec.ts` | 1 | EA15 | journey `journeys/einab/guide-offers-intro-pickups.journey.ts` (checkpoint `guide-settings`); unit `IcalServicesTest` (feed token, unknown token 404) |
| `intro-pickups/ea16-pickup-candidates.spec.ts` | 1 | EA16 | journey `journeys/einab/guide-offers-intro-pickups.journey.ts` (checkpoints `guide-dashboard`, `linked-form`, `signout-warning`); unit `IntroPickupLinkedPickupTest` |
| `intro-pickups/ea17-guides.spec.ts` | 4 | EA17 Q4 | journey `journeys/einab/foodsaver-becomes-guide.journey.ts` (checkpoints `ready-for-release`, `release-dialog`); journey `journeys/einab/ambassador-manages-intro-pickups.journey.ts` (checkpoint `guides-tab`); unit `IntroPickupAdminServiceGuideTest` (release needs a pass and a region administrator, user managers only); unit `IntroPickupGuideServiceRulesTest` (no EinAb without the guide right) |
| `intro-pickups/ea18-admin.spec.ts` | 2 | D10.3 EA13 EA18 | journey `journeys/einab/ambassador-manages-intro-pickups.journey.ts` (checkpoints `admin-einabs`, `delete-dialog`, `past-bookings`, `admin-comment`); unit `IntroPickupAdminServiceGuideTest` (delete reason); unit `NewcomerProgressServiceTest` (counted bookings) |
| `intro-pickups/ea19-import.spec.ts` | 1 | EA19 | journey `journeys/einab/ambassador-manages-intro-pickups.journey.ts` (checkpoint `import-preview`); unit `IntroPickupValidationTest` (import keys) |
| `intro-pickups/ea2-public-slots.spec.ts` | 2 | EA2 | journey `journeys/einab/newcomer-subscribes-to-new-dates.journey.ts` (checkpoint `public-dates`) (the unknown region step too) |
| `intro-pickups/ea20-progress-job.spec.ts` | 1 | EA20 | unit `PastImportJobTest` (imports above the threshold run as a job that always ends; e2e/coverage-map.json EA20) |
| `intro-pickups/ea21-funnel.spec.ts` | 1 | EA21 | journey `journeys/einab/ambassador-manages-intro-pickups.journey.ts` (checkpoint `funnel`); unit `NewcomerFunnelAccessTest` (names for user managers, counts for guides); unit `NewcomerProgressServiceTest` (steps, gaps) |
| `intro-pickups/ea22-settings.spec.ts` | 2 | EA22 Q4 | journey `journeys/einab/ambassador-manages-intro-pickups.journey.ts` (checkpoint `booking-rules`); journey `journeys/einab/foodsaver-becomes-guide.journey.ts` (checkpoint `threshold-setting`); unit `BookingRulesTest` |
| `intro-pickups/ea3-foodsaver-id.spec.ts` | 1 | EA3 | journey `journeys/einab/newcomer-subscribes-to-new-dates.journey.ts` (checkpoint `foodsaver-id-help`) |
| `intro-pickups/ea5-booking-rules.spec.ts` | 6 | EA5 | journey `journeys/einab/newcomer-books-intro-pickups.journey.ts` (checkpoints `bookable-list`, `nothing-left`); unit `BookingRulesTest` (each rule); unit `intro-pickups-pages.spec.ts` (empty kinds) |
| `intro-pickups/ea6-categories.spec.ts` | 1 | EA6 | journey `journeys/einab/newcomer-books-intro-pickups.journey.ts` (checkpoints `bookable-list`, `booking-success`) |
| `intro-pickups/ea7-my-bookings.spec.ts` | 1 | EA7 | journey `journeys/einab/newcomer-books-intro-pickups.journey.ts` (checkpoints `confirm-booking`, `booking-success`, `my-bookings`, `after-cancel`) |
| `intro-pickups/ea8-progress.spec.ts` | 1 | EA8 | journey `journeys/einab/newcomer-becomes-foodsaver.journey.ts` (checkpoints `progress-one-done`, `waiting-for-id-check`); unit `NewcomerProgressServiceTest` (stages and next steps) |
| `intro-pickups/ea9-notifications.spec.ts` | 2 | EA9 | journey `journeys/einab/newcomer-subscribes-to-new-dates.journey.ts` (checkpoints `subscribed`, `unsubscribed`); unit `IntroPickupSubscriptionServiceTest` (verified users, members only, tokens); unit `intro-pickups-pages.spec.ts` (unsubscribe without token) |
| `intro-pickups/intro-pickups-error-states.spec.ts` | 3 | EA7 EA10 |  |
| `legal/ea4-consent.spec.ts` | 5 | EA4 |  |
| `legal/legal-error-states.spec.ts` | 3 | Ve1 |  |
| `legal/ve1-consent.spec.ts` | 5 | Ve1 |  |
| `mailboxes/mb1-mailbox-admin.spec.ts` | 2 | MB1 MB6 |  |
| `mailboxes/mb2-folders.spec.ts` | 4 | MB2 MB3 |  |
| `mailboxes/mb4-compose.spec.ts` | 2 | MB4 N6 |  |
| `mailboxes/mb5-inbound.spec.ts` | 2 | D6.3 MB5 |  |
| `map/m1-map.spec.ts` | 6 | C11 M1 |  |
| `map/mt1-tiles.spec.ts` | 4 | MT1 MT2 |  |
| `messaging/n1-conversations.spec.ts` | 8 | N1 N2 S10 |  |
| `messaging/n2-realtime.spec.ts` | 6 | N1 N2 N3 |  |
| `newsletters/n7-newsletter.spec.ts` | 4 | N7 |  |
| `notifications/n3-bell-icons.spec.ts` | 1 | N3 |  |
| `notifications/n3-bells.spec.ts` | 7 | N3 |  |
| `notifications/n4-push.spec.ts` | 4 | N4 |  |
| `notifications/n5-telegram-link.spec.ts` | 3 | A10 N4 N5 N6 |  |
| `oauth/c13-oauth.spec.ts` | 5 | C13 |  |
| `onboarding/d11-journey.spec.ts` | 5 | A19 D11.5.1 D11.5.2 D11.5.4 EA8 |  |
| `onboarding/d11-lighthouse.spec.ts` | 1 | D11.5.3 | kept: moved to `e2e/lighthouse/d11-lighthouse.spec.ts` (project `lighthouse`) |
| `onboarding/ea8-journey.spec.ts` | 1 | D11.5.2 EA8 | journey `journeys/einab/newcomer-becomes-foodsaver.journey.ts` (checkpoints `journey-start`, `first-booking`, `progress-one-done`, `waiting-for-id-check`, `journey-complete`) |
| `onboarding/onboarding-error-states.spec.ts` | 2 | D11.5.2 EA8 |  |
| `onboarding/onboarding-nudges.spec.ts` | 2 | EA8 EA21 N3 |  |
| `personas/ambassador.spec.ts` | 1 | D11.5.5 G1 N7 Ve2 |  |
| `personas/foodsaver.spec.ts` | 1 | B1 B2 D11.5.5 N3 PU3 S1 | journey `journeys/foodsaver/sign-up-and-offer-a-basket.journey.ts` (checkpoints `signup-dialog`, `signed-up-unconfirmed`, `signed-up-confirmed`, `basket-form`, `basket-requested`) |
| `personas/newcomer.spec.ts` | 1 | A19 C1 D11.5.5 EA7 EA8 Q2 |  |
| `personas/orga.spec.ts` | 1 | C11 D11.5.5 |  |
| `personas/store-manager.spec.ts` | 1 | D11.5.5 PU1 PU3 S1 S12 | journey `journeys/store-manager/confirm-and-plan-pickups.journey.ts` (checkpoints `my-stores`, `store-overview`, `slot-menu`, `pickups-confirmed`, `regular-slots-dialog`, `store-log`) |
| `pickups/a19-dashboard-pickups.spec.ts` | 1 | A19 | journey `journeys/foodsaver/plan-my-pickups.journey.ts` (checkpoint `dashboard`); newcomer without the card: unit `home-cards.spec.ts` (my next pickups and your stores: nothing for newcomers) |
| `pickups/a19-home-cards.spec.ts` | 2 | A19 PU7 | journey `journeys/foodsaver/plan-my-pickups.journey.ts` (checkpoints `dashboard`, `help-card-signed-up`); newcomer cards and card rules (3 options, events without WONT_JOIN, stores urgent first): unit `home-cards.spec.ts` |
| `pickups/pickups-error-states.spec.ts` | 4 | PU1 PU3 PU4 PU7 |  |
| `pickups/pu1-regular.spec.ts` | 2 | PU1 PU3 | regular editor flow: journey `journeys/store-manager/confirm-and-plan-pickups.journey.ts` (checkpoint `regular-slots-dialog`); duplicate day/time: unit `RegularPickupServiceTest`, `weekday-time-picker.spec.ts`; team bell: unit `RegularPickupReplaceTest`; prefetch horizon: unit `PickupListRangeTest`, `PickupEligibilityRulesTest`; two-weeks range click: store-manager journey (pending, routed to the store-manager area) |
| `pickups/pu10-move-pickup.spec.ts` | 4 | EA16 PU10 | store-manager journey (pending, routed to the store-manager area); rules: unit `PickupMoveTest`, `IntroPickupLinkSyncTest`, `move-pickup-dialog.component.spec.ts` |
| `pickups/pu2-one-time.spec.ts` | 2 | PU2 | add/edit/cancel flow: store-manager journey (pending, routed to the store-manager area); fewer slots than sign-ups, 0 slots cancel: unit `PickupSignupEditAndConfirmTest` |
| `pickups/pu3-day-chat.spec.ts` | 2 | PU3 | journey `journeys/foodsaver/coordinate-a-pickup-day.journey.ts` (checkpoint `day-chat`); manager joins the chat: store-manager journey (pending, routed to the store-manager area) + unit `DayChatRuleTest`; no button when only I am signed up: unit `store-pickups.tab.spec.ts` |
| `pickups/pu3-signup.spec.ts` | 8 | PU3 | sign-up, bells, confirmation seen: journey `journeys/foodsaver/sign-up-and-offer-a-basket.journey.ts`; sign out self: journey `journeys/foodsaver/coordinate-a-pickup-day.journey.ts` (checkpoints `sign-out-confirm`, `signed-out`); manager pre-confirmed: unit `PickupSignupEditAndConfirmTest`; remove other, confirm from ✓, confirm-all, pending filter: store-manager journey (pending, routed to the store-manager area) + unit `store-pickups.tab.spec.ts`; slot names: unit `pickup-slot.component.spec.ts`; before the horizon: unit `PickupEligibilityRulesTest` |
| `pickups/pu3-slot-details.spec.ts` | 3 | PU3 S9 | co-pickup and team member dialogs: journey `journeys/foodsaver/coordinate-a-pickup-day.journey.ts` (checkpoints `slot-co-pickup`, `slot-team-member`); manager dialog with phone: store-manager journey (pending, routed to the store-manager area) + unit `store-pickups.tab.spec.ts`, `SlotDetailRuleTest` |
| `pickups/pu4-eligibility.spec.ts` | 8 | PU4 | already signed up: journey `journeys/foodsaver/coordinate-a-pickup-day.journey.ts` (checkpoint `pickups-tab`); every reason (phone, past, already, full, hygiene, jumper, not a member): unit `PickupEligibilityRulesTest`; Gesperrt chip and dialog: journey `journeys/foodsaver/coordinate-a-pickup-day.journey.ts` (checkpoint `blocked-region-rule`) + unit `pickup-slot.component.spec.ts`, `store-pickups.tab.spec.ts`; own profile cannot clear the mobile number: unit `UserProfileServiceTest`; jumper/unverified refused: unit `StorePermissionMatrixTest`; five languages: `npm run i18n:check` |
| `pickups/pu5-region-rule.spec.ts` | 3 | PU5 | journey `journeys/foodsaver/coordinate-a-pickup-day.journey.ts` (checkpoint `blocked-region-rule`); 24 h exemption, day limit, store without rule: unit `PickupEligibilityRulesTest`; rule lines: unit `eligibility-reasons.component.spec.ts` |
| `pickups/pu6-history.spec.ts` | 2 | D10.3 PU6 | own past pickups: journey `journeys/foodsaver/plan-my-pickups.journey.ts` (checkpoint `history`); admin deletes future pickups: journey `journeys/foodsaver/plan-my-pickups.journey.ts` (orga test, checkpoint `delete-future-dialog`) + unit `pickup-history-page.component.spec.ts`; store history by range: store-manager journey (pending, routed to the store-manager area); KAM may, member may not: unit `StorePermissionMatrixTest` |
| `pickups/pu7-options.spec.ts` | 3 | PU7 | journey `journeys/foodsaver/plan-my-pickups.journey.ts` (checkpoints `pickups-page`, `options-signed-up`); left-out and full options: unit `PickupOptionsRuleTest`; order and summary counts: unit `pickups.util.spec.ts` |
| `pickups/pu7-pickups-page.spec.ts` | 2 | PU3 PU7 | journey `journeys/foodsaver/plan-my-pickups.journey.ts` (checkpoint `pickups-page`); 5 first of my pickups, /pickups/mine redirect: unit `pickups-page.spec.ts` |
| `pickups/pu8-agenda.spec.ts` | 1 | PU8 | journey `journeys/foodsaver/plan-my-pickups.journey.ts` (checkpoints `agenda`, `agenda-empty-day`) |
| `pickups/pu9-reminders.spec.ts` | 1 | PU9 | journey `journeys/foodsaver/pickup-reminders.journey.ts` (checkpoints `settings-reminders-on`, `settings-reminders-off`, `reminder-opened`); job window and setting: unit `PickupReminderJobTest` |
| `polls/v1-polls.spec.ts` | 4 | N3 V1 |  |
| `profiles/a15-bananas.spec.ts` | 1 | A15 G4 N3 | journey `journeys/account/profiles-bananas-buddies.journey.ts` (checkpoints `other-profile`, `banana-dialog`, `received-bananas`); 100 characters, once per pair, who deletes: unit `ProfilePermissionMatrixTest`, client `profiles.util.spec` |
| `profiles/a16-buddies.spec.ts` | 1 | A16 N3 | journey `journeys/account/profiles-bananas-buddies.journey.ts` (checkpoints `buddy-request`, `buddies`; request bell, accept, remove); relations: client `profiles.util.spec` |
| `profiles/a6-profile.spec.ts` | 3 | A6 D10.3 | journey `journeys/account/profiles-bananas-buddies.journey.ts` (checkpoint `own-profile`; own profile, wall post, a foreign profile without composer); what ORGA and strangers see: unit `ProfilePermissionMatrixTest`, client `profiles-pages.spec`; deleted/unknown user: unit `AccountAdminRulesTest`, client `profiles-pages.spec`; error state dropped: per-page data states, decision 180 #5 (shared `fs-data-state` spec + things-go-wrong journey) |
| `profiles/c10-seed-avatars.spec.ts` | 1 | A6 C10 | journey `journeys/account/profile-and-settings.journey.ts` (photo variants served as JPEG); unit `ImageVariantsTest`; uploads restored by the seed reset: server `SeedResetServiceIT` (every table as after startup, incl. `UPLOAD`) |
| `public/c17-landing.spec.ts` | 18 | C17 M1 |  |
| `public/c17-public-reskin.spec.ts` | 3 | C17 |  |
| `quiz/q1-quizzes.spec.ts` | 2 | Q1 |  |
| `quiz/q2-session.spec.ts` | 7 | A8 D10.3 Q2 |  |
| `quiz/q3-editor.spec.ts` | 2 | Q3 |  |
| `quiz/q4-guide-quiz.spec.ts` | 2 | Q4 | journey `journeys/einab/foodsaver-becomes-guide.journey.ts` (checkpoints `guide-track-locked`, `quiz-passed`, `guide-track-waiting`, `ready-for-release`); unit `GuideQuizGateTest` (threshold) |
| `quiz/quiz-error-states.spec.ts` | 4 | A8 Q1 Q2 |  |
| `regions/ad1-users.spec.ts` | 3 | A4 AD1 D10.3 R8 |  |
| `regions/r12-conference.spec.ts` | 3 | R12 |  |
| `regions/r13-statistics.spec.ts` | 2 | R3 R13 |  |
| `regions/r2-membership.spec.ts` | 4 | R2 |  |
| `regions/r3-region-page.spec.ts` | 6 | D10.3 R3 R5 R6 R7 R8 R13 |  |
| `regions/r4-public-region.spec.ts` | 2 | R4 |  |
| `regions/r7-region-admin.spec.ts` | 4 | R1 R7 R10 |  |
| `regions/r9-groups.spec.ts` | 4 | R9 R11 |  |
| `regions/regions-error-states.spec.ts` | 4 | R2 R3 R9 |  |
| `reports/g1-reports.spec.ts` | 2 | D10.3 G1 N3 R5 |  |
| `reports/g2-mediation.spec.ts` | 1 | G2 N3 |  |
| `resources/c6-resources.spec.ts` | 3 | C6 N3 |  |
| `search/c5-search.spec.ts` | 7 | C5 |  |
| `shell/ad1-date-inputs.spec.ts` | 4 | AD1 |  |
| `shell/ad1-i18n.spec.ts` | 3 | AD1 |  |
| `shell/ad1-request-bar.spec.ts` | 2 | AD1 |  |
| `shell/au12-offline.spec.ts` | 2 | AU12 |  |
| `shell/d4-dashboard-next-step.spec.ts` | 2 | D4.3 |  |
| `shell/d4-error-pages.spec.ts` | 2 | D4.3 |  |
| `shell/d4-menu-capabilities.spec.ts` | 4 | D4.3 |  |
| `shell/d4-region-switcher.spec.ts` | 3 | D4.3 |  |
| `shell/d4-shell-navigation.spec.ts` | 6 | D4.3 |  |
| `shell/d4-sidenav.spec.ts` | 7 | D4.3 |  |
| `shell/i18n-smoke.spec.ts` | 4 | A6 AC1 B1 C1 C5 C13 D18.3 FS1 G1 M1 |  |
| `shell/phone-width.spec.ts` | 5 | EA18 S4 S14 V1 |  |
| `stores/s1-store-list.spec.ts` | 3 | S1 | journey `journeys/store-manager/set-up-a-store.journey.ts` (checkpoint `region-stores-search`), `journeys/store-manager/confirm-and-plan-pickups.journey.ts` (checkpoint `my-stores`); groups, inactive toggle, chip: unit `StoreListServiceTest`, `stores-pages.spec.ts`, `m3-shared.spec.ts`; jumper tabs: unit `stores-pages.spec.ts`, `StorePermissionMatrixTest` |
| `stores/s11-wall.spec.ts` | 2 | S11 | journey `journeys/store-manager/follow-wall-log-and-statistics.journey.ts` (checkpoint `store-wall`); delete matrix, jumper forbidden: unit `StoreWallServiceTest`, `store-wall-stats.spec.ts` |
| `stores/s12-store-log.spec.ts` | 1 | D10.3 S12 | journey `journeys/store-manager/follow-wall-log-and-statistics.journey.ts` (checkpoint `store-log-details`); date range, right: unit `StoreLogServiceTest`, `store-wall-stats.spec.ts` |
| `stores/s13-delete.spec.ts` | 1 | S13 | journey `journeys/store-manager/curate-stores-as-coordinator.journey.ts` (checkpoints `delete-dialog`, `delete-refused`); unit `StoreServiceTest` (deleteRefusedWhenTheStoreEverHadSignups, deleteArchivesStoreLogsAndNotifiesTeamJumpersApplicants) |
| `stores/s14-statistics.spec.ts` | 3 | S14 | journey `journeys/store-manager/follow-wall-log-and-statistics.journey.ts` (checkpoints `statistics`, `statistics-kg-member-search`); kg, sort, former, managers only: unit `StoreStatisticsServiceTest`, `store-wall-stats.spec.ts` |
| `stores/s15-navigation.spec.ts` | 1 | S15 | journey `journeys/store-manager/follow-wall-log-and-statistics.journey.ts` (checkpoint `navigate-menu`) |
| `stores/s16-region.spec.ts` | 1 | S16 | journey `journeys/store-manager/curate-stores-as-coordinator.journey.ts` (checkpoint `move-refused`); unit `StoreServiceTest` (moveNeedsWholeTeamInTargetRegion) |
| `stores/s17-store-visibility.spec.ts` | 5 | S17 | journey `journeys/store-manager/set-up-a-store.journey.ts` (map bubbles and partners page, checkpoint `store-form-edit`); unverified and booking: server `StoreIdentityLeakIT`, unit `StoreVisibilityTest`, `StoreAreaRulesTest`, `MarkerServiceTest`, `StoreListServiceTest`; public name switch: unit `PublicNamingRulesTest` |
| `stores/s2-import-api.spec.ts` | 2 | D16.2 S2 | server `StoresCsvImportServiceTest` (dry run writes nothing, row errors/warnings, rerun UNCHANGED, coordination right), `StoresCsvImportZuerichIT`; mayCoordinateStores: unit `stores-import.spec.ts`; empty body 400: dropped: contract `minLength: 1` (generated validation); runbook CLI: dropped: also run by `foundation/m7-launch-api.spec.ts` |
| `stores/s2-import.spec.ts` | 2 | D16.2 S2 | journey `journeys/store-manager/import-stores-from-csv.journey.ts` (checkpoints `import-form`, `import-preview`, `import-preview-stale`, `import-result`); forbidden: unit `stores-import.spec.ts`, `StoresCsvImportServiceTest` |
| `stores/s2-store-form.spec.ts` | 6 | S2 S12 | journey `journeys/store-manager/set-up-a-store.journey.ts` (checkpoints `store-form-address`, `new-store-overview`, `store-form-edit`); who may create/edit: unit `StorePermissionMatrixTest`; description vs public info: unit `StoreMapperTest`, `store-overview.tab.spec.ts` |
| `stores/s3-chains.spec.ts` | 2 | S3 | journey `journeys/store-manager/set-up-a-store.journey.ts` (checkpoints `chain-edit-dialog`, `chain-page`); create, KAMs, read-only: unit `StoreChainServiceTest` |
| `stores/s4-categories.spec.ts` | 2 | S4 | journey `journeys/store-manager/curate-stores-as-coordinator.journey.ts` (checkpoints `categories`, `merge-dialog`); ORGA only, in use, merge: unit `StoreCategoryServiceTest` |
| `stores/s5-team.spec.ts` | 2 | S5 | journey `journeys/store-manager/build-the-team.journey.ts` (checkpoint `team-with-application`); members without actions: unit `store-team.spec.ts`, `StoreTeamServiceTest` (teamViewHasOnlyApplicationsAndInvitations); team status badge: journey `journeys/store-manager/set-up-a-store.journey.ts` |
| `stores/s6-apply.spec.ts` | 5 | S6 | journey `journeys/store-manager/build-the-team.journey.ts` (checkpoints `apply-dialog-text-required`, `team-with-application`); decline, cancel, accept bell: unit `StoreTeamServiceTest` (decliningAnApplicationNeedsAMessageThatReachesTheApplicant, anApplicantCancelsOnlyTheirOwnOpenApplication, acceptingAnApplicationMakesAMemberAndRingsTheApplicantsBell), `m2-shared.spec.ts`; closed team, hygiene: unit `StorePermissionMatrixTest`, `StoreTeamServiceTest` (requirementsInUpstreamOrder), `store-team.spec.ts` |
| `stores/s7-invitations.spec.ts` | 2 | S7 | journey `journeys/store-manager/build-the-team.journey.ts` (checkpoint `invite-dialog`); decline, withdraw: unit `StoreTeamServiceTest` (anInvitationIsWithdrawnByTheManagerOrDeclinedByTheInvitedPerson); who can be invited: unit `StoreTeamServiceTest` (inviteCandidatesAreRegionMembersInviteWouldAccept, inviteBlockerNamesEachReasonInTheOrderInviteChecks) |
| `stores/s8-standby-managers.spec.ts` | 4 | PU6 S8 | journey `journeys/store-manager/build-the-team.journey.ts` (checkpoints `member-menu`, `standby-message`); limits, last manager: unit `StoreTeamServiceTest` (appointManagerLimitsUnlessCoordinator, lastManagerStaysUnlessCoordinator, managerCannotLeaveAndLeavingDeletesFutureSignups), `store-team.spec.ts`; future pickups (PU6): unit `StoreTeamServiceTest` (removingSomeoneElseDeletesTheirFutureSignups) |
| `stores/s9-team-list.spec.ts` | 6 | S9 | journey `journeys/store-manager/build-the-team.journey.ts` (checkpoints `team-with-application`, `team-jumpers`; phone width on mobile-chrome); sort, pages, distance, phone: unit `StoreTeamServiceTest` (teamListSortsByPickupsAndLastPickupWithNoPickupLast, teamListPagesWithinBounds, teamListKeepsDistanceAndPhoneVisibility, appointBlockerNamesEachReasonInTheOrderAppointManagerChecks), `store-team.spec.ts` |
| `stores/stores-error-states.spec.ts` | 4 | S1 S2 S6 |  |
| `stores/team-error-states.spec.ts` | 4 | S5 S6 S8 S9 |  |
| `stores/wall-log-stats-error-states.spec.ts` | 3 | PU6 S11 S12 S14 |  |
| `surveys/sv1-surveys.spec.ts` | 3 | A2 SV1 |  |
| `uploads/c10-upload-storage.spec.ts` | 1 | C10 |  |
| `verification/ve2-verify.spec.ts` | 7 | A8 D10.3 Ve2 |  |
| `verification/ve3-passport.spec.ts` | 7 | A8 D10.3 EA20 Ve3 |  |
| `verification/verification-error-states.spec.ts` | 3 | A8 Ve2 Ve3 |  |

## Screen files (`e2e/screenshots/*.screen.ts`)

| Old screen file | Captures | IDs | Now covered by |
|---|---|---|---|
| `account.screen.ts` | 12 | A3 A4 A5 A9 A11 A13 A18 A20 AD1 | journey `journeys/account/profile-and-settings.journey.ts` (`profile-mobile-required`, `photo-crop`, `sleep-mode-temporary`, `calendar-card`), journey `journeys/account/privacy-and-sessions.journey.ts` (`bounce-banner`, `bounced-list`, `email-confirmed`, `delete-dialog`); the bad confirm token, the settings error and the two date-input captures (AD1): dropped: shared states and components go to the gallery journey (step 4) |
| `achievements.screen.ts` | 8 | AC1 | journey `journeys/account/achievements-and-notes.journey.ts` (`achievements-board`, `define-dialog`, `award-dialog`, `awards-list`, `profile-with-award`); the member view, the global admin page, loading and error: dropped: the member view differs only by hidden buttons (unit `AchievementVisibilityTest`), states go to the gallery journey (step 4) |
| `admin.screen.ts` | 12 | AD1 C10 C11 C12 C14 D17.2 | |
| `auth.screen.ts` | 13 | A1 A2 A12 A17 A22 D7.3 | journey `journeys/account/sign-up-and-log-in.journey.ts` (`login`, `magic-link-sent`, `consent`, `dashboard-passkey-nudge`, `link-used`, `login-methods`), journey `journeys/account/passkey-login.journey.ts` (`passkey-dialog`, `passkey-added`, `login-passkey-button`, `security-log`), journey `journeys/account/privacy-and-sessions.journey.ts` (`sessions`, `security-log`), journey `journeys/account/profile-and-settings.journey.ts` (`settings`, `profile-form`); the Google error, verify loading and the sessions/security-log/methods states: dropped: shared states go to the gallery journey (step 4) |
| `automation.screen.ts` | 6 | AU1 AU4 AU5 AU7 AU8 AU9 AU10 | journeys `journeys/store-manager/set-pickup-rules.journey.ts` (`pickup-policy`, `store-picker`, `linked-rule-one-way`, `gap-rule-with-store-list`, `protocol`), `journeys/store-manager/advertise-open-slots.journey.ts` (`advert-unsaved`, `store-chats`, `signout-alerts`), `journeys/store-manager/review-region-automation.journey.ts` (`region-overview`, `grouping`, `metrics`, `metrics-decline-reasons`); picker loading/error, gap conflict, empty channels: dropped: states shown once in the gallery (decision 180 §2, §5) |
| `baskets.screen.ts` | 8 | B1 B2 | |
| `content.screen.ts` | 15 | C1 C2 C3 C4 C7 C8 C9 S17 | |
| `dashboard.screen.ts` | 8 | A19 | journey `journeys/account/dashboard-feed.journey.ts` (`dashboard`, `feed-filtered`, `nearby-baskets`, `baskets-without-address`); the single home cards, the newcomer's chips and loading/error: dropped: the `dashboard` checkpoint shows the cards of the first screen, the newcomer journeys start on the dashboard, states go to the gallery journey (step 4) |
| `events.screen.ts` | 3 | E1 E2 E3 | |
| `forum.screen.ts` | 4 | D10.3 F1 F2 F3 F5 G3 | |
| `fsp.screen.ts` | 20 | AU1 FS1 FS2 | |
| `gallery.screen.ts` | 2 |  | |
| `history.screen.ts` | 7 | D10.3 | journey `journeys/account/my-activity.journey.ts` (`my-activity`, `activity-details`, `activity-date-range`); empty, loading, error, forbidden and the ORGA export view: dropped: shared states go to the gallery journey (step 4), the export button is client `my-activity-page.spec` |
| `intro-pickups.screen.ts` | 11 | EA1 EA2 EA5 EA7 EA8 EA9 EA10 EA11 EA13 EA15 EA16 EA17 EA18 EA19 EA21 Q4 | journey `journeys/einab/newcomer-books-intro-pickups.journey.ts` (checkpoints `bookable-list`, `booking-success`, `nothing-left`, `my-bookings`); journey `journeys/einab/newcomer-subscribes-to-new-dates.journey.ts` (checkpoints `public-dates`, `unsubscribed`); journey `journeys/einab/guide-offers-intro-pickups.journey.ts` (checkpoints `guide-dashboard`, `online-form-error`, `linked-form`, `upcoming-einabs`, `guide-settings`, `signout-warning`); journey `journeys/einab/guide-hosts-a-newcomer.journey.ts` (checkpoint `comment-written`); journey `journeys/einab/ambassador-manages-intro-pickups.journey.ts` (checkpoints `admin-einabs`, `past-bookings`, `guides-tab`, `import-preview`, `funnel`); journey `journeys/einab/foodsaver-becomes-guide.journey.ts` (checkpoint `ready-for-release`); journey `journeys/einab/newcomer-becomes-foodsaver.journey.ts` (checkpoint `no-region`); unit `intro-pickups-pages.spec.ts` (empty kinds none, fairteiler, noMatch); dropped: dark and French variants (decision 180: light German only); dropped: loading, error and forbidden captures (shared `fs-data-state` states, gallery and things-go-wrong journey) |
| `landing.screen.ts` | 10 | C17 | |
| `layout.screen.ts` | 11 | D4.3 | |
| `legal.screen.ts` | 3 | EA4 Ve1 | |
| `mailboxes.screen.ts` | 3 | MB1 MB2 MB3 MB4 | |
| `map.screen.ts` | 7 | M1 MT1 S17 | |
| `messaging.screen.ts` | 7 | N1 S10 | |
| `notifications.screen.ts` | 3 | N3 | |
| `oauth.screen.ts` | 7 | C13 | |
| `onboarding.screen.ts` | 4 | D11.5.2 EA8 Q4 | |
| `pages.screen.ts` | 6 | AU12 D4.3 | |
| `pickups.screen.ts` | 19 | A19 AU1 EA16 FS2 PU1 PU2 PU3 PU4 PU7 PU8 PU10 S9 || foodsaver captures: journeys `journeys/foodsaver/coordinate-a-pickup-day.journey.ts`, `journeys/foodsaver/sign-up-and-offer-a-basket.journey.ts`, `journeys/foodsaver/plan-my-pickups.journey.ts` (tab, slot dialogs, sign-up, booked, Gesperrt reasons, pickups page, agenda, dashboard, help card); manager captures (regular editor, one-time dialog, manager tab, manager slot dialog, move dialog): store-manager journey (pending, routed to the store-manager area); loading/error captures: dropped: per-page states, replaced by the shared fs-data-state spec and gallery (decision 180.5); guide cleaning row (FS2) and store-rule/linked-step captures (AU1): dropped here, walked by the Fairteiler and automation journeys |
| `polls.screen.ts` | 2 | V1 | |
| `profiles.screen.ts` | 6 | A6 A15 A16 | journey `journeys/account/profiles-bananas-buddies.journey.ts` (`own-profile`, `other-profile`, `received-bananas`, `buddies`); sleep banner, loading and error: dropped: the sleep banner is client `profiles-pages.spec` and the moon is asserted in the profile-and-settings journey, states go to the gallery journey (step 4) |
| `quiz.screen.ts` | 12 | A8 Q1 Q2 Q3 | |
| `regions.screen.ts` | 19 | A4 AD1 R2 R3 R4 R5 R7 R8 R9 R13 | |
| `reports.screen.ts` | 9 | D10.3 G1 G2 | |
| `resources.screen.ts` | 7 | C6 | |
| `search.screen.ts` | 6 | C5 | |
| `store-chains.screen.ts` | 5 | PU9 S3 S4 | journeys `journeys/store-manager/set-up-a-store.journey.ts` (`chain-page`, `chain-edit-dialog`), `journeys/store-manager/curate-stores-as-coordinator.journey.ts` (`categories`, `merge-dialog`); chain list error: dropped: generic error state (decision 180 §5); pickup reminder setting (PU9): dropped: belongs to the foodsaver pickups area, not a store-manager state |
| `stores-import.screen.ts` | 5 | D16.2 S2 | journey `journeys/store-manager/import-stores-from-csv.journey.ts` (`import-form`, `import-preview`, `import-result`); loading, forbidden: dropped: generic DataState states (decision 180 §5) |
| `stores-team.screen.ts` | 14 | S5 S6 S7 S8 S9 S14 | journey `journeys/store-manager/build-the-team.journey.ts` (`team-with-application`, `team-jumpers`, `member-menu`, `standby-message`, `invite-dialog`; phone width on mobile-chrome), apply refusal: `apply-dialog-text-required`; empty filter, page 2, appoint blocked, member view, invitations on my stores, statistics former: dropped: state variants of the same screens (decision 180 §2); loading/error: dropped: generic DataState states (decision 180 §5) |
| `stores-wall-log.screen.ts` | 8 | PU6 S11 S12 S13 S14 | journey `journeys/store-manager/follow-wall-log-and-statistics.journey.ts` (`store-wall`, `store-log-details`, `statistics`, `pickup-history`), `journeys/store-manager/curate-stores-as-coordinator.journey.ts` (`delete-dialog`); store pickup history (PU6): `pickup-history`; my past pickups: dropped: the foodsaver's own history belongs to the foodsaver pickups area; wall error, empty wall: dropped: generic DataState states (decision 180 §5) |
| `stores.screen.ts` | 12 | S1 S2 S15 S17 | journeys `journeys/store-manager/confirm-and-plan-pickups.journey.ts` (`my-stores`, `store-overview`), `journeys/store-manager/set-up-a-store.journey.ts` (`region-stores-search`, `store-form-address`, `store-form-edit`), `journeys/store-manager/follow-wall-log-and-statistics.journey.ts` (`navigate-menu`); jumper store page, region empty, forbidden, loading, my stores error: dropped: generic DataState and role variants (decision 180 §2, §5) |
| `surveys.screen.ts` | 2 | SV1 | |
| `verification.screen.ts` | 8 | A8 Ve2 Ve3 | |

## Integration tests (`server/src/test/**/*IT.java`)

| Class | Now covered by |
|---|---|
| `BackupRestoreIT` | |
| `IdorSpotCheckIT` | |
| `IndexUsagePostgresIT` | |
| `LiquibasePostgresIT` | |
| `LoadPathsPostgresIT` | |
| `LocalProfileSmokeIT` | |
| `QueryCountIT` | |
| `StagingSeedOnExistingSchemaPostgresIT` | |
| `achievement/AchievementFlowIT` | |
| `activity/ActivitySourcesIT` | |
| `admin/AdminOperationsIT` | |
| `admin/AdminSettingsIT` | |
| `auth/GoogleLoginIT` | |
| `auth/MagicLinkFlowIT` | |
| `auth/PasskeyRepositoriesIT` | |
| `auth/SessionCapabilitiesIT` | |
| `auth/WebAuthnEndpointsIT` | |
| `automation/AdvertFlowIT` | |
| `automation/GapGroupMigrationIT` | |
| `automation/LinkCandidateFlowIT` | |
| `automation/LinkedBookingFlowIT` | |
| `automation/PickupPolicyFlowIT` | |
| `basket/BasketFlowIT` | |
| `calendar/CalendarFeedIT` | |
| `common/jobs/ShedLockIT` | |
| `common/realtime/WebSocketHandshakeIT` | |
| `config/ForwardedHeadersIT` | |
| `config/StaticResourceTilesIT` | |
| `config/StaticResourceTilesOffIT` | |
| `content/BlogFlowIT` | |
| `content/ContentApiIT` | |
| `dev/service/AuthSeedContractIT` | |
| `dev/service/SeedResetServiceIT` | |
| `dev/web/FakeOidcFlowIT` | |
| `event/EventFlowIT` | |
| `forum/ForumFlowIT` | |
| `fsp/FspCleaningFlowIT` | |
| `fsp/FspFlowIT` | |
| `intropickup/GuideQuizApiIT` | |
| `intropickup/IntroPickupApiIT` | |
| `intropickup/IntroPickupLinkedPickupIT` | |
| `intropickup/PastImportZuerichIT` | |
| `mailbox/ImapMailSourceIT` | |
| `mailbox/MailboxApiIT` | |
| `map/MapIT` | |
| `messaging/ConversationSyncIT` | |
| `messaging/MessageSeqPostgresIT` | |
| `messaging/MessagingApiIT` | |
| `notification/NotificationFlowIT` | |
| `notification/telegram/TelegramLinkFlowIT` | |
| `oauthserver/OAuthFlowIT` | |
| `onboarding/OnboardingApiIT` | |
| `pickup/ExtraSlotMigrationIT` | |
| `pickup/PickupMoveIT` | |
| `pickup/PickupUserApiIT` | |
| `pickup/PickupsApiIT` | |
| `poll/PollFlowIT` | |
| `profile/ProfileFlowIT` | |
| `quiz/QuizApiIT` | |
| `region/RegionApiIT` | |
| `region/RegionsCsvImportZuerichIT` | |
| `report/ReportFlowIT` | |
| `resource/ResourceFlowIT` | |
| `search/SearchFlowIT` | |
| `search/SearchPostgresIT` | |
| `statistics/StatisticsFlowIT` | |
| `store/StoreApiIT` | |
| `store/StoreChainApiIT` | |
| `store/StoreIdentityLeakIT` | |
| `store/StorePublicNamingIT` | |
| `store/StoreSeedIT` | |
| `store/StoreTeamFlowIT` | |
| `store/StoreWallApiIT` | |
| `store/StoresCsvImportZuerichIT` | |
| `survey/SurveyFlowIT` | |
| `upload/UploadPublicAccessIT` | |
| `user/AccountDeletionIT` | |
| `user/AccountFlowIT` | |
| `user/EmailChangeFlowIT` | |
| `verification/VerificationApiIT` | |

## Slow Spring-context unit classes (over 10 s)

| Class | Fork seconds | Now |
|---|---|---|

## Client specs (`client/src/app/**/*.spec.ts`)

| Deleted spec | Now covered by |
|---|---|
