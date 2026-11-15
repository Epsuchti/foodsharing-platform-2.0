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
| `account/a11-calendar.spec.ts` | 2 | A11 E3 |  |
| `account/a12-language.spec.ts` | 1 | A12 |  |
| `account/a13-delete.spec.ts` | 2 | A13 |  |
| `account/a14-business-card.spec.ts` | 2 | A14 |  |
| `account/a17-theme.spec.ts` | 1 | A17 |  |
| `account/a18-bounce.spec.ts` | 2 | A18 MB5 |  |
| `account/a20-info-mail.spec.ts` | 1 | A20 N7 |  |
| `account/a21-export.spec.ts` | 2 | A21 |  |
| `account/a22-sessions.spec.ts` | 4 | A22 D8.4 D10.3 |  |
| `account/a3-email-change.spec.ts` | 3 | A3 N6 |  |
| `account/a4-profile.spec.ts` | 4 | A4 |  |
| `account/a5-photo.spec.ts` | 2 | A5 C10 |  |
| `account/a7-notes.spec.ts` | 2 | A7 |  |
| `account/a9-sleep.spec.ts` | 2 | A9 |  |
| `account/d17-3-privacy-api.spec.ts` | 1 | D17.3 |  |
| `achievements/ac1-achievements.spec.ts` | 3 | AC1 Q2 S3 |  |
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
| `auth/a1-signup.spec.ts` | 7 | A1 D8.1 D8.2 |  |
| `auth/a2-login-translations.spec.ts` | 1 | A2 |  |
| `auth/a2-login.spec.ts` | 6 | A2 D8.3 |  |
| `auth/auth-error-states.spec.ts` | 5 | A2 A22 D4.3 D7.3 |  |
| `auth/d7-dev-switcher.spec.ts` | 1 | D7.3 |  |
| `automation/au1-au10-link-picker-channels.spec.ts` | 4 | AU1 AU10 N5 |  |
| `automation/au1-blocked-signup.spec.ts` | 3 | AU1 PU3 PU4 |  |
| `automation/au1-gap-store-list.spec.ts` | 5 | AU1 |  |
| `automation/au1-linked-signup.spec.ts` | 2 | AU1 PU3 |  |
| `automation/au1-pickup-policy.spec.ts` | 6 | AU1 D10.3 |  |
| `automation/au11-region-sender.spec.ts` | 2 | AU11 |  |
| `automation/au13-audit-retention.spec.ts` | 1 | AU13 |  |
| `automation/au2-ownership.spec.ts` | 3 | AU2 |  |
| `automation/au3-auto-accept.spec.ts` | 1 | AU3 |  |
| `automation/au4-adverts.spec.ts` | 4 | AU4 AU9 |  |
| `automation/au5-signout-alerts.spec.ts` | 2 | AU5 |  |
| `automation/au6-extra-slot.spec.ts` | 2 | AU6 |  |
| `automation/au7-au9-au10-region.spec.ts` | 3 | AU7 AU9 AU10 N5 |  |
| `automation/au8-audits.spec.ts` | 1 | AU8 D10.3 |  |
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
| `dashboard/a19-dashboard.spec.ts` | 3 | A19 |  |
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
| `history/d10-history.spec.ts` | 9 | A22 D10.1 D10.3 |  |
| `intro-pickups/ea1-landing.spec.ts` | 1 | EA1 |  |
| `intro-pickups/ea10-guide.spec.ts` | 1 | EA10 |  |
| `intro-pickups/ea11-templates.spec.ts` | 1 | EA11 |  |
| `intro-pickups/ea12-assign.spec.ts` | 1 | EA12 |  |
| `intro-pickups/ea13-comments.spec.ts` | 1 | EA13 |  |
| `intro-pickups/ea14-done.spec.ts` | 1 | EA14 |  |
| `intro-pickups/ea15-ics.spec.ts` | 1 | EA15 |  |
| `intro-pickups/ea16-pickup-candidates.spec.ts` | 1 | EA16 |  |
| `intro-pickups/ea17-guides.spec.ts` | 4 | EA17 Q4 |  |
| `intro-pickups/ea18-admin.spec.ts` | 2 | D10.3 EA13 EA18 |  |
| `intro-pickups/ea19-import.spec.ts` | 1 | EA19 |  |
| `intro-pickups/ea2-public-slots.spec.ts` | 2 | EA2 |  |
| `intro-pickups/ea20-progress-job.spec.ts` | 1 | EA20 |  |
| `intro-pickups/ea21-funnel.spec.ts` | 1 | EA21 |  |
| `intro-pickups/ea22-settings.spec.ts` | 2 | EA22 Q4 |  |
| `intro-pickups/ea3-foodsaver-id.spec.ts` | 1 | EA3 |  |
| `intro-pickups/ea5-booking-rules.spec.ts` | 6 | EA5 |  |
| `intro-pickups/ea6-categories.spec.ts` | 1 | EA6 |  |
| `intro-pickups/ea7-my-bookings.spec.ts` | 1 | EA7 |  |
| `intro-pickups/ea8-progress.spec.ts` | 1 | EA8 |  |
| `intro-pickups/ea9-notifications.spec.ts` | 2 | EA9 |  |
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
| `onboarding/ea8-journey.spec.ts` | 1 | D11.5.2 EA8 |  |
| `onboarding/onboarding-error-states.spec.ts` | 2 | D11.5.2 EA8 |  |
| `onboarding/onboarding-nudges.spec.ts` | 2 | EA8 EA21 N3 |  |
| `personas/ambassador.spec.ts` | 1 | D11.5.5 G1 N7 Ve2 |  |
| `personas/foodsaver.spec.ts` | 1 | B1 B2 D11.5.5 N3 PU3 S1 |  |
| `personas/newcomer.spec.ts` | 1 | A19 C1 D11.5.5 EA7 EA8 Q2 |  |
| `personas/orga.spec.ts` | 1 | C11 D11.5.5 |  |
| `personas/store-manager.spec.ts` | 1 | D11.5.5 PU1 PU3 S1 S12 |  |
| `pickups/a19-dashboard-pickups.spec.ts` | 1 | A19 |  |
| `pickups/a19-home-cards.spec.ts` | 2 | A19 PU7 |  |
| `pickups/pickups-error-states.spec.ts` | 4 | PU1 PU3 PU4 PU7 |  |
| `pickups/pu1-regular.spec.ts` | 2 | PU1 PU3 |  |
| `pickups/pu10-move-pickup.spec.ts` | 4 | EA16 PU10 |  |
| `pickups/pu2-one-time.spec.ts` | 2 | PU2 |  |
| `pickups/pu3-day-chat.spec.ts` | 2 | PU3 |  |
| `pickups/pu3-signup.spec.ts` | 8 | PU3 |  |
| `pickups/pu3-slot-details.spec.ts` | 3 | PU3 S9 |  |
| `pickups/pu4-eligibility.spec.ts` | 8 | PU4 |  |
| `pickups/pu5-region-rule.spec.ts` | 3 | PU5 |  |
| `pickups/pu6-history.spec.ts` | 2 | D10.3 PU6 |  |
| `pickups/pu7-options.spec.ts` | 3 | PU7 |  |
| `pickups/pu7-pickups-page.spec.ts` | 2 | PU3 PU7 |  |
| `pickups/pu8-agenda.spec.ts` | 1 | PU8 |  |
| `pickups/pu9-reminders.spec.ts` | 1 | PU9 |  |
| `polls/v1-polls.spec.ts` | 4 | N3 V1 |  |
| `profiles/a15-bananas.spec.ts` | 1 | A15 G4 N3 |  |
| `profiles/a16-buddies.spec.ts` | 1 | A16 N3 |  |
| `profiles/a6-profile.spec.ts` | 3 | A6 D10.3 |  |
| `profiles/c10-seed-avatars.spec.ts` | 1 | A6 C10 |  |
| `public/c17-landing.spec.ts` | 18 | C17 M1 |  |
| `public/c17-public-reskin.spec.ts` | 3 | C17 |  |
| `quiz/q1-quizzes.spec.ts` | 2 | Q1 |  |
| `quiz/q2-session.spec.ts` | 7 | A8 D10.3 Q2 |  |
| `quiz/q3-editor.spec.ts` | 2 | Q3 |  |
| `quiz/q4-guide-quiz.spec.ts` | 2 | Q4 |  |
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
| `stores/s1-store-list.spec.ts` | 3 | S1 |  |
| `stores/s11-wall.spec.ts` | 2 | S11 |  |
| `stores/s12-store-log.spec.ts` | 1 | D10.3 S12 |  |
| `stores/s13-delete.spec.ts` | 1 | S13 |  |
| `stores/s14-statistics.spec.ts` | 3 | S14 |  |
| `stores/s15-navigation.spec.ts` | 1 | S15 |  |
| `stores/s16-region.spec.ts` | 1 | S16 |  |
| `stores/s17-store-visibility.spec.ts` | 5 | S17 |  |
| `stores/s2-import-api.spec.ts` | 2 | D16.2 S2 |  |
| `stores/s2-import.spec.ts` | 2 | D16.2 S2 |  |
| `stores/s2-store-form.spec.ts` | 6 | S2 S12 |  |
| `stores/s3-chains.spec.ts` | 2 | S3 |  |
| `stores/s4-categories.spec.ts` | 2 | S4 |  |
| `stores/s5-team.spec.ts` | 2 | S5 |  |
| `stores/s6-apply.spec.ts` | 5 | S6 |  |
| `stores/s7-invitations.spec.ts` | 2 | S7 |  |
| `stores/s8-standby-managers.spec.ts` | 4 | PU6 S8 |  |
| `stores/s9-team-list.spec.ts` | 6 | S9 |  |
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
| `account.screen.ts` | 12 | A3 A4 A5 A9 A11 A13 A18 A20 AD1 | |
| `achievements.screen.ts` | 8 | AC1 | |
| `admin.screen.ts` | 12 | AD1 C10 C11 C12 C14 D17.2 | |
| `auth.screen.ts` | 13 | A1 A2 A12 A17 A22 D7.3 | |
| `automation.screen.ts` | 6 | AU1 AU4 AU5 AU7 AU8 AU9 AU10 | |
| `baskets.screen.ts` | 8 | B1 B2 | |
| `content.screen.ts` | 15 | C1 C2 C3 C4 C7 C8 C9 S17 | |
| `dashboard.screen.ts` | 8 | A19 | |
| `events.screen.ts` | 3 | E1 E2 E3 | |
| `forum.screen.ts` | 4 | D10.3 F1 F2 F3 F5 G3 | |
| `fsp.screen.ts` | 20 | AU1 FS1 FS2 | |
| `gallery.screen.ts` | 2 |  | |
| `history.screen.ts` | 7 | D10.3 | |
| `intro-pickups.screen.ts` | 11 | EA1 EA2 EA5 EA7 EA8 EA9 EA10 EA11 EA13 EA15 EA16 EA17 EA18 EA19 EA21 Q4 | |
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
| `pickups.screen.ts` | 19 | A19 AU1 EA16 FS2 PU1 PU2 PU3 PU4 PU7 PU8 PU10 S9 | |
| `polls.screen.ts` | 2 | V1 | |
| `profiles.screen.ts` | 6 | A6 A15 A16 | |
| `quiz.screen.ts` | 12 | A8 Q1 Q2 Q3 | |
| `regions.screen.ts` | 19 | A4 AD1 R2 R3 R4 R5 R7 R8 R9 R13 | |
| `reports.screen.ts` | 9 | D10.3 G1 G2 | |
| `resources.screen.ts` | 7 | C6 | |
| `search.screen.ts` | 6 | C5 | |
| `store-chains.screen.ts` | 5 | PU9 S3 S4 | |
| `stores-import.screen.ts` | 5 | D16.2 S2 | |
| `stores-team.screen.ts` | 14 | S5 S6 S7 S8 S9 S14 | |
| `stores-wall-log.screen.ts` | 8 | PU6 S11 S12 S13 S14 | |
| `stores.screen.ts` | 12 | S1 S2 S15 S17 | |
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
