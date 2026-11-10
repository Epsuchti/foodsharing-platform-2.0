import { S01_THURSDAY, S02_SATURDAY, S04_WEDNESDAY, S07_WEDNESDAY, expect, expectDataState, seedStore, test, zurichPickupAt } from '../../fixtures';

/** PU7 "where can I help": free slots in my stores. Seed: S-02 Saturday is full; S-07 Wednesday 20:30 is free. */

test.use({ seedReset: 'test', role: 'foodsaver' });

test('PU7 options list free slots in my stores and hides full ones @PU7 @page', async ({ page }) => {
  await page.goto('/pickups');
  await expectDataState(page, 'pickups.options.state', 'success');
  await expect(page.locator(`[data-testid="pickups.options.option"][data-pickup-at="${S02_SATURDAY}"][data-store="Bäckerei Vohdin"]`)).toHaveCount(0);
  const hiltl = page.locator(`[data-testid="pickups.options.option"][data-store="Hiltl"][data-pickup-at="${S07_WEDNESDAY}"]`);
  await expect(hiltl).toBeVisible();
  // sorted by time
  const times = await page.getByTestId('pickups.options.option').evaluateAll((els) => els.map((el) => el.getAttribute('data-pickup-at')!));
  expect([...times].sort()).toEqual(times);

  await hiltl.getByTestId('pickups.options.signUp').click();
  await expectDataState(page, 'pickups.signup.state', 'success');
  await page.getByTestId('pickups.signup.confirm').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message-success')).toBeVisible();

  // one page (owner feedback 2026-11-04): "My pickups" above reloads with the new, unconfirmed pickup
  await expectDataState(page, 'pickups.mine.state', 'success');
  await expect(page.locator('[data-testid="pickups.mine.pickup"][data-store="Hiltl"]')).toHaveAttribute('data-confirmed', 'false');
  // review fix A2: the pickup stays in the options, now as "Eingetragen · noch unbestätigt" instead of "Eintragen"
  await expectDataState(page, 'pickups.options.state', 'success');
  await expect(hiltl.getByTestId('pickups.options.signedUp')).toHaveText('Eingetragen · noch unbestätigt');
  await expect(hiltl.getByTestId('pickups.options.signUp')).toHaveCount(0);
});

test('PU7 options leave out what the sign-up would refuse and show own sign-ups as a state @PU7', async ({ page }) => {
  // review fix A1: Zürich's region rule (2 per ±7 days) refuses S-01 on Sunday and Monday for the foodsaver (S-04
  // Wednesday and S-01 Thursday); the store page still lists them as "Gesperrt"
  const s01 = seedStore('S-01');
  const refused = [zurichPickupAt(6, '10:00'), zurichPickupAt(7, '19:00')];
  await page.goto(`/stores/${s01.id}/pickups`);
  await expectDataState(page, 'stores.pickups.state', 'success');
  for (const at of refused) {
    await expect(page.locator(`[data-testid="stores.pickups.pickup"][data-pickup-at="${at}"] [data-testid="stores.pickups.slot.blocked"]`)).toBeVisible();
  }

  await page.goto('/pickups');
  await expectDataState(page, 'pickups.options.state', 'success');
  for (const at of refused) {
    await expect(page.locator(`[data-testid="pickups.options.option"][data-store="${s01.name}"][data-pickup-at="${at}"]`)).toHaveCount(0);
  }
  // review fix A2: own sign-ups stay, with a chip instead of the button
  const wednesday = page.locator(`[data-testid="pickups.options.option"][data-store="${seedStore('S-04').name}"][data-pickup-at="${S04_WEDNESDAY}"]`);
  await expect(wednesday.getByTestId('pickups.options.signedUp')).toHaveAttribute('data-confirmed', 'true');
  await expect(wednesday.getByTestId('pickups.options.signedUp')).toHaveText('Eingetragen · bestätigt');
  await expect(wednesday.getByTestId('pickups.options.signUp')).toHaveCount(0);
  const thursday = page.locator(`[data-testid="pickups.options.option"][data-store="${s01.name}"][data-pickup-at="${S01_THURSDAY}"]`);
  await expect(thursday.getByTestId('pickups.options.signedUp')).toHaveAttribute('data-confirmed', 'false');
  await expect(thursday.getByTestId('pickups.options.signUp')).toHaveCount(0);
});

test('PU7 options show how many sign-ups are confirmed @PU7', async ({ page }) => {
  await page.goto('/pickups');
  await expectDataState(page, 'pickups.options.state', 'success');
  // S-01 Thursday: foodsaver unconfirmed + sleepy confirmed, one slot free
  const thursday = page.locator(`[data-testid="pickups.options.option"][data-store="${seedStore('S-01').name}"][data-pickup-at="${S01_THURSDAY}"]`);
  const summary = thursday.getByTestId('pickups.options.summary');
  await expect(summary).toHaveAttribute('data-confirmed', '1');
  await expect(summary).toHaveAttribute('data-pending', '1');
  await expect(summary).toContainText('1 von 3 bestätigt');
  await expect(summary).toContainText('1 unbestätigt');
  // nobody signed up yet: no summary
  const hiltl = page.locator(`[data-testid="pickups.options.option"][data-store="Hiltl"][data-pickup-at="${S07_WEDNESDAY}"]`);
  await expect(hiltl.getByTestId('pickups.options.summary')).toHaveCount(0);
});
