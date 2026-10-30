import type { Locator, Page } from '@playwright/test';
import { acceptPendingConsents, expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';

/**
 * M1 map (plan-m7 Q7, plan-m6 B7). Public layers: 5 active FSPs, 10 open baskets (HOME positions rounded by the
 * server), region pins. Signed in: stores with the filter bar, events and — with MAP_USERS on, for region
 * administrators (bot administers Zürich Stadt) — the members of a region. Layers are remembered per browser in
 * `localStorage['fs.map.layers']` (fresh per test context: fsps, regions, baskets, stores).
 */

interface StoreMarkerDto {
  id: string;
  name: string;
  cooperationStatus: string;
}

const pins = (page: Page, kind: string): Locator => page.getByTestId('map.page.map').locator(`.fs-map-marker[data-kind="${kind}"]`);

/** Selects an option of an Optimus `p-select` by its visible label. */
async function choose(page: Page, testId: string, label: string | RegExp): Promise<void> {
  await page.getByTestId(testId).click();
  await page.getByRole('option', { name: label }).first().click();
  await expect(page.locator('.p-overlay')).toHaveCount(0);
}

/** Opens a pin's bubble with the keyboard (pins overlap at country zoom; Leaflet markers are focusable buttons). */
async function openPin(pin: Locator): Promise<void> {
  await pin.focus();
  await pin.press('Enter');
}

test('M1 public map shows FSP, region and basket markers; logged-in user filters stores by status and team help and opens a bubble @M1 @page', async ({
  as,
  apiOf,
}) => {
  // visitor: the three public layers, no stores, no search
  const visitor = await as(null);
  await visitor.goto('/map');
  await expectDataState(visitor, 'map.page.map.state', 'success');
  await expect(pins(visitor, 'fsp')).toHaveCount(5);
  await expect(pins(visitor, 'basket')).toHaveCount(10);
  await expect(pins(visitor, 'region-pin').first()).toBeAttached();
  await expect(visitor.getByTestId('map.page.layer.stores')).toHaveCount(0);
  await expect(visitor.getByTestId('map.page.search')).toHaveCount(0);
  await expect(visitor.getByTestId('map.page.loginHint')).toBeVisible();
  await openPin(pins(visitor, 'fsp').first());
  await expectDataState(visitor, 'map.bubble.state', 'success');
  await expect(visitor.getByTestId('map.bubble.link')).toHaveAttribute('href', /^\/food-share-points\/[0-9a-f-]{36}$/);

  // foodsaver: stores layer with the filter bar
  const page = await as('foodsaver');
  const api = apiOf(page);
  await acceptPendingConsents(api);
  const all = await api.getJson<StoreMarkerDto[]>('/api/map/markers/stores');
  const filtered = await api.getJson<StoreMarkerDto[]>('/api/map/markers/stores?status=COOPERATING&help=SEARCHING');
  expect(filtered.length).toBeGreaterThan(0);
  expect(filtered.length).toBeLessThan(all.length);

  await page.goto('/map');
  await expectDataState(page, 'map.page.map.state', 'success');
  await expect(page.getByTestId('map.page.layer.stores')).toHaveAttribute('aria-pressed', 'true');
  await expect(pins(page, 'store')).toHaveCount(all.length);
  await expect(page.getByTestId('map.page.layer.users')).toHaveCount(0);

  await choose(page, 'map.page.storeFilter.status', 'Kooperiert');
  await choose(page, 'map.page.storeFilter.help', 'Sucht dringend Hilfe');
  await expectDataState(page, 'map.page.map.state', 'success');
  await expect(pins(page, 'store')).toHaveCount(filtered.length);

  const store = filtered[0];
  await openPin(page.getByTestId('map.page.map').locator(`.fs-map-marker[data-kind="store"][title="${store.name}"]`).first());
  await expectDataState(page, 'map.bubble.state', 'success');
  await expect(page.getByTestId('map.bubble')).toHaveAttribute('data-kind', 'STORE');
  await expect(page.getByTestId('map.bubble.title')).toHaveText(store.name);
  await expect(page.getByTestId('map.bubble.subtitle')).toHaveText('Kooperiert');
  await expect(page.getByTestId('map.bubble.lines')).toContainText('Team sucht dringend Hilfe');
  await page.getByTestId('map.bubble.link').click();
  await expect(page).toHaveURL(new RegExp(`/stores/${store.id}`));

  // the layer choice is remembered
  await page.goto('/map');
  await page.getByTestId('map.page.layer.baskets').click();
  await expect(page.getByTestId('map.page.layer.baskets')).toHaveAttribute('aria-pressed', 'false');
  await page.reload();
  await expect(page.getByTestId('map.page.layer.baskets')).toHaveAttribute('aria-pressed', 'false');
  await expect(pins(page, 'basket')).toHaveCount(0);
  await expect(pins(page, 'fsp')).toHaveCount(5);
});

test('M1 user markers only for region admins and only when the toggle is on @M1 @C11', async ({ as, apiOf }) => {
  const orga = apiOf(await as('orga'));
  const bot = await as('bot');
  await acceptPendingConsents(apiOf(bot));
  try {
    // MAP_USERS is on in the seed: the ambassador of Zürich Stadt sees its members (rounded positions)
    await bot.goto('/map');
    await bot.getByTestId('map.page.layer.users').click();
    await expect(bot.getByTestId('map.page.layer.users')).toHaveAttribute('aria-pressed', 'true');
    await expect(bot.getByTestId('map.page.userFilter.regionId')).toContainText('Zürich Stadt');
    await expectDataState(bot, 'map.page.map.state', 'success');
    await expect(pins(bot, 'user').first()).toBeAttached();
    const users = await apiOf(bot).getJson<{ lat: number; lon: number }[]>('/api/map/markers/users?regionId=00000010-0001-4000-8000-000000000040');
    expect(users.length).toBeGreaterThan(0);
    for (const user of users) {
      expect(Math.round(user.lat * 1000) / 1000).toBe(user.lat);
      expect(Math.round(user.lon * 1000) / 1000).toBe(user.lon);
    }
    await expect(pins(bot, 'user')).toHaveCount(users.length);

    // a plain member never gets the layer
    const member = await as('foodsaver');
    await acceptPendingConsents(apiOf(member));
    await member.goto('/map');
    await expect(member.getByTestId('map.page.layer.stores')).toBeVisible();
    await expect(member.getByTestId('map.page.layer.users')).toHaveCount(0);

    // orga switches MAP_USERS off: the layer disappears and the API refuses
    await orga.patchJson('/api/admin/feature-toggles/MAP_USERS', { enabled: false });
    await bot.reload();
    await expect(bot.getByTestId('map.page.layer.stores')).toBeVisible();
    await expect(bot.getByTestId('map.page.layer.users')).toHaveCount(0);
    await expect(pins(bot, 'user')).toHaveCount(0);
    const refused = await apiOf(bot).get('/api/map/markers/users?regionId=00000010-0001-4000-8000-000000000040');
    expect(refused.status()).toBe(404);
  } finally {
    await orga.patchJson('/api/admin/feature-toggles/MAP_USERS', { enabled: true });
  }
});

test('M1 location search centres the map @M1', async ({ apiOf, as }) => {
  const member = await as('foodsaver');
  await acceptPendingConsents(apiOf(member));
  await member.goto('/map');
  await expectDataState(member, 'map.page.map.state', 'success');
  await member.getByTestId('map.page.search').fill('8400');
  await member.getByTestId('map.page.searchSubmit').click();
  await expectDataState(member, 'map.page.searchState', 'success');
  await expect(member.getByTestId('map.page.searchResult').first()).toHaveText('8400');
  // the postcode centre of Winterthur (static geocoding seed)
  await expect(member.getByTestId('map.page.map')).toHaveAttribute('data-center', '47.500,8.724');

  // a region name finds the region
  await member.getByTestId('map.page.search').fill('Winterthur');
  await member.getByTestId('map.page.searchSubmit').click();
  await expect(member.getByTestId('map.page.searchResult').filter({ hasText: 'Winterthur' }).first()).toBeVisible();
  await expect(member.getByTestId('map.page.map')).toHaveAttribute('data-center', '47.500,8.726');

  await member.getByTestId('map.page.search').fill('Nirgendwo');
  await member.getByTestId('map.page.searchSubmit').click();
  await expectDataState(member, 'map.page.searchState', 'empty');
});

test('M1 a failed layer shows the error state and loads again on retry @M1', async ({ page }) => {
  // the retry interceptor repeats a failed GET twice: fail all three attempts
  await injectApiError(page, { url: '/api/public/map/markers/regions', status: 503, times: 3 });
  await page.goto('/map');
  const { retry } = await expectErrorState(page, 'map.page.map.state');
  await retry.click();
  await expectDataState(page, 'map.page.map.state', 'success');
  await expect(pins(page, 'region-pin').first()).toBeAttached();
});
