# D10.3 audit — history views (plan §10.2)

Final-gate audit (plan-m7 Part D) of the deliverable "All views from the table above" (plan §10.2, ledger D10.3).
For every view of the table: the client route/component that renders it and the Playwright test that proves it,
tagged `@D10.3`. Audited 2026-10-30 on `build/m7-gate-prep` (main at `527282f`).

Result: **13 / 13 views built and proven by a tagged e2e test.** 11 tests were already present but carried only
their feature tag; `@D10.3` was added to their titles (tagging only, no assertion changed). Two small test
additions closed proof gaps: the profile history tab now asserts that entries load, and a new test proves the
orga side of the login & security log. One content gap was fixed in the backend: the region admin log
(`GET /api/regions/{id}/history`) only returned REGION-subject entries, so working-group changes (GROUP subject,
`region_id` = the parent region) never appeared, and a working group's own history tab was always empty. It now
goes through `HistoryQueryService.regionLog` (region: own entries + GROUP entries recorded for it; group: its GROUP
entries; the subject's access rule still decides), with a unit test. No UI change was needed.

| # | View (§10.2) | Who sees it | Route / component | E2E proof (file:line, tags) | Status |
|---|---|---|---|---|---|
| 1 | Store log | store managers, store coordination, ambassadors | `/stores/:storeId/log` → `features/stores/tabs/store-log.tab.ts` (`fs-history-timeline`, STORE subject, date range + grouped action filter) | `e2e/tests/stores/s12-store-log.spec.ts:25` `@S12 @D10.3 @page` | done |
| 2 | Profile change history | ambassadors, FS management, the user | admin: `/users/:userId` → `features/users-admin/pages/user-admin-page.component.html` (history section); public profile tab `/profile/:userId?tab=history` → `features/profiles/pages/profile-page.component.html`; own: `/me/activity` | `e2e/tests/regions/ad1-users.spec.ts:22` `@A4 @AD1 @D10.3` (admin edit + deactivation appear); `e2e/tests/profiles/a6-profile.spec.ts:12` `@A6 @D10.3 @page` (orga opens the history tab, entries load — assertion added) | done (tags + one assertion added) |
| 3 | Verification history | ambassadors, FS management | `/users/:userId/verification-history` → `features/verification/pages/user-verification-history-page.component.ts` | `e2e/tests/verification/ve2-verify.spec.ts:58` `@Ve2 @A8 @D10.3` | done |
| 4 | Passport history | ambassadors, the user | own: `/me/passport` → `features/verification/pages/my-passport-page.component.ts`; ambassador: `/users/:userId/verification-history` (passport timeline) | `e2e/tests/verification/ve3-passport.spec.ts:114` `@Ve3 @A8 @D10.3` | done (tag added) |
| 5 | Quiz session history | ambassadors, quiz AG, the user | `/quizzes/history`, `/quizzes/history/:userId` → `features/quiz/pages/quiz-history-page.component.ts` (linked from `/users/:userId`) | `e2e/tests/quiz/q2-session.spec.ts:165` `@Q2 @A8 @D10.3` (user, ambassador, stranger forbidden) | done (tag added) |
| 6 | Pickup history | store team, the user (own) | store: `/stores/:storeId/pickups/history` → `features/pickups/tabs/store-pickup-history.tab.ts`; own: `/me/pickups/history`, `/users/:userId/pickups` → `features/pickups/pages/pickup-history-page.component.ts` | `e2e/tests/pickups/pu6-history.spec.ts:16` `@PU6 @D10.3 @page` (store, manager + KAM); `e2e/tests/pickups/pu6-history.spec.ts:58` `@PU6 @D10.3` (own past pickups) | done (tag added to :58) |
| 7 | EinAb history | guides, ambassadors | `/regions/:regionId/intro-pickups/admin` "Bookings" tab with past bookings (status DONE/CANCELLED = attendance/cancellations, counted switch, comments drawer) → `features/intro-pickups/pages/admin-intro-pickups-page.component.ts`; guide side `/guide` (slot comments); verification queue shows EinAb count + guide comments | `e2e/tests/intro-pickups/ea18-admin.spec.ts:7` `@EA18 @D10.3 @page` (past bookings, cancelled booking counted); `e2e/tests/intro-pickups/ea18-admin.spec.ts:51` `@EA18 @EA13 @D10.3` (comments); also `ea13-comments.spec.ts:6` `@EA13`, `ve2-verify.spec.ts:11` `@Ve2` | done (tags added) |
| 8 | Automation audit | `CAN_SEE_ALL_AUTOMATION_DECISIONS`, store managers | region: `/regions/:regionId/automation/audits` → `features/automation` `RegionAuditsSection`; store: `/stores/:storeId/automation/audits` → `features/automation/sections/store-audits.section.ts` (reasons, dry-run flag) | `e2e/tests/automation/au8-audits.spec.ts:6` `@AU8 @D10.3 @page` (coordinator all stores, member only own, kind/dry-run filters, reasons); `e2e/tests/automation/au1-pickup-policy.spec.ts:21` `@AU1 @D10.3 @page` (store manager sees decline with GAP reason) | done (tags added) |
| 9 | Region admin log | ambassadors, orga | `/regions/:regionId/history` tab → `features/regions/tabs/region-history.tab.ts` (`getRegionHistory`, REGION subject) | `e2e/tests/regions/r3-region-page.spec.ts:93` `@R7 @D10.3` (option change recorded and listed); tab hidden for members: `r3-region-page.spec.ts:19`; working-group changes: `HistoryQueryServiceTest.regionLogAddsTheWorkingGroupChangesOfTheRegion` | done (fix: working-group changes were missing, see below) |
| 10 | Moderation log | moderation AG | `/regions/:regionId/moderation-log` → `features/forum/pages/moderation-log-page.component.ts` | `e2e/tests/forum/f3-posts.spec.ts:57` `@G3 @D10.3 @page`; forbidden: `f3-posts.spec.ts:94` `@G3 @D10.3` | done |
| 11 | Report log | report / arbitration team | `/regions/:regionId/report-log` → `features/reports/tabs/region-report-log.tab.ts` | `e2e/tests/reports/g1-reports.spec.ts:30` `@G1 @D10.3 @N3 @page` | done |
| 12 | Login & security log | the user (own), orga | own: `/account/security-log` → `features/account/pages/security-log-page.component.ts` (+ link to `/me/activity?preset=logins`); orga: `/users/:userId` history with the action filter (any USER action) | `e2e/tests/account/a22-sessions.spec.ts:68` `@A22 @D10.3 @page`; `e2e/tests/history/d10-history.spec.ts:268` `@D10.3 @A22`; orga: `d10-history.spec.ts:330` `@D10.3 @A22` (new test) | done (tag + new test) |
| 13 | My activity | the user | `/me/activity` → `features/history/pages/my-activity-page.component.ts` (filters, paging, CSV export) | `e2e/tests/history/d10-history.spec.ts:85` `@D10.3 @page`, `:205` (access denied), `:226` (loading/error/offline), `:255`, `:285` (labels in 5 languages), `:299` (CSV export) | done |

## Observations (no gap, recorded for the review)
- Plan §10.2 says every view uses `HistoryTimeline`. Views 1–4, 9–13 do. Pickup history (6), quiz session history
  (5), EinAb history (7) and automation audit (8) are domain lists (pickups, quiz sessions, bookings, automation
  audits are their own tables, not `history_entry` rows), with the same DataState states and date/kind filters but
  no CSV export. Their history *events* (sign-ups, bookings, quiz results) also appear in the timeline views (store
  log, my activity, profile history).
- Region admin log (9): the e2e proves an option change in the UI; working-group changes are proven at service level
  (unit test). Member removals and admin grants are REGION entries on the same endpoint and are not asserted
  separately. The tab's action filter offers REGION actions only (GROUP entries are labelled correctly but cannot be
  filtered by their own action codes).
- Profile change history (2) shows the actions of `UserAdminController.PROFILE_HISTORY_ACTIONS` by default; region
  joins/leaves are REGION-subject entries (region log, my activity), not in that default list.

## Specs run
chromium, `--workers=1`, against the packaged jar of this branch (Windows machine, no Docker): a22-sessions,
au8-audits, au1-pickup-policy, ea18-admin, pu6-history, a6-profile, q2-session, ad1-users, r3-region-page,
ve3-passport, d10-history: **51 passed** (3.9 min); e2e typecheck green. Server: `HistoryQueryServiceTest`, `ArchitectureTest` green.
