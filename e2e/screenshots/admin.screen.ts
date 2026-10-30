import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Admin settings, uploads and admins (plan-m7 Q13, C10–C14, AD1) as ORGA: feature toggles, configuration with the
 * history drawer, donation settings, email blocklist, IP blocks, uploads, admins, loading and error × light/dark ×
 * de/fr. Baselines are generated in Docker in the end batch (decision 69: `e2e/scripts/screenshots.sh
 * --update-snapshots`).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`admin settings ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'orga' });

    screenshotTest('feature toggles @C11 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/feature-toggles');
      await expect(page.getByTestId('adminSettings.toggles.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `admin-toggles-${variant.name}`);
    });

    screenshotTest('feature toggle disable confirm @C11 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/feature-toggles');
      await expect(page.getByTestId('adminSettings.toggles.state')).toHaveAttribute('data-state', 'success');
      await page.locator('[data-testid="adminSettings.toggles.row"][data-key="BASKETS"] [data-testid="adminSettings.toggles.switch"]').click();
      await expect(page.locator('.p-confirmdialog-accept-button')).toBeVisible();
      await visual.expectScreen(page, `admin-toggles-confirm-${variant.name}`);
    });

    screenshotTest('configuration with validation error @C14 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/configuration');
      await expect(page.getByTestId('adminSettings.configuration.state')).toHaveAttribute('data-state', 'success');
      const row = page.locator('[data-testid="adminSettings.configuration.row"][data-key="support.email"]');
      await row.getByTestId('adminSettings.configuration.input').fill('keine-adresse');
      await row.getByTestId('adminSettings.configuration.save').click();
      await expect(row.getByTestId('adminSettings.configuration.error')).toBeVisible();
      await visual.expectScreen(page, `admin-configuration-${variant.name}`);
    });

    screenshotTest('configuration history drawer @C14 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/configuration');
      await expect(page.getByTestId('adminSettings.configuration.state')).toHaveAttribute('data-state', 'success');
      await page.getByTestId('adminSettings.configuration.historyButton').click();
      await expect(page.getByTestId('adminSettings.configuration.history.list')).toHaveAttribute('data-state', /success|empty/);
      await visual.expectScreen(page, `admin-configuration-history-${variant.name}`);
    });

    screenshotTest('donation settings @C14 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/donations');
      await expect(page.getByTestId('adminSettings.donations.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `admin-donations-${variant.name}`);
    });

    screenshotTest('email blocklist @C12 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/email-blocklist');
      await expect(page.getByTestId('adminSettings.blocklist.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `admin-blocklist-${variant.name}`);
    });

    screenshotTest('ip blocks empty @C12 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/ip-blocks');
      await expect(page.getByTestId('adminSettings.ipBlocks.state')).toHaveAttribute('data-state', /success|empty/);
      await visual.expectScreen(page, `admin-ip-blocks-${variant.name}`);
    });

    screenshotTest('uploads @C10 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/uploads');
      await expect(page.getByTestId('adminSettings.uploads.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `admin-uploads-${variant.name}`);
    });

    screenshotTest('admins @AD1 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/admins');
      await expect(page.getByTestId('admin.admins.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `admin-admins-${variant.name}`);
    });

    screenshotTest('admins loading @AD1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/admin/admins', delayMs: 30_000 });
      await page.goto('/admin/admins');
      await expect(page.getByTestId('admin.admins.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `admin-admins-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('uploads error @C10 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/admin/uploads', status: 503 });
      await page.goto('/admin/uploads');
      await expect(page.getByTestId('adminSettings.uploads.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `admin-uploads-error-${variant.name}`);
    });
  });
});
