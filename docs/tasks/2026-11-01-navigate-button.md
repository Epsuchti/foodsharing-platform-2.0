# Navigate button (resume item 15)

## Goal
One shared "Navigate" button that opens a dropdown with Google Maps and Apple Maps on stores, Fairteiler (FSP), baskets and events. The links are directions deep links to the coordinates, or to the address when there are no coordinates. The button replaces the store's "Route" toggle, its two side-by-side links and the FSP's two plain links.

## Decisions (owner, 2026-11-01)
1. The shared component lives in `client/src/app/shared/navigate-button/` (`fs-navigate-button`), with `navigate.util.ts` `navigationLinks(...)`. It replaces `mapLinks` (stores) and `fspMapLinks` (FSP); both are deleted.
2. Test IDs on all four pages: the trigger is `<module>.<page>.navigate` and the menu items are `….navigate.google` and `….navigate.apple`. `stores.page.route`, `stores.page.navigateApple`, `foodSharePoints.detail.googleMaps` and `foodSharePoints.detail.appleMaps` are removed. `stores.page.navigate` now names the trigger.
3. Directions links instead of a map pin:
   - Google: `https://www.google.com/maps/dir/?api=1&destination=<lat,lon | encoded address>`
   - Apple: `https://maps.apple.com/?daddr=<lat,lon | encoded address>&q=<encoded label>` (`q` only with a label)
   - With no coordinates and no address there is no button (the util returns `null`).
4. It is one button with a dropdown on every screen size; there is no desktop/phone split anymore.
   - Labels: de "Navigieren", en "Navigate", fr "Itinéraire", it "Indicazioni", gws "Navigiere".
   - No designer mockup.
5. HOME baskets:
   - The button shows for everyone. Non-owners get the coordinates the server already rounds to about 100 m (decision 72).
   - For non-owners the menu also shows the note "approximate location, about 100 m".
   - The Apple label is the generic "Food basket", never the basket description (the description can be personal).
6. No contract, server, Liquibase or seed change. Tests are tagged with the existing feature IDs `@S15`, `@FS1`, `@B1`, `@E1`. Record this as a new row in `docs/decisions.md`.

## Scope
- **In:**
  - the shared component and util
  - replacing the store and FSP navigation
  - adding the button to the basket and event pages
  - i18n in five languages
  - dev gallery section
  - unit, e2e and screenshot specs
- **Out:**
  - other map apps (OSM, Waze)
  - detecting the platform to pre-select an app
  - list and map-bubble links
  - any change to the basket API or to coordinate rounding

## Changes
### Client, shared
- `client/src/app/shared/navigate-button/navigate.util.ts`
  - Exports `NavigationLinks { google: string; apple: string }` and `navigationLinks(target: { lat?: number | null; lon?: number | null; street?: string; postcode?: string; city?: string; label?: string }): NavigationLinks | null`.
  - It uses the coordinates when both are set; otherwise it joins street, postcode and city (skipping empty parts); with neither it returns `null`.
  - Copy the encoding from the existing `stores.util.ts` `mapLinks`.
- `client/src/app/shared/navigate-button/navigate-button.component.ts` (`fs-navigate-button`, OnPush)
  - Inputs:
    - `links: NavigationLinks` (required)
    - `testId: string` (required)
    - `note?: string` (already translated text, shown above the items)
    - `small?: boolean`
  - The trigger is a `p-button`:
    - secondary, outlined
    - `pi pi-directions` icon plus a chevron
    - label `shared.navigate.button`
    - `aria-haspopup="menu"`, with `aria-expanded` bound to the popover state
  - The menu is an Optimus `p-popover`, following the `.menu`/`.menu__item` pattern in `features/stores/tabs/store-team.tab.ts`.
    - It holds two `<a target="_blank" rel="noopener noreferrer">` items (Google: `pi pi-map`, Apple: `pi pi-apple`) with labels `shared.navigate.google` and `shared.navigate.apple`.
    - Clicking an item closes the popover.
  - Touch targets follow decision 124 (44 px on coarse pointers).
  - Note that `p-popover` renders to the body. The e2e specs find the items by test ID, not by DOM nesting.
- `client/src/app/shared/i18n/{de,en,fr,it,gws}.json`: add `shared.navigate.button`, `.google`, `.apple`, `.approximate` and `.basketLabel` ("Food basket" / "Essenskorb" / …).

### Client, features
- **Stores**
  - `features/stores/pages/store-page.component.ts`: remove the `.route*` markup, styles, the `routeOpen` signal and the `mapLinks` import.
  - `links(store)` calls `navigationLinks({ lat, lon, ...address, label: name })`.
  - Render `<fs-navigate-button [links]="nav" testId="stores.page.navigate" [small]="true" />` in `hero__actions`.
- `features/stores/stores.util.ts`: delete `MapLinks` and `mapLinks`.
- `features/stores/i18n/*.json`: delete `stores.page.route`, `stores.page.navigate` and `stores.page.navigateApple`. Check first with `grep` that no other code uses them.
- **FSP**
  - `features/food-share-points/pages/fsp-page.component.ts` (address card, around line 122): replace the two links with `<fs-navigate-button testId="foodSharePoints.detail.navigate">`.
  - `features/food-share-points/fsp.util.ts`: delete `MapLinks` and `fspMapLinks`.
  - `features/food-share-points/i18n/*.json`: drop `foodSharePoints.detail.googleMaps` and `foodSharePoints.detail.appleMaps`.
- **Baskets**
  - `features/baskets/pages/basket-page.component.ts`, location card (around line 199), below the location text:
    - The button uses `navigationLinks({ lat, lon, label: t('shared.navigate.basketLabel') })` with `testId="baskets.detail.navigate"`.
    - `note` is set to `shared.navigate.approximate` when `locationType === 'HOME' && !isOwner()`, the same condition as `baskets.detail.locationHome`.
    - The button shows whenever the page shows the location card; the card is the only visibility rule.
- **Events**
  - `features/events/pages/event-page.component.ts`, location block (around line 66), OFFLINE events only (where the map renders):
    - `navigationLinks({ lat, lon, street, postcode: zip, city, label: loc.name })`
    - `testId="events.event.navigate"`
- `client/src/app/features/dev/gallery/gallery-sections.ts`: add a navigate-button section (plain, and with the note).

## UI
This is a visible UI change:
- The store hero loses two desktop buttons and gains one dropdown button.
- The FSP address card trades two links for one button.
- The basket and event location cards gain a button.

No designer mockup (owner decision). It reuses the existing popover-menu look.

## Tests
- **New unit tests:** extend `client/src/app/shared/m7-shared.spec.ts` (or add `navigate-button/navigate.spec.ts`). Cover:
  - coordinate links for Google and Apple, including `0` coordinates (not treated as missing)
  - address fallback with empty parts skipped
  - `null` without coordinates or an address
  - label encoding, and no `q` without a label
  - component: menu opens, both hrefs, `target=_blank`, the note renders only when given
- **Unit tests changed on purpose:** `features/stores/stores-pages.spec.ts` (around lines 196–209) tests the trigger and the menu instead of the route toggle, and imports `navigationLinks` instead of `mapLinks`.
- **E2E changed on purpose:**
  - `e2e/tests/stores/s15-navigation.spec.ts`: open the menu, expect `destination=47.3779,8.5427` and `daddr=47.3779,8.5427`.
  - `e2e/tests/fsp/fs1-food-share-points.spec.ts` (around line 150): new test IDs and `destination=` URLs.
  - `e2e/tests/personas/store-manager.spec.ts` (lines 36–39): click `stores.page.navigate`, expect `.google` and `.apple`. Adjust the step budget only if it now fails by exactly this one click on desktop. Note the change in the PR text; do not loosen the budget otherwise.
- **New e2e tests:**
  - `@B1` in the baskets tests:
    - A non-owner on a seed HOME basket sees the note.
    - The owner does not see it.
    - The hrefs contain the rounded coordinates.
  - `@E1` in the events tests: an OFFLINE seed event has the button with its coordinates.
- **Screenshots:** in `e2e/screenshots/{stores,fsp,baskets,events}.screen.ts`, add a "navigate menu open" state on desktop and mobile. Existing baselines of the four pages change on purpose. Do not update baselines in this task (decision 69, batch at the end).
- **Checks to run:**
  - `npm run i18n:check`
  - lint
  - `npm run a11y:routes` (no route change expected)
  - the axe sweep over the four pages

## Done when
- [ ] `fs-navigate-button` and `navigationLinks` exist in `shared/navigate-button/`; `mapLinks`/`fspMapLinks` and their `MapLinks` types are gone.
- [ ] The store, FSP, basket and event (OFFLINE) pages show one "Navigate" dropdown with Google and Apple directions links using the decided test IDs.
- [ ] HOME baskets show the approximate note to non-owners only; the Apple label is never the basket description.
- [ ] No old route/navigate i18n keys or test IDs are left (`grep` is clean); five languages are present; `i18n:check` is green.
- [ ] Unit and e2e tests (`@S15`, `@FS1`, `@B1`, `@E1`) and the updated screenshot specs are green. Baselines are not updated.
- [ ] Axe is clean on the four pages with the menu open.
- [ ] A decision row is added in `docs/decisions.md`; `docs/resume.md` item 15 is marked done.

## Risks
- **Popover and axe/focus:** `p-popover` attaches to the body.
  - Focus must return to the trigger on Escape and close.
  - An open popover must not leave an unnamed or hidden focus trap (see decision 112).
- **Persona step budget:** the store-manager desktop walk gains one click. Keep the change minimal and explained.
- **Apple `daddr` without coordinates:** it depends on Apple geocoding a free-text address, which is acceptable (same as today's `q`).
- **Basket privacy:** the rounding is done by the server. The client must never derive a more precise position, and the owner's own view shows exact coordinates by design.
- **Screenshot diffs:** all four pages' baselines will differ until the batch update.
