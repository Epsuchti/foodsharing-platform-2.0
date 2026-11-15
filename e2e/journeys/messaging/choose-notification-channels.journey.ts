import type { Page } from '@playwright/test';
import { acceptPendingConsents } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { stubPush } from '../../fixtures/flows/notifications';
import { Walk } from '../../fixtures/persona';

/**
 * Jonas (jumper@local, no push subscription in the seed) chooses how he is notified (N4, N5, A10): on his second
 * visit the dashboard offers push and he turns it on in one click (fake browser push, server in LOG mode: the push is
 * captured), sends himself a test push, links Telegram through the bot's /start token (the "Start" in Telegram is
 * simulated), gets the test message there, chooses Telegram for pickups and turns pickup mails off (the bell stays
 * locked on), and finally unlinks Telegram and turns push off on this device again. Rules: when the opt-in card shows
 * and the device push states (incl. a denied permission) are client unit tests (notifications.spec), the settings
 * matrix, defaults and which channel delivers what are NotificationSettingsServiceTest and ChannelDispatcherTest,
 * the link tokens TelegramLinkServiceTest, subscriptions PushSubscriptionServiceTest.
 */
test.use({ role: 'jumper' });

/** The Telegram waiting text names the token's expiry time (browser clock). */
const clockTexts = (page: Page) => [page.getByTestId('notifications.telegram.waiting'), page.getByTestId('notifications.telegram.token')];

test('jumper turns on push and Telegram, sets pickup channels and turns them off again @N4 @N5 @A10', async ({
  page,
  api,
  notifications,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'jumper');
  await acceptPendingConsents(api);
  await stubPush(page);
  // his second visit on this device: the opt-in card shows from the second browser session on
  await page.addInitScript(() => {
    if (!localStorage.getItem('fs.pushPrompt')) localStorage.setItem('fs.pushPrompt', JSON.stringify({ visits: 1 }));
  });
  const pushCard = page.getByTestId('notifications.push.card');
  const telegramCard = page.getByTestId('notifications.telegram.card');
  const pickupsPush = page.getByTestId('notifications.settings.toggle.PICKUPS.push');

  await walk.step('turns on push from the dashboard', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    const card = page.getByTestId('notifications.optIn.card');
    await expect(card).toBeVisible();
    await checkpoint(page, 'push-opt-in');
    await walk.click(page.getByTestId('notifications.optIn.enable'));
    await expect(card).toHaveCount(0);
  });

  await walk.step('sends himself a test push', async () => {
    await walk.nav('notifications');
    await expect(page).toHaveURL(/\/account\/notifications$/);
    await expect(pushCard).toHaveAttribute('data-state', 'on');
    await expect(page.getByTestId('notifications.push.logMode')).toBeVisible();
    await expect(pickupsPush.locator('input')).toBeEnabled();
    await notifications.clearNotifications();
    await walk.click(page.getByTestId('notifications.push.test'));
    await expect.poll(async () => (await notifications.pushes('jumper')).length).toBeGreaterThan(0);
    expect((await notifications.pushes('jumper'))[0].target).toContain('push.e2e.invalid');
    await checkpoint(page, 'push-on');
  });

  await walk.step('links Telegram and gets the test message there', async () => {
    await expect(telegramCard).toHaveAttribute('data-linked', 'false');
    await expect(page.getByTestId('notifications.settings.toggle.PICKUPS.telegram').locator('input')).toBeDisabled();
    await walk.click(page.getByTestId('notifications.telegram.connect'));
    await expect(page.getByTestId('notifications.telegram.waiting')).toBeVisible();
    const deepLink = page.getByTestId('notifications.telegram.deepLink');
    await expect(deepLink).toHaveAttribute('href', /start=/);
    const token = new URL((await deepLink.getAttribute('href'))!).searchParams.get('start')!;
    await expect(page.getByTestId('notifications.telegram.token')).toContainText(token);
    await checkpoint(page, 'telegram-waiting', { mask: clockTexts(page) });

    // he presses "Start" in Telegram: the page notices by itself
    await notifications.simulateTelegramStart(token, '777001');
    await expect(telegramCard).toHaveAttribute('data-linked', 'true', { timeout: 15_000 });
    await expect(page.getByTestId('notifications.telegram.linked')).toBeVisible();
    await notifications.clearNotifications();
    await walk.click(page.getByTestId('notifications.telegram.test'));
    await expect.poll(async () => (await notifications.telegram('jumper')).length).toBeGreaterThan(0);
    expect((await notifications.telegram('jumper'))[0].target).toBe('777001');
  });

  await walk.step('chooses Telegram instead of mail for pickups', async () => {
    const telegram = page.getByTestId('notifications.settings.toggle.PICKUPS.telegram');
    await expect(telegram.locator('input')).toBeEnabled();
    const before = await telegram.getAttribute('data-checked');
    await walk.click(telegram);
    await expect(telegram).toHaveAttribute('data-checked', before === 'true' ? 'false' : 'true');
    const mail = page.getByTestId('notifications.settings.toggle.PICKUPS.mail');
    await expect(mail).toHaveAttribute('data-checked', 'true');
    await walk.click(mail);
    await expect(mail).toHaveAttribute('data-checked', 'false');
    // the bell stays locked on
    await expect(page.locator('[data-testid="notifications.settings.row"][data-category="PICKUPS"] .pi-lock')).toBeVisible();
    await checkpoint(page, 'pickup-channels');
    await page.reload();
    await expect(page.getByTestId('notifications.settings.toggle.PICKUPS.mail')).toHaveAttribute('data-checked', 'false');
    await expect(telegram).toHaveAttribute('data-checked', before === 'true' ? 'false' : 'true');
  });

  await walk.step('unlinks Telegram and turns push off on this device', async () => {
    await walk.click(page.getByTestId('notifications.telegram.disconnect'));
    await expect(telegramCard).toHaveAttribute('data-linked', 'false');
    await expect(page.getByTestId('notifications.telegram.connect')).toBeVisible();
    await expect(pushCard).toHaveAttribute('data-state', 'on');
    await walk.click(page.getByTestId('notifications.push.disable'));
    await expect(pushCard).toHaveAttribute('data-state', 'off');
    await expect(pickupsPush.locator('input')).toBeDisabled();
    await checkpoint(page, 'channels-off');
  });

  walk.finish({ steps: 5, clicks: 10, fields: 0 });
});
