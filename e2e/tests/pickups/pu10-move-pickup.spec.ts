import { expect, test, zurichPickupAt } from '../../fixtures';
import { fillDate, fillTime } from '../../support/date-inputs';
import { einabId } from '../intro-pickups/_intro';
import { openPickups, pickupRow } from './_pickups';

/**
 * PU10 + EA16 move sync (decision 170): a store manager moves one pickup to another date and time of the same store.
 * Seed (docs/seed-ids.md): S-03 Migros (manager bot) has the regular Friday 6.11 18:30 with guide@local (Julien Favre)
 * signed up and confirmed; the seeded EinAb `…-28` of the guide is linked to it and booked by pending@local. The
 * regular Tuesday 10.11 18:30 is a pickup of the store already.
 */
test.use({ seedReset: 'test', role: 'bot' });

const FRIDAY = zurichPickupAt(4, '18:30');
const SATURDAY = zurichPickupAt(5, '11:00');
const TUESDAY = zurichPickupAt(8, '18:30');

async function openMove(page: import('@playwright/test').Page, at: string): Promise<void> {
  await pickupRow(page, at).getByTestId('stores.pickups.edit').click();
  await page.getByTestId('pickups.slot.move').click();
  await expect(page.getByTestId('pickups.move.dialog')).toBeVisible();
}

test('PU10 a manager moves a pickup; sign-ups and the linked EinAb move along @PU10 @EA16', async ({
  page,
  as,
  apiOf,
  notifications,
}) => {
  await openPickups(page, 'S-03');
  await expect(pickupRow(page, FRIDAY).getByTestId('stores.pickups.regularBadge')).toBeVisible();
  await openMove(page, FRIDAY);
  // the guide is told, his linked EinAb moves along, the regular occurrence is cancelled that day
  await expect(page.getByTestId('pickups.move.introPickups')).toBeVisible();
  await expect(page.getByTestId('pickups.move.regularHint')).toBeVisible();

  await fillDate(page.getByTestId('pickups.move.newDate'), '2026-11-07');
  await fillTime(page.getByTestId('pickups.move.newTime'), '11:00');
  await page.getByTestId('pickups.move.message').fill('Der Laden schliesst am Freitag früher.');
  await page.getByTestId('pickups.move.save').click();
  await expect(page.getByTestId('pickups.move.dialog')).toBeHidden();

  // the slot shows up at the new time with its confirmed sign-up, as a one-time pickup; the old time is gone
  const moved = pickupRow(page, SATURDAY);
  await expect(moved).toBeVisible();
  await expect(moved.locator('[data-testid="stores.pickups.slot.taken"][data-user="Julien Favre"]')).toBeVisible();
  await expect(moved.getByTestId('stores.pickups.regularBadge')).toHaveCount(0);
  await expect(pickupRow(page, FRIDAY)).toHaveCount(0);

  // the guide's EinAb shows the new time; the guide got PICKUP_MOVED, the newcomer INTRO_PICKUP_MOVED
  const guide = await as('guide');
  await guide.goto('/guide');
  await expect(guide.getByTestId('introPickups.guide.state')).toHaveAttribute('data-state', 'success');
  await expect(guide.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(28)}"]`)).toBeVisible();
  const einab = await apiOf(guide).getJson<{ startAt: string; linkedPickupAt?: string }>(
    `/api/intro-pickups/${einabId(28)}`,
  );
  expect(einab.startAt).toBe(SATURDAY);
  expect(einab.linkedPickupAt).toBe(SATURDAY);
  await expect.poll(async () => (await notifications.bells('guide', 'PICKUP_MOVED')).length).toBe(1);
  await expect.poll(async () => (await notifications.bells('pending', 'INTRO_PICKUP_MOVED')).length).toBe(1);
  expect(await notifications.bells('bot', 'PICKUP_MOVED')).toHaveLength(0);
});

test('PU10 a time the store already has a pickup at is refused in the dialog @PU10', async ({ page }) => {
  await openPickups(page, 'S-03');
  await openMove(page, FRIDAY);
  await fillDate(page.getByTestId('pickups.move.newDate'), '2026-11-10');
  await fillTime(page.getByTestId('pickups.move.newTime'), '18:30');
  await page.getByTestId('pickups.move.save').click();
  await expect(page.getByTestId('pickups.move.error')).toBeVisible();
  await expect(page.getByTestId('pickups.move.dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await page.reload();
  await expect(pickupRow(page, FRIDAY)).toBeVisible();
  await expect(pickupRow(page, TUESDAY)).toBeVisible();
});
