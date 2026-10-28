import { expect, forEachVariant, screenshotTest } from './_setup';

/** EA4 consent page (pending privacy policy of newbie@local) × light/dark × de/fr; M2 T20 adds the document pages. */

forEachVariant((variant) => {
  screenshotTest.describe(`legal ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'newbie' });

    screenshotTest('consent page @EA4 @screenshot', async ({ page, visual }) => {
      await page.goto('/dashboard');
      await expect(page).toHaveURL(/\/consent\?/);
      await expect(page.getByTestId('legal.consent.documents')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `consent-${variant.name}`);
    });
  });
});
