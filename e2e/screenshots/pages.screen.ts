import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Shell pages of M1 (plan §18.4): landing, dashboard, forbidden, not found and the offline state of the shell,
 * × light/dark × de/fr on desktop and mobile.
 */

screenshotTest.use({ seedReset: 'off' });

forEachVariant((variant) => {
  screenshotTest.describe(variant.name, () => {
    screenshotTest.use({ variant });

    screenshotTest(`landing page ${variant.name} @D4.3 @screenshot`, async ({ page, visual }) => {
      await page.goto('/');
      await expect(page.getByTestId('public.landing.hero')).toBeVisible();
      await visual.expectScreen(page, `landing-${variant.name}`);
    });

    screenshotTest(`not found page ${variant.name} @D4.3 @screenshot`, async ({ page, visual }) => {
      await page.goto('/some/deep/link');
      await expect(page.locator('main h1')).toBeVisible();
      await visual.expectScreen(page, `not-found-${variant.name}`);
    });

    screenshotTest(`forbidden page ${variant.name} @D4.3 @screenshot`, async ({ page, visual }) => {
      await page.goto('/forbidden');
      await expect(page.locator('main h1')).toBeVisible();
      await visual.expectScreen(page, `forbidden-${variant.name}`);
    });

    screenshotTest.describe('logged in', () => {
      screenshotTest.use({ role: 'foodsaver' });

      // The account settings win over the device settings when logged in: store the variant on the account.
      screenshotTest.beforeEach(async ({ page, apiOf }) => {
        const response = await apiOf(page).patch('/api/users/me/settings', {
          language: variant.lang,
          theme: variant.theme.toUpperCase(),
        });
        expect(response.ok(), `PATCH /api/users/me/settings → ${response.status()}`).toBe(true);
      });

      screenshotTest(`dashboard ${variant.name} @D4.3 @screenshot`, async ({ page, visual }) => {
        await page.goto('/dashboard');
        await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
        await visual.expectScreen(page, `dashboard-${variant.name}`);
      });

      screenshotTest(`offline shell ${variant.name} @AU12 @screenshot`, async ({ page, context, visual }) => {
        await page.goto('/dashboard');
        await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
        await context.setOffline(true);
        await expect(page.getByTestId('shell.offlineBanner')).toBeVisible();
        await visual.expectScreen(page, `offline-${variant.name}`);
        await context.setOffline(false);
      });
    });
  });
});
