# Landing page: real map in "In deiner Nähe", plus a `/map` deep link (C17 + M1, amends decision 160 map option 1)

Status: plan, owner answered Q1–Q5 on 2026-11-10 (Q1–Q4 as recommended, Q5 changed: deep link in this branch). Ready for the implementer.
Branch: `feat/landing-real-map`, cut from `feat/public-reskin-old-colours` once that branch has its tokens commit. Rebase onto `main` after the reskin merges. It is not merged into the reskin: different risks (Lighthouse, a shared MapView change) and its own review.

## Goal
Replace the static SVG preview (`landing-map-preview.component.ts`) with a real, pannable and zoomable map that uses the self-hosted PMTiles basemap (decisions 159/163, same origin `/tiles/basemap.pmtiles`). It shows the public region and Fairteiler pins, opens a small popup when a pin is clicked, and moves to the region a postcode search found. Nothing about it (no Leaflet chunk, no PMTiles probe, no tile, no marker request) loads before the map scrolls into view. "Karte öffnen" opens `/map` at the landing map's current view (`/map?lat=…&lon=…&z=…`).

## Decisions
Owner answers: Q1 delete the SVG (D10) · Q2 popup (D3) · Q3 zoom 11 on the found region (D4) · Q4 two-finger gestures with Leaflet's own options (D5) · Q5 **changed**: the deep link is part of this branch (D12–D14).
- **D1 Reuse `shared/map-view/map-view.component.ts`** with opt-in inputs, one output and CSS variables. Defaults keep every existing map unchanged, apart from D6.
- **D2 Two-stage defer.** The section's `@defer (on viewport)` stays as it is (search + marker resource). Inside the new `fs-landing-map` sits a second `@defer (on viewport)` around `<fs-map-view>`, so the Leaflet/pmtiles/protomaps chunks and tiles load only when the map frame is visible. Prerender outputs only the placeholder.
- **D3 Pin click = small popup in the map frame**, filled by `getPublicMapBubble({ kind: apiKind.toLowerCase(), id })` (as in `map-page.component.ts`). It shows a kind label, the title, the address line for an FSP, and one button "Ansehen" → `bubble.link` (`/r/:slug` or the FSP page). No image.
- **D4 Search → map focus:** on a result with `region.pin`, the map moves to `{ lat, lon, zoom: 11 }` via MapView `focus`. No pin, empty or error → the map stays. The signal lives in `LandingMarkersResource` (per page, shared by both children).
- **D5 No scroll trap:** new input `cooperative`. On touch (`pointer: coarse`) Leaflet `dragging` is off and `touchZoom` on, so one finger scrolls the page and two fingers pan/zoom. A one-finger move shows "Mit zwei Fingern bewegen" for 1.5 s. The wheel never zooms (`scrollWheelZoom: false` already). Mouse drag and +/− stay. No gesture plugin.
- **D6 Reduced motion for every map** (rule + unit test): with `prefers-reduced-motion: reduce`, `zoomAnimation`, `fadeAnimation` and `markerZoomAnimation` are false and `setView(…, { animate: false })`.
- **D7 Keyboard:** new input `markerTabStops` (default true), false on `/`, so the pins don't become hundreds of Tab stops. The map stays one Tab stop (arrow-key pan, +/−). The keyboard path is the postcode search and "Karte öffnen". With it off, the hidden marker list isn't rendered, and the region's aria-label carries the counts.
- **D8 Colours from `.fs-public`:** MapView reads `--fs-map-surface`, `--fs-map-pin-region-bg/-fg` and `--fs-map-pin-fsp-bg/-fg`, falling back to today's hex values. `fs-landing-map` sets them to `var(--fs-pub-map-land)`, `var(--fs-primary)`/`var(--fs-on-primary)` and `var(--fs-accent)`/`#1a2a20`. Inside `main.fs-public` they resolve to the reskin tokens. O1 holds (accent only on the FSP pin, with `pi-box`). The tiles follow the theme (light/dark).
- **D9 Initial landing view:** DACH, `center [47.9, 10.4]`, zoom 6 (≥ 48rem) or 5 (phones), `fitMarkers=false`.
- **D10 SVG removed:** delete the preview component and spec, plus `FRAME`, `LAND_SHAPES`, `shapePath`, `projectToFrame`, `framePercent` and `insideShape` from `landing-geo.ts` (keep `distanceKm`, `nearestFsps`, `isPostcode`) and the keys `previewAlt`/`previewNote*`. The placeholder is a plain frame of the same height.
- **D11 Bookkeeping:** decision **175** (174 = reskin; re-check at merge) amends 160's "Map (option 1)", the O1 wording ("Fairteiler pin of the map") and "no MapView on `/`", and records D12–D14 for M1. Ledger notes on C17 ("real map") and M1 ("deep link `?lat&lon&z`"), both pointing to decision 175.
- **D12 Deep-link format** `/map?lat=<deg>&lon=<deg>&z=<int>`, written with 4 decimals (about 11 m). Parsing lives in the shared `shared/map-view/map-view-query.ts` (`parseMapView(params): MapFocus | null`, `mapViewQuery(view): {lat, lon, z}`), used by both features, so there's no cross-feature import. Rules: `lat`/`lon` must both be finite decimals with `lat ∈ [-90, 90]` and `lon ∈ [-180, 180]`; otherwise the whole link is ignored and the default view (Zürich, zoom 8) applies. `z` is rounded and clamped to `[3, 19]`; if it's missing or not a number while `lat`/`lon` are valid, it is 12. Extra or duplicated params: the first value counts, the rest are ignored. Nothing is ever shown as an error.
- **D13 `/map` keeps the URL in sync** (recommended): on MapView `viewChange` (moveend), debounced 300 ms, `router.navigate([], { queryParams, replaceUrl: true })`. That way reload, share and back-from-a-bubble-link return to the same view, and history doesn't grow. It is not written while the view equals the default (plain `/map` stays `/map`). **Data protection:** a move caused by "near me" (browser location) is never written. The URL drops its params instead, until the user pans or zooms by hand. That keeps a person's position out of URLs that get shared or bookmarked. A location search hit is a place name the user typed, so that one is written.
- **D14 Links into `/map`:** the landing map's "Karte öffnen" (footer and tile-error action) uses its current view (`viewChange`, before any move: the D9 view). The result card's "Karte öffnen" in `landing-near` uses the found region's pin at z 11. Plain `/map` links elsewhere (nav, other pages) are unchanged.

## Scope
**In:** `fs-landing-map`; MapView `cooperative`, `markerTabStops`, `viewChange` output, tile-error action slot, colour vars and reduced motion; search → focus; popup; states; deep-link parsing and `/map` URL sync; i18n; unit, e2e and screenshot tests; docs.
**Out:** clustering; deep links for layers, filters or the open bubble; baskets and store areas on `/`; contract, server, Liquibase and seed changes (existing public endpoints, existing seed); the reskin branch itself.

## Changes
**Contract / server / Liquibase / seed:** none. `getPublicMapBubble` already serves FSP/REGION/REGION_PIN publicly.

**Client, shared** (`client/src/app/shared/map-view/`):
- `map-view.component.ts`:
  - inputs `cooperative = input(false)` and `markerTabStops = input(true)` (false → `keyboard: false`, no hidden `<ul>`);
  - output `viewChange = output<MapFocus>()` on `moveend`, next to the existing `updateViewCenter`. It is emitted with a flag `byUser`: true after drag/zoom/keyboard, false after `focus`/`setView`. `/map` needs it for D13;
  - `cooperative`: `dragging: !coarse` at init, plus a one-touch touchmove listener showing a `.gesture-hint` overlay (`shared.map.twoFingers`, `aria-hidden`);
  - reduced motion (D6), read once at init via `matchMedia`, as in `count-up.component.ts`;
  - `<ng-content select="[fsMapTilesAction]" />` in `.tiles-error`;
  - styles: `.map` background `var(--fs-map-surface, var(--fs-surface-muted))`; region/default pin `var(--fs-map-pin-region-bg, #25834a)`/`-fg, #fff`; fsp `var(--fs-map-pin-fsp-bg, #8a4fb0)`/`-fg, #fff`.
- new `map-view-query.ts` + `map-view-query.spec.ts` (D12).
- `shared/i18n/*.json`: `shared.map.twoFingers` in 5 languages.

**Client, public feature:**
- new `components/landing-map.component.ts` (`fs-landing-map`):
  - pins: `computed` from `LandingMarkersResource.state` with a local REGION→`region`, REGION_PIN→`region-pin`, FSP→`fsp` mapping, labelled `public.landing.near.map.kind.region|fsp`;
  - `<fs-map-view [markers] [state]="markers.state" [center] [zoom] [fitMarkers]="false" [focus]="markers.focus()" [cooperative]="true" [markerTabStops]="false" height="clamp(16rem, 36vw, 24rem)" testId="public.landing.near.map" (markerClick)="open($event)" (viewChange)="view.set($event)">`;
  - `openMapQuery = computed(() => mapViewQuery(view() ?? D9 view))` on both "Karte öffnen" links (`routerLink="/map" [queryParams]`);
  - popup: `selected` signal, `bubble = loadResource({ params: selected, loader: getPublicMapBubble })`, `<fs-data-state>` with skeleton, error + retry, not-found (404 MARKER_NOT_FOUND → "nicht mehr verfügbar") and offline. Close button and Escape; focus moves to the heading on open and back to the map on close. Desktop: overlay bottom-left; phones (< 48rem): below the map;
  - footer note `public.landing.near.map.note` (`{regions} Bezirke · {fsps} Fairteiler`, `noteNoCount` when a list failed) + "Karte öffnen";
  - inner defer: `@placeholder` = a frame of the same height + footer (link with the D9 view; no request); `@error` = card "Karte gerade nicht verfügbar" + "Karte öffnen".
- `landing-markers.resource.ts`: `readonly focus = signal<MapFocus | null>(null)`; update the doc comment.
- `landing-near.component.ts`: set `markers.focus` from `region.pin`; add `pin` to `NearResult`; the card's "Karte öffnen" gets `[queryParams]="mapViewQuery({lat, lon, zoom: 11})"` when there is a pin, plain `/map` otherwise.
- `pages/landing-page.component.{ts,html}`: `fs-landing-map-preview` → `fs-landing-map`, doc comment updated. Check the `.near--placeholder` height in the scss (the reskin owns this file, so rebase first).
- `landing-geo.ts` / `landing-geo.spec.ts`: remove the frame/shape code and its tests.
- `features/public/i18n/*.json` (5 languages): `public.landing.near.map.{label,note,noteNoCount,kind.region,kind.fsp,bubble.label,bubble.open,bubble.close,bubble.gone,unavailable,openMap}`; remove `previewAlt` and `previewNote*`. `npm run i18n:check` passes.

**Client, map feature** (`features/map/pages/map-page.component.ts`):
- inject `ActivatedRoute`; on init `parseMapView(route.snapshot.queryParamMap)` → initial `focus` (else the default Zürich/8, unchanged);
- `(viewChange)` → D13 sync (debounced, `replaceUrl`, skip the default view and geolocation moves; `locate()` sets a `suppressSync` flag that the next `byUser` move clears). The doc comment names the deep link.

**Docs:** `docs/decisions.md` row 175; `docs/ledger.md` C17 + M1 notes; `docs/conventions.md` MapView paragraph (inputs, `viewChange`, colour vars, reduced motion, deep-link format).

## Effect on today's `/map`
Without params it behaves and looks exactly as today: same default view, no URL change on load, so `d4-sidenav`/`d4-shell-navigation` (`path === '/map'`), `i18n-smoke`, `a11y/routes.ts` and all `e2e/screenshots/map.screen.ts` baselines stay. The only visible differences are that the URL gains `?lat&lon&z` after the user moves the map or picks a search hit, and that animations are off under reduced motion. `m1-map.spec.ts` "location search centres the map" still passes. It gets one extra assertion (on purpose) that the URL now carries the hit. "Near me" asserts the URL has no params.

## UI
A visible change on `/` (map, popup, gesture hint, fallback states). No visible change on `/map` apart from the URL. A designer mockup is optional, not advised; it's only worth doing if the owner wants to approve the popup placement on phones.

## Tests
- **Unit `map-view-query.spec.ts`** (rule tests): valid round trip at 4 decimals; `z` clamped (0 → 3, 25 → 19, 7.6 → 8); missing/NaN `z` → 12; lat 91 / lon -181 / `abc` / empty / only one of lat or lon → `null`; duplicated params → first value; `Infinity`/`1e400` → `null`.
- **Unit map-view:** `cooperative` toggles dragging by pointer type; `markerTabStops=false` → no marker `tabindex` and no hidden list; reduced motion turns animations off; `viewChange` after a move with `byUser`; tiles action projected.
- **Unit map-page** (`map-page.spec.ts`): query → initial focus; invalid query → default view; sync writes `replaceUrl` params after a user move, nothing for the default view, nothing after "near me" until a user move.
- **Unit landing-map** (replaces the preview spec): mapping and labels; failed list → no pins and no counts; click → bubble kind; 404 → not-found; focus follows the resource; the "Karte öffnen" query follows `viewChange`.
- **Unit landing-near:** pin → focus + card query; no pin → plain `/map`. **landing-page spec** (on purpose): "never embeds a MapView" → "MapView only after the inner defer".
- **E2E `c17-landing.spec.ts` @C17** (on purpose): line 91 → no `/tiles/` or `/api/public/map/` request before scrolling, `.leaflet-container` after. Lines 121/122/130 → hrefs with `?lat=…&lon=…&z=…`. New tests: pins present; pin → popup → "Ansehen"; 8004 → `data-center` ≈ 47.37, 8.54; tile 500 → overlay with "Karte öffnen"; failing FSP markers → region pins only (replaces line 179); a mobile one-finger swipe scrolls the page; axe with the popup open; **"Karte öffnen" after a search lands on `/map` with `data-center` ≈ Zürich at zoom 11 (@C17 @M1)**.
- **E2E `m1-map.spec.ts` @M1:** new "M1 deep link opens and keeps the view": `/map?lat=47.3769&lon=8.5417&z=13` → `data-center`; pan → URL updated with `replaceUrl` (`history.length` unchanged); reload → same view; `/map?lat=999&lon=x&z=50` → default view, no error UI.
- **Screenshots `landing.screen.ts`:** `landing-map` (light + dark), `landing-map-bubble`, `landing-map-tiles-error`, `landing-map-placeholder` (`__fsTileUrl` stub); `landing-near-result-*` changes on purpose. `map.screen.ts`: unchanged, no new spec (no visible change). Baselines through CI (decision 161), with every PNG reviewed.
- **Lighthouse** (`d11-lighthouse.spec.ts`): thresholds unchanged, green without scrolling.

## Done when
- [ ] `/` loads no Leaflet chunk, no `/tiles/` request and no marker request before the map frame is in view (e2e)
- [ ] Real map with region + FSP pins in `.fs-public` colours, light and dark tiles; pin → popup with all states
- [ ] Postcode search moves the map; phones scroll with one finger, the map moves with two; reduced motion = no animations
- [ ] Tile or chunk failure degrades to a message plus "Karte öffnen"
- [ ] "Karte öffnen" opens `/map` at the landing view; `/map` reads, validates and clamps `lat/lon/z`, and keeps the URL in sync (except the default view and "near me")
- [ ] Plain `/map` is unchanged: existing M1 e2e and screenshot baselines pass untouched
- [ ] Preview and frame code removed; i18n check green in 5 languages
- [ ] Unit, e2e @C17/@M1, Lighthouse and axe green; landing baselines regenerated in CI and reviewed
- [ ] Decision 175, ledger C17 + M1 notes and the conventions MapView paragraph written

## Risks
- **Lighthouse full-page screenshot** grows the viewport at the end of the run and can trigger both defers. That comes after the metrics; if TBT/LCP still moves, report it to the owner rather than loosening the gate.
- **Pin count:** hundreds to thousands of divIcons on phones without clustering. Measure on staging; a clustering follow-up is possible.
- **Touch:** Leaflet two-finger pan differs between WebKit and Chromium, so test both mobile projects.
- **Shared MapView:** the CSS-var fallbacks must equal today's hex exactly, or every map's baselines move.
- **URL sync loops:** writing query params must not re-trigger `focus` (parse only once on init, never from later URL changes); covered by the map-page unit test.
- **Merge order:** the reskin and review-fix branches touch `landing-page.component.*`. Rebase before the baseline run.
