# Store visibility: unverified people must not learn which stores work with foodsharing (S17)

## Goal
Nobody unverified, anonymous or signed in, can get a store's name, address, position or chain from any endpoint, mail or bell. Exceptions: the one store of an intro pickup they booked, and stores flagged as publicly named. They see exact store counts per city as bubbles, with no explanation that anything is withheld. Verified members keep the full map. The public-site redesign is a separate plan.

## Decisions
Owner decisions 1–7 (2026-11-02) and the owner's later answers are binding; the answers replace the minimum count in decision 1. Record them as decision 156, ledger row S17. It amends 73 (the limited store view now needs `User.verified`) and A19 (`checkEligibility` for an unverified member).
- D1 **The check is `User.verified`, never role.** `QuizSessionService:392` raises the role on quiz confirmation and `VerificationService.unverify` leaves it, so FOODSAVER and unverified can occur together. ORGA is exempt. `SessionPrincipal` has no `verified`, and `verify()` does not revoke sessions, so read it live as `StorePermissionService.rights` does today.
- D2 **Denial shape.** Lists and markers: 403. A single store: 404 `STORE_NOT_FOUND`, so a known and an unknown id look the same. Empty results where the schema requires a field (`kamChains`, search hits, metadata chains).
- D3 **Areas (owner).** One bubble per CITY-level region (CITY or BIG_CITY) with at least one store, with the exact count: no minimum, no rounding, no merging by count, no hiding. PART_OF_TOWN and DISTRICT roll up into their city. A store with no city-level region at or above its own region, or whose city has no coordinates, counts at the nearest coarser region with coordinates, up to COUNTRY (planner's choice, owner recommended it); with none, it appears in no bubble. Never WORKING_GROUP or CONTINENT. The position is the region's own coordinates, never derived from store coordinates. Only live `COOPERATION_ESTABLISHED` stores count.
- D4 **Counts are always fine (owner).** `PublicRegion.storeCount`, `PublicStatistics`, `ExplainerCounters.stores`, the landing counter and `listStoreCategories.storeCount` stay unchanged.
- D5 **Public and bookable pickups.** Fields: `id`, `startAt`, `online`, `city`, `freeSlots` (`regionSlug` on public, plus `guide`, `slotId`, `bookable`, `notBookableReason`, `minimumPickupCount` on bookable). Removed from the payload: `category`, `businessName`, `publicGeneralLocation`, `publicLocation`, `publicInfo`, `visitFairteiler`. The query filters `category`, `visitFairteiler` and bookable `search` go too: each one lets a caller test for a hidden value. `city` = linked store's `city`, else the region's name, null when online; no small-town exception (owner). Free text is never parsed. `BookedIntroPickup` is unchanged: that is decision 3.
- D6 **Flag `store.publicly_named`**, boolean, default false. On and off (owner): ORGA and this store's managers (`Rights.isManager`, team status COORDINATOR), with the warning. Region store coordinators only if they also manage this store. It goes on only with `publicity = ALLOWED`; moving `publicity` off ALLOWED clears the flag, whoever does it (`mayEditStore` includes coordinators). The existing `publicity` column is not reused, since existing ALLOWED rows would go public at once, against "defaults to false". CSV import leaves it false.
- D7 **Unverified people can never join a store (owner).** `apply`, `invite` and `addMemberDirectly` require verified, regardless of the store's `requiresVerified`. `unverify()` ends all store memberships through the existing team path, which also leaves the team chats. The `rights()` gate covers any row that already exists. `exportMyData` keeps a person's own past data (right of access), and `listPastPickups` keeps self access.
- D8 **Partners.** `/about/partners` lists flagged stores (name and city) from the server, below the CMS text. Named stores come only from the flag. The existing text stays as it is, including "Grossverteiler-Filialen" and the charities (owner): no rewrite and no production-text check. Region descriptions, blog and press stay free text: only an editor hint is possible.
- D9 **Explainer durations (redesign decision touching the contract).** `JourneyStepPreview.estimatedDuration` becomes optional and is sent only for online steps, so `P21D` (INTRO_PICKUPS) and `P7D` (ID_CHECK) leave the public response. PASSPORT keeps `PT2M`, read as online. The signed-in `JourneyStep` is unchanged. The public explainer's step list changes in the redesign plan, not here.

## Scope
In: server enforcement, contract, Liquibase, seed, map bubbles, intro pickup cards, store form control, partners list, copy, nav and route gating, tests.
Out: public-site redesign, shell, stepper, `/how-it-works`; scanning or filtering free text; removing the `requiresVerified` column (the form control goes, see Changes).

## Changes
**Contract** (`server/src/main/resources/openapi/`)
- `schemas/intro-pickups.yaml`: reshape `PublicIntroPickup` and `BookableIntroPickup` (D5). `paths/public-intro-pickups.yaml`, `paths/intro-pickups.yaml`: drop the filters.
- `schemas/onboarding.yaml`: remove `estimatedDuration` from the required list of `JourneyStepPreview` (D9).
- `paths/public-map.yaml`, `schemas/map.yaml`: new `listPublicStoreAreas` (`GET /api/public/map/store-areas`, `security: []`), returning `StoreArea {name, latitude, longitude, count}`.
- `paths/public-content.yaml`, `schemas/content.yaml`: new `listPublicPartnerStores` (`GET /api/public/partner-stores`), returning `PublicPartnerStore {name, city}`.
- `schemas/stores.yaml`: `publiclyNamed` on `StoreDetail` and `StorePatch`; `mayChangePublicNaming` on `StorePermissions`. `schemas/common.yaml`: `PUBLICITY_NOT_ALLOWED`.
- `x-permission` becomes verified on `listStoreMarkers`, `getMapBubble` (store kind), `listRegionStores`, `getStore`, `getStorePermissions`, `listStoreChains`, `getStoreChain`, `applyToStore`, `checkEligibility`. Add 403/404 to each. `updateStore` `x-history` also names the new action. Regenerate.

**Server** (`server/src/main/java/network/foodsharing/`)
- New `store/service/StoreVisibility.java`: `mayKnowStores(actor)` (ORGA or verified), built on `users.findActive`, moving the lookup out of `StorePermissionService.rights`. It is the single rule, unit tested.
- `store/service/StorePermissionService.java`: `rights()` returns no rights when `!mayKnowStores`, including `mayApply`. `mayViewPublic` also needs it, which closes every path behind rights (detail, team, wall, log, statistics, pickups).
- `store/service/StoreQueryService.java`: gate `regionStores` (403) and `detail` (404); `metadata` returns `chains = []` when unverified, because `basket-form-page` needs the groceries; `myStores` returns empty.
- `store/service/StoreChainService.java`: `listStoreChains`, `getStoreChain` use `mayKnowStores`.
- `onboarding/service/ExplainerService.java`: `PREVIEW` without duration for INTRO_PICKUPS and ID_CHECK (D9).
- `store/service/StoreTeamService.java`: verified check in `checkRequirements`, `invite` (line 366) and `addMemberDirectly` (line 453). The invite bell names the store. `verification/service/VerificationService.unverify`: end memberships (D7).
- `map/service/MarkerService.java`: `stores()` 403 when unverified; new `storeAreas()`. New pure `map/service/StoreAreaRules.java` (D3). `store/repository/StoreRepository.java`: count established by region. `map/service/BubbleService.java`: store kind denied for the unverified. Public controller for `store-areas`.
- `search/service/SearchService.java`: for the unverified, `SearchKind.STORE` gives no hits and `index()` no stores. The index row visibility `REGION_MEMBERS` is too weak alone.
- `profile/service/ProfileService.java:95`: `kamChains` empty for an unverified viewer; `ProfilePermissionService` sets `maySeeStores` false for them.
- `survey/service/SurveyService.java`: `form()` returns no store for the unverified (it returns a `StoreRef` for any `storeId` today); `submit()` with a STORE question gives 404 for them.
- `region/service/UpcomingIntroPickups.java`, `intropickup/service/PublicIntroPickupService.java`, `region/mapper/RegionMapper.java`, `intropickup/mapper/IntroPickupMapper.java`: new view (D5), cities loaded in one batch, no N+1. `RegionStoreStats` stays.
- `intropickup/service/IntroPickupBookingService.java`: drop `matches` and the filters. `intropickup/service/IntroPickupNotifications.java`: `notifySubscribers` uses new `publicParams` without `businessName`, for mail and bell, because subscribers are by definition unverified. Other templates go to booked users or guides and stay.
- `templates/mail/intropickup/new-bookable_{de,en,fr,gws,it}.txt`: replace `businessName` with city or online.
- `store/service/StoreService.java`: flag rules (D6) as a pure `PublicNamingRules`; history `StoreLogAction.STORE_PUBLIC_NAMING_CHANGED` on every change (with `publicity` clearing). New read-only partner query: live, flagged, established, sorted by name.

**Database**
- `db/changelog/changes/185-store-publicly-named.yaml`: `addColumn` BOOLEAN NOT NULL `defaultValueBoolean: false` (PostgreSQL and H2).
- `db/seed/changes/185-store-publicly-named-seed.yaml`, context `seed`: S-01 and S-02 true (both have `publicity = ALLOWED`). No new ids, note in `docs/seed-ids.md`. No new seed user: tests get an unverified FOODSAVER through `UserService.changeRole` (IT) or the dev role API (e2e).

**Client** (`client/src/app/`, generated API only)
- `features/map/map.util.ts`, `pages/map-page.component.ts`: new layer `storeAreas` for everyone not verified, in the defaults; `stores` only when `session.user.verified`; events stay for signed-in. Bubble with the count; popup "n Betriebe · Zürich" without link. Delete the `loginHint` paragraph (lines 167–172, including its link). A stored `fs.map.layers` with `stores` stays harmless.
- `layout/nav.config.ts:284`: `stores` needs verified (reuse the helper at line 188). `features/stores/stores.routes.ts`, store-chain routes: replace `roles: [Foodsaver]` with a verified check that shows not-found. `features/regions/pages/region-page.component.ts:32`: hide the stores tab. `shared/journey-stepper/journey-stepper.component.ts:95`: show the preview duration only when present (D9).
- `features/intro-pickups/components/einab-card.component.ts`: optional `online`, `city`; `businessName` stays only for `my-bookings`. `public-slots-page`, `bookable-list-page`: remove filters and `data-category`.
- `features/stores/pages/store-form-page.component.ts`: toggle `stores.form.publicName` beside `publicity` with a warning, disabled unless `mayChangePublicNaming`; remove the `requiresVerified` control (no effect now).
- `features/content/pages/content-page.component.ts`: for slug `partners`, a list via `loadResource` and `<fs-data-state>`; `content-editor-page.component.ts`: hint "name stores only through the store setting".
- `features/search/`: hide the stores filter for the unverified; placeholder and subtitle without "Betriebe" through new keys, `search.global.placeholder` kept for verified.
- `features/map/i18n/*`: `map.page.subtitle` → "Fairteiler, Bezirke und Essenskörbe in deiner Nähe." everywhere; delete `map.page.loginHint`, `map.page.login`; add `map.layers.storeAreas`, popup text. Edit the source files, not git-ignored `client/public/i18n/`. Five languages, `npm run i18n:check`.

## UI
Visible changes: map bubbles, reduced pickup cards, store form toggle, partners list. A mockup is **not advised**: the running redesign already covers the card and map language, and this task only removes things, adds a bubble and a toggle. No text anywhere explains the missing stores (decision 5).

## Tests
**New**
- `StoreAreaRulesTest`: count of 1 shown exactly, PART_OF_TOWN and DISTRICT roll-up, BIG_CITY, no city ancestor, missing coordinates up to COUNTRY, none at all, WORKING_GROUP, non-established and deleted stores not counted, each store in one bubble.
- `StoreVisibilityTest`, `PublicNamingRulesTest`.
- `StoreIdentityLeakIT`: walks **every GET in the contract** as anonymous, as PENDING and as an unverified FOODSAVER, fills path ids from a map (an unmapped path fails, so new endpoints must opt in), and asserts no response holds a seeded store name, street or chain, except flagged names on the partners endpoint and the booked store on `listMyBookings`. Seed bubbles: Zürich Stadt 16, Winterthur 2. Explainer IT: no `P21D` or `P7D` in the response.
- `OpenApiContractTest`: schemas reachable from `security: []` operations hold no `businessName`, `storeName`, `storeId`, `StoreRef`.
- `IntroPickupNotifications` test: mail variables and bell params lack `businessName`. IT: flag rules, history, store manager may switch on and off, 403 for a region coordinator who does not manage the store, migration default.
- Client specs: layers per viewer, no store request when unverified, nav, card, toggle, stepper preview without a duration.
- E2E `e2e/tests/stores/s17-store-visibility.spec.ts` `@S17`: anonymous map bubbles, unverified map bubbles and no `/markers/stores` request, `/stores/:id` not found, booking reveals the one store, flag on then listed on partners, verified full map. Screenshot specs in `e2e/screenshots/map.screen.ts`, `intro-pickups.screen.ts`, `regions.screen.ts`, stores and content. Baselines generated in batch at the end (decision 69).

**Change on purpose**
- Server: `MapIT`, `StoreApiIT`, `StoreChainApiIT`, `StoreTeamServiceTest`, `RegionApiIT`, `IntroPickupApiIT`, `PickupUserApiIT`, `VerificationApiIT`, `QueryCountIT`, `LoadPathsPostgresIT`.
- Client: `map-page.spec.ts`, `map.util.spec.ts`, `regions-pages.spec.ts` (pickup fields only), `sidenav.component.spec.ts`, `stores-pages.spec.ts`, intro-pickup specs.
- E2E: `m1-map` (loginHint), `r4-public-region`, `ea1-landing`, `ea2-public-slots`, `ea6-categories`, `ea9-notifications`, `s6-apply`, `pu4-eligibility` (unverified), `s3-chains`, personas, `a11y/routes.ts`, `i18n-smoke`.
- Baselines that change: map, public region, intro public and bookable, store form, partners, unverified nav.

## Done when
- [ ] `StoreIdentityLeakIT` and the contract test pass for anonymous, PENDING and unverified FOODSAVER.
- [ ] Public pickup payloads carry only the D5 fields; the filters are gone.
- [ ] `new-bookable` mail and bell carry no business name.
- [ ] Booking reveals exactly that store; nothing else changes for the booker.
- [ ] Map: exact city-level bubbles for the not verified, no store position or name; verified see no bubbles and the full map.
- [ ] The public explainer no longer sends `P21D` or `P7D`.
- [ ] Flag: default false on both databases, warning shown, history recorded, partners list follows it.
- [ ] Copy replaced in five languages, `i18n:check` passes, no text mentions withheld stores.
- [ ] Decision 156 and ledger S17 recorded by the main session; 73 and A19 marked as amended.

## Risks
- **A count of 1 in a small town can hint at a store.** The owner accepted this. The same goes for a public pickup's city.
- **Free text** (region descriptions, blog, press, forum, events, FSP names, guide-written info) can still name stores. Only the editor hint covers it. FSP names may reveal a host business.
- **Guide name** stays on bookable cards: not store identity, but a guide known locally can hint at one.
- **Extra user lookup** per gated call: `QueryCountIT` budgets may move, and a budget change needs a reason.
- **Unverifying now removes people from teams**, so re-verified people must apply again. Existing rows are gated but stay in team chats until removed: not fixed by a migration.
- **Module boundaries:** if ArchUnit forbids `search` or `survey` using `StoreVisibility`, place the rule in `security` instead.
- **Not read line by line:** websocket payloads, admin and guide intro pickup endpoints, automation and region-admin endpoints (all permission-gated), `getPublicTeam`, FAQ, release notes. `StoreIdentityLeakIT` covers the GET ones against seed data, not websockets.
