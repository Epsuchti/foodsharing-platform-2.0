import { expect, expectDataState, test } from '../../fixtures';

/**
 * Home cards (owner feedback 2026-11-04): help needed soon, waiting for you, upcoming events, your stores. Seed:
 * foodsaver has 18 open options, one unconfirmed pickup, 2 unread conversations, events and five teams with free
 * slots.
 */

test.use({ role: 'foodsaver' });

test('A19 home cards link to their pages and help signs up inline @A19 @PU7', async ({ page }) => {
  await page.goto('/dashboard');

  const waiting = page.getByTestId('dashboard.waiting');
  await expectDataState(page, 'dashboard.waiting.state', 'success');
  await expect(waiting.getByTestId('dashboard.waiting.unread')).toHaveAttribute('data-count', '2');
  await expect(waiting.getByTestId('dashboard.waiting.unconfirmed')).toHaveAttribute('href', '/pickups#mine');

  const help = page.getByTestId('dashboard.help');
  await help.scrollIntoViewIfNeeded();
  await expectDataState(page, 'dashboard.help.state', 'success');
  await expect(help.getByTestId('dashboard.help.option')).toHaveCount(3);
  await expect(help.getByTestId('dashboard.help.all')).toHaveAttribute('href', '/pickups#options');

  const events = page.getByTestId('dashboard.events');
  await events.scrollIntoViewIfNeeded();
  await expectDataState(page, 'dashboard.events.state', 'success');
  await expect(events.getByTestId('dashboard.events.event')).toHaveCount(3);
  await expect(events.locator('[data-testid="dashboard.events.event"][data-status="WONT_JOIN"]')).toHaveCount(0);

  const stores = page.getByTestId('dashboard.stores');
  await stores.scrollIntoViewIfNeeded();
  await expectDataState(page, 'dashboard.stores.state', 'success');
  const statuses = await stores.getByTestId('dashboard.stores.store').evaluateAll((rows) => rows.map((row) => row.getAttribute('data-status')));
  expect(statuses.length).toBeGreaterThan(0);
  expect(statuses.indexOf('RED')).toBe(0);

  // sign up from the help card: the same dialog as /pickups
  await help.getByTestId('dashboard.help.signUp').first().click();
  await expectDataState(page, 'pickups.signup.state', 'success');
  await expect(page.getByTestId('pickups.signup.confirm')).toBeVisible();
});

test('A19 a newcomer sees no pickup cards on the home screen @A19', async ({ as }) => {
  const pending = await as('pending');
  await pending.goto('/dashboard');
  await expect(pending.getByTestId('dashboard.page.welcome')).toBeVisible();
  for (const card of ['dashboard.pickups', 'dashboard.help', 'dashboard.stores']) {
    await expect(pending.getByTestId(card), card).toHaveCount(0);
  }
});
