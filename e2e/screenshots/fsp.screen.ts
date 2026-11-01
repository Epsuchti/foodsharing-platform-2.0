import { FSPS, injectApiError, regionId } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

const ZURICH = regionId('zuerich-stadt');

/**
 * Food share points (plan-m7 Q6, FS1): region tab with map, detail as follower, inactive proposal as its creator,
 * form, my FSPs, the FS2 cleaning page (list, sign-up, empty, error, forbidden, loading, manager) and the region's
 * "Putzen" section, loading and error × light/dark × de/fr. Map tiles are stubbed by `_setup.ts`. Baselines are
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

    screenshotTest('fsp navigate menu open @FS1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/food-share-points/${FSPS.wiedikon}`);
      await expect(page.getByTestId('foodSharePoints.detail.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('foodSharePoints.detail.wall.state')).toHaveAttribute('data-state', 'success');
      await page.getByTestId('foodSharePoints.detail.navigate').click();
      await expect(page.getByTestId('foodSharePoints.detail.navigate.google')).toBeVisible();
      // viewport only: a full-page capture resizes the viewport, and p-popover closes on resize (desktop)
      await visual.expectScreen(page, `fsp-detail-navigate-${variant.name}`, { fullPage: false });
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

    screenshotTest('fsp cleaning shifts as foodsaver @FS2 @screenshot', async ({ page, visual }) => {
      await page.goto(`/food-share-points/${FSPS.wiedikon}/cleaning`);
      await expect(page.getByTestId('fsp.cleaning.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('fsp.cleaning.team.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `fsp-cleaning-${variant.name}`);
    });

    screenshotTest('fsp cleaning sign-up dialog @FS2 @screenshot', async ({ page, visual }) => {
      await page.goto(`/food-share-points/${FSPS.wiedikon}/cleaning`);
      await expect(page.getByTestId('fsp.cleaning.state')).toHaveAttribute('data-state', 'success');
      await page.getByTestId('fsp.cleaning.slot.free').first().click();
      await expect(page.getByTestId('fsp.cleaning.signupConfirm')).toBeVisible();
      await visual.expectScreen(page, `fsp-cleaning-signup-${variant.name}`, { fullPage: false });
    });

    screenshotTest('fsp cleaning empty @FS2 @screenshot', async ({ page, visual }) => {
      await page.route(`**/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts*`, (route) => route.fulfill({ json: [] }));
      await page.goto(`/food-share-points/${FSPS.wiedikon}/cleaning`);
      await expect(page.getByTestId('fsp.cleaning.state')).toHaveAttribute('data-state', 'empty');
      await visual.expectScreen(page, `fsp-cleaning-empty-${variant.name}`);
    });

    screenshotTest('fsp cleaning error @FS2 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: `/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts`, status: 503 });
      await page.goto(`/food-share-points/${FSPS.wiedikon}/cleaning`);
      await expect(page.getByTestId('fsp.cleaning.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `fsp-cleaning-error-${variant.name}`);
    });

    screenshotTest('fsp cleaning forbidden @FS2 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: `/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts`, status: 403 });
      await page.goto(`/food-share-points/${FSPS.wiedikon}/cleaning`);
      await expect(page.getByTestId('fsp.cleaning.state')).toHaveAttribute('data-state', 'forbidden');
      await visual.expectScreen(page, `fsp-cleaning-forbidden-${variant.name}`);
    });

    screenshotTest('fsp cleaning loading @FS2 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: `/api/food-share-points/${FSPS.wiedikon}/cleaning/shifts`, delayMs: 30_000 });
      await page.goto(`/food-share-points/${FSPS.wiedikon}/cleaning`);
      await expect(page.getByTestId('fsp.cleaning.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `fsp-cleaning-loading-${variant.name}`, { waitForData: false });
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

    // bot is ambassador of zuerich-stadt: the "Putzen" section of the region automation (FS2)
    screenshotTest('region cleaning fairteiler section @FS2 @AU1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/automation/cleaning-fsps`);
      await expect(page.getByTestId('automation.cleaningFsps.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `fsp-cleaning-region-${variant.name}`);
    });
  });

  screenshotTest.describe(`food share points cleaning manager ${variant.name}`, () => {
    // bieb manages Wiedikon: editors, split pill with ✓ for guide's unconfirmed sign-up
    screenshotTest.use({ variant, role: 'bieb' });

    screenshotTest('fsp cleaning as manager @FS2 @screenshot', async ({ page, visual }) => {
      await page.goto(`/food-share-points/${FSPS.wiedikon}/cleaning`);
      await expect(page.getByTestId('fsp.cleaning.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('fsp.cleaning.toolbar')).toBeVisible();
      await expect(page.getByTestId('fsp.cleaning.slot.confirm').first()).toBeVisible();
      await visual.expectScreen(page, `fsp-cleaning-manager-${variant.name}`);
    });
  });
});
