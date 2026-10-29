import { FSPS, injectApiError, regionId } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

const ZURICH = regionId('zuerich-stadt');

/**
 * Food share points (plan-m7 Q6, FS1): region tab with map, detail as follower, inactive proposal as its creator,
 * form, my FSPs, loading and error × light/dark × de/fr. Map tiles are stubbed by `_setup.ts`. Baselines are
 * generated in Docker in the end batch (decision 69: `e2e/scripts/screenshots.sh --update-snapshots`).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`food share points ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('fsp region tab with map @FS1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/food-share-points`);
      await expect(page.getByTestId('foodSharePoints.region.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `fsp-region-${variant.name}`);
    });

    screenshotTest('fsp detail as follower @FS1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/food-share-points/${FSPS.wiedikon}`);
      await expect(page.getByTestId('foodSharePoints.detail.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('foodSharePoints.detail.wall.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `fsp-detail-${variant.name}`);
    });

    screenshotTest('fsp inactive proposal as creator @FS1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/food-share-points/${FSPS.altstetten}`);
      await expect(page.getByTestId('foodSharePoints.detail.inactive')).toBeVisible();
      await visual.expectScreen(page, `fsp-inactive-${variant.name}`);
    });

    screenshotTest('fsp form @FS1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/food-share-points/new?regionId=${ZURICH}`);
      await expect(page.getByTestId('foodSharePoints.form.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `fsp-form-${variant.name}`);
    });

    screenshotTest('my fsps @FS1 @screenshot', async ({ page, visual }) => {
      await page.goto('/food-share-points/mine');
      await expect(page.getByTestId('foodSharePoints.mine.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `fsp-mine-${variant.name}`);
    });

    screenshotTest('fsp region tab loading @FS1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: `/api/regions/${ZURICH}/food-share-points`, delayMs: 30_000 });
      await page.goto(`/regions/${ZURICH}/food-share-points`);
      await expect(page.getByTestId('foodSharePoints.region.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `fsp-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('fsp detail error @FS1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: `/api/food-share-points/${FSPS.wiedikon}`, status: 503 });
      await page.goto(`/food-share-points/${FSPS.wiedikon}`);
      await expect(page.getByTestId('foodSharePoints.detail.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `fsp-error-${variant.name}`);
    });
  });

  screenshotTest.describe(`food share points admin ${variant.name}`, () => {
    // bot administers the Zürich FSP group: activate button and manager editing
    screenshotTest.use({ variant, role: 'bot' });

    screenshotTest('fsp inactive proposal as FSP admin @FS1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/food-share-points/${FSPS.altstetten}`);
      await expect(page.getByTestId('foodSharePoints.detail.activate')).toBeVisible();
      await page.getByTestId('foodSharePoints.managers.edit').click();
      await expect(page.getByTestId('foodSharePoints.managers.picker.search')).toBeVisible();
      await visual.expectScreen(page, `fsp-admin-${variant.name}`);
    });
  });
});
