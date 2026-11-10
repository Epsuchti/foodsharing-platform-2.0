import type { Page } from '@playwright/test';
import { expect, expectDataState, test } from '../../fixtures';

/**
 * Cards below the fold load when their placeholder comes into view (`@defer (on viewport)`). The placeholder is
 * replaced as soon as it is seen, possibly while it is being scrolled to, so retry until the card exists.
 */
async function reveal(page: Page, card: 'help' | 'events' | 'stores') {
  const element = page.getByTestId(`dashboard.${card}`);
  await expect(async () => {
    if (!(await element.count())) await page.locator(`.defer-slot.o-${card}`).scrollIntoViewIfNeeded({ timeout: 1_000 });
    await expect(element).toBeAttached({ timeout: 1_000 });
  }).toPass();
  await element.scrollIntoViewIfNeeded();
  return element;
}

/**
 * Home cards (owner feedback 2026-11-04): help needed soon, waiting for you, upcoming events, your stores. Seed:
 * foodsaver has 18 open options, one unconfirmed pickup, 2 unread conversations, events and five teams with free
 * slots.
 */

test.use({ role: 'foodsaver', seedReset: 'test' });

test('A19 home cards link to their pages and help signs up inline @A19 @PU7', async ({ page }) => {
  await page.goto('/dashboard');

  const waiting = page.getByTestId('dashboard.waiting');
  await expectDataState(page, 'dashboard.waiting.state', 'success');
  await expect(waiting.getByTestId('dashboard.waiting.unread')).toHaveAttribute('data-count', '2');
  await expect(waiting.getByTestId('dashboard.waiting.unconfirmed')).toHaveAttribute('href', '/pickups#mine');

  const help = await reveal(page, 'help');
  await expectDataState(page, 'dashboard.help.state', 'success');
  await expect(help.getByTestId('dashboard.help.option')).toHaveCount(3);
  await expect(help.getByTestId('dashboard.help.all')).toHaveAttribute('href', '/pickups#options');

  const events = await reveal(page, 'events');
  await expectDataState(page, 'dashboard.events.state', 'success');
  await expect(events.getByTestId('dashboard.events.event')).toHaveCount(3);
  await expect(events.locator('[data-testid="dashboard.events.event"][data-status="WONT_JOIN"]')).toHaveCount(0);

  const stores = await reveal(page, 'stores');
  await expectDataState(page, 'dashboard.stores.state', 'success');
  const statuses = await stores.getByTestId('dashboard.stores.store').evaluateAll((rows) => rows.map((row) => row.getAttribute('data-status')));
  expect(statuses.length).toBeGreaterThan(0);
  expect(statuses.indexOf('RED')).toBe(0);

  // sign up from the help card: the same dialog as /pickups
  await help.scrollIntoViewIfNeeded();
  const first = help.getByTestId('dashboard.help.option').first();
  const pickupAt = await first.getAttribute('data-pickup-at');
  await first.getByTestId('dashboard.help.signUp').click();
  await expectDataState(page, 'pickups.signup.state', 'success');
  await page.getByTestId('pickups.signup.confirm').click();
  // review fix A2: the card reloads and shows the own sign-up as a state instead of "Eintragen"
  const signedUp = help.locator(`[data-testid="dashboard.help.option"][data-pickup-at="${pickupAt}"]`);
  await expect(signedUp.getByTestId('dashboard.help.signedUp')).toHaveText('Eingetragen · noch unbestätigt');
  await expect(signedUp.getByTestId('dashboard.help.signUp')).toHaveCount(0);
});

test('A19 a newcomer sees no pickup cards on the home screen @A19', async ({ as }) => {
  const pending = await as('pending');
  await pending.goto('/dashboard');
  await expect(pending.getByTestId('dashboard.page.welcome')).toBeVisible();
  for (const card of ['dashboard.pickups', 'dashboard.help', 'dashboard.stores']) {
    await expect(pending.getByTestId(card), card).toHaveCount(0);
  }
});
