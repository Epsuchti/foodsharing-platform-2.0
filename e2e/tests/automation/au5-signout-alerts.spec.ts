import { expect, expectDataState, test, zurichPickupAt } from '../../fixtures';
import { S06, openAutomation } from './_automation';

/** AU5: sign-out alerts. Seed: S-06 alert live (BELL + TELEGRAM, cutoff 72 h), five seeded cancellations. */
test.use({ role: 'bieb', seedReset: 'test' });

test('AU5 sign-out within cutoff alerts managers immediately; cancellations list shows status, free/total @AU5 @page', async ({ page, store, notifications }) => {
  const tuesday = zurichPickupAt(1, '19:00');
  await store.signUp('sleepy', 'S-06', tuesday);
  await store.withRole('sleepy', async (client) => {
    const me = await client.getJson<{ user: { id: string } }>('/api/auth/session');
    const response = await client.fetch('DELETE', `/api/stores/${S06.id}/pickups/${encodeURIComponent(tuesday)}/signups/${me.user.id}`);
    expect(response.status(), await response.text()).toBeLessThan(300);
  });
  // the manager is alerted at once (bell)
  await expect.poll(async () => (await notifications.bells('bieb')).length, { timeout: 15_000 }).toBeGreaterThan(0);

  await openAutomation(page, 'S-06', 'signout-alerts');
  await expect(page.getByTestId('automation.signout.root')).toHaveAttribute('data-enabled', 'true');
  await expectDataState(page, 'automation.signout.state', 'success');
  await expect(page.locator('[data-testid="automation.signout.row"][data-status="NOT_REPLACED"]').first()).toBeVisible();
  await expect(page.locator('[data-testid="automation.signout.row"]').filter({ hasText: 'Marco Bernasconi' }).first()).toBeVisible();
});

test('AU5 daily job reports a sign-out nobody took over (NOT_REPLACED summary) once per day @AU5', async ({ page, store, notifications, clock }) => {
  const tuesday = zurichPickupAt(1, '19:00');
  await store.signUp('sleepy', 'S-06', tuesday);
  await store.withRole('sleepy', async (client) => {
    const me = await client.getJson<{ user: { id: string } }>('/api/auth/session');
    const response = await client.fetch('DELETE', `/api/stores/${S06.id}/pickups/${encodeURIComponent(tuesday)}/signups/${me.user.id}`);
    expect(response.status(), await response.text()).toBeLessThan(300);
  });
  expect(await notifications.bells('bieb', 'PICKUP_SIGNOUT_NOT_REPLACED')).toHaveLength(0);

  // the morning after the pickup (after app.automation.signout-daily-time 03:00 Zürich): nobody took the slot over
  await clock.fixServer(zurichPickupAt(2, '05:00'));
  await notifications.runJob('signout-alerts');
  const summaries = await notifications.bells('bieb', 'PICKUP_SIGNOUT_NOT_REPLACED');
  expect(summaries).toHaveLength(1);
  expect(summaries[0].params.storeId).toBe(S06.id);
  expect(summaries[0].params.pickupAt).toBe(tuesday);
  expect(summaries[0].params.userName).toBe('Marco Bernasconi');
  expect(summaries[0].params.message).toContain('niemand hat übernommen');

  // the job runs hourly but reports once per store and day
  await notifications.runJob('signout-alerts');
  expect(await notifications.bells('bieb', 'PICKUP_SIGNOUT_NOT_REPLACED')).toHaveLength(1);

  // the cancellations list shows the final outcome
  await openAutomation(page, 'S-06', 'signout-alerts');
  await expectDataState(page, 'automation.signout.state', 'success');
  await expect(page.locator('[data-testid="automation.signout.row"][data-status="NOT_REPLACED"]').filter({ hasText: 'Marco Bernasconi' }).first()).toBeVisible();
});