import { expect, expectDataState, test } from '../../fixtures';

/** PU8 agenda. Seed: foodsaver picks up at S-04 Wednesday 2026-11-04 and S-01 Thursday 2026-11-05. */

test.use({ role: 'foodsaver' });

test('PU8 agenda shows pickups and EinAbs of a day @PU8 @page', async ({ page }) => {
  await page.goto('/pickups/mine');
  await expectDataState(page, 'pickups.mine.state', 'success');
  await page.getByTestId('pickups.mine.agenda').click();
  await expect(page).toHaveURL(/\/pickups\/agenda\/2026-11-04$/);
  await expectDataState(page, 'pickups.agenda.state', 'success');
  await expect(page.locator('[data-testid="pickups.agenda.pickup"][data-store="Denner Langstrasse"]')).toBeVisible();

  await page.getByTestId('pickups.agenda.next').click();
  await expect(page).toHaveURL(/2026-11-05$/);
  await expect(page.locator('[data-testid="pickups.agenda.pickup"][data-store="Coop Bahnhofbrücke"]')).toBeVisible();
  await expect(page.locator('[data-testid="pickups.agenda.pickup"][data-store="Denner Langstrasse"]')).toHaveCount(0);

  await page.getByTestId('pickups.agenda.next').click();
  await expectDataState(page, 'pickups.agenda.state', 'empty');
});
