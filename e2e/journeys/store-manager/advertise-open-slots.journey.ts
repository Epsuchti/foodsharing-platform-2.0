import { type ApiClient, type DevNotification, expectDataState, zurichPickupAt } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { S06, openAutomation, saveSection, setSwitch, typeNumber } from '../../fixtures/flows/automation';
import { openPickups, pickupRow } from '../../fixtures/flows/pickups';
import { Walk } from '../../fixtures/persona';

/**
 * Store manager Nina (bieb@local, manager of S-06 Babu's Bakery) gets free places filled: she sets the live advert #1
 * (AU4) to 48 hours with a Telegram text and sends it right away to the region chat, adds the team's Telegram group as
 * a store chat and sends it a test message (Kanäle, AU10/N5), reads the sign-out Marco left without a replacement
 * (AU5), sees the extra place Marco added as the first sign-up and lowers the store's limit (AU6), and switches on the
 * automatic acceptance of applications within 5 km (AU3). The advert, alert, extra-slot and request rules are unit
 * tests (OpenSlotAdvertRunnerTest, SignoutAlertServiceTest, ExtraSlotRuleTest, RequestPolicyRunnerTest).
 */
test.use({ role: 'bieb' });

const TUESDAY = zurichPickupAt(1, '19:00');

async function telegramTo(api: ApiClient, chatId: string): Promise<DevNotification[]> {
  const all = await api.getJson<DevNotification[]>('/api/dev/notifications?channel=TELEGRAM');
  return all.filter((n) => n.target === chatId);
}

test('store manager advertises free places, adds a store chat and reads the sign-out alerts @AU3 @AU4 @AU5 @AU6 @AU10 @N5', async ({
  page,
  api,
  as,
  store,
  notifications,
}, testInfo) => {
  test.setTimeout(180_000);
  const walk = new Walk(page, testInfo, 'store manager');
  await notifications.clearNotifications();

  await walk.step('sends the advert for free places to the region chat', async () => {
    await openAutomation(page, 'S-06', 'adverts');
    const card = page.locator('[data-testid="automation.adverts.card"][data-number="1"]');
    await expect(card).toHaveAttribute('data-enabled', 'true');
    await expectDataState(page, 'automation.adverts.plannedState', 'success');
    await expect(page.getByTestId('automation.adverts.plannedRow').first()).toBeVisible();
    await typeNumber(card.getByTestId('automation.adverts.trigger'), 48);
    walk.count(1);
    await walk.click(card.getByTestId('automation.adverts.addMessage.TELEGRAM'));
    await walk.fill(card.getByTestId('automation.adverts.message.TELEGRAM.1.text'), 'Noch Platz bei ');
    await walk.click(card.getByTestId('automation.adverts.message.TELEGRAM.1.placeholder.storeName'));
    await expect(card.getByTestId('automation.adverts.message.TELEGRAM.1.preview')).toContainText("Babu's Bakery");
    // "jetzt senden" works on the saved adverts: blocked while the form has unsaved changes
    const run = page.getByTestId('automation.adverts.run').locator('button');
    await expect(run).toBeDisabled();
    await expect(page.getByTestId('automation.adverts.saveFirst')).toBeVisible();
    await checkpoint(page, 'advert-unsaved');
    await walk.click(card.getByTestId('automation.adverts.save'));
    await expect(card.getByTestId('automation.adverts.save').locator('button')).toBeDisabled();
    await expect(run).toBeEnabled();
    await expect(page.getByTestId('automation.adverts.saveFirst')).toBeHidden();
    await walk.click(run);
    await expect(page.getByTestId('automation.adverts.runResult')).toBeVisible();
    await expect.poll(async () => (await telegramTo(api, '-1001')).length, { timeout: 15_000 }).toBeGreaterThan(0);
    expect((await telegramTo(api, '-1001')).some((n) => (n.body ?? '').includes("Babu's Bakery"))).toBe(true);
  });

  // the bot has seen the team's Telegram group (dev: a simulated update)
  const incoming = await api.fetch('POST', '/api/dev/telegram/incoming', { chatId: '-2006', text: 'Hallo Bot', title: 'Babu Team' });
  expect(incoming.status(), await incoming.text()).toBe(204);

  await walk.step('adds the team group as a store chat and tests it', async () => {
    await walk.click(page.getByTestId('automation.store.nav.channels'));
    await expectDataState(page, 'automation.channels.state', 'success');
    await expect(page.getByTestId('automation.store.summary.channels')).toHaveAttribute('data-count', '0');
    await expect(page.getByTestId('automation.channels.none')).toBeVisible();
    // who sends here: the Telegram advert and the sign-out alert
    await expect(page.getByTestId('automation.channels.user')).toHaveCount(2);
    await walk.click(page.getByTestId('automation.channels.pick'));
    await walk.click(page.locator('.p-select-overlay .p-select-option').filter({ hasText: 'Babu Team' }));
    await walk.click(page.getByTestId('automation.channels.add'));
    const saved = page.waitForResponse((r) => r.request().method() === 'PUT' && r.url().endsWith('/telegram-chats'));
    await walk.click(page.getByTestId('automation.channels.save'));
    expect((await saved).ok()).toBe(true);
    await expect(page.getByTestId('automation.channels.save').locator('button')).toBeDisabled();
    await expect(page.getByTestId('automation.store.summary.channels')).toHaveAttribute('data-count', '1');
    await expect(page.getByTestId('automation.store.nav.channels')).toContainText('1');
    const added = page.locator('[data-testid="automation.channels.chat"][data-chat-id="-2006"]');
    const tested = page.waitForResponse((r) => r.request().method() === 'POST' && r.url().endsWith('/telegram-chats/test'));
    await walk.click(added.getByTestId('automation.channels.test'));
    expect((await tested).ok()).toBe(true);
    await expect
      .poll(async () => (await telegramTo(api, '-2006')).length, { timeout: 15_000 })
      .toBeGreaterThan(0);
    await checkpoint(page, 'store-chats');
  });

  await test.step('Marco signs up for Tuesday and adds the two extra places', async () => {
    await store.signUp('sleepy', 'S-06', TUESDAY);
    const sleepy = await as('sleepy');
    await openPickups(sleepy, 'S-06');
    const row = pickupRow(sleepy, TUESDAY);
    await expect(row).toHaveAttribute('data-slots', '2');
    await row.getByTestId('stores.pickups.addExtraSlot').click();
    await expect(row).toHaveAttribute('data-slots', '3');
    await row.getByTestId('stores.pickups.addExtraSlot').click();
    await expect(row).toHaveAttribute('data-slots', '4');
    await expect(row.getByTestId('stores.pickups.addExtraSlot')).toHaveCount(0);
    await expect(row.getByTestId('stores.pickups.extraSlot').first()).toContainText('Marco Bernasconi');
    await sleepy.close();
  });

  await walk.step('sees the extra places and lowers the limit', async () => {
    // Nina is not the first sign-up: no extra place for her
    await openPickups(page, 'S-06');
    await expect(pickupRow(page, TUESDAY).getByTestId('stores.pickups.addExtraSlot')).toHaveCount(0);
    await openAutomation(page, 'S-06', 'extra-slot');
    const input = page.getByTestId('automation.extraSlot.max').locator('input');
    await expect(input).toHaveValue('2');
    await expect(page.locator('[data-testid="automation.extraSlot.audits.row"][data-kind="EXTRA_SLOT"]').first()).toBeVisible();
    await expect(page.getByTestId('automation.extraSlot.save').locator('button')).toBeDisabled();
    await walk.fill(input, '1');
    await input.blur();
    await checkpoint(page, 'extra-places');
    await walk.click(page.getByTestId('automation.extraSlot.save').locator('button'));
    await expect(page.getByTestId('automation.extraSlot.save').locator('button')).toBeDisabled();
    await page.reload();
    await expect(page.getByTestId('automation.extraSlot.max').locator('input')).toHaveValue('1');
  });

  await test.step('Marco signs out of Tuesday again', async () => {
    await store.withRole('sleepy', async (client) => {
      const me = await client.getJson<{ user: { id: string } }>('/api/auth/session');
      const response = await client.fetch('DELETE', `/api/stores/${S06.id}/pickups/${encodeURIComponent(TUESDAY)}/signups/${me.user.id}`);
      expect(response.status(), await response.text()).toBeLessThan(300);
    });
    // within the 72 h cutoff the managers are alerted at once (bell)
    await expect.poll(async () => (await notifications.bells('bieb')).length, { timeout: 15_000 }).toBeGreaterThan(0);
  });

  await walk.step('reads the sign-out alerts', async () => {
    await walk.click(page.getByTestId('automation.store.nav.signout-alerts'));
    await expect(page.getByTestId('automation.signout.root')).toHaveAttribute('data-enabled', 'true');
    await expectDataState(page, 'automation.signout.state', 'success');
    // Tuesday's sign-out: still open (the daily job reports it if nobody takes over), all four places free
    const marco = page.getByTestId('automation.signout.row').filter({ hasText: '03.11.2026, 19:00' }).filter({ hasText: 'Marco Bernasconi' });
    await expect(marco).toHaveCount(1);
    await expect(marco).toHaveAttribute('data-status', 'OPEN');
    await expect(marco).toContainText('4 von 4 frei');
    await checkpoint(page, 'signout-alerts');
  });

  await walk.step('accepts applications within 5 km automatically', async () => {
    await walk.click(page.getByTestId('automation.store.nav.auto-accept'));
    await expect(page.getByTestId('automation.request.root')).toHaveAttribute('data-enabled', 'false');
    for (const gone of ['dryRun', 'dryRunBanner', 'runDry', 'run', 'runResult', 'saveFirst']) {
      await expect(page.getByTestId(`automation.request.${gone}`)).toHaveCount(0);
    }
    await setSwitch(page.getByTestId('automation.request.enabled'), true);
    await setSwitch(page.getByTestId('automation.request.distanceEnabled'), true);
    walk.count(2);
    await typeNumber(page.getByTestId('automation.request.km'), 5);
    walk.count(1);
    await checkpoint(page, 'auto-accept');
    await saveSection(page, 'automation.request');
    walk.count(1);
    await expect(page.getByTestId('automation.request.root')).toHaveAttribute('data-enabled', 'true');
  });

  walk.finish({ steps: 5, clicks: 18, fields: 2 });
});
