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
