import type { Locator, Page } from '@playwright/test';
import { acceptPendingConsents, expectDataState } from '../../fixtures';
import { checkpoint, expect, isCheckpointProject, journey as test } from '../../fixtures/checkpoint';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local, Zürich Stadt) uses the map (M1, plan-m7 Q7): the stores layer with its filter
 * bar (cooperation status, team help), a store's bubble and its page (whose own map also draws the basemap), the
 * layer choice remembered per browser, the location search (postcode, region name, nothing found), a deep link that
 * follows her panning in place and "near me", which never goes into the URL. On the phone the map stays below the tab
 * bar. A missing tile file shows the tile error with retry while the pins stay usable, and the map draws the
 * self-hosted vector basemap (MT1, decision 159; checkpoints use the raster stub, so this is asserted, not captured).
 * The region's ambassador sees its members on the map while MAP_USERS is on, and not after orga switched it off.
 *
 * Rules behind it (unit): MarkerServiceTest (store status/help and user filters; member layer: plain members,
 * region admins, rounding), MapControllerTest (anonymous), FeatureToggleServiceTest (404 when off), map-page.spec (layers
 * per role, member layer only with MAP_USERS, deep link written debounced with replaceUrl, "near me" never in the URL,
 * failed layer + retry), map.util.spec (layers in localStorage, filters), map-view-query.spec (link parsing, invalid
 * links), basemap.spec (dark flavour, labels per language, own origin), MapSearchServiceTest; the range request
 * contract of /tiles/basemap.pmtiles (MT1, MT2): StaticResourceTilesIT.
 */
test.use({ role: 'foodsaver' });

interface StoreMarkerDto {
  id: string;
  name: string;
  cooperationStatus: string;
}

const ZURICH = '00000010-0001-4000-8000-000000000040';
const map = (page: Page): Locator => page.getByTestId('map.page.map');
const pins = (page: Page, kind: string): Locator => map(page).locator(`.fs-map-marker[data-kind="${kind}"]`);

/** Opens a pin's bubble with the keyboard (pins overlap at country zoom; Leaflet markers are focusable buttons). */
async function openPin(walk: Walk, pin: Locator): Promise<void> {
  await pin.focus();
  walk.count(1);
  await pin.press('Enter');
}

/** Selects an option of an Optimus `p-select` by its visible label. */
async function choose(walk: Walk, testId: string, label: string | RegExp): Promise<void> {
  const { page } = walk;
  await walk.click(page.getByTestId(testId));
  await walk.click(page.getByRole('option', { name: label }).first());
  await expect(page.locator('.p-overlay')).toHaveCount(0);
}

/** Phones fold a filter bar behind its toggle (design review M7 R2); wider screens always show the selects. */
async function openFilters(walk: Walk, testId: string): Promise<void> {
  const toggle = walk.page.getByTestId(`${testId}.toggle`);
  if (await toggle.isVisible()) {
    await walk.click(toggle);
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  }
}

test('member filters the stores on the map, searches a place and shares the view @M1 @MT1 @C11', async ({
  page,
  as,
  apiOf,
  baseURL,
}, testInfo) => {
  test.setTimeout(150_000);
  const api = apiOf(page);
  await acceptPendingConsents(api);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const all = await api.getJson<StoreMarkerDto[]>('/api/map/markers/stores');
  const filtered = await api.getJson<StoreMarkerDto[]>('/api/map/markers/stores?status=COOPERATING&help=SEARCHING');
  expect(filtered.length).toBeGreaterThan(0);
  expect(filtered.length).toBeLessThan(all.length);
  const origin = new URL(baseURL!).origin;
  const foreign: string[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (/^https?:$/.test(url.protocol) && url.origin !== origin) foreign.push(request.url());
  });

  await walk.step('opens the map with the stores of her region', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('map');
    await expect(page).toHaveURL(/\/map$/);
    await expectDataState(page, 'map.page.map.state', 'success');
    await expect(map(page)).toHaveAttribute('data-tiles', 'ok');
    await expect(page.getByTestId('map.page.layer.stores')).toHaveAttribute('aria-pressed', 'true');
    await expect(pins(page, 'store')).toHaveCount(all.length);
    // a plain member never gets the member layer
    await expect(page.getByTestId('map.page.layer.users')).toHaveCount(0);
    await checkpoint(page, 'member-map');
  });

  await walk.step('shows only cooperating stores whose team urgently needs help and opens one', async () => {
    await openFilters(walk, 'map.page.storeFilter');
    await choose(walk, 'map.page.storeFilter.status', 'Kooperiert');
    await choose(walk, 'map.page.storeFilter.help', 'Sucht dringend Hilfe');
    await expectDataState(page, 'map.page.map.state', 'success');
    await expect(pins(page, 'store')).toHaveCount(filtered.length);

    const store = filtered[0];
    await openPin(walk, map(page).locator(`.fs-map-marker[data-kind="store"][title="${store.name}"]`).first());
    await expectDataState(page, 'map.bubble.state', 'success');
    await expect(page.getByTestId('map.bubble')).toHaveAttribute('data-kind', 'STORE');
    await expect(page.getByTestId('map.bubble.title')).toHaveText(store.name);
    await expect(page.getByTestId('map.bubble.subtitle')).toHaveText('Kooperiert');
    await expect(page.getByTestId('map.bubble.lines')).toContainText('Team sucht dringend Hilfe');
    await checkpoint(page, 'store-bubble');
    await walk.click(page.getByTestId('map.bubble.link'));
    await expect(page).toHaveURL(new RegExp(`/stores/${store.id}`));
    // the store page's own map draws the basemap too
    const storeMap = page.getByTestId('stores.overview.mapView');
    await expect(storeMap).toHaveAttribute('data-tiles', 'ok');
    await expect(storeMap.locator('.leaflet-tile').first()).toBeVisible();
  });

  await walk.step('hides the baskets, and the map remembers it', async () => {
    await page.goto('/map');
    await expectDataState(page, 'map.page.map.state', 'success');
    await walk.click(page.getByTestId('map.page.layer.baskets'));
    await expect(page.getByTestId('map.page.layer.baskets')).toHaveAttribute('aria-pressed', 'false');
    await page.reload();
    await expectDataState(page, 'map.page.map.state', 'success');
    await expect(page.getByTestId('map.page.layer.baskets')).toHaveAttribute('aria-pressed', 'false');
    await expect(pins(page, 'basket')).toHaveCount(0);
    await expect(pins(page, 'fsp')).toHaveCount(5);
  });

  await walk.step('searches Winterthur by postcode and by name', async () => {
    await walk.fill(page.getByTestId('map.page.search'), '8400');
    await walk.click(page.getByTestId('map.page.searchSubmit'));
    await expectDataState(page, 'map.page.searchState', 'success');
    await expect(page.getByTestId('map.page.searchResult').first()).toHaveText('8400');
    // the postcode centre of Winterthur (static geocoding seed)
    await expect(map(page)).toHaveAttribute('data-center', '47.500,8.724');
    // a search hit is a place the user typed: the URL carries it (decision 179)
    await expect(page).toHaveURL(/\/map\?lat=47\.(49|50)\d\d&lon=8\.72\d\d&z=13$/);
    await checkpoint(page, 'search-result');

    await walk.fill(page.getByTestId('map.page.search'), 'Winterthur');
    await walk.click(page.getByTestId('map.page.searchSubmit'));
    await expect(page.getByTestId('map.page.searchResult').filter({ hasText: 'Winterthur' }).first()).toBeVisible();
    await expect(map(page)).toHaveAttribute('data-center', '47.500,8.726');

    await walk.fill(page.getByTestId('map.page.search'), 'Nirgendwo');
    await walk.click(page.getByTestId('map.page.searchSubmit'));
    await expectDataState(page, 'map.page.searchState', 'empty');
  });

  await walk.step('pans the map, shares the link and centres it on herself', async () => {
    await page.goto('/map?lat=47.3769&lon=8.5417&z=13');
    await expectDataState(page, 'map.page.map.state', 'success');
    await expect(map(page)).toHaveAttribute('data-center', '47.377,8.542');
    const entries = await page.evaluate(() => history.length);
    // a pan by hand (arrow key on the focused map) rewrites the URL in place
    await map(page).locator('.leaflet-container').focus();
    await page.keyboard.press('ArrowRight');
    await expect(map(page)).not.toHaveAttribute('data-center', '47.377,8.542');
    await expect(page).toHaveURL(/\/map\?lat=47\.\d{4}&lon=8\.\d{4}&z=13$/);
    await expect(page).not.toHaveURL(/lon=8\.5417/);
    expect(await page.evaluate(() => history.length)).toBe(entries);
    const moved = await map(page).getAttribute('data-center');
    // the shared link opens the same view
    const shared = await as('bieb');
    await shared.goto(page.url());
    await expectDataState(shared, 'map.page.map.state', 'success');
    await expect(shared.getByTestId('map.page.map')).toHaveAttribute('data-center', moved!);
    await shared.context().close();

    // "near me": the browser location never goes into the URL
    await page.context().grantPermissions(['geolocation'], { origin });
    await page.context().setGeolocation({ latitude: 47.3851, longitude: 8.5312 });
    await walk.click(page.getByTestId('map.page.nearMe'));
    await expect(map(page)).toHaveAttribute('data-center', '47.385,8.531');
    await expect(page).toHaveURL(/\/map$/);
  });

  if (isPhone(page)) {
    await walk.step('reaches the tab bar over the map on her phone', async () => {
      // reduced motion too: without Leaflet's animations the map's panes once painted over the shell (decision 179)
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.goto('/map');
      await expectDataState(page, 'map.page.map.state', 'success');
      await expect(map(page)).toHaveAttribute('data-tiles', 'ok');
      const bar = page.getByTestId('shell.tabBar');
      await expect(bar).toBeVisible();
      // the map frame reaches under the fixed tab bar
      const frame = (await map(page).locator('.leaflet-container').boundingBox())!;
      const barBox = (await bar.boundingBox())!;
      expect(frame.y + frame.height).toBeGreaterThan(barBox.y + barBox.height / 2);
      // the map is its own stacking context below the bar: Leaflet's panes (z-index 200–1000) can't reach the shell
      const stacking = await page.evaluate(() => {
        const pane = document.querySelector('[data-testid="map.page.map"] .leaflet-map-pane')!;
        const tabBar = document.querySelector('fs-mobile-tab-bar')!;
        const createsContext = (el: Element) => {
          const style = getComputedStyle(el);
          return style.isolation === 'isolate' || (style.position !== 'static' && style.zIndex !== 'auto');
        };
        let root: Element | null = null;
        for (let el: Element | null = pane; el && !el.contains(tabBar); el = el.parentElement) {
          if (createsContext(el)) root = el;
        }
        return { zIndex: root ? Number(getComputedStyle(root).zIndex) || 0 : null, bar: Number(getComputedStyle(tabBar).zIndex) };
      });
      expect(stacking.zIndex, 'the map has a stacking context of its own').not.toBeNull();
      expect(stacking.zIndex!).toBeLessThan(stacking.bar);
      // what is painted on top at every tab of the bar belongs to the bar, not to the map (tiles, pins, controls)
      const tabs = bar.locator('[data-testid^="nav.tab."]:not([data-testid$=".badge"])');
      for (const tab of await tabs.all()) {
        const box = (await tab.boundingBox())!;
        const onTop = await page.evaluate(
          ([x, y]) => {
            const hit = document.elementFromPoint(x, y);
            return { inBar: !!hit?.closest('[data-testid="shell.tabBar"]'), inMap: !!hit?.closest('.leaflet-container') };
          },
          [box.x + box.width / 2, box.y + box.height / 2] as const,
        );
        expect(onTop, (await tab.getAttribute('data-testid')) ?? '').toEqual({ inBar: true, inMap: false });
      }
      await walk.click(bar.locator('[data-testid^="nav.tab."]').first());
      await expect(page).not.toHaveURL(/\/map/);
      await page.emulateMedia({ reducedMotion: null });
    });
  }

  await walk.step('still finds the Fairteiler while the map tiles are missing', async () => {
    // the self-hosted file and, on the checkpoint projects, the raster stub both fail
    const missing = (url: URL) => url.pathname.startsWith('/tiles/') || url.pathname.startsWith('/__fs-stub-tile/');
    await page.route(missing, (route) => route.fulfill({ status: 404, body: '' }));
    await page.goto('/map');
    await expect(map(page)).toHaveAttribute('data-tiles', 'error');
    const error = page.getByTestId('map.page.map.tileError');
    await expect(error).toBeVisible();
    // the pins stay usable: a pin still opens its bubble
    await openPin(walk, pins(page, 'fsp').first());
    await expectDataState(page, 'map.bubble.state', 'success');
    await checkpoint(page, 'tile-error');
    await page.unroute(missing);
    await walk.click(error.getByRole('button'));
    await expect(map(page)).toHaveAttribute('data-tiles', 'ok');
    await expect(error).toHaveCount(0);
  });

  await walk.step('sees the vector basemap from our own server', async () => {
    if (isCheckpointProject()) {
      // the checkpoint projects draw a raster stub (window.__fsTileUrl): this page draws the real basemap
      await page.addInitScript(() => {
        delete (window as unknown as { __fsTileUrl?: string }).__fsTileUrl;
      });
    }
    await page.goto('/map');
    await expect(map(page)).toHaveAttribute('data-tiles', 'ok');
    await expect(map(page)).toHaveAttribute('data-basemap', 'light');
    await expect(map(page).locator('canvas.leaflet-tile').first()).toBeVisible();
    await expect(map(page).locator('.leaflet-control-attribution')).toContainText('OpenStreetMap contributors');
    await expect(map(page).locator('.fs-map-marker').first()).toBeVisible();
    expect(foreign, 'requests to other hosts').toEqual([]);
  });

  await walk.step("the region's ambassador sees its members until orga switches MAP_USERS off", async () => {
    const orga = apiOf(await as('orga'));
    const bot = await as('bot');
    await acceptPendingConsents(apiOf(bot));
    try {
      await bot.goto('/map');
      await expectDataState(bot, 'map.page.map.state', 'success');
      await bot.getByTestId('map.page.layer.users').click();
      await expect(bot.getByTestId('map.page.layer.users')).toHaveAttribute('aria-pressed', 'true');
      const toggle = bot.getByTestId('map.page.userFilter.toggle');
      if (await toggle.isVisible()) await toggle.click();
      await expect(bot.getByTestId('map.page.userFilter.regionId')).toContainText('Zürich Stadt');
      await expectDataState(bot, 'map.page.map.state', 'success');
      const users = await apiOf(bot).getJson<unknown[]>(`/api/map/markers/users?regionId=${ZURICH}`);
      expect(users.length).toBeGreaterThan(0);
      await expect(pins(bot, 'user')).toHaveCount(users.length);

      await orga.patchJson('/api/admin/feature-toggles/MAP_USERS', { enabled: false });
      await bot.reload();
      await expect(bot.getByTestId('map.page.layer.stores')).toBeVisible();
      await expect(bot.getByTestId('map.page.layer.users')).toHaveCount(0);
      await expect(pins(bot, 'user')).toHaveCount(0);
    } finally {
      await orga.patchJson('/api/admin/feature-toggles/MAP_USERS', { enabled: true });
    }
  });

  walk.finish({ steps: 9, clicks: 17, fields: 3 });
});
