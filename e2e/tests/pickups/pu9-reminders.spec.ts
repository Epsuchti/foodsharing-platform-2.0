import { S01_THURSDAY, S04_WEDNESDAY, expect, test } from '../../fixtures';

/**
 * PU9 pickup reminders: the hourly job reminds sign-ups 23–24 h ahead (bell + mail). foodsaver is signed up for S-04
 * Wednesday 2026-11-04 18:30 Zürich (17:30Z) and S-01 Thursday 2026-11-05 19:00 (18:00Z). Disabled in the account
 * settings → the run at 2026-11-03T18:00Z sends nothing for S-04 (and marks it handled); enabled again → the run at
 * 2026-11-04T18:30Z reminds of S-01.
 */
const NOW = '2026-11-03T18:00:00Z';
const LATER = '2026-11-04T18:30:00Z';

interface BellPage {
  items: { type: string }[];
}

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver', seedReset: 'test' });

  test('PU9 reminder bell and mail 24 h before, disabled by setting @PU9', async ({ page, api, dev, clock, mails }) => {
    expect(Date.parse(S04_WEDNESDAY) - Date.parse(NOW)).toBeLessThanOrEqual(24 * 3_600_000);
    const reminders = async () =>
      (await api.getJson<BellPage>('/api/bells', { params: { size: 50 } })).items.filter((b) => b.type === 'PICKUP_REMINDER').length;
    const runJob = async () => expect((await api.post('/api/dev/jobs/pickup-reminder/run')).ok()).toBe(true);

    // disable in the account settings
    await page.goto('/account/settings');
    const toggle = page.getByTestId('pickups.reminders.toggle');
    await expect(page.getByTestId('pickups.reminders.state')).toHaveAttribute('data-state', 'success');
    await expect(toggle.locator('input')).toBeChecked();
    await toggle.click();
    await expect(toggle.locator('input')).not.toBeChecked();
    await expect.poll(async () => (await api.getJson<{ pickupRemindersEnabled?: boolean }>('/api/users/me')).pickupRemindersEnabled).toBe(false);

    await clock.fixServer(NOW);
    const before = await reminders();
    await runJob();
    expect(await reminders()).toBe(before);

    // enable again: reminded on the next run
    await page.reload();
    await expect(toggle.locator('input')).not.toBeChecked();
    await toggle.click();
    await expect.poll(async () => (await api.getJson<{ pickupRemindersEnabled?: boolean }>('/api/users/me')).pickupRemindersEnabled).toBe(true);
    expect(Date.parse(S01_THURSDAY) - Date.parse(LATER)).toBeLessThanOrEqual(24 * 3_600_000);
    await clock.fixServer(LATER);
    await runJob();
    await expect.poll(reminders).toBeGreaterThan(before);
    const me = await dev.user('foodsaver');
    await mails.waitForMail({ to: me.email, sentAfter: LATER });
  });
});
