import { injectApiError, regionId } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

const ZURICH = regionId('zuerich-stadt');
const PUTZHELD = '00000051-0001-4000-8000-000000000003';
const HYGIENE = '00000051-0001-4000-8000-000000000001';

/**
 * Achievements (plan-m7 Q9, AC1): region tab as member and as ambassador, define dialog, award dialog, awards list,
 * global admin page, loading and error × light/dark × de/fr. Baselines are generated in Docker in the end batch
 * (decision 69: `e2e/scripts/screenshots.sh --update-snapshots`).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`achievements member ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('achievements region tab as member @AC1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/achievements`);
      await expect(page.getByTestId('achievements.board.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `achievements-region-${variant.name}`);
    });

    screenshotTest('achievements loading @AC1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/achievements', delayMs: 30_000 });
      await page.goto(`/regions/${ZURICH}/achievements`);
      await expect(page.getByTestId('achievements.board.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `achievements-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('achievements error @AC1 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/achievements', status: 503 });
      await page.goto(`/regions/${ZURICH}/achievements`);
      await expect(page.getByTestId('achievements.board.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `achievements-error-${variant.name}`);
    });
  });

  screenshotTest.describe(`achievements ambassador ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'bot' });

    screenshotTest('achievements region tab as ambassador @AC1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/achievements`);
      await expect(page.getByTestId('achievements.board.define')).toBeVisible();
      await visual.expectScreen(page, `achievements-admin-region-${variant.name}`);
    });

    screenshotTest('achievements define dialog @AC1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/achievements`);
      await page.getByTestId('achievements.board.define').click();
      await page.getByTestId('achievements.form.names.field.de').fill('Velokurier-Held');
      await expect(page.getByTestId('achievements.form.submit')).toBeEnabled();
      await visual.expectScreen(page, `achievements-define-${variant.name}`);
    });

    screenshotTest('achievements award dialog @AC1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/achievements`);
      await page.locator(`[data-achievement-id="${PUTZHELD}"]`).getByTestId('achievements.card.award').click();
      await page.getByTestId('achievements.award.picker.search').fill('Julien');
      await expect(page.getByTestId('achievements.award.picker.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `achievements-award-${variant.name}`);
    });

    screenshotTest('achievements awards list @AC1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${ZURICH}/achievements`);
      await page.locator(`[data-achievement-id="${PUTZHELD}"]`).getByTestId('achievements.card.awards').click();
      await expect(page.getByTestId('achievements.awards.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `achievements-awards-${variant.name}`);
    });
  });

  screenshotTest.describe(`achievements orga ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'orga' });

    screenshotTest('achievements global admin page @AC1 @screenshot', async ({ page, visual }) => {
      await page.goto('/admin/achievements');
      await expect(page.locator(`[data-achievement-id="${HYGIENE}"]`)).toBeVisible();
      await visual.expectScreen(page, `achievements-global-${variant.name}`);
    });
  });
});
