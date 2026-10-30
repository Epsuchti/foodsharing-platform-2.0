import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Global search (plan-m7 Q12, C5): header combobox with instant index and server results, the /search page with
 * results, empty, loading and error. Baselines are generated in the end batch (decision 69:
 * `e2e/scripts/screenshots.sh --update-snapshots`); light/German only (decision 109).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`search ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('search header combobox @C5 @screenshot', async ({ page, visual }) => {
      // the header box shows from 48rem (phones reach /search through the drawer): tablet width on every project
      await page.setViewportSize({ width: 1024, height: 768 });
      await page.goto('/dashboard');
      await page.getByTestId('search.global.input').fill('Wiedikon');
      await expect(page.getByTestId('search.global.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `search-header-${variant.name}`, { fullPage: false });
    });

    screenshotTest('search page results @C5 @screenshot', async ({ page, visual }) => {
      await page.goto('/search?q=Wiedikon');
      await expect(page.getByTestId('search.page.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `search-page-${variant.name}`);
    });

    screenshotTest('search page empty @C5 @screenshot', async ({ page, visual }) => {
      await page.goto('/search?q=Zzzyx');
      await expect(page.getByTestId('search.page.state')).toHaveAttribute('data-state', 'empty');
      await visual.expectScreen(page, `search-empty-${variant.name}`);
    });

    screenshotTest('search page loading @C5 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/search', delayMs: 30_000 });
      await page.goto('/search?q=Wiedikon');
      await expect(page.getByTestId('search.page.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `search-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('search page error @C5 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/search', status: 503 });
      await page.goto('/search?q=Wiedikon');
      await expect(page.getByTestId('search.page.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `search-error-${variant.name}`);
    });
  });
});
