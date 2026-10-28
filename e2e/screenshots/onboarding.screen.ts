import { type ApiClient, acceptPendingConsents, injectApiError, type Role } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Onboarding screens (M2 T22): journey page per seed stage (newbie, quiz, einab1, pending, foodsaver = complete),
 * profile step, header journey bar, dashboard "your next step", explainer sections and error — × light/dark × de/fr.
 * Baselines are generated in Docker (`e2e/scripts/screenshots.sh`, decision 69).
 */
screenshotTest.use({ seedReset: 'test' });

const STAGES: Role[] = ['newbie', 'quiz', 'einab1', 'pending', 'foodsaver'];

async function ready(api: ApiClient): Promise<void> {
  await acceptPendingConsents(api);
}

forEachVariant((variant) => {
  screenshotTest.describe(`onboarding ${variant.name}`, () => {
    for (const role of STAGES) {
      screenshotTest.describe(role, () => {
        screenshotTest.use({ variant, role });

        screenshotTest(`journey ${role} @D11.5.2 @screenshot`, async ({ page, apiOf, visual }) => {
          await ready(apiOf(page));
          await page.goto('/onboarding');
          await expect(page.getByTestId('onboarding.journey.state')).toHaveAttribute('data-state', 'success');
          await visual.expectScreen(page, `journey-${role}-${variant.name}`);
          await page.goto('/dashboard');
          await expect(page.getByTestId('dashboard.page.nextStep')).toBeVisible();
          await visual.expectScreen(page, `journey-dashboard-${role}-${variant.name}`);
        });
      });
    }

    screenshotTest.describe('profile step', () => {
      screenshotTest.use({ variant, role: 'newbie' });

      screenshotTest('profile and region step @EA8 @screenshot', async ({ page, apiOf, visual }) => {
        await ready(apiOf(page));
        await page.goto('/onboarding/profile');
        await expect(page.getByTestId('onboarding.profile.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `onboarding-profile-${variant.name}`);
        await page.getByTestId('onboarding.profile.picker.postcode').fill('8004');
        await page.getByTestId('onboarding.profile.picker.suggestion.zuerich-stadt').click();
        await expect(page.getByTestId('onboarding.profile.regionCard')).toBeVisible();
        await visual.expectScreen(page, `onboarding-profile-region-${variant.name}`);
      });

      screenshotTest('journey error @D11.5.2 @screenshot', async ({ page, apiOf, visual }) => {
        await ready(apiOf(page));
        await injectApiError(page, { url: '**/api/onboarding/journey', status: 500 });
        await page.goto('/onboarding');
        await expect(page.getByTestId('onboarding.journey.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `journey-error-${variant.name}`);
      });
    });

    screenshotTest.describe('explainer', () => {
      screenshotTest.use({ variant, role: undefined });

      screenshotTest('explainer sections @C1 @screenshot', async ({ page, visual }) => {
        await page.goto('/');
        await expect(page.getByTestId('public.explainer.state')).toHaveAttribute('data-state', 'success');
        await page.getByTestId('public.explainer.region').scrollIntoViewIfNeeded();
        await page.getByTestId('public.explainer.picker.postcode').fill('8004');
        await page.getByTestId('public.explainer.picker.suggestion.zuerich-stadt').click();
        await expect(page.getByTestId('public.explainer.regionCard')).toBeVisible();
        await visual.expectScreen(page, `explainer-${variant.name}`, { fullPage: true });
      });
    });
  });
});
