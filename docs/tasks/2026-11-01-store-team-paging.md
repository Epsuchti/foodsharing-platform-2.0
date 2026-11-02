# Store team: sort, filter, server-side paging (resume item 14)

## Goal
The store team tab and the "Abholungen pro Mitglied" table in the store statistics load one page of people at a time from the server. Both can be searched by name, filtered by status and role, and sorted by name, pickup count or last pickup.

## Decisions
- Owner, 2026-11-01:
  - Team tab: members and jumpers become one paged list with chips Alle / Team / Springer, plus a "Verantwortliche" (managers) chip, a name search and a sort dropdown. Page size 20. Jumper rows get a status badge.
  - Mockup round 1 feedback (owner, 2026-11-01): "Verantwortliche" is one value of the single-select status chip group (Alle / Verantwortliche / Team / Springer), not a separate toggle. The status filter therefore gets a `MANAGER` value and there is no `managersOnly` parameter. "Team" keeps meaning status `MEMBER` (managers included; Alle = Team + Springer matches `StoreTeamCounts`). Each person is a card: avatar, name, badges, the numbers as small stat tiles, actions menu (`docs/design/store-team-paging-proposal.md` §3).
  - Statistics: the same search, sort and paging, plus the status "Ehemalige" (former: picked up in the range, no longer in the team). "Last pickup" means the last pickup within the selected range. Managers only (`mayEditStore`), as today.
  - **A designer mockup comes first.** The implementer starts the UI only after the owner approves it.
- Planner (record as a new decision in `docs/decisions.md`):
  - Sorting, filtering and paging happen in the service, over one query per source table. Pickup numbers live in the `pickup` module behind `StorePickupInfo`, so a SQL join would cross module boundaries. Teams are bounded (hundreds at most); the client only ever receives one page.
  - Stable order (decision 138): the chosen key, then display name (case-insensitive), then user id.
  - Sort `NAME` keeps today's default order: managers first, then name. `PICKUP_COUNT` and `LAST_PICKUP` sort descending; people with no pickup go last.
  - `members`/`jumpers` are removed from `StoreTeam`, and `perMember` is removed from `StoreStatistics` (deliberate contract change). Their only client is this app.

## Scope
- In:
  - Two new paged endpoints and the changed `StoreTeam`/`StoreStatistics` schemas.
  - Both UIs, i18n, tests, and seed data if paging needs it.
- Out:
  - Applications and invitations stay unpaged in `getStoreTeam`.
  - The month chart is unchanged.
  - No changes to the region members tab.

## Changes
**Contract** (`server/src/main/resources/openapi/`)
- `paths/store-team.yaml`: `GET /api/stores/{storeId}/team/members`, operationId `listStoreTeamMembers`, `x-permission: mayAccessStore`. Query parameters:
  - `status` `TeamListStatus` (ALL|MANAGER|MEMBER|JUMPER, default ALL; MANAGER = members with `isManager`)
  - `search` (maxLength 100)
  - `sort` `TeamSort` (NAME|PICKUP_COUNT|LAST_PICKUP, default NAME)
  - `page` (default 0), `size` (1..100, default 20)
  - Response `TeamMemberPage { items: TeamMember[], page: PageMeta }`.
- `paths/stores.yaml`: `GET /api/stores/{storeId}/statistics/members`, operationId `listStoreStatisticsMembers`, mayEditStore (else 403 `ACCESS_DENIED`). Query parameters:
  - `from`, `to`: same meaning and validation as `getStoreStatistics`
  - `status` `StatisticsMemberStatus` (ALL|MANAGER|MEMBER|JUMPER|FORMER), `search`
  - `sort` (PICKUP_COUNT default | LAST_PICKUP | NAME)
  - `page`, `size`
  - Response `StoreMemberPickupsPage`.
- `schemas/stores.yaml`:
  - Add the new enums and page schemas.
  - `StoreMemberPickups` gains `status` (MEMBER|JUMPER|FORMER), `isManager` and `lastPickupAt` (within the range).
  - Remove `StoreTeam.members/jumpers` and `StoreStatistics.perMember`.
- Regenerate the server and client code.

**Server**
- `store/service/StoreTeamService.java`: new `teamMembers(storeId, TeamQuery)` returning `Page<TeamMemberView>`.
  - Reuse the row building and `distance()` from `team()`; extract a private helper so both share it.
  - Search: case-insensitive and accent-insensitive on `displayName`. Reuse a normaliser if one exists (search module A55; otherwise a small `java.text.Normalizer` helper in `store`).
  - Build the page with `PageImpl`.
  - `team()` no longer returns members/jumpers.
- `store/service/StoreStatisticsService.java`:
  - New `memberPickups(storeId, from, to, MemberPickupsQuery)` returning `Page<MemberPickups>`. It reuses the range logic from `statistics()` (extract it into one private method) and `perMember()`.
  - Status comes from `StoreMembershipRepository.findByStoreId` (MEMBER/JUMPER, otherwise FORMER).
  - `statistics()` no longer computes `perMember`.
  - Read-only: no history.
- `store/web/StoreTeamController.java` and `store/web/StoresController.java` implement the new operations. Map pages with `PageMapper.toPageMeta`, as `listRegionStores` does.
- `store/mapper/StoreMapper.java`: page mappers.

**Client**
- `client/src/app/features/stores/tabs/store-team.tab.ts`:
  - The members/jumpers sections become one `loadResource` of `listStoreTeamMembers`, with `{storeId, status, search, sort, page}` as params.
  - Search is debounced, as in `regions/tabs/region-members.tab.ts`.
  - One single-select chip group Alle / Verantwortliche / Team / Springer (counts for Alle, Team, Springer from `ctx` `StoreTeamCounts`; none for Verantwortliche), a sort select, `p-paginator`, and person cards with stat tiles and a jumper badge (mockup).
  - Applications and invitations keep using `getStoreTeam`. After a mutation, `refresh()` reloads both.
  - Empty state when the filter matches nobody: "Niemand passt zum Filter".
- `client/src/app/features/stores/tabs/store-statistics.tab.ts`: the per-member card becomes its own `loadResource` (params include the range), with the same controls plus "Ehemalige" and a "Letzte Abholung" column. It loads only with `mayEditStore`.
- New test ids:
  - `stores.team.search|filter.<all|manager|member|jumper>|sort|count|paginator|jumperBadge|emptyFilter|resetFilter`
  - `stores.statistics.members.search|filter.<all|manager|member|jumper|former>|sort|count|paginator|emptyFilter|resetFilter`
- i18n in `client/src/app/features/stores/i18n/{de,en,gws,fr,it}.json`.

**Seed**
- If no seeded store has more than 20 people (members + jumpers), extend one seeded store's team with fixed UUIDs (`server/src/main/resources/db/seed/`, context `seed`) and update `docs/seed-ids.md`.

## UI
- A visible change on two tabs.
- A **designer mockup is required first** (owner). It should cover:
  - the filter bar on phone and desktop;
  - the jumper badge;
  - the "Ehemalige" chip;
  - the empty-filter state.

## Tests
- New:
  - `StoreTeamServiceTest`: each sort including ties and the id tiebreaker, people without pickups last, the status (incl. MANAGER) and search filters (accents, case), paging bounds, and the distance/phone visibility still applied.
  - `StoreStatisticsServiceTest`: FORMER status, last pickup within the range, sorts, filters, paging, 403 without mayEditStore.
  - The store API IT (find it by searching `getStoreTeam` in `server/src/test`): both endpoints, 400 on `size` > 100.
  - `client/src/app/features/stores/store-team.spec.ts` and `store-wall-stats.spec.ts`: params sent, chips, paginator, empty-filter state.
  - e2e:
    - `e2e/tests/stores/s9-team-list.spec.ts` (`@S9`): filter, sort, page 2.
    - `e2e/tests/stores/s14-statistics.spec.ts` (`@S14`): per-member search, sort, Former.
  - Screenshot specs: `e2e/screenshots/stores-team.screen.ts` (filtered, empty filter, paged), plus statistics members states. Baselines are generated in batch later (decision 69).
- Existing tests that change on purpose:
  - `s5-team`, `s7-invitations`, `s8-standby-managers`, `s6-apply`, `team-error-states` and `a9-sleep`: jumpers are now in the same list (default ALL). Locate them by `data-status` or by the filter.
  - Unit tests that read `StoreTeam.members`/`perMember`.

## Done when
- [ ] Contract changed and regenerated; ArchUnit and ModulesTest green
- [ ] Both endpoints page, filter and sort with a stable order; unit tests and IT green
- [ ] Mockup approved, UIs built to it, all `DataState` states covered
- [ ] `npm run i18n:check` passes; lint and unit tests green
- [ ] `@S9` and `@S14` e2e green; screenshot specs added (no baselines)
- [ ] Decision recorded; `docs/ledger.md` updated

## Risks
- The default order changes: jumpers are now mixed into "Alle". Tests that count rows per section break and must be updated on purpose.
- Sorting in memory loads the whole team on every page request. This is fine at store sizes; note it in the decision in case stores ever get very large.
- The chip counts come from `StoreTeamCounts` in the page context and must be reloaded after mutations, as `refresh()` already does.
