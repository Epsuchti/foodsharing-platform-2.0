import type { Page } from '@playwright/test';
import { expect, expectDataState, test } from '../../fixtures';

/** Cards below the fold load when their placeholder comes into view (`@defer (on viewport)`): scroll to it first. */
async function reveal(page: Page, card: 'help' | 'events' | 'stores') {
  const placeholder = page.locator(`.defer-slot.o-${card}`);
  if (await placeholder.count()) await placeholder.scrollIntoViewIfNeeded();
  const element = page.getByTestId(`dashboard.${card}`);
  await element.scrollIntoViewIfNeeded();
  return element;
}

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
