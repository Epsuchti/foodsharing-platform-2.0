# Menu priorities (resume item 9, decision 135)

## Goal
Order the side menu, user menu, phone tabs and phone drawer by what a foodsaver does daily or weekly (decision 135). Meine Aktivität and Quiz move into the user menu. Forum gets its own item. Postfächer shows only to people who have, or can create, a mailbox.

## Decisions
- Decision 135 plus the owner's answers of 2026-11-01. Record them as a new decision in `docs/decisions.md` (next free number after 140) that amends 135:
  1. Forum links to `/regions/home/forum`, which resolves to the active or home region (`homeRegionGuard`). It is shown only to people in a region, and it is the current item on `/regions/<id>/forum…` and `/forum/threads/…`.
  2. Postfächer is shown to people who read at least one mailbox (personal, region/group admin of a unit with a mailbox, or `mailbox_member`). It is also shown to people who can create one: ORGA, and admins of any unit with role ≥ STORE_MANAGER. The server computes this as a new flag, `SessionInfo.mailboxes`.
  3. Einarbeitungen (`introPickups`) and Einarbeiten (`guide`) stay in Community, after Postfächer.
  4. No filler phone tabs. A newcomer sees Mein Einstieg, Nachrichten and Menu. An unverified foodsaver sees Übersicht, Betriebe, Nachrichten and Menu.
  5. Phone drawer order:
     - the profile block
     - Meine Aktivität and Quiz
     - the bells link
     - the main items in side-menu order (daily, Community, Verwalten, Orga), without headings
     - the remaining user items as today
- Unchanged: Verwalten and Orga sections (decision 133), "Was ist neu" in the bottom block, `/me/activity` and all history. Only entry points move.

## Scope
In:
- `nav.config.ts`: sections, order, tabs and the new items
- `SessionInfo.mailboxes`, with its enricher
- the forum route guard
- the drawer order
- unit, e2e and persona tests, and the screenshot specs

Out:
- the Ctrl/⌘ K removal (item 7, branch `worktree-agent-a973aca1f1b0e84b6`)
- a forum overview across all regions
- moving the mailbox "create" button
- any database, Liquibase or seed change

**Sequencing:** start only after item 7 is merged into main, then branch from main. The overlapping files are `layout/user-menu.component.ts` (shortcuts block next to the menu items), `layout/sidenav.component.ts`, `layout/i18n/*.json`, `layout/sidenav.component.spec.ts` and `e2e/tests/shell/d4-sidenav.spec.ts`.

## Changes
**Contract**
- `server/src/main/resources/openapi/schemas/auth.yaml`, `SessionInfo`:
  - add optional `mailboxes: boolean`
  - description: "reads a mailbox or may create one (menu item Postfächer)"
  - regenerate the server and client models
- No path changes.

**Server**
- `security/SessionInfoBuilder.java`: add a `mailboxes(boolean)` setter and getter, following the `onboarding` pattern.
- `auth/mapper/AuthMapper.java`: map the flag when it is set.
- New `mailbox/service/MailboxIntegrations.java`: a `@Configuration` with an `@Order(40)` `SessionInfoEnricher` bean, like `onboarding/service/OnboardingIntegrations.java`. It calls a new read-only method `MailboxAccessService.showsMailboxes(SessionPrincipal)`, which returns true when:
  - `permissions.isOrga(actor)`, or
  - `mayHaveMailbox(role)` and the user admins any unit (`regions.adminRegionIds(userId)` is not empty), or
  - `!visibleMailboxes(actor).isEmpty()`.

  Keep it cheap: check in that order, so the costly query runs last. It must never fail the session (see the `SessionInfoEnricher` javadoc). It is read-only, so it needs no history.

**Client: `client/src/app/layout/nav.config.ts`**
- `mailboxes`:
  - `section: 'community'`
  - `visible: ctx.isAuthenticated && ctx.session?.mailboxes === true`
- New `forum` item:
  - `section: 'community'`, `labelKey: 'nav.forum'`, `icon: 'pi pi-comments'`, `route: '/regions/home/forum'`
  - `visible`: logged in and `homeRegion(ctx.session)` is set (reuse `core/auth/permission.util.ts`)
  - current on forum pages, through the new `activeMatch` support below
- `activity` and `quizzes`:
  - `area: 'user'`, with no `section`
  - drop `mobileTab` and `secondary`
- `bells` and `account`: drop the leftover `mobileTab`.
- `stores`: becomes a phone tab.
- `NAV_SECTIONS` order:
  - daily: `home, dashboard, onboarding, pickups, stores, messages, bells, baskets, map, statistics, blog` (map, statistics and blog stay for visitors)
  - community: `map, events, regions, polls, forum, mailboxes, introPickups, guide`
- User menu order follows the declared order in `NAV_ITEMS`. Declare in this order: `activity`, `quizzes`, `account`, `notifications`, `passport`, `help` (`profile` remains the head of the menu).
- `activeMatch`: allow `string | readonly string[]`, with a `*` wildcard for one path segment (`/regions/*/forum`, `/forum`). Extend `matchesPrefix`/`activeNavId` so the longest pattern wins over `regions`' `/regions`.
- `mobileTabs(ctx)`: replace "first four flagged" with an explicit list:
  - `[onboardingOpen ? 'onboarding' : 'dashboard', 'pickups', 'stores', 'messages']`, filtered by `visible`
  - no filler
  - keep the `mobileTab` field only if still needed; otherwise remove it
- New export `drawerItems(ctx)`: the main items flattened in side-menu order (reuse `sidenavSections`). Logged-in only, so footer pages are excluded.

**Client: other files**
- `client/src/app/features/forum/forum.routes.ts`: add `homeRegionGuard` (`core/region/home-region.guard.ts`) to the `regions/:regionId/forum…` and `moderation-log` routes.
- `client/src/app/layout/nav-context.service.ts`: add `drawer = computed(() => drawerItems(...))`.
- `client/src/app/layout/mobile-tab-bar.component.ts`, drawer:
  - after `mm__user`, render `nav.user()` items `activity` and `quizzes` (test ids `nav.drawer.activity` / `nav.drawer.quizzes`)
  - then the bells link
  - then `nav.drawer()`
  - then the other user items (without `activity` and `quizzes`)
- `user-menu.component.ts`: no template change expected (items come from `nav.user()`). Verify the desktop card and the phone popover branch.
- i18n `nav.forum` in `de`, `en`, `gws`, `fr` and `it`, in the global nav catalog where the `nav.*` keys live. `npm run i18n:check` must pass.

**Docs**
- the new decision row
- `docs/design/sidenav-proposal.md` §1.3: a short note that decision 135 applies
- `docs/resume.md` item 9: mark it done
- `docs/design-review-m7.md`: a note on the lowered persona budgets

## UI
Yes, a visible change: menu order, two items move to the user menu, a new Forum item, phone tabs and drawer order. It reuses existing components, so no designer mockup is advised.

## Tests
New:
- **Server:** a unit test of `showsMailboxes`, one case each for ORGA, unit admin with STORE_MANAGER, unit admin below STORE_MANAGER, member reader, personal mailbox and none. Also an integration test that `GET /api/auth/session` carries `mailboxes` for a seed ambassador with a mailbox, and false for a plain foodsaver.
- **Client unit (`shell.component.spec.ts`, `sidenav.component.spec.ts`):**
  - tabs per persona: newcomer `[onboarding, messages]`, verified foodsaver `[dashboard, pickups, stores, messages]`, unverified foodsaver `[dashboard, stores, messages]`
  - section shapes per role (no `activity`/`quizzes` in Community; `forum` and `mailboxes` in place)
  - mailboxes is hidden without the flag
  - forum is hidden without a home region
  - `activeNavId`: `/regions/12/forum` → `forum`, `/forum/threads/3` → `forum`, `/regions/12/members` → `regions`
  - user items order; drawer order
- **e2e `@D4.3` (`d4-shell-navigation.spec.ts`):** desktop opens Meine Aktivität from the user menu; phone shows the Betriebe tab and Meine Aktivität directly under the profile in the drawer.
- **e2e `d4-sidenav.spec.ts`:** Forum lands on the home region forum and is the current item; Postfächer is visible for the mailbox seed user and absent for a foodsaver.

Existing tests that change on purpose:
- `d4-shell-navigation.spec.ts`: `nav.activity` and `nav.tab.activity` move to `nav.user.activity` and `nav.drawer.activity`
- `shell.component.spec.ts`: the `mobileTabs` and header split expectations around line 200 to 260
- `sidenav.component.spec.ts`: the `community` shapes and `mailboxes` in daily
- `a11y/pages.spec.ts:141`: the drawer activity still works, but check its position
- `mailboxes/mb2-folders.spec.ts`: the badge is still in the drawer or side menu, now under Community
- `e2e/fixtures/nav.ts` `openNav`:
  - for `area: 'user'` ids, open `shell.userMenu` and click `nav.user.<id>` on desktop (2 clicks)
  - on phones, use `nav.drawer.<id>` (unchanged path)
- Personas (`e2e/tests/personas/*.spec.ts`): the foodsaver and store-manager walks lose a phone click (Betriebe tab). Lower their budgets to the new measured maximum and add a note in the review; never raise them.
- Screenshots (`e2e/screenshots/layout.screen.ts`):
  - add the user menu open (foodsaver), the phone drawer open (foodsaver) and a newcomer's phone tab bar
  - existing side menu captures change
  - baselines are regenerated deliberately in the batch run (decision 69), not in this task

## Done when
- [ ] The side menu, user menu, tabs and drawer match the Decisions section for visitor, newcomer, foodsaver, store manager, ambassador and ORGA
- [ ] `SessionInfo.mailboxes` is in the contract, filled by the enricher, and unit- and integration-tested
- [ ] Forum resolves the home region, is the current item on forum pages, and is hidden without a region
- [ ] Mein Einstieg is visible only while `onboarding.complete === false` (tested for the menu and the tab)
- [ ] Client unit tests, lint and `npm run i18n:check` are green; server tests including ArchUnit are green
- [ ] D4/D4.3, sidenav, a11y, mailbox and persona e2e specs are green on chromium and mobile-chrome
- [ ] The screenshot specs are updated; no baselines are committed by accident
- [ ] The decision row, the resume item and the review note are written

## Risks
- **Stale flag:** the session flag is stale after creating the first mailbox or being added as a member, until the session reloads. Refresh the session after a successful create in `mailbox-create-page.component.ts` (`SessionService` reload), or accept it and note it.
- **Login cost:** the enricher adds up to three queries per session load. Keep the short-circuit order and never throw.
- **Wildcard matching:** a wildcard `activeMatch` must not make `regions` lose `/regions/12/forum-like` paths. Use whole segments only, and unit-test it.
- **Merge conflicts with item 7:** if item 7 isn't merged first, the user-menu shortcuts block and `d4-sidenav.spec.ts` will conflict.
- **Fewer than four tabs:** newcomers get only two tabs plus Menu. Check that the tab bar layout spreads fewer tabs evenly at 360px.
