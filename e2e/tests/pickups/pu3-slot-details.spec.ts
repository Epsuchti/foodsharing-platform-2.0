import { S01_THURSDAY, expect, expectDataState, test, zurichPickupAt } from '../../fixtures';
import { openPickups, pickupRow } from './_pickups';

/**
 * PU3 slot dialog (decision 165). Seed S-01: Thursday 2026-11-05 19:00 foodsaver unconfirmed + sleepy confirmed,
 * Monday 2026-11-09 19:00 sleepy unconfirmed + bieb confirmed; bieb manages S-01, foodsaver and sleepy are members.
 */
const S01_MONDAY = zurichPickupAt(7, '19:00');

const takenSlot = (page: import('@playwright/test').Page, pickupAt: string, name: string) =>
  pickupRow(page, pickupAt).locator(`[data-testid="stores.pickups.slot.taken"][data-user="${name}"]`);

test('PU3 manager: rich slot dialog with phone, counts and Bestätigen/Austragen @PU3 @S9 @page', async ({ as }) => {
  const bieb = await as('bieb');
  await openPickups(bieb, 'S-01');
  await takenSlot(bieb, S01_THURSDAY, 'Sarah Müller').click();
  const dialog = bieb.getByTestId('stores.pickups.slotDialog');
  await expect(dialog).toHaveAttribute('data-mode', 'rich');
  await expectDataState(bieb, 'stores.pickups.slotDetail.state', 'success');
  await expect(dialog.getByTestId('stores.pickups.slotDetail.name')).toHaveText('Sarah Müller');
  await expect(dialog.getByTestId('stores.pickups.slotDetail.call')).toHaveAttribute('href', 'tel:+41791234506');
  await expect(dialog.getByTestId('stores.pickups.slotState')).toHaveAttribute('data-confirmed', 'false');
  await expect(dialog.getByTestId('stores.pickups.slotDetail.pickupCount')).toBeVisible();
  await dialog.getByTestId('stores.pickups.slotDetail.upcoming').locator('summary').click();
  await expect(dialog.getByTestId('stores.pickups.slotDetail.upcomingItem').first()).toBeVisible();
  await expect(bieb.getByTestId('stores.pickups.confirmSlot')).toBeVisible();
  await expect(bieb.getByTestId('stores.pickups.removeSlot')).toBeVisible();
});

test('PU3 co-pickup: rich slot dialog without manager actions @PU3 @page', async ({ as }) => {
  const foodsaver = await as('foodsaver');
  await openPickups(foodsaver, 'S-01');
  await takenSlot(foodsaver, S01_THURSDAY, 'Marco Bernasconi').click();
  const dialog = foodsaver.getByTestId('stores.pickups.slotDialog');
  await expect(dialog).toHaveAttribute('data-mode', 'rich');
  await expectDataState(foodsaver, 'stores.pickups.slotDetail.state', 'success');
  await expect(dialog.getByTestId('stores.pickups.slotState')).toHaveAttribute('data-confirmed', 'true');
  await expect(dialog.getByTestId('stores.pickups.slotDetail.message')).toBeVisible();
  await expect(foodsaver.getByTestId('stores.pickups.confirmSlot')).toHaveCount(0);
  await expect(foodsaver.getByTestId('stores.pickups.removeSlot')).toHaveCount(0);
});

test('PU3 team member: short slot dialog with a profile link @PU3 @page', async ({ as, dev }) => {
  const bieb = await dev.user('bieb');
  const foodsaver = await as('foodsaver');
  await openPickups(foodsaver, 'S-01');
  await takenSlot(foodsaver, S01_MONDAY, 'Nina Schneider').click();
  const dialog = foodsaver.getByTestId('stores.pickups.slotDialog');
  await expect(dialog).toHaveAttribute('data-mode', 'short');
  await expect(dialog.getByTestId('stores.pickups.slotDetail.message')).toHaveCount(0);
  await expect(dialog.getByTestId('stores.pickups.slotDetail.pickupCount')).toHaveCount(0);
  await dialog.getByTestId('stores.pickups.slotDetail.profile').click();
  await expect(foodsaver).toHaveURL(new RegExp(`/profile/${bieb.id}$`));
});
