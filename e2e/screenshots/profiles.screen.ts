import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Public profile and buddies (plan-m7 Q2, A6/A15/A16): overview + wall, bananas tab, sleepy's sleep banner, buddies
 * page, loading and error × light/dark × de/fr. Baselines are generated in Docker in the end batch (decision 69:
 * `e2e/scripts/screenshots.sh --update-snapshots`).
 */
const FOODSAVER = '00000001-0001-4000-8000-000000000006';
const SLEEPY = '00000001-0001-4000-8000-000000000008';

forEachVariant((variant) => {
  screenshotTest.describe(`profiles ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'bieb' });

    screenshotTest('profile overview with wall @A6 @screenshot', async ({ page, visual }) => {
      await page.goto(`/profile/${FOODSAVER}`);
      await expect(page.getByTestId('profiles.page.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('profiles.wall.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `profiles-overview-${variant.name}`);
    });

    screenshotTest('profile bananas @A15 @screenshot', async ({ page, visual }) => {
      await page.goto(`/profile/${FOODSAVER}?tab=bananas`);
      await expect(page.getByTestId('profiles.bananas.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `profiles-bananas-${variant.name}`);
    });

    screenshotTest('profile sleep banner @A6 @screenshot', async ({ page, visual }) => {
      await page.goto(`/profile/${SLEEPY}`);
      await expect(page.getByTestId('profiles.page.sleep')).toBeVisible();
      await expect(page.getByTestId('profiles.wall.state')).not.toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `profiles-sleeping-${variant.name}`);
    });

    screenshotTest('profile buddies @A16 @screenshot', async ({ page, visual }) => {
      await page.goto('/profile/me/buddies');
      await expect(page.getByTestId('profiles.buddies.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `profiles-buddies-${variant.name}`);
    });

    screenshotTest('profile loading @A6 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/users/*/profile', delayMs: 30_000 });
      await page.goto(`/profile/${FOODSAVER}`);
      await expect(page.getByTestId('profiles.page.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectScreen(page, `profiles-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('profile error @A6 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/users/*/profile', status: 503 });
      await page.goto(`/profile/${FOODSAVER}`);
      await expect(page.getByTestId('profiles.page.state')).toHaveAttribute('data-state', 'error');
      await visual.expectScreen(page, `profiles-error-${variant.name}`);
    });
  });
});
