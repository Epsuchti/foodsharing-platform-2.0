import { expect, expectDataState, test } from '../../fixtures';

/** A19 dashboard card "my next pickups". Seed: foodsaver at S-04 (Wednesday) and S-01 (Thursday). */

test.use({ role: 'foodsaver' });

test('A19 dashboard shows my next pickups and free slots @A19', async ({ page, as }) => {
  await page.goto('/dashboard');
  await expectDataState(page, 'dashboard.pickups.state', 'success');
  const items = page.getByTestId('dashboard.pickups.pickup');
  await expect(items).toHaveCount(2);
  await expect(items.first()).toHaveAttribute('data-store', 'Denner Langstrasse');
  await expect(page.getByTestId('dashboard.pickups.free')).toContainText(/\d/);
  await page.getByTestId('dashboard.pickups.all').click();
  await expect(page).toHaveURL(/\/pickups\/mine$/);

  // not for users who cannot pick up
  const pending = await as('pending');
  await pending.goto('/dashboard');
  await expect(pending.locator('h1').first()).toBeVisible();
  await expect(pending.getByTestId('dashboard.pickups')).toHaveCount(0);
});
