import { S01_THURSDAY, expect, test, zurichPickupAt } from '../../fixtures';
import { openPickups, pickupRow } from './_pickups';

/** PU2 one-time pickups. Seed: S-01 (manager bieb), Thursday 2026-11-05 19:00 has 3 slots, 2 taken. */

test.use({ seedReset: 'test', role: 'bieb' });

test('PU2 add one-time pickup, change slots and description, cancel with zero slots @PU2 @page', async ({ page }) => {
  const at = zurichPickupAt(8, '10:00');
  await openPickups(page, 'S-01');
  await page.getByTestId('stores.pickups.add').click();
  await page.getByTestId('stores.pickups.edit.date').fill('2026-11-10');
  await page.getByTestId('stores.pickups.edit.time').fill('10:00');
  await page.getByTestId('stores.pickups.edit.description').fill('Sonderabholung Inventur');
  await page.getByTestId('stores.pickups.edit.save').click();
  const row = pickupRow(page, at);
  await expect(row).toBeVisible();
  await expect(row.getByTestId('stores.pickups.description')).toHaveText('Sonderabholung Inventur');
  await expect(row.getByTestId('stores.pickups.regularBadge')).toHaveCount(0);

  await row.getByTestId('stores.pickups.edit').click();
  await page.locator('#pickup-edit-slots').fill('4');
  await page.getByTestId('stores.pickups.edit.description').fill('Inventur, bitte grosse Taschen');
  await page.getByTestId('stores.pickups.edit.save').click();
  await expect(row).toHaveAttribute('data-slots', '4');
  await expect(row.getByTestId('stores.pickups.description')).toHaveText('Inventur, bitte grosse Taschen');

  await row.getByTestId('stores.pickups.edit').click();
  await page.locator('#pickup-edit-slots').fill('0');
  await page.getByTestId('stores.pickups.edit.save').click();
  await expect(page.getByTestId('stores.pickups.edit.description')).toBeHidden();
  // a cancelled one-time pickup (0 slots) is no longer offered
  await expect(row).toHaveCount(0);
});

test('PU2 fewer slots than occupied refused @PU2', async ({ page }) => {
  await openPickups(page, 'S-01');
  const row = pickupRow(page, S01_THURSDAY);
  await row.getByTestId('stores.pickups.edit').click();
  await page.locator('#pickup-edit-slots').fill('1');
  await page.getByTestId('stores.pickups.edit.save').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message-error')).toBeVisible();
  await expect(page.getByTestId('stores.pickups.edit.description')).toBeVisible();
  await page.reload();
  await expect(pickupRow(page, S01_THURSDAY)).toHaveAttribute('data-slots', '3');
});
