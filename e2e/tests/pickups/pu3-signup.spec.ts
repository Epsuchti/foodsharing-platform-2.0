import { S01_THURSDAY, S07_WEDNESDAY, acceptPendingConsents, expect, test, zurichPickupAt } from '../../fixtures';
import { bellCount, openPickups, openSignup, pickupRow } from './_pickups';
import { acceptConfirm } from '../stores/_stores';

/**
 * PU3 sign-up. Seed: S-07 Hiltl Wednesday 2026-11-04 20:30 is free (team: welcome manager, foodsaver); S-01 Thursday
 * 2026-11-05 19:00 has foodsaver unconfirmed + sleepy confirmed (manager bieb, a store coordinator).
 */

test.use({ seedReset: 'test', role: 'foodsaver' });

test('PU3 foodsaver signs up unconfirmed, manager confirms, bells both ways @PU3 @page', async ({ page, api, as, apiOf }) => {
  const welcome = await as('welcome');
  const welcomeApi = apiOf(welcome);
  await acceptPendingConsents(welcomeApi);
  const managerBells = await bellCount(welcomeApi, 'PICKUP_SIGNUP_UNCONFIRMED');
  const myBells = await bellCount(api, 'PICKUP_CONFIRMED');

  await openPickups(page, 'S-07');
  await openSignup(page, S07_WEDNESDAY);
  await expect(page.getByTestId('pickups.signup.eligible')).toBeVisible();
  await page.getByTestId('pickups.signup.confirm').click();
  const row = pickupRow(page, S07_WEDNESDAY);
  await expect(row.getByTestId('stores.pickups.unconfirmed')).toBeVisible();
  await expect(row.locator('[data-testid="stores.pickups.slot.taken"][data-confirmed="false"]')).toHaveCount(1);
  await expect.poll(() => bellCount(welcomeApi, 'PICKUP_SIGNUP_UNCONFIRMED')).toBeGreaterThan(managerBells);

  await openPickups(welcome, 'S-07');
  await pickupRow(welcome, S07_WEDNESDAY).locator('[data-testid="stores.pickups.slot.taken"][data-user="Sarah Müller"]').click();
  await welcome.getByTestId('stores.pickups.confirmSlot').click();
  await expect(
    pickupRow(welcome, S07_WEDNESDAY).locator('[data-testid="stores.pickups.slot.taken"][data-user="Sarah Müller"]'),
  ).toHaveAttribute('data-confirmed', 'true');
  await expect.poll(() => bellCount(api, 'PICKUP_CONFIRMED')).toBeGreaterThan(myBells);
});

test('PU3 manager sign-up is preconfirmed @PU3', async ({ as, apiOf }) => {
  const welcome = await as('welcome');
  await acceptPendingConsents(apiOf(welcome));
  await openPickups(welcome, 'S-07');
  await openSignup(welcome, S07_WEDNESDAY);
  await welcome.getByTestId('pickups.signup.confirm').click();
  const row = pickupRow(welcome, S07_WEDNESDAY);
  await expect(row.locator('[data-testid="stores.pickups.slot.taken"][data-user="Priya Sharma"]')).toHaveAttribute('data-confirmed', 'true');
  await expect(row.getByTestId('stores.pickups.unconfirmed')).toHaveCount(0);
});

test('PU3 sign out self and remove other with message @PU3', async ({ page, as, store, api }) => {
  // foodsaver signs out of S-01 Thursday
  await openPickups(page, 'S-01');
  const row = pickupRow(page, S01_THURSDAY);
  await row.locator('[data-testid="stores.pickups.slot.taken"][data-user="Sarah Müller"]').click();
  await acceptConfirm(page);
  await expect(row.locator('[data-user="Sarah Müller"]')).toHaveCount(0);

  // the manager removes sleepy with a message
  const bieb = await as('bieb');
  await openPickups(bieb, 'S-01');
  await pickupRow(bieb, S01_THURSDAY).locator('[data-testid="stores.pickups.slot.taken"][data-user="Marco Bernasconi"]').click();
  await bieb.getByTestId('stores.pickups.removeSlot').click();
  await bieb.getByTestId('stores.pickups.removeDialog.message').fill('Du bist im Urlaub, wir übernehmen das.');
  await bieb.getByTestId('stores.pickups.removeDialog.confirm').click();
  await expect(pickupRow(bieb, S01_THURSDAY).getByTestId('stores.pickups.slot.taken')).toHaveCount(0);
  const after = (await store.pickupsOf(api, 'S-01')).find((p) => p.pickupAt === S01_THURSDAY);
  expect(after?.occupiedSlots ?? []).toHaveLength(0);
});

test('PU3 sign-up before horizon opens is refused @PU3', async ({ api, store }) => {
  // S-07's prefetch horizon is 14 days: a Wednesday 5 weeks ahead is not open yet
  const later = zurichPickupAt(37, '20:30');
  const response = await api.post(`/api/stores/${store.id('S-07')}/pickups/${encodeURIComponent(later)}/signups`);
  expect(response.status()).toBe(409);
  expect((await response.json()).code).toBe('PICKUP_NOT_ELIGIBLE');
});
