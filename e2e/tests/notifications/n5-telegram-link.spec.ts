import { S07_WEDNESDAY, acceptPendingConsents, expect, expectErrorState, injectApiError, test } from '../../fixtures';

/** N5 (user side): Telegram linking through the bot's /start token, test message, unlink; per-category channels. */
test.use({ seedReset: 'test' });

test('N5 link Telegram via /start token, receive test message, unlink @N5 @page', async ({ as, notifications }) => {
  const page = await as('foodsaver');
  await page.goto('/account/notifications');
  const card = page.getByTestId('notifications.telegram.card');
  await expect(card).toHaveAttribute('data-linked', 'false');
  await expect(page.getByTestId('notifications.settings.toggle.PICKUPS.telegram').locator('input')).toBeDisabled();

  await page.getByTestId('notifications.telegram.connect').click();
  await expect(page.getByTestId('notifications.telegram.waiting')).toBeVisible();
  await expect(page.getByTestId('notifications.telegram.deepLink')).toHaveAttribute('href', /start=/);
  const href = (await page.getByTestId('notifications.telegram.deepLink').getAttribute('href'))!;
  const token = new URL(href).searchParams.get('start')!;
  await expect(page.getByTestId('notifications.telegram.token')).toContainText(token);
  expect(token.length).toBeGreaterThan(8);

  // the user presses "Start" in Telegram: the page notices by itself
  await notifications.simulateTelegramStart(token, '777001');
  await expect(card).toHaveAttribute('data-linked', 'true', { timeout: 15_000 });
  await expect(page.getByTestId('notifications.telegram.linked')).toBeVisible();

  await notifications.clearNotifications();
  await page.getByTestId('notifications.telegram.test').click();
  await expect.poll(async () => (await notifications.telegram('foodsaver')).length).toBeGreaterThan(0);
  expect((await notifications.telegram('foodsaver'))[0].target).toBe('777001');

  // Telegram can now be chosen per category
  const pickupsTelegram = page.getByTestId('notifications.settings.toggle.PICKUPS.telegram');
  await expect(pickupsTelegram.locator('input')).toBeEnabled();
  const before = await pickupsTelegram.getAttribute('data-checked');
  await pickupsTelegram.click();
  await expect(pickupsTelegram).toHaveAttribute('data-checked', before === 'true' ? 'false' : 'true');

  await page.getByTestId('notifications.telegram.disconnect').click();
  await expect(card).toHaveAttribute('data-linked', 'false');
  await expect(page.getByTestId('notifications.telegram.connect')).toBeVisible();
});

test('N5 notification settings turn mail off for pickups @N5 @N6 @A10', async ({ as, apiOf, store, notifications }) => {
  // welcome@local manages S-07: a sign-up there is a PICKUPS bell (+ mail by default) for the managers
  const page = await as('welcome');
  await acceptPendingConsents(apiOf(page));
  await page.goto('/account/notifications');
  const mail = page.getByTestId('notifications.settings.toggle.PICKUPS.mail');
  await expect(mail).toHaveAttribute('data-checked', 'true');
  await mail.click();
  await expect(mail).toHaveAttribute('data-checked', 'false');
  await page.reload();
  await expect(page.getByTestId('notifications.settings.toggle.PICKUPS.mail')).toHaveAttribute('data-checked', 'false');
  // the bell stays locked on
  await expect(page.locator('[data-testid="notifications.settings.row"][data-category="PICKUPS"] .pi-lock')).toBeVisible();

  const bellsBefore = (await notifications.bells('welcome', 'PICKUP_SIGNUP_UNCONFIRMED')).length;
  const mailsBefore = (await notifications.mails('welcome', 'PICKUP_SIGNUP_UNCONFIRMED')).length;
  await store.signUp('foodsaver', 'S-07', S07_WEDNESDAY);
  await expect.poll(async () => (await notifications.bells('welcome', 'PICKUP_SIGNUP_UNCONFIRMED')).length).toBe(bellsBefore + 1);
  expect((await notifications.mails('welcome', 'PICKUP_SIGNUP_UNCONFIRMED')).length).toBe(mailsBefore);
});

test('N5 notification settings error states @N4 @N5', async ({ as }) => {
  const page = await as('foodsaver');
  await injectApiError(page, { url: '**/api/users/me/notification-settings', method: 'GET', status: 500 });
  await page.goto('/account/notifications');
  await expectErrorState(page, 'notifications.settings.state');

  // a failed save rolls the switch back
  const fresh = await as('foodsaver');
  await injectApiError(fresh, { url: '**/api/users/me/notification-settings', method: 'PUT', status: 409 });
  await injectApiError(fresh, { url: '**/api/users/me/notification-settings', method: 'PATCH', status: 409 });
  await fresh.goto('/account/notifications');
  const mail = fresh.getByTestId('notifications.settings.toggle.REGION.mail');
  const before = await mail.getAttribute('data-checked');
  await mail.click();
  await expect(mail).toHaveAttribute('data-checked', before!);

  // Telegram link token failure keeps the connect button
  await injectApiError(fresh, { url: '**/api/telegram/link-token', status: 503 });
  await fresh.getByTestId('notifications.telegram.connect').click();
  await expect(fresh.getByTestId('notifications.telegram.connect')).toBeVisible();
  await expect(fresh.getByTestId('notifications.telegram.waiting')).toHaveCount(0);
});
