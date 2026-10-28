import { S07_WEDNESDAY, injectApiError, seedStore } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Pickup screens (M3 F4): store pickups tab (day list), sign-up dialog eligible / not eligible, regular editor,
 * one-time pickup dialog, options, my pickups, agenda, dashboard card, loading and error. Baselines in the end batch
 * (decision 69).
 */
const S01 = seedStore('S-01').id;
const S02 = seedStore('S-02').id;
const S07 = seedStore('S-07').id;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`pickups ${variant.name}`, () => {
    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('pickups tab day list @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-tab-${variant.name}`);
      });

      screenshotTest('sign-up dialog eligible @PU3 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S07}/pickups`);
        await page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${S07_WEDNESDAY}"]`).getByTestId('stores.pickups.slot.free').first().click();
        await expect(page.getByTestId('pickups.signup.eligible')).toBeVisible();
        await visual.expectScreen(page, `pickups-signup-eligible-${variant.name}`);
      });

      screenshotTest('options, my pickups, agenda, dashboard @PU7 @PU8 @A19 @screenshot', async ({ page, visual }) => {
        await page.goto('/pickups');
        await expect(page.getByTestId('pickups.options.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-options-${variant.name}`);
        await page.goto('/pickups/mine');
        await expect(page.getByTestId('pickups.mine.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-mine-${variant.name}`);
        await page.goto('/pickups/agenda/2026-11-05');
        await expect(page.getByTestId('pickups.agenda.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-agenda-${variant.name}`);
        await page.goto('/dashboard');
        await expect(page.getByTestId('dashboard.pickups.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `pickups-dashboard-${variant.name}`);
      });

      screenshotTest('pickups tab loading and error @PU3 @screenshot', async ({ page, visual }) => {
        const delay = await injectApiError(page, { url: `/api/stores/${S01}/pickups`, delayMs: 30_000 });
        await page.goto(`/stores/${S01}/pickups`);
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `pickups-tab-loading-${variant.name}`, { waitForData: false });
        await delay.remove();
        await injectApiError(page, { url: `/api/stores/${S01}/pickups`, status: 500 });
        await page.reload();
        await expect(page.getByTestId('stores.pickups.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `pickups-tab-error-${variant.name}`);
      });
    });

    screenshotTest.describe('jumper', () => {
      screenshotTest.use({ variant, role: 'jumper' });

      screenshotTest('eligibility dialog with reasons @PU4 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S02}/pickups`);
        await page.getByTestId('stores.pickups.slot.free').first().click();
        await expect(page.getByTestId('shared.eligibility.reason').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-signup-reasons-${variant.name}`);
      });
    });

    screenshotTest.describe('bieb', () => {
      screenshotTest.use({ variant, role: 'bieb' });

      screenshotTest('regular editor and one-time dialog @PU1 @PU2 @screenshot', async ({ page, visual }) => {
        await page.goto(`/stores/${S01}/pickups`);
        await page.getByTestId('stores.pickups.regular').click();
        await expect(page.getByTestId('stores.regular.row').first()).toBeVisible();
        await visual.expectScreen(page, `pickups-regular-${variant.name}`);
        await page.getByRole('button', { name: /Abbrechen|Annuler/ }).click();
        await page.getByTestId('stores.pickups.add').click();
        await expect(page.getByTestId('stores.pickups.edit.date')).toBeVisible();
        await visual.expectScreen(page, `pickups-one-time-${variant.name}`);
      });
    });
  });
});
