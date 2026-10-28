import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * M3 F6 screens: chain list, chain page (KAM view with edit dialog), categories admin with merge dialog, pickup
 * reminder setting, error state × light/dark × de/fr. Baselines in the end batch (decision 69).
 */
const COOP = '00000020-0003-4000-8000-000000000001';

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`store chains ${variant.name}`, () => {
    screenshotTest.describe('KAM', () => {
      screenshotTest.use({ variant, role: 'bieb' });

      screenshotTest('chain list @S3 @screenshot', async ({ page, visual }) => {
        await page.goto('/store-chains');
        await expect(page.getByTestId('storeChains.list.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `store-chains-list-${variant.name}`);
      });

      screenshotTest('chain page and edit dialog @S3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/store-chains/${COOP}`);
        await expect(page.getByTestId('storeChains.chain.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `store-chains-chain-${variant.name}`, { fullPage: true });
        await page.getByTestId('storeChains.chain.edit').click();
        await expect(page.getByTestId('storeChains.edit.name')).toBeVisible();
        await visual.expectScreen(page, `store-chains-edit-${variant.name}`);
      });

      screenshotTest('chain list error @S3 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: /\/api\/store-chains(\?|$)/, status: 500 });
        await page.goto('/store-chains');
        await expect(page.getByTestId('storeChains.list.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `store-chains-list-error-${variant.name}`);
      });

      screenshotTest('pickup reminder setting @PU9 @screenshot', async ({ page, visual }) => {
        await page.goto('/account/settings');
        await expect(page.getByTestId('pickups.reminders.state')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('pickups.reminders.card').scrollIntoViewIfNeeded();
        await visual.expectScreen(page, `pickups-reminders-${variant.name}`);
      });
    });

    screenshotTest.describe('orga', () => {
      screenshotTest.use({ variant, role: 'orga' });

      screenshotTest('categories admin and merge dialog @S4 @screenshot', async ({ page, visual }) => {
        await page.goto('/admin/store-categories');
        await expect(page.getByTestId('storeCategories.category.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `store-categories-${variant.name}`, { fullPage: true });
        await page.getByTestId('storeCategories.category.merge').first().click();
        await expect(page.getByTestId('storeCategories.mergeHint')).toBeVisible();
        await visual.expectScreen(page, `store-categories-merge-${variant.name}`);
      });
    });
  });
});
