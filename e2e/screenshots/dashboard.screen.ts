import { injectApiError } from '../fixtures';
import type { Page } from '@playwright/test';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Brings a home card into view. Help, events and stores load on `@defer (on viewport)`: their 1px placeholder is
 * replaced by the card as soon as it is seen, possibly while it is being scrolled to, so retry until the card exists.
 */
async function revealCard(page: Page, card: string) {
  const element = page.getByTestId(`dashboard.${card}`);
  await expect(async () => {
    if (!(await element.count())) await page.locator(`.defer-slot.o-${card}`).scrollIntoViewIfNeeded({ timeout: 1_000 });
    await expect(element).toBeAttached({ timeout: 1_000 });
  }).toPass();
  await element.scrollIntoViewIfNeeded();
  return element;
}

/**
 * Dashboard (plan-m7 Q7, A19, home rework 2026-11-04): activity feed with filter chips, nearby baskets card, the card
 * without a profile address, feed loading and error, the date-tile pickups card and the new home cards (help needed,
 * waiting for you, upcoming events, your stores; help loading and error) × light/dark × de/fr. Baselines are generated in Docker in the end batch
 * (decision 69: `e2e/scripts/screenshots.sh --update-snapshots`).
 */
forEachVariant((variant) => {
  screenshotTest.describe(`dashboard ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('dashboard with activity feed and nearby baskets @A19 @screenshot', async ({ page, visual }) => {
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.activity.state')).toHaveAttribute('data-state', 'success');
      await expect(page.getByTestId('dashboard.baskets.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `dashboard-feed-${variant.name}`);
    });

    screenshotTest('activity feed card @A19 @screenshot', async ({ page, visual }) => {
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.activity.state')).toHaveAttribute('data-state', 'success');
      await visual.expectElement(page.getByTestId('dashboard.activity'), `dashboard-activity-${variant.name}`);
    });

    screenshotTest('activity feed loading @A19 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/activity', delayMs: 30_000 });
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.activity.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectElement(page.getByTestId('dashboard.activity'), `dashboard-activity-loading-${variant.name}`, { waitForData: false });
    });

    screenshotTest('home cards: next pickups, help needed, waiting, events, stores @A19 @screenshot', async ({ page, visual }) => {
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.pickups.state')).toHaveAttribute('data-state', 'success');
      await visual.expectElement(page.getByTestId('dashboard.pickups'), `dashboard-pickups-tiles-${variant.name}`);
      for (const card of ['help', 'waiting', 'events', 'stores']) {
        const element = await revealCard(page, card);
        await expect(page.getByTestId(`dashboard.${card}.state`)).toHaveAttribute('data-state', 'success');
        await visual.expectElement(element, `dashboard-${card}-${variant.name}`);
      }
    });

    screenshotTest('help needed card loading and error @screenshot', async ({ page, visual }) => {
      const delay = await injectApiError(page, { url: '/api/users/me/pickups/options', delayMs: 30_000 });
      await page.goto('/dashboard');
      const card = await revealCard(page, 'help');
      await expect(page.getByTestId('dashboard.help.state')).toHaveAttribute('data-state', 'loading');
      await visual.expectElement(card, `dashboard-help-loading-${variant.name}`, { waitForData: false });
      await delay.remove();
      await injectApiError(page, { url: '/api/users/me/pickups/options', status: 503 });
      await page.reload();
      await revealCard(page, 'help');
      await expect(page.getByTestId('dashboard.help.state')).toHaveAttribute('data-state', 'error');
      await visual.expectElement(page.getByTestId('dashboard.help'), `dashboard-help-error-${variant.name}`);
    });

    screenshotTest('activity feed error @A19 @screenshot', async ({ page, visual }) => {
      await injectApiError(page, { url: '/api/activity', status: 503 });
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.activity.state')).toHaveAttribute('data-state', 'error');
      await visual.expectElement(page.getByTestId('dashboard.activity'), `dashboard-activity-error-${variant.name}`);
    });
  });

  screenshotTest.describe(`dashboard without address ${variant.name}`, () => {
    // profile@local has no address in the seed
    screenshotTest.use({ variant, role: 'profile' });

    screenshotTest('nearby baskets without an address @A19 @screenshot', async ({ page, visual }) => {
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.baskets.noPosition')).toBeVisible();
      await visual.expectElement(page.getByTestId('dashboard.baskets'), `dashboard-baskets-no-address-${variant.name}`);
    });
  });
});
