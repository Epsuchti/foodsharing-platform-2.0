import type { Page } from '@playwright/test';
import { expect, test } from '../../fixtures';
import { openStore } from '../stores/_stores';

/**
 * MT1 self-hosted map tiles (decision 159): every map draws the vector basemap from /tiles/basemap.pmtiles on our
 * own origin (e2e serves the committed Zurich fixture, e2e/fixtures/tiles). A missing file shows the tile error with
 * retry while the markers stay usable; the dark theme gets the dark flavor. MT2 (decision 163) moves the file to its own
 * tile server behind the same path, so the range request contract below is also its browser-facing test.
 */

const map = (page: Page) => page.getByTestId('map.page.map');

test('MT1 the basemap answers a range request with 206 and the PMTiles v3 magic @MT1 @MT2', async ({ page }) => {
  const response = await page.request.get('/tiles/basemap.pmtiles', { headers: { Range: 'bytes=0-126' } });
  expect(response.status()).toBe(206);
  expect(response.headers()['content-range']).toMatch(/^bytes 0-126\/\d+$/);
  expect(response.headers()['content-encoding']).toBeUndefined();
  const body = await response.body();
  expect(body.length).toBe(127);
  expect(body.subarray(0, 7).toString('ascii')).toBe('PMTiles');
  expect(body[7]).toBe(3);
});

test('MT1 /map and a store page draw the vector basemap, and every map request stays on our origin @MT1', async ({
  page,
  baseURL,
  as,
}) => {
  const origin = new URL(baseURL!).origin;
  const foreign: string[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if ((url.protocol === 'http:' || url.protocol === 'https:') && url.origin !== origin) {
      foreign.push(request.url());
    }
  });

  await page.goto('/map');
  await expect(map(page)).toHaveAttribute('data-tiles', 'ok');
  await expect(map(page)).toHaveAttribute('data-basemap', 'light');
  await expect(map(page).locator('canvas.leaflet-tile').first()).toBeVisible();
  await expect(map(page).locator('.leaflet-control-attribution')).toContainText('OpenStreetMap contributors');
  await expect(map(page).locator('.fs-map-marker').first()).toBeVisible();
  expect(foreign, 'requests to other hosts').toEqual([]);

  const member = await as('foodsaver');
  await openStore(member, 'S-01');
  const storeMap = member.getByTestId('stores.overview.mapView');
  await expect(storeMap).toHaveAttribute('data-tiles', 'ok');
  await expect(storeMap.locator('canvas.leaflet-tile').first()).toBeVisible();
});

test('MT1 a missing tile file shows the tile error with retry and keeps the markers @MT1', async ({ page }) => {
  await page.route('**/tiles/**', (route) => route.fulfill({ status: 404, body: '' }));
  await page.goto('/map');
  await expect(map(page)).toHaveAttribute('data-tiles', 'error');
  const error = page.getByTestId('map.page.map.tileError');
  await expect(error).toBeVisible();
  // markers stay usable: a pin still opens its bubble
  const pin = map(page).locator('.fs-map-marker[data-kind="fsp"]').first();
  await pin.focus();
  await pin.press('Enter');
  await expect(page.getByTestId('map.bubble.state')).toHaveAttribute('data-state', 'success');

  await page.unroute('**/tiles/**');
  await error.getByRole('button').click();
  await expect(map(page)).toHaveAttribute('data-tiles', 'ok');
  await expect(error).toHaveCount(0);
});

test('MT1 the dark theme draws the dark basemap @MT1', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.goto('/map');
  await expect(map(page)).toHaveAttribute('data-basemap', 'dark');
  await expect(map(page)).toHaveAttribute('data-tiles', 'ok');
});
