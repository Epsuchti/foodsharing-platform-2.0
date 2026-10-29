import { BASKETS, injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Food baskets (plan-m7 Q5, B1/B2): nearby list + map, detail as requester and as owner with requests, offer form,
 * my baskets, loading and error × light/dark × de/fr. Map tiles are stubbed by `_setup.ts`. Baselines are generated
 * in Docker in the end batch (decision 69: `e2e/scripts/screenshots.sh --update-snapshots`).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`baskets ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('baskets nearby list and map @B2 @screenshot', async ({ page, visual }) => {
      await page.goto('/baskets');
      await expect(page.getByTestId('baskets.nearby.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `baskets-nearby-${variant.name}`);
    });

    screenshotTest('basket detail as requester @B2 @screenshot', async ({ page, visual }) => {
      await page.goto(`/baskets/${BASKETS.joghurt}`);
      await expect(page.getByTestId('baskets.detail.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('baskets.detail.myRequest')).toBeVisible();
      await visual.expectScreen(page, `baskets-detail-${variant.name}`);
    });

    screenshotTest('basket offer form @B1 @screenshot', async ({ page, visual }) => {
      await page.goto('/baskets/new');
      await expect(page.getByTestId('baskets.form.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `baskets-form-${variant.name}`);
    });

    screenshotTest('my baskets @B1 @screenshot', async ({ page, visual }) => {
      await page.goto('/baskets/mine');
      await expect(page.getByTestId('baskets.mine.offeredState')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('baskets.mine.requestedState')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `baskets-mine-${variant.name}`);
    });

    screenshotTest('baskets loading @B2 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/baskets/nearby', delayMs: 30_000 });
      await page.goto('/baskets');
      await expect(page.getByTestId('baskets.nearby.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `baskets-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('baskets error @B2 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/baskets/nearby', status: 503 });
      await page.goto('/baskets');
      await expect(page.getByTestId('baskets.nearby.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `baskets-error-${variant.name}`);
    });
  });

  screenshotTest.describe(`baskets owner ${variant.name}`, () => {
    // foodsaver owns basket 01, which guide requested (seed)
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('basket detail as owner with requests @B2 @screenshot', async ({ page, visual }) => {
      await page.goto(`/baskets/${BASKETS.brot}`);
      await expect(page.getByTestId('baskets.detail.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('baskets.detail.requestRow').first()).toBeVisible();
      await visual.expectScreen(page, `baskets-owner-${variant.name}`);
    });
  });
});
