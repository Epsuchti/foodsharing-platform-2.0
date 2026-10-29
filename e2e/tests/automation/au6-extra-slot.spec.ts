import { expect, test, zurichPickupAt } from '../../fixtures';
import { openPickups, pickupRow } from '../pickups/_pickups';
import { openAutomation } from './_automation';

/** AU6: extra slot. Seed: S-06 allows it; Tuesday 3 Nov 19:00 is empty (2 slots). */
test.use({ seedReset: 'test' });

test('AU6 first sign-up adds one extra slot once; second person cannot @AU6 @page', async ({ as, store }) => {
  const tuesday = zurichPickupAt(1, '19:00');
  await store.signUp('sleepy', 'S-06', tuesday);

  const sleepy = await as('sleepy');
  await openPickups(sleepy, 'S-06');
  const row = pickupRow(sleepy, tuesday);
  await expect(row).toHaveAttribute('data-slots', '2');
  await row.getByTestId('stores.pickups.addExtraSlot').click();
  await expect(row).toHaveAttribute('data-slots', '3');
  await expect(row.getByTestId('stores.pickups.addExtraSlot')).toHaveCount(0);
  await expect(row.getByTestId('stores.pickups.extraSlot')).toContainText('Marco Bernasconi');

  // bieb is not the first sign-up: no button, and the audit lists the extra slot
  const bieb = await as('bieb');
  await openPickups(bieb, 'S-06');
  await expect(pickupRow(bieb, tuesday).getByTestId('stores.pickups.addExtraSlot')).toHaveCount(0);
  await openAutomation(bieb, 'S-06', 'extra-slot');
  await expect(bieb.getByTestId('automation.extraSlot.allowed').locator('input')).toBeChecked();
  await expect(bieb.locator('[data-testid="automation.extraSlot.audits.row"][data-kind="EXTRA_SLOT"]').first()).toBeVisible();
});
