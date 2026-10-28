import { injectApiError } from '../fixtures';
import { USERS, ZH } from '../tests/verification/_verification';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Verification and passport screens (M2 T23): queue (ready, all unverified, verify dialog, error), user verification
 * history, own passport (valid, expired, locked, wallets not configured, error), region passports (requests, job done)
 * — × light/dark × de/fr. Baselines are generated in Docker (`e2e/scripts/screenshots.sh`, decision 69).
 */
screenshotTest.use({ seedReset: 'test' });

forEachVariant((variant) => {
  screenshotTest.describe(`verification ${variant.name}`, () => {
    screenshotTest.describe('ambassador', () => {
      screenshotTest.use({ variant, role: 'bot' });

      screenshotTest('verification queue and verify dialog @Ve2 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZH}/verification`);
        await expect(page.getByTestId('verification.queue.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `verification-queue-${variant.name}`);
        await page.getByTestId('verification.queue.filter').locator('.p-togglebutton').nth(1).click();
        await expect(page.getByTestId('verification.queue.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `verification-queue-all-${variant.name}`);
        await page.locator(`[data-testid="verification.queue.card"][data-user="${USERS.pending}"]`).getByTestId('verification.queue.verify').click();
        await expect(page.getByTestId('verification.verifyDialog.message')).toBeVisible();
        await visual.expectScreen(page, `verification-verify-dialog-${variant.name}`);
      });

      screenshotTest('verification queue error @Ve2 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: `**/api/regions/${ZH}/verification/queue**`, status: 500, code: 'INTERNAL_ERROR' });
        await page.goto(`/regions/${ZH}/verification`);
        await expect(page.getByTestId('verification.queue.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `verification-queue-error-${variant.name}`);
      });

      screenshotTest('user verification history @A8 @screenshot', async ({ page, visual }) => {
        await page.goto(`/users/${USERS.foodsaver}/verification-history`);
        await expect(page.getByTestId('verification.history.userState')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `verification-history-${variant.name}`);
      });

      screenshotTest('region passports with requests and finished job @Ve3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZH}/passports`);
        await expect(page.getByTestId('verification.passports.membersState')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `verification-passports-${variant.name}`);
        await page.getByTestId('verification.passports.selectRequests').click();
        await page.getByTestId('verification.passports.create').click();
        await expect(page.getByTestId('verification.passports.job')).toHaveAttribute('data-status', 'DONE', { timeout: 30_000 });
        await visual.expectScreen(page, `verification-passports-done-${variant.name}`);
      });
    });

    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('my passport valid with wallets not configured @Ve3 @screenshot', async ({ page, visual }) => {
        await page.goto('/me/passport');
        await expect(page.getByTestId('verification.passport.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `passport-valid-${variant.name}`);
      });

      screenshotTest('my passport error @Ve3 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '**/api/users/me/passport', status: 500, code: 'INTERNAL_ERROR' });
        await page.goto('/me/passport');
        await expect(page.getByTestId('verification.passport.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `passport-error-${variant.name}`);
      });
    });

    screenshotTest.describe('sleepy', () => {
      screenshotTest.use({ variant, role: 'sleepy' });

      screenshotTest('my passport expired @Ve3 @screenshot', async ({ page, visual }) => {
        await page.goto('/me/passport');
        await expect(page.getByTestId('verification.passport.status')).toHaveAttribute('data-valid', 'false');
        await visual.expectScreen(page, `passport-expired-${variant.name}`);
      });
    });

    screenshotTest.describe('newcomer', () => {
      screenshotTest.use({ variant, role: 'einab1' });

      screenshotTest('my passport locked @Ve3 @screenshot', async ({ page, visual }) => {
        await page.goto('/me/passport');
        await expect(page.getByTestId('verification.passport.locked')).toBeVisible();
        await visual.expectScreen(page, `passport-locked-${variant.name}`);
      });
    });
  });
});
