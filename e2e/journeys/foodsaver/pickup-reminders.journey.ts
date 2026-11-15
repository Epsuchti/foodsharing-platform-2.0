import { S01_THURSDAY, S04_WEDNESDAY } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { bellCount } from '../../fixtures/flows/pickups';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local) turns the pickup reminders off in her account settings and on again (PU9). The
 * hourly job reminds sign-ups 23–24 h ahead with a bell and a mail. Seed: S-04 Wednesday 2026-11-04 18:30 Zürich
 * (17:30Z) and S-01 Thursday 2026-11-05 19:00 (18:00Z). Reminders off → the run at 2026-11-03T18:00Z sends nothing
 * for Wednesday (and marks it handled); on again → the run at 2026-11-04T18:30Z reminds her of Thursday.
 */
test.use({ role: 'foodsaver' });

const NOW = '2026-11-03T18:00:00Z';
const LATER = '2026-11-04T18:30:00Z';

test('foodsaver switches pickup reminders off and on and is reminded of her next pickup @PU9 @N3', async ({
  page,
  api,
  dev,
  clock,
  mails,
}, testInfo) => {
  const walk = new Walk(page, testInfo, 'foodsaver');
  expect(Date.parse(S04_WEDNESDAY) - Date.parse(NOW)).toBeLessThanOrEqual(24 * 3_600_000);
  expect(Date.parse(S01_THURSDAY) - Date.parse(LATER)).toBeLessThanOrEqual(24 * 3_600_000);
  const runJob = async () => expect((await api.post('/api/dev/jobs/pickup-reminder/run')).ok()).toBe(true);
  const enabled = async () => (await api.getJson<{ pickupRemindersEnabled?: boolean }>('/api/users/me')).pickupRemindersEnabled;
  const toggle = page.getByTestId('pickups.reminders.toggle');
  const before = await bellCount(api, 'PICKUP_REMINDER');

  try {
    await walk.step('switches the reminders off in her settings', async () => {
      await page.goto('/account/settings');
      await expect(page.getByTestId('pickups.reminders.state')).toHaveAttribute('data-state', 'success');
      await expect(toggle.locator('input')).toBeChecked();
      await toggle.scrollIntoViewIfNeeded();
      await checkpoint(page, 'settings-reminders-on');
      await walk.click(toggle);
      await expect(toggle.locator('input')).not.toBeChecked();
      await expect.poll(enabled).toBe(false);
      // the job runs the evening before Wednesday's pickup: nothing for her
      await clock.fixServer(NOW);
      await runJob();
      expect(await bellCount(api, 'PICKUP_REMINDER')).toBe(before);
    });

    await walk.step('switches them on again', async () => {
      await page.reload();
      await expect(page.getByTestId('pickups.reminders.state')).toHaveAttribute('data-state', 'success');
      await expect(toggle.locator('input')).not.toBeChecked();
      await checkpoint(page, 'settings-reminders-off');
      await walk.click(toggle);
      await expect(toggle.locator('input')).toBeChecked();
      await expect.poll(enabled).toBe(true);
    });

    await walk.step('is reminded of Thursday by bell and mail', async () => {
      await clock.fixServer(LATER);
      await runJob();
      await expect.poll(() => bellCount(api, 'PICKUP_REMINDER')).toBeGreaterThan(before);
      const me = await dev.user('foodsaver');
      await mails.waitForMail({ to: me.email, sentAfter: LATER });
      await clock.fixServer();
      await page.goto('/dashboard');
      await openNewestBell(walk, api, 'PICKUP_REMINDER');
      await checkpoint(page, 'reminder-opened');
    });
  } finally {
    await clock.fixServer();
  }

  // the phone opens bells through the menu drawer (one tap more)
  walk.finish({ steps: 3, clicks: 5, fields: 0 });
});
