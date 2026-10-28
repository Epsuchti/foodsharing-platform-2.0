import { injectApiError, seedStore } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Store screens (M3 F2…F6): my stores, region store list, store header + overview, store form — filled, empty,
 * loading, error and forbidden — × light/dark × de/fr. Baselines are generated in Docker in the end batch
 * (decision 69: `e2e/scripts/screenshots.sh --update-snapshots`).
 */
const ZURICH = '00000010-0001-4000-8000-000000000040';
const S01 = seedStore('S-01').id;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`stores ${variant.name}`, () => {
    screenshotTest.describe('jumper', () => {
      screenshotTest.use({ variant, role: 'jumper' });

      screenshotTest('my stores @S1 @screenshot', async ({ page, visual }) => {
        await page.goto('/stores/mine');
        await expect(page.getByTestId('stores.mine.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-mine-${variant.name}`);
      });

      screenshotTest('store page as jumper @S1 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/overview`);
        await expect(page.getByTestId('stores.overview')).toBeVisible();
        await visual.expectScreen(page, `stores-page-jumper-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('region store list @S1 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/stores`);
        await expect(page.getByTestId('stores.region.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-region-${variant.name}`);
      });

      screenshotTest('region store list empty @S1 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/stores`);
        await page.getByTestId('stores.region.search').fill('zzzz');
        await expect(page.getByTestId('stores.region.state')).toHaveAttribute('data-state', 'empty');
        await visual.expectScreen(page, `stores-region-empty-${variant.name}`);
      });

      screenshotTest('store page as member @S1 @S15 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/overview`);
        await expect(page.getByTestId('stores.overview.contact')).toBeVisible();
        await visual.expectScreen(page, `stores-page-member-${variant.name}`);
      });

      screenshotTest('store page loading @S1 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: `/api/stores/${S01}`, delayMs: 30_000 });
        await page.goto(`/stores/${S01}/overview`);
        await expect(page.getByTestId('stores.page.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `stores-page-loading-${variant.name}`, { waitForData: false });
      });

      screenshotTest('store page forbidden @S1 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${seedStore('S-11').id}`);
        await expect(page.getByTestId('stores.page.state')).toHaveAttribute('data-state', 'forbidden');
        await visual.expectScreen(page, `stores-page-forbidden-${variant.name}`);
      });

      screenshotTest('my stores error @S1 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/users/me/stores', status: 500 });
        await page.goto('/stores/mine');
        await expect(page.getByTestId('stores.mine.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `stores-mine-error-${variant.name}`);
      });
    });

    screenshotTest.describe('store manager', () => {
      screenshotTest.use({ variant, role: 'bieb' });

      screenshotTest('store form edit @S2 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/edit`);
        await expect(page.getByTestId('stores.form.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-form-edit-${variant.name}`, { fullPage: true });
      });
    });
  });
});
