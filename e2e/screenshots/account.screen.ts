import { acceptPendingConsents, injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Account settings (plan-m7 Q3, A3/A5/A9/A20): settings page with the pending email change of profile@local, jumper's
 * temporary sleep mode card, the photo crop dialog, the email confirm link page and the settings error state ×
 * light/dark × de/fr. Baselines are generated in Docker in the end batch (decision 69:
 * `e2e/scripts/screenshots.sh --update-snapshots`).
 */
screenshotTest.use({ seedReset: 'test' });

forEachVariant((variant) => {
  screenshotTest.describe(`account ${variant.name}`, () => {
    screenshotTest.describe('profile', () => {
      screenshotTest.use({ variant, role: 'profile' });

      screenshotTest('settings with pending email change @A3 @A20 @screenshot', async ({ page, apiOf, visual }) => {
        await acceptPendingConsents(apiOf(page));
        await page.goto('/account/settings');
        await expect(page.getByTestId('account.settings.email.pending')).toBeVisible();
        await visual.expectScreen(page, `account-settings-email-pending-${variant.name}`);
      });

      screenshotTest('email confirm link with a bad token @A3 @screenshot', async ({ page, apiOf, visual }) => {
        await acceptPendingConsents(apiOf(page));
        await page.goto('/account/email/confirm?token=not-a-token');
        await expect(page.getByTestId('account.email.confirm.tokenError')).toBeVisible();
        await visual.expectScreen(page, `account-email-confirm-invalid-${variant.name}`);
      });
    });

    screenshotTest.describe('jumper', () => {
      screenshotTest.use({ variant, role: 'jumper' });

      screenshotTest('sleep mode card, temporary @A9 @screenshot', async ({ page, apiOf, visual }) => {
        await acceptPendingConsents(apiOf(page));
        await page.goto('/account/settings');
        await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'TEMPORARY');
        await visual.expectElement(page.getByTestId('account.settings.sleep'), `account-sleep-temporary-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('photo crop dialog @A5 @screenshot', async ({ page, visual }) => {
        await page.goto('/account/settings');
        await page.getByTestId('account.settings.photo.change').click();
        const png = await page.evaluate(() => {
          const canvas = document.createElement('canvas');
          canvas.width = 900;
          canvas.height = 600;
          const context = canvas.getContext('2d')!;
          context.fillStyle = '#25834a';
          context.fillRect(0, 0, 900, 600);
          context.fillStyle = '#f6d79b';
          context.beginPath();
          context.arc(450, 300, 180, 0, Math.PI * 2);
          context.fill();
          return canvas.toDataURL('image/png').split(',')[1];
        });
        await page
          .getByTestId('account.settings.photo.crop.input')
          .setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: Buffer.from(png, 'base64') });
        await expect(page.getByTestId('account.settings.photo.crop.stage')).toBeVisible();
        await visual.expectElement(page.locator('.p-dialog'), `account-photo-crop-${variant.name}`);
      });

      screenshotTest('settings error @A5 @A9 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/users/me', method: 'GET', status: 503 });
        await page.goto('/account/settings');
        await expect(page.getByTestId('account.settings.photo.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `account-settings-error-${variant.name}`);
      });
    });
  });
});
