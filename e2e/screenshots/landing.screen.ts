import { type Page } from '@playwright/test';

import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest, variants } from './_setup';

/**
 * Landing page, Variant D (C17, decision 159): full page, the result card of the postcode search, the counters'
 * skeleton and error, and the search's empty and error states — × light/dark on the desktop and mobile projects.
 * Moved here from onboarding.screen.ts (the old `explainer-*` baselines are gone). Baselines are generated in the
 * end batch in Docker (`e2e/scripts/screenshots.sh`, decision 69).
 */
screenshotTest.use({ seedReset: 'test' });

async function search(page: Page, postcode: string): Promise<void> {
  await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
  await page.getByTestId('public.landing.near.search').fill(postcode);
  await page.getByTestId('public.landing.near.search.submit').click();
}

forEachVariant(
  (variant) => {
    screenshotTest.describe(`landing ${variant.name}`, () => {
      screenshotTest.use({ variant, role: undefined });

      screenshotTest('landing full page @C17 @screenshot', async ({ page, visual }) => {
        await page.goto('/');
        await expect(page.getByTestId('public.explainer.state')).toHaveAttribute('data-state', 'success');
        // render the deferred "In deiner Nähe" before the full-page capture
        await page.getByTestId('public.landing.near').scrollIntoViewIfNeeded();
        await expect(page.getByTestId('public.landing.near.idle')).toBeVisible();
        await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
        await visual.expectScreen(page, `landing-${variant.name}`, { fullPage: true });
      });

      screenshotTest('landing result card @C17 @screenshot', async ({ page, visual }) => {
        await page.goto('/');
        await search(page, '8004');
        const near = page.getByTestId('public.landing.near');
        await expect(page.getByTestId('public.landing.near.result')).toBeVisible();
        await visual.expectElement(near, `landing-near-result-${variant.name}`);
      });

      screenshotTest('landing counters skeleton @C17 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/public/onboarding/explainer', delayMs: 30_000 });
        await page.goto('/');
        const state = page.getByTestId('public.explainer.state');
        await expect(state).toHaveAttribute('data-state', 'loading');
        await visual.expectElement(state, `landing-counters-skeleton-${variant.name}`, { waitForData: false });
      });

      screenshotTest('landing counters error @C17 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/public/onboarding/explainer', status: 503 });
        await page.goto('/');
        const state = page.getByTestId('public.explainer.state');
        await expect(state).toHaveAttribute('data-state', 'error');
        await visual.expectElement(state, `landing-counters-error-${variant.name}`);
      });

      screenshotTest('landing near empty @C17 @screenshot', async ({ page, visual }) => {
        await page.route('**/api/public/regions/suggest**', (route) => route.fulfill({ json: [] }));
        await page.goto('/');
        await search(page, '9999');
        await expect(page.getByTestId('public.landing.near.state')).toHaveAttribute('data-state', 'empty');
        await visual.expectElement(page.getByTestId('public.landing.near'), `landing-near-empty-${variant.name}`);
      });

      screenshotTest('landing near error @C17 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: '/api/public/regions/suggest', status: 503, code: 'GEOCODING_UNAVAILABLE' });
        await page.goto('/');
        await search(page, '8004');
        await expect(page.getByTestId('public.landing.near.state')).toHaveAttribute('data-state', 'error');
        await visual.expectElement(page.getByTestId('public.landing.near'), `landing-near-error-${variant.name}`);
      });
    });
  },
  variants(['light', 'dark']),
);
