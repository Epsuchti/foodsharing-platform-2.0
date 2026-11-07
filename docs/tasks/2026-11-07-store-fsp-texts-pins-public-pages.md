# Store/Fairteiler texts, address pins, inline cleaning shifts, fewer public pages (one PR)

## Goal
Seven owner requests from 2026-11-07 in one PR: Markdown and bigger editors for store description, store public info
and Fairteiler description; the map pin follows the typed address; the next cleaning shifts on the Fairteiler page;
store description only for the team, public info in its own card for everyone; the pages Vision, About us, Team,
Cities, Demands, Transparency, Communities, International, Security and For companies deleted; "What's new" only for signed-in users.

## Decisions (owner answers 2026-11-07; record as decisions 171 and 172 in `docs/decisions.md`)
- D1 Markdown via `<fs-markdown>` for store `description`, store `publicInfo`, Fairteiler `description`; Markdown hint under each field; textareas `rows="10"` + `autoResize`.
- D2 `GET /api/map/geocode-address` (server-side Photon, full address). The static client falls back to the postcode. Both forms get a "Find address on map" button and an automatic lookup once street, postcode and city are filled in and no pin was set by hand; the pin stays draggable. Saving a Fairteiler without a pin geocodes the full address (postcode fallback). Store save is unchanged (no server geocoding).
- D3 Fairteiler detail: the next 5 upcoming shifts inline in the cleaning card, sign-up/sign-out for oneself; the sub-page `/food-share-points/:fspId/cleaning` stays (manager tools, full list, team) behind a smaller "All shifts" link.
- D4 Store `description` only with `mayReadWall` (MEMBER, COORDINATOR, ORGA, region store coordinators); WAITING_LIST does not see it. Enforced in `StoreMapper`. `publicInfo` gets its own "Public information" card for everyone who can open the store.
- D5 (decision 172) Vision (`/about/vision`), About us (`/about` overview), Team (`/team` C16 page + `/about/team` CMS page), Cities (`/about/cities`), Demands (`/about/demands`) and, owner 2026-11-07, Transparency (`/about/transparency`), Communities (`/about/communities`), International (`/about/international`), Security (`/about/security`) and For companies (`/for-companies`) are **deleted**: routes, components, nav/footer entries, about cards, i18n keys, flags, endpoint `getPublicTeam`, seed and DB content. Removed paths redirect to `/`. Partners, Press and Education stay (`/about/*`, linked from the visitor side menu and footer via `ABOUT_PAGES` in `nav.config.ts`); Imprint, FAQ, Donate, Statistics, Release notes keep their own nav entries.
- D6 (part of 172, supersedes the C4 "for everybody, visitors included" note) "What's new" link (header + sidenav bottom) only for signed-in users; `/release-notes` stays public by URL.

## Scope
In: the items above, tests, seed, i18n (de, en, gws, fr, it), docs. Out: geocoding on store save, moving manager cleaning tools to the detail page.

## Changes
**Contract** (`server/src/main/resources/openapi/`)
- `paths/map.yaml`: `GET /api/map/geocode-address` (`geocodeAddress`, x-permission authenticated) with query `street`, `postcode` (required), `city`, `country` (default CH, 2 letters); 200 `GeoResult` or 204 when unknown; 400, 401, 503 `GEOCODING_UNAVAILABLE`. Reuse `common.yaml#/GeoResult`.
- `paths/public-content.yaml`, `schemas/` + `openapi.yaml`: remove `/api/public/team` and `TeamGroup` (if only used there).
- `schemas/stores.yaml` `StoreDetail.description`: description "Only with mayReadWall".
**Server**
- `region/service/GeocodingClient`: add `Optional<GeoPoint> geocodeAddress(String country, String street, String postcode, String city)`. `PhotonGeocodingClient`: `q=street postcode city`, `layer=house,street`, `countrycode`, same breaker/timeouts/24 h cache, falls back to `geocodePostcode` when empty. `StaticGeocodingClient`: delegates to `geocodePostcode`.
- `map/service/MapSearchService` (or new `AddressGeocodeService`) + `map/web/MapController` implementing the new operation; `@NoHistory` not needed (read-only).
- `fsp/service/FspService.locate`: `geocodeAddress` instead of `geocodePostcode`.
- `store/mapper/StoreMapper.toDetail`: move `setDescription` into the `sensitive` block.
- Delete `content/service/TeamPageService`, its controller method, mapper code and `TeamPageServiceTest`.
- Liquibase `db/changelog/changes/193-drop-removed-about-pages.yaml`: `DELETE FROM content_page WHERE slug IN ('vision','team','foodsharing-cities','demands','transparency','communities','international','security','for-companies')` (i18n rows cascade, `fk_content_page_i18n_owner_id`); works on PostgreSQL and H2. Check other tables referencing `content_page` (images, revisions) first.
- Seed: remove the 9 pages from `db/seed/content/content_page.csv` and their rows in `content_page_i18n.csv` (seed-035-1 is `runOnChange`; check no other seed row references them); fix seed bodies linking to deleted pages (the Partner page body links `/content/for-companies` in all 5 languages).
**Client**
- Stores: `tabs/store-overview.tab.ts`: "Public information" card (`stores.overview.publicInfo*`) for everyone; description card only when `store.description` is present (the server already gates it), both via `fs-markdown`. `pages/store-form-page.component.ts`: both textareas `rows="10"` `autoResize`, Markdown hint.
- Fairteiler: `pages/fsp-page.component.ts` description via `fs-markdown`; new `components/fsp-cleaning-card.component.ts` (`loadResource` on `listCleaningShifts`, first 5 future shifts, `shiftsByDay` from `cleaning.util.ts`, `fs-pickup-slot`, `signUpForCleaning`/`signOutOfCleaning` with `runMutation`, `EligibilityReasonsComponent`, `<fs-data-state>` with all states, "All shifts" link `fsp.cleaning.open`). `pages/fsp-form-page.component.ts`: rows 10 + autoResize + hint; geocode button and auto lookup.
- Geocoding UI: `shared/address-form/address-form.component.ts` gets the button (`<testId>.geocode`), auto lookup (debounced ~600 ms, only when the three fields are filled in and `pinSetByHand` is false; a loaded existing pin counts as set by hand), status/error line (not found, unavailable). Shared helper `shared/address-form/address-geocode.ts` used by the Fairteiler form too (its fields stay its own).
- Public pages: `features/content/content-paths.ts` (drop vision, team, cities, demands, transparency, communities, international, security, forCompanies from `ABOUT_PAGES`; `ABOUT_FEATURE_PAGES` goes with the overview), `content.routes.ts` (drop `about` overview and `team`; redirects to `/` for `about`, `team`, `for-companies` and `about/{vision,team,cities,demands,transparency,communities,international,security}`), delete `pages/about-page.component.ts` and `pages/team-page.component.ts`, `layout/nav.config.ts` (drop `vision`, `about` footer items, `team`/`cities`/`demands` from `ABOUT_PAGES` and their `M6_PAGES_READY` flags, the section `order`), i18n keys `nav.vision|about|team|cities|demands`, `content.about.*` (incl. `content.about.page.forCompanies` etc.) and `content.team.*` that are no longer used (all five languages). Check `search.util.ts`, the landing page, release-note bodies and other components for links to the deleted paths.
- What's new: `layout/header.component.ts` and `layout/sidenav.component.ts`: the `shell.whatsNew` link only when signed in.
- Regenerate the API client; `npm run i18n:check`.
**Docs**: `docs/decisions.md` 171 (D1–D4) and 172 (D5, D6, supersedes the C4 note); `docs/ledger.md` rows C1 (page list), C16 (removed), S1/S2, FS1/FS2; plan.md §11 public pages if it lists them.

## UI
Visible changes: store overview cards, both forms (bigger fields, geocode button/status), Fairteiler cleaning card, visitor nav/footer. Everything reuses existing patterns (`fs-pickup-slot`, cards, `fs-markdown`), so **no designer mockup is needed**. The owner can ask for one for the cleaning card.

## Tests
- Unit (server): `StoreMapperTest`: description present for MEMBER/COORDINATOR/ORGA/coordinator, absent for WAITING_LIST and region-member viewers, publicInfo present for all. `PhotonGeocodingClientTest` (query building, postcode fallback, breaker), `StaticGeocodingClientTest` fallback, `FspService` locate uses the address. `MapControllerTest` for every documented error. Migration runs in `LocalProfileSmokeIT`/ITs.
- Unit (client): store overview cards, address-form auto lookup rules (not after a manual pin, not with missing fields, button always), fsp-cleaning-card states and sign-up/out, `sidenav.component.spec.ts` (visitor about list without the 5 items; whatsNew only signed in), `content-q10.spec.ts`/`content.util.spec.ts` updated on purpose (cities/team/about/for-companies cases removed, redirects added).
- E2E: new `@S2` geocode button + description visibility; `@FS1` geocode + Markdown; `@FS2` inline sign-up/out on the detail page; `@C1` redirects of removed paths. Changed on purpose: `c1-pages.spec.ts` (page list down to partners, press, education, imprint; no overview; the `/content/for-companies` case becomes `/content/partners`), `c1-content.spec.ts`, `content-error-states.spec.ts` and `screenshots/content.screen.ts` (retarget `vision` to `partners`), delete `c16-team.spec.ts` and the team/about screenshots, `c4-release-notes.spec.ts` and `c7-donations.spec.ts` (they open `/about`; use `/release-notes` and `/donate` directly; whatsNew only signed in), `fs2-cleaning-shifts.spec.ts` (smaller "All shifts" link), `s2-store-form.spec.ts`.
- Screenshots for the new states (stores, fsp, layout, content) via `screenshots-update.yml` on the branch; review every PNG.

## Done when
- [ ] Contract regenerated; `geocodeAddress` live with Photon and static clients; FSP save geocodes the full address.
- [ ] Store description only with `mayReadWall` (unit test); "Public information" card for every viewer.
- [ ] Three text fields render Markdown, 10 rows + autoResize, hint in 5 languages.
- [ ] Button + auto lookup in both forms; manual pins are never overwritten.
- [ ] Next 5 shifts with sign-up/out on the Fairteiler page; sub-page reachable via "All shifts".
- [ ] Vision, About us, Team, Cities, Demands, Transparency, Communities, International, Security, For companies gone (routes redirect to `/`, endpoint, components, keys, flags, seed, changelog 193); no dead link left (grep).
- [ ] "What's new" hidden for visitors; decisions 171/172 and ledger updated.
- [ ] Server + client unit tests, ArchUnit, i18n check, affected e2e green; screenshot diffs reviewed.

## Risks
- Nothing else becomes unreachable: Partners, Press, Education (side menu + footer), Imprint, FAQ, Donate, Statistics, Release notes keep nav entries; the explainer, donation and privacy-overview CMS pages were never linked from the overview. Production CMS bodies edited by the PR team may still link deleted pages (`/content/<slug>` then lands on not-found); grep the prod content after deploy.
- Deleting CMS rows in production is irreversible: PR-team edits to these pages are lost. Note it in the PR; take a dump before deploying.
- Photon's public instance is rate limited: debounce, cache, no lookup when nothing changed; a failure shows a hint and never blocks saving.
- Server-side address search returns street precision only where OSM has house numbers; the user can still drag the pin.
- Redirecting removed paths to `/` hides the change from people with bookmarks; acceptable versus a 404.
