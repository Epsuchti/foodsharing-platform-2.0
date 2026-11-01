import { S01_THURSDAY, S02_SATURDAY, S07_WEDNESDAY, expect, expectDataState, seedStore, test } from '../../fixtures';

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

  await page.goto('/pickups/mine');
  await expectDataState(page, 'pickups.mine.state', 'success');
  await expect(page.locator('[data-testid="pickups.mine.pickup"][data-store="Hiltl"]')).toHaveAttribute('data-confirmed', 'false');
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
