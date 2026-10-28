import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, test } from '../../fixtures';
import { stubPush } from './_push';

/** N4: web push on this device (fake browser push, server in LOG mode) and the dashboard opt-in card. */
test.use({ seedReset: 'test' });

test('N4 enable push on this device, test push is captured, unsubscribe @N4 @page', async ({ as, apiOf, notifications }) => {
  // jumper@local has no push subscription in the seed (foodsaver@local has one)
  const page = await as('jumper');
  await acceptPendingConsents(apiOf(page));
  await stubPush(page);
  await page.goto('/account/notifications');
  const card = page.getByTestId('notifications.push.card');
  await expect(card).toHaveAttribute('data-state', 'off');
  await expect(page.getByTestId('notifications.push.logMode')).toBeVisible();
  // push cannot be chosen per category before a device is subscribed
  await expect(page.getByTestId('notifications.settings.toggle.PICKUPS.push').locator('input')).toBeDisabled();

  await page.getByTestId('notifications.push.enable').click();
  await expect(card).toHaveAttribute('data-state', 'on');
  await expect(page.getByTestId('notifications.settings.toggle.PICKUPS.push').locator('input')).toBeEnabled();

  await notifications.clearNotifications();
  await page.getByTestId('notifications.push.test').click();
  await expect.poll(async () => (await notifications.pushes('jumper')).length).toBeGreaterThan(0);
  const [push] = await notifications.pushes('jumper');
  expect(push.target).toContain('push.e2e.invalid');

  // the subscription survives a reload, then is removed on this device and on the server
  await page.reload();
  await expect(card).toHaveAttribute('data-state', 'on');
  await page.getByTestId('notifications.push.disable').click();
  await expect(card).toHaveAttribute('data-state', 'off');
  await expect(page.getByTestId('notifications.settings.toggle.PICKUPS.push').locator('input')).toBeDisabled();
});

test('N4 denied permission shows the blocked state @N4', async ({ as }) => {
  const page = await as('foodsaver');
  await stubPush(page, 'denied');
  await page.goto('/account/notifications');
  await page.getByTestId('notifications.push.enable').click();
  await expect(page.getByTestId('notifications.push.error')).toBeVisible();
  await expect(page.getByTestId('notifications.push.card')).toHaveAttribute('data-state', 'denied');
});

/** The opt-in card (deferred until idle) counted this browser session. */
function counted(page: Page): Promise<string | null> {
  return page.evaluate(() => sessionStorage.getItem('fs.pushPrompt.counted'));
}

test('N4 opt-in card appears once and can be dismissed @N4', async ({ as }) => {
  const page = await as('foodsaver');
  await stubPush(page);
  // first session: no card yet
  await page.goto('/dashboard');
  await expect(page.getByTestId('dashboard.page.nextStep')).toBeVisible();
  await expect(page.getByTestId('notifications.optIn.card')).toHaveCount(0);
  await expect.poll(() => counted(page)).toBe('1');

  // second session (new browser session, same device storage): the card appears
  await page.evaluate(() => sessionStorage.clear());
  await page.reload();
  const card = page.getByTestId('notifications.optIn.card');
  await expect(card).toBeVisible();
  await page.getByTestId('notifications.optIn.later').click();
  await expect(card).toHaveCount(0);

  // never again on this device
  await page.evaluate(() => sessionStorage.clear());
  await page.reload();
  await expect(page.getByTestId('dashboard.page.nextStep')).toBeVisible();
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem('fs.pushPrompt') ?? '{}').done)).toBe('dismissed');
  await expect(page.getByTestId('notifications.optIn.card')).toHaveCount(0);
});

test('N4 opt-in card enables push in one click @N4', async ({ as, notifications }) => {
  const page = await as('foodsaver');
  await stubPush(page);
  await page.addInitScript(() => localStorage.setItem('fs.pushPrompt', JSON.stringify({ visits: 1 })));
  await page.goto('/dashboard');
  await page.getByTestId('notifications.optIn.enable').click();
  await expect(page.getByTestId('notifications.optIn.card')).toHaveCount(0);
  await page.goto('/account/notifications');
  await expect(page.getByTestId('notifications.push.card')).toHaveAttribute('data-state', 'on');
  await notifications.clearNotifications();
});
