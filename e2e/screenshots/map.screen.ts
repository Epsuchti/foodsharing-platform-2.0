import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Map (plan-m7 Q7, M1): public layers as a visitor, the signed-in map with the store filter bar and an open bubble,
 * a search result, loading and error × light/dark × de/fr. OSM tiles are stubbed by `_setup.ts`. Baselines are
 * generated in Docker in the end batch (decision 69: `e2e/scripts/screenshots.sh --update-snapshots`).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`map visitor ${variant.name}`, () => {
    screenshotTest.use({ variant });

    screenshotTest('public map @M1 @screenshot', async ({ page, visual }) => {
      await page.goto('/map');
      await expect(page.getByTestId('map.page.map')).toHaveAttribute('data-tiles', 'ok');
      await expect(page.getByTestId('map.page.map.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `map-public-${variant.name}`);
    });

    screenshotTest('map loading @M1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/public/map/markers/regions', delayMs: 30_000 });
      await page.goto('/map');
      await expect(page.getByTestId('map.page.map.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `map-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('map error @M1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/public/map/markers/regions', status: 503 });
      await page.goto('/map');
      await expect(page.getByTestId('map.page.map.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `map-error-${variant.name}`);
    });
  });

  screenshotTest.describe(`map member ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('map with store filter and bubble @M1 @screenshot', async ({ page, visual }) => {
      await page.goto('/map');
      await expect(page.getByTestId('map.page.map.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('map.page.storeFilter')).toBeVisible();
      const pin = page.getByTestId('map.page.map').locator('.fs-map-marker[data-kind="fsp"]').first();
      await pin.focus();
      await pin.press('Enter');
      await expect(page.getByTestId('map.bubble.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `map-member-bubble-${variant.name}`);
    });

    screenshotTest('map search result @M1 @screenshot', async ({ page, visual }) => {
      await page.goto('/map');
      await page.getByTestId('map.page.search').fill('8400');
      await page.getByTestId('map.page.searchSubmit').click();
      await expect(page.getByTestId('map.page.map')).toHaveAttribute('data-center', '47.500,8.724');
      await visual.expectScreen(page, `map-search-${variant.name}`);
    });
  });
});
