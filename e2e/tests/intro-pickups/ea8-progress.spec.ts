import { expect, test } from '../../fixtures';

/** EA8: progress "x of N" with the next step. */

test('EA8 progress 1 of 3 and next step @EA8', async ({ as }) => {
  // einab1: 1 done, 1 booked (without Fairteiler) → the third needs a Fairteiler visit
  const einab1 = await as('einab1');
  await einab1.goto('/me/intro-pickups');
  const progress = einab1.getByTestId('introPickups.my.progress');
  await expect(progress).toHaveAttribute('data-done', '1');
  await expect(progress).toHaveAttribute('data-required', '3');
  await expect(einab1.getByTestId('introPickups.my.nextStep')).toHaveAttribute('data-step', 'FAIRTEILER');
  await expect(einab1.getByTestId('introPickups.my.pastRow')).toHaveCount(1);
  await expect(einab1.getByTestId('introPickups.my.counted')).toHaveCount(1);
  await einab1.getByTestId('introPickups.my.bookMore').click();
  await expect(einab1).toHaveURL(/\/intro-pickups$/);

  // pending: all three done → waiting for the identity check
  const pending = await as('pending');
  await pending.goto('/me/intro-pickups');
  await expect(pending.getByTestId('introPickups.my.progress')).toHaveAttribute('data-done', '3');
  await expect(pending.getByTestId('introPickups.my.nextStep')).toHaveAttribute('data-step', 'WAIT');
  await expect(pending.getByTestId('introPickups.my.pastRow')).toHaveCount(3);
});
