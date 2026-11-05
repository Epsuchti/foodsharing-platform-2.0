# UI feedback batch 2, group B: team, texts, people (owner feedback 2026-11-04)

Status: approved by the owner. **Designer first** for the compact team card (step 7) and the mention pill (step 5), in the
same designer run as the pickup cards of the parallel plan. Steps marked ⏸ wait for the mockup; all others start now.
Branch `feat/ui-feedback-batch-2` (shared with the pickups plan, stay out of `features/pickups` and the store pickups tab).

## Goal
Inviting and appointing only offer what the server allows; managers clean the whole wall; no raw codes in wall milestones
or history; mentions read as "@Name"; every name/avatar opens the profile; compact team cards; one text rename.

## Decisions (record as one new decisions row on merge, amending decision 54)
1. Wall delete: ORGA, region admins / store coordinators of the store's region (`Rights.coordinate()`) and the store's
   managers (team status COORDINATOR) delete **any** post at any time. Authors delete their own. The 30-day rule and
   `app.store.wall-delete-after-days` go.
2. Eligibility is decided by the server with the same rule as the mutation: invite candidates come from a new endpoint;
   `TeamMember` gets `appointBlocker`. Appoint menu item: **hidden** when the person can never be appointed
   (role < STORE_MANAGER, not MEMBER/JUMPER, already manager); **shown disabled with the reason** on `TOO_MANY_MANAGERS`
   (it becomes possible after removing a manager).
3. History: no raw codes for anyone. The details "Aktion" row shows the translated label; every visible metadata key has a
   label; ISO dates/instants formatted; enum values translated. Pure id keys (`*Id`, `id`, `traceId` stays) are hidden.
4. Mentions: one shared `fs-user-mention` pill "@Name" linking to `/profile/:id`, in chat and forum.
5. `fs-user-chip` links to `/profile/:userId` by default; opt out with `[link]="null"`.
Out of scope: Priya Sharma seed (owner undecided), intro-pickup refactor (owner), store pickups tab (other plan).

## Changes

### 1. Invite candidates (S7)
- Contract: `openapi/paths/store-team.yaml` new `GET /api/stores/{storeId}/invite-candidates?search=&size=` (operationId
  `listInviteCandidates`, x-permission `mayEditTeam`, `search` min 2 chars, `size` ≤ 20) → `RegionMemberPage`-like
  `InviteCandidateList { items: UserSummary[] }` in `schemas/stores.yaml`. Regenerate.
- Server `StoreTeamService`: extract `Optional<ApiErrorCode> inviteBlocker(UUID storeId, User user)` (ALREADY_IN_TEAM,
  ROLE_TOO_LOW, NOT_VERIFIED, inactive) used by `invite()` (lines 370-379) and by new read-only `inviteCandidates(storeId,
  search, size)`: members of the store's region via `RegionMembersService.members(regionId, search, verified=true, …)`,
  filtered by `inviteBlocker` (fetch pages until `size` hits or 5 pages). Controller method in `StoreTeamController`.
- Client `stores/components/invite-dialog.component.ts`: load from `listInviteCandidates`; empty text "Niemand gefunden,
  der eingeladen werden kann" (`stores.invite.empty` reworded). On ROLE_TOO_LOW / NOT_VERIFIED / ALREADY_IN_TEAM show
  `stores.invite.notEligible` ("{name} kann nicht eingeladen werden: …") via `runMutation` `onError` + `errorToast:false`.

### 2. Appoint manager (S9)
- Contract: `TeamMember.appointBlocker` (nullable `ApiErrorCode`-string enum subset: `ROLE_TOO_LOW`, `NOT_TEAM_MEMBER`,
  `ALREADY_MANAGER`, `TOO_MANY_MANAGERS`; only filled when the viewer `mayEditTeam`) + `mayBeAppointed: boolean`.
- Server: extract `appointBlocker(store, membership, user, coordinator)` from `appointManager` (lines 535-549) and use it
  in both places; `StoreMapper` fills the fields. Unit tests per blocker.
- Client `store-team.tab.ts:351`: show "appoint" only if `mayBeAppointed || appointBlocker === 'TOO_MANY_MANAGERS'`; the
  latter disabled with hint `stores.team.appointBlocked.TOO_MANY_MANAGERS` ("Schon {max} Verantwortliche").
- `errors.ROLE_TOO_LOW` stays; add `stores.team.appointError.ROLE_TOO_LOW` for the race case.

### 3. Wall delete (S11)
- `store/service/StoreWallService.java:127-142`: managers/coordinators → true; remove the age check; delete
  `wallDeleteAfterDays` from `AppProperties` and `application.properties`. Update `StoreWallService` unit tests (old
  30-day cases change on purpose) and the `mayDelete` doc in `store-wall.tab.ts` / `StoreMapper` comments.

### 4. Wall milestones and history texts
- `stores/tabs/store-wall.tab.ts:~92`: new pure `milestoneText(item)` in `stores.util.ts`: STATUS_CHANGED /
  COOPERATION_STARTED + enum → `shared.store.cooperation.<X>`; TEAM_STATUS_CHANGED → `shared.store.team.<X>`;
  `yyyy-mm-dd` → fsDate `dateLong`; COOPERATION_STARTED whose text is `COOPERATION_ESTABLISHED` → no body; else text.
  Unit spec. No server/seed change (old rows covered).
- `shared/history-timeline/history-entry.component.ts:124-125`: "Aktion" row shows `labeler.actionLabel(...)`.
- `history-labels.ts`: `metadata()` drops id-only keys (`/^(id|.*Id|.*Ids)$/`); `metaValue` formats ISO instants
  (`dateTime`, entry/store time zone if present) and dates (`date`), maps enum values via `shared.historyTimeline.value.<key>.<V>`
  or known enums (cooperation/team status, role, language), booleans → Ja/Nein.
- i18n (5 languages): the 37 missing `history.action.*` labels (AdminAction 12, NotificationAction 22, StatisticsAction 2,
  SupportAction 1) in `features/history/i18n`; `shared.historyTimeline.meta.<key>` for every non-id metadata key the
  server writes (literal `.metadata("…")` and `Map.of(...)`/`.metadata(map)` keys, e.g. `pickupAt`, `previousCount`,
  `newCount`).
- Guard: `server/src/test/.../history/HistoryI18nCoverageTest` (or a node script in `npm run i18n:check`) that lists all
  `HistoryAction` codes and metadata keys and fails when `de.json` lacks a label. Implementer picks one; it must run in CI.

### 5. Mention pill ⏸ (mockup)
- New `shared/user-chip/user-mention.component.ts` (`fs-user-mention`, inputs `name`, `userId`): `<a routerLink>` "@Name",
  primary text on primary-tinted background, rounded, inline (no line-height jump).
- `messaging/components/conversation-view.component.ts:188-189` uses it (test id unchanged).
- Forum: `forum.resource.ts:51 renderMentions` emits `[@Name](/profile/<id>)` with a marker class; `fs-markdown` renders
  internal links as routerLinks or plain `<a>`; style `.fs-mention` globally in `styles` so both look the same.

### 6. Profile links everywhere
- `user-chip.component.ts`: `link` default = `['/profile', userId]` when `userId` set and a session exists; `null` = no link.
  Opt out where the row is already a link or a button (user-picker, messages list rows, mentions list, dev switcher,
  user menu, own profile header, invite results row if needed). Replace the two `['/users', id]` links (automation
  audit table, region-sections) only if the target is meant to be the admin page: keep them.
- `user-avatar.component.ts`: optional `userId` + `link` input wraps in `<a>`; use it in store-team card, pickup-slot (×3),
  conversation bubble avatar, team-group-card, dashboard feed.
- History actor (`history-entry.component.ts:154` and the "von …" meta): `fs-user-chip size="xs"` for USER actors.
- No nested `<a>`: check each usage inside an existing link (grep `routerLink` parents) and opt out there.

### 7. Compact team card ⏸ (mockup)
- `stores/tabs/store-team.tab.ts:252-340` + styles 400-440: line 1 avatar sm (linked), name, verified, flag pills inline;
  line 2 muted inline stats (pickups · last · since · km) as one `<dl class="stats">`; phone + kebab right. Keep all
  `data-testid`s. Exact layout from the mockup.

### 8. Text rename
- `introPickups.guide.title`: de "Meine angebotenen Einführungsabholungen", en "My offered intro pickups", gws "Mini
  aabotene Iifüehrigsabholige", fr "Mes prises en main proposées", it "Le mie prese in carico introduttive offerte"
  (implementer checks the existing per-language term for "Einführungsabholung" and reuses it).

## UI
Yes: invite dialog, team menu, team card (mockup), wall milestones, history details, mention pill (mockup), links.

## Tests
- Server unit: `inviteBlocker`, `inviteCandidates` (filters unverified, foodsharers, team members), `appointBlocker` (each
  code), wall `mayDelete` (manager any age, coordinator any age, member not others', ORGA), i18n coverage guard.
- Client unit: `milestoneText`, `HistoryLabeler` (date formatting, id keys hidden, action row label), user-chip default link.
- E2e: `@S7` invite shows Sarah-type candidates only, not Lea/Chiara/Amélie or team members; `@S9` Nina's menu has no
  "appoint" for Sarah, has it for a STORE_MANAGER-role member (add none to seed if one exists; else assert absence only);
  `@S11` Nina deletes Sarah's fresh post; history detail shows "Anzahl Plätze geändert" and no `STORE.` text; chat mention
  is a link to the profile; clicking a team-card name opens the profile. Screenshot specs for changed states.
- Existing tests that change on purpose: wall 30-day unit cases, invite dialog spec, team card screenshots.

## Done when
- [ ] Contract regenerated; ArchUnit, `@NoHistory`/history rules green
- [ ] Items 1-8 implemented; ⏸ steps match the approved mockup
- [ ] `npm run i18n:check` and the history i18n guard pass in all 5 languages
- [ ] Unit + e2e green; screenshot baselines via CI workflow, PNGs reviewed
- [ ] decisions row (amends 54) added on merge

## Risks
- Candidate filtering after paging can return fewer than `size`; acceptable (bounded page loop).
- Default chip links may create nested links in untouched places: grep-based audit required.
- Large i18n batch (~150 keys × 5); gws/fr/it quality needs owner spot check.
