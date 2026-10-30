import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Dashboard (plan-m7 Q7, A19): activity feed with filter chips, nearby baskets card, the card without a profile
 * address, feed loading and error × light/dark × de/fr. Baselines are generated in Docker in the end batch
 * (decision 69: `e2e/scripts/screenshots.sh --update-snapshots`).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`dashboard ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('dashboard with activity feed and nearby baskets @A19 @screenshot', async ({ page, visual }) => {
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.activity.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('dashboard.baskets.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `dashboard-feed-${variant.name}`);
    });

    screenshotTest('activity feed card @A19 @screenshot', async ({ page, visual }) => {
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.activity.state')).toHaveAttribute('data-state', 'success');
      await visual.expectElement(page.getByTestId('dashboard.activity'), `dashboard-activity-${variant.name}`);
    });

    screenshotTest('activity feed loading @A19 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/activity', delayMs: 30_000 });
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.activity.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectElement(page.getByTestId('dashboard.activity'), `dashboard-activity-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('activity feed error @A19 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/activity', status: 503 });
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.activity.state')).toHaveAttribute('data-state', 'error');
      await visual.expectElement(page.getByTestId('dashboard.activity'), `dashboard-activity-error-${variant.name}`);
    });
  });

  screenshotTest.describe(`dashboard without address ${variant.name}`, () => {
    // profile@local has no address in the seed
    screenshotTest.use({ variant, role: 'profile' });

    screenshotTest('nearby baskets without an address @A19 @screenshot', async ({ page, visual }) => {
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.baskets.noPosition')).toBeVisible();
      await visual.expectElement(page.getByTestId('dashboard.baskets'), `dashboard-baskets-no-address-${variant.name}`);
    });
  });
});
