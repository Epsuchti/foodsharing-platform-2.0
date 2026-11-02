import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';
import { acceptConfirm, openBookable } from '../intro-pickups/_intro';

/**
 * S17 (decision 153): nobody unverified learns which stores work with foodsharing. They see exact counts per city as
 * bubbles; the store of an EinAb appears only after booking it; flagged stores are named on the partners page.
 */
test.use({ seedReset: 'test' });

const S01 = '00000020-0001-4000-8000-000000000001';
const STORE_MARKERS = '/api/map/markers/stores';
const bubbles = (page: Page) => page.getByTestId('map.page.map').locator('.fs-map-marker[data-kind="store-area"]');

test('S17 anonymous visitors see count bubbles and no store @S17 @page', async ({ as }) => {
  const visitor = await as(null);
  const asked: string[] = [];
  visitor.on('request', (request) => asked.push(request.url()));
  await visitor.goto('/map');
  await expectDataState(visitor, 'map.page.map.state', 'success');
  await expect(bubbles(visitor)).toHaveCount(2);
  await expect(bubbles(visitor).first()).toContainText(/^\d+$/);
  await bubbles(visitor).first().focus();
  await bubbles(visitor).first().press('Enter');
  await expect(visitor.getByTestId('map.area.text')).toContainText('Winterthur');
  await expect(visitor.getByTestId('map.area').locator('a')).toHaveCount(0);
  expect(asked.filter((url) => url.includes(STORE_MARKERS))).toEqual([]);
  await expect(visitor.getByTestId('map.page.layer.stores')).toHaveCount(0);
});

test('S17 an unverified member sees bubbles, no store pages and no store link @S17', async ({ as, apiOf, dev }) => {
  await dev.setRole('einab1', 'FOODSAVER');
  const page = await as('einab1');
  await acceptPendingConsents(apiOf(page));
  const asked: string[] = [];
  page.on('request', (request) => asked.push(request.url()));
  await page.goto('/map');
  await expectDataState(page, 'map.page.map.state', 'success');
  await expect(bubbles(page)).toHaveCount(2);
  expect(asked.filter((url) => url.includes(STORE_MARKERS))).toEqual([]);
  await expect(page.getByTestId('map.page.layer.stores')).toHaveCount(0);
  await expect(page.getByTestId('nav.stores')).toHaveCount(0);

  await page.goto(`/stores/${S01}`);
  await expect(page.getByTestId('errorPages.notFound')).toBeVisible();
  await page.goto('/stores/mine');
  await expect(page.getByTestId('errorPages.notFound')).toBeVisible();
  await page.goto('/store-chains');
  await expect(page.getByTestId('errorPages.notFound')).toBeVisible();

  const api = apiOf(page);
  expect((await api.get(STORE_MARKERS)).status()).toBe(403);
  expect((await api.get(`/api/stores/${S01}`)).status()).toBe(404);
  expect((await api.get('/api/store-chains')).status()).toBe(403);
});

test('S17 booking reveals the one store, and only that one @S17', async ({ as, apiOf }) => {
  const page = await as('quiz');
  const api = apiOf(page);
  await acceptPendingConsents(api);
  await openBookable(page);
  const list = page.getByTestId('introPickups.bookable.state');
  await expect(list).not.toContainText('Babu');
  await expect(list).not.toContainText('Stauffacher');

  await page.getByTestId('introPickups.bookable.card').getByTestId('introPickups.bookable.book').first().click();
  await acceptConfirm(page);
  await expect(page.getByTestId('introPickups.bookable.success')).toBeVisible();
  const bookings = await api.getJson<{ introPickup: { businessName?: string } }[]>('/api/users/me/intro-pickups');
  expect(bookings).toHaveLength(1);
  const names = await api.getJson<{ items: Record<string, unknown>[] }>(
    '/api/public/regions/zuerich-stadt/intro-pickups?size=50',
  );
  expect(JSON.stringify(names)).not.toContain('businessName');
  expect((await api.get(STORE_MARKERS)).status()).toBe(403);
});

test('S17 a manager flags a store with a warning and the partners page follows @S17', async ({ as, apiOf }) => {
  const visitor = await as(null);
  await visitor.goto('/about/partners');
  await expectDataState(visitor, 'content.partners.state', 'success');
  await expect(visitor.getByTestId('content.partners.store')).toHaveCount(2);
  await expect(visitor.getByTestId('content.partners.state')).toContainText('Coop Bahnhofbrücke');

  const bieb = await as('bieb');
  await acceptPendingConsents(apiOf(bieb));
  await bieb.goto(`/stores/${S01}/edit`);
  await expectDataState(bieb, 'stores.form.state', 'success');
  await expect(bieb.getByTestId('stores.form.publicNameWarning')).toBeVisible();
  await expect(bieb.getByTestId('stores.form.requiresVerified')).toHaveCount(0);
  await bieb.locator('label[for="sf-public-name"]').click();
  await bieb.getByTestId('stores.form.submit').click();
  await expect(bieb).toHaveURL(new RegExp(`/stores/${S01}(/overview)?$`));

  await visitor.reload();
  await expectDataState(visitor, 'content.partners.state', 'success');
  await expect(visitor.getByTestId('content.partners.store')).toHaveCount(1);
  await expect(visitor.getByTestId('content.partners.state')).not.toContainText('Coop Bahnhofbrücke');
});

test('S17 verified members keep the full map and see no bubbles @S17', async ({ as, apiOf }) => {
  const page = await as('foodsaver');
  await acceptPendingConsents(apiOf(page));
  await page.goto('/map');
  await expectDataState(page, 'map.page.map.state', 'success');
  await expect(page.getByTestId('map.page.layer.stores')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('map.page.layer.storeAreas')).toHaveCount(0);
  await expect(bubbles(page)).toHaveCount(0);
  await expect(page.getByTestId('map.page.map').locator('.fs-map-marker[data-kind="store"]').first()).toBeAttached();
});
