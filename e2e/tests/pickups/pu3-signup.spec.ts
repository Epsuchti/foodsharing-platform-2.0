import { S01_THURSDAY, S07_WEDNESDAY, acceptPendingConsents, expect, expectStatus, test, zurichPickupAt } from '../../fixtures';
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

test('PU3 manager confirms from the ✓: the slot dialog opens with "Bestätigen" focused @PU3', async ({ as }) => {
  const bieb = await as('bieb');
  await openPickups(bieb, 'S-01');
  const row = pickupRow(bieb, S01_THURSDAY);
  const summary = row.getByTestId('stores.pickups.summary');
  await expect(summary).toHaveAttribute('data-confirmed', '1');
  await expect(summary).toHaveAttribute('data-pending', '1');

  await row.locator('[data-testid="stores.pickups.slot.confirm"][data-user="Sarah Müller"]').click();
  await expect(bieb.getByTestId('stores.pickups.slotState')).toHaveAttribute('data-confirmed', 'false');
  // no one-click confirm: the dialog is the review moment, its "Bestätigen" has the focus
  const confirmButton = bieb.getByTestId('stores.pickups.confirmSlot').locator('button');
  await expect(confirmButton).toBeFocused();
  await bieb.keyboard.press('Enter');

  await expect(summary).toHaveAttribute('data-pending', '0');
  await expect(summary).toHaveAttribute('data-confirmed', '2');
  await expect(row.locator('[data-testid="stores.pickups.slot.taken"][data-user="Sarah Müller"]')).toHaveAttribute('data-confirmed', 'true');
  await expect(row.getByTestId('stores.pickups.slot.confirm')).toHaveCount(0);
});

test('PU3 manager confirms all unconfirmed sign-ups of a pickup in one go @PU3', async ({ as, store }) => {
  // second unconfirmed sign-up on S-01 Thursday: sleepy (confirmed in the seed) signs out and in again
  const path = `/api/stores/${store.id('S-01')}/pickups/${encodeURIComponent(S01_THURSDAY)}/signups`;
  await store.withRole('sleepy', async (client) => {
    const me = await client.session();
    await expectStatus(await client.delete(`${path}/${me.user!.id}`), [200, 204], 'sleepy signs out');
  });
  await store.signUp('sleepy', 'S-01', S01_THURSDAY);

  const bieb = await as('bieb');
  await openPickups(bieb, 'S-01');
  const row = pickupRow(bieb, S01_THURSDAY);
  const summary = row.getByTestId('stores.pickups.summary');
  await expect(summary).toHaveAttribute('data-pending', '2');
  await row.getByTestId('stores.pickups.confirmAll').click();
  // one dialog that names the people
  const dialog = bieb.locator('.p-confirmdialog');
  await expect(dialog).toContainText('Sarah Müller');
  await expect(dialog).toContainText('Marco Bernasconi');
  await acceptConfirm(bieb);

  await expect(summary).toHaveAttribute('data-pending', '0');
  await expect(summary).toHaveAttribute('data-confirmed', '2');
  await expect(row.getByTestId('stores.pickups.confirmAll')).toHaveCount(0);
  await expect(row.locator('[data-testid="stores.pickups.slot.taken"][data-confirmed="false"]')).toHaveCount(0);
});

test('PU3 manager filters by "Unbestätigt (n)" and sees the empty text when all are confirmed @PU3', async ({ as }) => {
  const bieb = await as('bieb');
  await openPickups(bieb, 'S-01');
  const chip = bieb.getByTestId('stores.pickups.range.pending');
  await expect(chip).toHaveAttribute('aria-pressed', 'false');
  const count = Number(await chip.getAttribute('data-count'));
  expect(count).toBeGreaterThan(0);

  await chip.click();
  await expect(chip).toHaveAttribute('aria-pressed', 'true');
  const rows = bieb.getByTestId('stores.pickups.pickup');
  await expect(rows.first()).toBeVisible();
  // every listed pickup has an unconfirmed slot; together they hold the chip's count
  const pending = await rows.evaluateAll((els) =>
    els.map((el) => el.querySelectorAll('[data-testid="stores.pickups.slot.taken"][data-confirmed="false"]').length),
  );
  expect(pending.every((n) => n > 0)).toBe(true);
  expect(pending.reduce((a, b) => a + b, 0)).toBe(count);

  // confirm them one by one; the chip stays pressed and the list says when nothing is left
  for (let left = count; left > 0; left--) {
    await bieb.getByTestId('stores.pickups.slot.confirm').first().click();
    await bieb.getByTestId('stores.pickups.confirmSlot').click();
    await expect(chip).toHaveAttribute('data-count', String(left - 1));
  }
  await expect(chip).toHaveAttribute('aria-pressed', 'true');
  await expect(bieb.getByTestId('stores.pickups.pendingEmpty')).toBeVisible();
  await expect(rows).toHaveCount(0);
});

test('PU3 every slot button names its state @PU3', async ({ as }) => {
  const bieb = await as('bieb');
  await openPickups(bieb, 'S-01');
  const slots = bieb.locator('[data-testid="stores.pickups.slot.taken"], [data-testid="stores.pickups.slot.free"]');
  await expect(slots.first()).toBeVisible();
  for (const name of await slots.evaluateAll((els) => els.map((el) => el.getAttribute('aria-label') ?? ''))) {
    expect(name).toMatch(/bestätigt|unbestätigt|frei/i);
  }
  const confirms = bieb.getByTestId('stores.pickups.slot.confirm');
  await expect(confirms.first()).toBeVisible();
  for (const name of await confirms.evaluateAll((els) => els.map((el) => el.getAttribute('aria-label') ?? ''))) {
    expect(name).toMatch(/^.+ bestätigen$/);
  }
});

test('PU3 sign-up before horizon opens is refused @PU3', async ({ api, store }) => {
  // S-07's prefetch horizon is 14 days: a Wednesday 5 weeks ahead is not open yet
  const later = zurichPickupAt(37, '20:30');
  const response = await api.post(`/api/stores/${store.id('S-07')}/pickups/${encodeURIComponent(later)}/signups`);
  expect(response.status()).toBe(409);
  expect((await response.json()).code).toBe('PICKUP_NOT_ELIGIBLE');
});
