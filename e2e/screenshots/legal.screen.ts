import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Legal screens: EA4 consent page with phone prompt (pending privacy policy of newbie@local), Ve1 public document
 * page and the ORGA versions page — × light/dark × de/fr.
 */

forEachVariant((variant) => {
  screenshotTest.describe(`legal ${variant.name}`, () => {
    screenshotTest.describe('newbie', () => {
      screenshotTest.use({ variant, role: 'newbie' });

      screenshotTest('consent page @EA4 @screenshot', async ({ page, visual }) => {
        await page.goto('/dashboard');
        await expect(page).toHaveURL(/\/consent\?/);
        await expect(page.getByTestId('legal.consent.documents')).toHaveAttribute('data-state', 'success');
        await expect(page.getByTestId('legal.consent.phone')).toBeVisible();
        await visual.expectScreen(page, `consent-${variant.name}`);
      });
    });

    screenshotTest.describe('guest', () => {
      screenshotTest.use({ variant, role: undefined });

      screenshotTest('legal document page @Ve1 @screenshot', async ({ page, visual }) => {
        await page.goto('/legal/privacy-policy');
        await expect(page.getByTestId('legal.document.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `legal-document-${variant.name}`);
      });
    });

    screenshotTest.describe('orga', () => {
      screenshotTest.use({ variant, role: 'orga' });

      screenshotTest('legal versions page @Ve1 @screenshot', async ({ page, visual }) => {
        await page.goto('/admin/legal');
        await expect(page.getByTestId('legal.admin.versions')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('legal.admin.version.input').fill('2026-11');
        await page.getByTestId('legal.admin.reconsent').locator('input').check();
        await visual.expectScreen(page, `legal-versions-${variant.name}`);
      });
    });
  });
});
