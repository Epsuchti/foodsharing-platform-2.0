import { expect, test, zurichPickupAt } from '../../fixtures';
import { openPickups, pickupRow } from '../pickups/_pickups';
import { openAutomation } from './_automation';

/** AU6: extra slots. Seed: S-06 allows 2 (maxExtraSlots, decision 164); Tuesday 3 Nov 19:00 is empty (2 slots). */
test.use({ seedReset: 'test' });

test('AU6 first sign-up adds up to two extra slots; second person cannot @AU6 @page', async ({ as, store }) => {
  const tuesday = zurichPickupAt(1, '19:00');
  await store.signUp('sleepy', 'S-06', tuesday);

  const sleepy = await as('sleepy');
  await openPickups(sleepy, 'S-06');
  const row = pickupRow(sleepy, tuesday);
  await expect(row).toHaveAttribute('data-slots', '2');
  await row.getByTestId('stores.pickups.addExtraSlot').click();
  await expect(row).toHaveAttribute('data-slots', '3');
  await row.getByTestId('stores.pickups.addExtraSlot').click();
  await expect(row).toHaveAttribute('data-slots', '4');
  await expect(row.getByTestId('stores.pickups.addExtraSlot')).toHaveCount(0);
  await expect(row.getByTestId('stores.pickups.extraSlot')).toHaveCount(2);
  await expect(row.getByTestId('stores.pickups.extraSlot').first()).toContainText('Marco Bernasconi');

  // bieb is not the first sign-up: no button, and the audit lists the extra slot
  const bieb = await as('bieb');
  await openPickups(bieb, 'S-06');
  await expect(pickupRow(bieb, tuesday).getByTestId('stores.pickups.addExtraSlot')).toHaveCount(0);
  await openAutomation(bieb, 'S-06', 'extra-slot');
  await expect(bieb.getByTestId('automation.extraSlot.max').locator('input')).toHaveValue('2');
  await expect(bieb.locator('[data-testid="automation.extraSlot.audits.row"][data-kind="EXTRA_SLOT"]').first()).toBeVisible();
});

test('AU6 the number of extra slots is set in Automatisierung; 0 switches it off @AU6', async ({ as }) => {
  const bieb = await as('bieb');
  await openAutomation(bieb, 'S-06', 'extra-slot');
  const input = bieb.getByTestId('automation.extraSlot.max').locator('input');
  await expect(input).toHaveValue('2');
  await expect(bieb.getByTestId('automation.extraSlot.save').locator('button')).toBeDisabled();
  await input.fill('0');
  await input.blur();
  await bieb.getByTestId('automation.extraSlot.save').locator('button').click();
  await expect(bieb.getByTestId('automation.extraSlot.save').locator('button')).toBeDisabled();
  await bieb.reload();
  await expect(bieb.getByTestId('automation.extraSlot.max').locator('input')).toHaveValue('0');
});
