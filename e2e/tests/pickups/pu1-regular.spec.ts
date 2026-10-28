import { SEED_NOW, expect, test, zurichPickupAt } from '../../fixtures';
import { bellCount, openPickups, pickupRow } from './_pickups';

/** PU1 regular pickups. Seed: S-01 Mon/Thu 19:00 × 3 (manager bieb, team foodsaver, sleepy); S-03 prefetch 7 days. */

test.use({ seedReset: 'test', role: 'bieb' });

test('PU1 manager edits regular pickups, duplicate day/time refused, team notified @PU1 @page', async ({ page, as, apiOf }) => {
  const foodsaver = apiOf(await as('foodsaver'));
  const before = await bellCount(foodsaver, 'STORE_REGULAR_PICKUP_CHANGED');

  await openPickups(page, 'S-01');
  await page.getByTestId('stores.pickups.regular').click();
  const rows = page.getByTestId('stores.regular.row');
  await expect(rows).toHaveCount(2);
  await page.getByTestId('stores.regular.add').click();
  await expect(rows).toHaveCount(3);
  const added = rows.nth(2);
  await added.getByTestId('stores.regular.time').fill('19:00');
  // Monday 19:00 twice
  await expect(added.getByTestId('stores.regular.duplicate')).toBeVisible();
  await expect(page.getByTestId('stores.regular.save').locator('button')).toBeDisabled();
  await added.getByTestId('stores.regular.weekday').click();
  await page.getByRole('option', { name: 'Dienstag' }).click();
  await expect(page.getByTestId('stores.regular.duplicate')).toHaveCount(0);
  await page.getByTestId('stores.regular.save').click();
  await expect(rows).toHaveCount(0);
  await expect(page.locator('.p-dialog')).toHaveCount(0);

  // Tuesday 2026-11-03 19:00 now exists
  await expect(pickupRow(page, zurichPickupAt(1, '19:00'))).toBeVisible();
  await expect(pickupRow(page, zurichPickupAt(1, '19:00')).getByTestId('stores.pickups.regularBadge')).toBeVisible();
  await expect.poll(() => bellCount(foodsaver, 'STORE_REGULAR_PICKUP_CHANGED')).toBeGreaterThan(before);
});

test('PU1 prefetch horizon hides pickups beyond it @PU1 @PU3', async ({ page, api, store }) => {
  // S-03 opens sign-up 7 days ahead: no regular pickup beyond the horizon is listed
  const horizon = Date.parse(SEED_NOW) + 8 * 86_400_000;
  const pickups = await store.pickupsOf(api, 'S-03');
  expect(pickups.length).toBeGreaterThan(0);
  for (const pickup of pickups.filter((p) => p.occupiedSlots.length === 0)) {
    expect(Date.parse(pickup.pickupAt)).toBeLessThan(horizon);
  }
  // S-01 (14 days) lists pickups in the second week
  const s01 = await store.pickupsOf(api, 'S-01');
  expect(s01.some((p) => Date.parse(p.pickupAt) > horizon)).toBe(true);
  await openPickups(page, 'S-01');
  await page.getByTestId('stores.pickups.range.twoWeeks').click();
  await expect(page.getByTestId('stores.pickups.day').first()).toBeVisible();
});
