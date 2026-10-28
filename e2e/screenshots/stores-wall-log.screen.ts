import { injectApiError, seedStore } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Store screens of M3 F5: wall (posts + milestones, composer), store log, statistics (month chart, per member),
 * store pickup history, my past pickups, delete-store dialog, empty and error states × light/dark × de/fr.
 * Baselines in the end batch (decision 69).
 */
const S01 = seedStore('S-01').id;
const S17 = seedStore('S-17').id;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`stores F5 ${variant.name}`, () => {
    screenshotTest.describe('store manager', () => {
      screenshotTest.use({ variant, role: 'bieb' });

      screenshotTest('store wall @S11 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/wall`);
        await expect(page.getByTestId('stores.wall.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-wall-${variant.name}`, { fullPage: true });
      });

      screenshotTest('store log @S12 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/log`);
        await expect(page.getByTestId('stores.log.list')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-log-${variant.name}`);
      });

      screenshotTest('store statistics @S14 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/statistics`);
        await expect(page.getByTestId('stores.statistics.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-statistics-${variant.name}`, { fullPage: true });
      });

      screenshotTest('store pickup history @PU6 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups/history`);
        await expect(page.getByTestId('stores.pickupHistory.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-pickup-history-${variant.name}`);
      });

      screenshotTest('store wall error @S11 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: new RegExp(`/api/stores/${S01}/wall`), status: 500 });
        await page.goto(`/stores/${S01}/wall`);
        await expect(page.getByTestId('stores.wall.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `stores-wall-error-${variant.name}`);
      });
    });

    screenshotTest.describe('orga', () => {
      screenshotTest.use({ variant, role: 'orga' });

      screenshotTest('delete store dialog @S13 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S17}/edit`);
        await expect(page.getByTestId('stores.form.state')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('stores.delete.open').click();
        await expect(page.getByTestId('stores.delete.dialog.message')).toBeVisible();
        await visual.expectScreen(page, `stores-delete-dialog-${variant.name}`);
      });

      screenshotTest('empty store wall @S11 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S17}/wall`);
        await expect(page.getByTestId('stores.wall.state')).toHaveAttribute('data-state', 'empty');
        await visual.expectScreen(page, `stores-wall-empty-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('my past pickups @PU6 @screenshot', async ({ page, visual }) => {
        await page.goto('/me/pickups/history');
        await expect(page.getByTestId('pickups.past.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-past-${variant.name}`);
      });
    });
  });
});
