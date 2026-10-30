import { REPORTS, injectApiError, regionId } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

const ZURICH = regionId('zuerich-stadt');
const JUMPER = '00000001-0001-4000-8000-000000000007';
/** Seed report 03: IN_PROGRESS with wall posts by reports@local. */
const IN_PROGRESS = '00000050-0001-4000-8000-000000000003';

/**
 * Reports, report log and mediation (plan-m7 Q8, G1/G2/D10.3): region reports tab, report detail with handling
 * form and wall, report log, reports block on a profile, report dialog, mediation page, loading and error ×
 * light/dark × de/fr. Baselines are generated in Docker in the end batch (decision 69:
 * `e2e/scripts/screenshots.sh --update-snapshots`).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`reports team ${variant.name}`, () => {
    // reports@local administers the Zürich REPORT group
    screenshotTest.use({ variant, role: 'reports' });

    screenshotTest('region reports tab @G1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/reports`);
      await expect(page.getByTestId('reports.region.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `reports-region-${variant.name}`);
    });

    screenshotTest('report detail with handling and wall @G1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/reports/${IN_PROGRESS}`);
      await expect(page.getByTestId('reports.detail.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('reports.detail.wall.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `reports-detail-${variant.name}`);
    });

    screenshotTest('report log @D10.3 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/report-log`);
      await expect(page.getByTestId('reports.log.list')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `reports-log-${variant.name}`);
    });

    screenshotTest('reports block on a profile @G1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/profile/${JUMPER}`);
      await expect(page.getByTestId('reports.user.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('profiles.wall.state')).not.toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `reports-profile-${variant.name}`);
    });

    screenshotTest('region reports loading @G1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: `/api/regions/${ZURICH}/reports`, delayMs: 30_000 });
      await page.goto(`/regions/${ZURICH}/reports`);
      await expect(page.getByTestId('reports.region.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `reports-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('report detail error @G1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: `/api/reports/${REPORTS.noShowS01}`, status: 503 });
      await page.goto(`/reports/${REPORTS.noShowS01}`);
      await expect(page.getByTestId('reports.detail.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `reports-error-${variant.name}`);
    });
  });

  screenshotTest.describe(`reports member ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('report dialog @G1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/profile/${JUMPER}`);
      await expect(page.getByTestId('profiles.page.state')).toHaveAttribute('data-state', 'success');
      await page.getByTestId('profiles.page.report').click();
      await expect(page.getByTestId('reports.dialog.options')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `reports-dialog-${variant.name}`);
    });

    screenshotTest('mediation page @G2 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/mediation`);
      await expect(page.getByTestId('reports.mediation.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `reports-mediation-${variant.name}`);
    });

    screenshotTest('region reports forbidden @G1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/reports`);
      await expect(page.getByTestId('reports.region.state')).toHaveAttribute('data-state', 'forbidden');
      await visual.expectScreen(page, `reports-forbidden-${variant.name}`);
    });
  });
});
