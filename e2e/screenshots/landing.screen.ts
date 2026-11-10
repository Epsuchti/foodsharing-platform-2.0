import { type Page } from '@playwright/test';

import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest, variants } from './_setup';

/**
 * Landing page, Variant D (C17, decision 160): full page, the result card of the postcode search, the counters'
 * skeleton and error, the search's empty and error states, and the real map (decision 176: map, popup, tile error,
 * placeholder; tiles are the raster stub of `_setup.ts`) — × light/dark on the desktop and mobile projects.
 * Moved here from onboarding.screen.ts (the old `explainer-*` baselines are gone). Baselines are generated in the
 * end batch in Docker (`e2e/scripts/screenshots.sh`, decision 69).
 */
screenshotTest.use({ seedReset: 'test' });

const MAP = 'public.landing.near.map';

/** Brings the map frame into view (it loads only then) and waits for its tiles and pins. */
async function showMap(page: Page): Promise<void> {
  await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
  await page.getByTestId('public.landing.near.mapBlock').scrollIntoViewIfNeeded();
  await expect(page.getByTestId(MAP)).toHaveAttribute('data-tiles', 'ok');
  await expect(page.getByTestId(`${MAP}.state`)).toHaveAttribute('data-state', 'success');
}

async function search(page: Page, postcode: string): Promise<void> {
  await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
  await page.getByTestId('public.landing.near.search').fill(postcode);
  await page.getByTestId('public.landing.near.search.submit').click();
}

forEachVariant(
  (variant) => {
    screenshotTest.describe(`landing ${variant.name}`, () => {
      screenshotTest.use({ variant, role: undefined });

      screenshotTest('landing full page @C17 @screenshot', async ({ page, visual }) => {
        await page.goto('/');
        await expect(page.getByTestId('public.explainer.state')).toHaveAttribute('data-state', 'success');
        // render the deferred "In deiner Nähe" and its map before the full-page capture
        await showMap(page);
        await expect(page.getByTestId('public.landing.near.idle')).toBeVisible();
        await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
        await visual.expectScreen(page, `landing-${variant.name}`, { fullPage: true });
      });

      screenshotTest('landing result card @C17 @screenshot', async ({ page, visual }) => {
        await page.goto('/');
        await showMap(page);
        await search(page, '8004');
        const near = page.getByTestId('public.landing.near');
        await expect(page.getByTestId('public.landing.near.result')).toBeVisible();
        // the map moved to the region (zoom 11)
        await expect(page.getByTestId(MAP)).toHaveAttribute('data-center', /^47\.37\d,8\.54\d$/);
        await expect(page.getByTestId(MAP)).toHaveAttribute('data-tiles', 'ok');
        await visual.expectElement(near, `landing-near-result-${variant.name}`);
      });

      screenshotTest('landing counters skeleton @C17 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/public/onboarding/explainer', delayMs: 30_000 });
        await page.goto('/');
        const state = page.getByTestId('public.explainer.state');
        await expect(state).toHaveAttribute('data-state', 'loading');
        await visual.expectElement(state, `landing-counters-skeleton-${variant.name}`, { waitForData: false });
      });

      screenshotTest('landing counters error @C17 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/public/onboarding/explainer', status: 503 });
        await page.goto('/');
        const state = page.getByTestId('public.explainer.state');
        await expect(state).toHaveAttribute('data-state', 'error');
        await visual.expectElement(state, `landing-counters-error-${variant.name}`);
      });

      screenshotTest('landing near empty @C17 @screenshot', async ({ page, visual }) => {
        await page.route('**/api/public/regions/suggest**', (route) => route.fulfill({ json: [] }));
        await page.goto('/');
        await showMap(page);
        await search(page, '9999');
        await expect(page.getByTestId('public.landing.near.state')).toHaveAttribute('data-state', 'empty');
        await visual.expectElement(page.getByTestId('public.landing.near'), `landing-near-empty-${variant.name}`);
      });

      screenshotTest('landing near error @C17 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/public/regions/suggest', status: 503, code: 'GEOCODING_UNAVAILABLE' });
        await page.goto('/');
        await showMap(page);
        await search(page, '8004');
        await expect(page.getByTestId('public.landing.near.state')).toHaveAttribute('data-state', 'error');
        await visual.expectElement(page.getByTestId('public.landing.near'), `landing-near-error-${variant.name}`);
      });

      screenshotTest('landing map @C17 @screenshot', async ({ page, visual }) => {
        await page.goto('/');
        await showMap(page);
        await visual.expectElement(page.getByTestId('public.landing.near.mapBlock'), `landing-map-${variant.name}`);
      });

      screenshotTest('landing map bubble @C17 @screenshot', async ({ page, visual }) => {
        await page.goto('/');
        await showMap(page);
        await page.getByTestId(MAP).locator('.fs-map-marker[data-kind="fsp"]').first().dispatchEvent('click');
        await expect(page.getByTestId(`${MAP}.bubble.state`)).toHaveAttribute('data-state', 'success');
        await visual.expectElement(page.getByTestId('public.landing.near.mapBlock'), `landing-map-bubble-${variant.name}`);
      });

      screenshotTest('landing map tiles error @C17 @screenshot', async ({ page, visual }) => {
        // the raster stub fails (registered after the stub route of _setup.ts, so it wins)
        await page.route('**/__fs-stub-tile/**', (route) => route.fulfill({ status: 500, body: '' }));
        await page.goto('/');
        await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
        await page.getByTestId('public.landing.near.mapBlock').scrollIntoViewIfNeeded();
        await expect(page.getByTestId(MAP)).toHaveAttribute('data-tiles', 'error');
        await expect(page.getByTestId(`${MAP}.state`)).toHaveAttribute('data-state', 'success');
        await visual.expectElement(page.getByTestId('public.landing.near.mapBlock'), `landing-map-tiles-error-${variant.name}`);
      });

      screenshotTest('landing map placeholder @C17 @screenshot', async ({ page, visual }) => {
        // find the MapView chunk of the inner defer, then hold it: the plain frame stays
        const chunks = new Set<string>();
        page.on('response', async (response) => {
          if (!new URL(response.url()).pathname.endsWith('.js')) return;
          const body = await response.text().catch(() => '');
          if (body.includes('fs-map-marker__pin')) chunks.add(new URL(response.url()).pathname);
        });
        await page.goto('/');
        await showMap(page);
        await expect.poll(() => chunks.size).toBeGreaterThan(0);
        const held = new RegExp(`(${[...chunks].map((path) => path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})$`);
        await injectApiError(page, { url: held, holdUntil: new Promise(() => undefined) });
        await page.reload();
        await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
        await page.getByTestId('public.landing.near.mapBlock').scrollIntoViewIfNeeded();
        await expect(page.getByTestId(`${MAP}.placeholder`)).toBeVisible();
        await expect(page.locator('fs-map-view')).toHaveCount(0);
        await visual.expectElement(page.getByTestId('public.landing.near.mapBlock'), `landing-map-placeholder-${variant.name}`);
      });
    });
  },
  variants(['light', 'dark']),
);
