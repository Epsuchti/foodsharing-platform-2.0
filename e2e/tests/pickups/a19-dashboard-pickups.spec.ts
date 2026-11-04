import { expect, expectDataState, test } from '../../fixtures';

/** A19 dashboard card "my next pickups". Seed: foodsaver at S-04 (Wednesday) and S-01 (Thursday). */

test.use({ role: 'foodsaver' });

test('A19 dashboard shows my next pickups as date rows and links to them @A19', async ({ page, as }) => {
  await page.goto('/dashboard');
  await expectDataState(page, 'dashboard.pickups.state', 'success');
  const items = page.getByTestId('dashboard.pickups.pickup');
  await expect(items).toHaveCount(2);
  await expect(items.first()).toHaveAttribute('data-store', 'Denner Langstrasse');
  // date tile (decorative) and a state word with its icon (owner feedback 2026-11-04)
  await expect(items.first().locator('fs-date-tile')).toHaveAttribute('aria-hidden', 'true');
  await expect(items.first().locator('time')).toHaveAttribute('datetime', /\d{4}-\d{2}-\d{2}T/);
  await expect(items.first().getByTestId('dashboard.pickups.confirmed').or(items.first().getByTestId('dashboard.pickups.unconfirmed'))).toBeVisible();
  // the free-slot count moved to "Help needed soon"
  await expect(page.getByTestId('dashboard.pickups.free')).toHaveCount(0);
  await page.getByTestId('dashboard.pickups.all').click();
  await expect(page).toHaveURL(/\/pickups#mine$/);

  // not for users who cannot pick up
  const pending = await as('pending');
  await pending.goto('/dashboard');
  await expect(pending.locator('h1').first()).toBeVisible();
  await expect(pending.getByTestId('dashboard.pickups')).toHaveCount(0);
});
