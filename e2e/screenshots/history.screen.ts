import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * History screens (plan-m1 T7, D10.3): "my activity" filled, filtered to nothing, loading, error and forbidden, plus the
 * orga view with the CSV export — × light/dark × de/fr on the desktop and mobile screenshot projects.
 * Baselines are generated in Docker (`e2e/scripts/screenshots.sh --update-snapshots`).
 */
const LIST = 'history.myActivity.list';
const MY_ACTIVITY = '/api/history/me';

// Every test logs in (one more LOGIN entry each): start each one from the seed so the list is reproducible.
screenshotTest.use({ seedReset: 'test' });

forEachVariant((variant) => {
  screenshotTest.describe(`history ${variant.name}`, () => {
    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('my activity @D10.3 @screenshot', async ({ page, visual }) => {
        await page.goto('/me/activity');
        await expect(page.getByTestId(LIST)).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `my-activity-${variant.name}`);
      });

      screenshotTest('my activity with details open @D10.3 @screenshot', async ({ page, visual }) => {
        await page.goto('/me/activity?preset=changes');
        await expect(page.getByTestId(LIST)).toHaveAttribute('data-state', 'success');
        // the newest entry with a recorded change (field: old → new); seed data of later milestones shifts the list
        const field = page
          .getByTestId('history.myActivity.entry')
          .filter({ has: page.getByTestId('history.myActivity.entry.change') })
          .first();
        await field.getByTestId('history.myActivity.entry.details').click();
        await expect(field.getByTestId('history.myActivity.entry.detailsPanel')).toBeVisible();
        await visual.expectScreen(page, `my-activity-details-${variant.name}`);
      });

      screenshotTest('my activity empty @D10.3 @screenshot', async ({ page, visual }) => {
        await page.route(
          (url) => url.pathname === MY_ACTIVITY,
          (route) =>
            route.fulfill({
              status: 200,
              contentType: 'application/json',
              body: JSON.stringify({ items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } }),
            }),
        );
        await page.goto('/me/activity');
        await expect(page.getByTestId(LIST)).toHaveAttribute('data-state', 'empty');
        await visual.expectScreen(page, `my-activity-empty-${variant.name}`);
      });

      screenshotTest('my activity loading @D10.3 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: MY_ACTIVITY, delayMs: 30_000 });
        await page.goto('/me/activity');
        await expect(page.getByTestId(LIST)).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `my-activity-loading-${variant.name}`, { waitForData: false });
      });

      screenshotTest('my activity error @D10.3 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: MY_ACTIVITY, status: 500 });
        await page.goto('/me/activity');
        await expect(page.getByTestId(LIST)).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `my-activity-error-${variant.name}`);
      });

      screenshotTest('my activity forbidden @D10.3 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: MY_ACTIVITY, status: 403, code: 'HISTORY_SUBJECT_NOT_READABLE' });
        await page.goto('/me/activity');
        await expect(page.getByTestId(LIST)).toHaveAttribute('data-state', 'forbidden');
        await visual.expectScreen(page, `my-activity-forbidden-${variant.name}`);
      });
    });

    screenshotTest.describe('orga', () => {
      screenshotTest.use({ variant, role: 'orga' });

      screenshotTest('my activity with export @D10.3 @screenshot', async ({ page, visual }) => {
        await page.goto('/me/activity');
        await expect(page.getByTestId(LIST)).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('history.myActivity.export')).toBeVisible();
        await visual.expectScreen(page, `my-activity-orga-${variant.name}`);
      });
    });
  });
});
