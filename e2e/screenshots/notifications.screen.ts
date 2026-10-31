import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Notification screens (M4 F1, N3): header bell panel, /bells page (all, unread, empty), error states — × light/dark
 * × de/fr, desktop and mobile project. Baselines are generated in Docker (`e2e/scripts/screenshots.sh`, decision 69).
 */
screenshotTest.use({ seedReset: 'test' });

forEachVariant((variant) => {
  screenshotTest.describe(`notifications ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('bell panel @N3 @screenshot', async ({ page, visual }) => {
      await page.goto('/dashboard');
      const button = page.getByTestId('bells.button');
      const menu = page.getByTestId('nav.tab.menu');
      // the side menu's bell renders after the first page; wait for whichever entry this layout has
      await expect(button.or(menu).filter({ visible: true }).first()).toBeVisible();
      if (await button.isVisible()) {
        await button.click();
        await expect(page.getByTestId('bells.panel.state')).toHaveAttribute('data-state', 'success');
      } else {
        // mobile: the badge sits on "Menu" and the drawer links the bells page
        await page.getByTestId('nav.tab.menu').click();
        await expect(page.getByTestId('nav.drawer.bells.badge')).toBeVisible();
      }
      await visual.expectScreen(page, `bells-panel-${variant.name}`);
    });

    screenshotTest('bells page, unread filter and empty @N3 @screenshot', async ({ page, visual }) => {
      await page.goto('/bells');
      const state = page.getByTestId('bells.page.state');
      await expect(state).toHaveAttribute('data-state', 'success');
      // the unread count (badge, "mark all read") loads after the first page has shown; until then the inner button
      // is disabled and a click on the p-button host does nothing
      const markAllRead = page.getByTestId('bells.page.markAllRead').locator('button');
      await expect(markAllRead).toBeEnabled();
      await visual.expectScreen(page, `bells-page-${variant.name}`);
      await page.getByTestId('bells.page.filter').locator('.p-togglebutton').nth(1).click();
      await expect(state).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `bells-page-unread-${variant.name}`);
      await markAllRead.click();
      await expect(state).toHaveAttribute('data-state', 'empty');
      await visual.expectScreen(page, `bells-page-empty-${variant.name}`);
    });

    screenshotTest('bells page error @N3 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: /\/api\/bells(\?|$)/, method: 'GET', status: 500 });
      await page.goto('/bells');
      await expect(page.getByTestId('bells.page.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `bells-page-error-${variant.name}`);
    });
  });
});
