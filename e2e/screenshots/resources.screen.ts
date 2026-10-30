import { injectApiError, regionId } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

const ZURICH = regionId('zuerich-stadt');
const KANTON = regionId('kanton-zuerich');

/**
 * Resources (plan-m7 Q12, C6): region tab (commons + members), form dialog, my resources, favourites, the forbidden
 * state of a canton, loading and error. Baselines are generated in the end batch (decision 69); light/German only
 * (decision 109).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`resources ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('resources region tab @C6 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/resources`);
      await expect(page.getByTestId('resources.region.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `resources-region-${variant.name}`);
    });

    screenshotTest('resources form dialog @C6 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/resources`);
      await page.getByTestId('resources.region.add').click();
      await expect(page.getByTestId('resources.form.categoriesState')).toHaveAttribute('data-state', 'success');
      await page.getByTestId('resources.form.name').fill('Dörrex');
      await visual.expectScreen(page, `resources-form-${variant.name}`, { fullPage: false });
    });

    screenshotTest('resources mine @C6 @screenshot', async ({ page, visual }) => {
      await page.goto('/resources/mine');
      await expect(page.getByTestId('resources.mine.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `resources-mine-${variant.name}`);
    });

    screenshotTest('resources favourites @C6 @screenshot', async ({ page, visual }) => {
      await page.goto('/resources/favourites');
      await expect(page.getByTestId('resources.favourites.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `resources-favourites-${variant.name}`);
    });

    screenshotTest('resources not available in a canton @C6 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${KANTON}/resources`);
      await expect(page.getByTestId('resources.region.state')).toHaveAttribute('data-state', 'forbidden');
      await visual.expectScreen(page, `resources-forbidden-${variant.name}`);
    });

    screenshotTest('resources loading @C6 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: `/api/regions/${ZURICH}/resources`, delayMs: 30_000 });
      await page.goto(`/regions/${ZURICH}/resources`);
      await expect(page.getByTestId('resources.region.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `resources-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('resources error @C6 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/resources/mine', status: 503 });
      await page.goto('/resources/mine');
      await expect(page.getByTestId('resources.mine.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `resources-error-${variant.name}`);
    });
  });
});
