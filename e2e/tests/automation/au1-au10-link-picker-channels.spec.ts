import { type DevNotification, expect, expectDataState, seedRegion, seedStore, test } from '../../fixtures';
import { openAutomation, pickLinkedStore, savePolicy } from './_automation';

/**
 * Decision 167 S4: the "Betrieb verknüpfen" picker finds stores across regions (StoreVisibility) and locks "both ways"
 * for a store bieb does not manage; the store tab's status strip and the Kanäle section (store Telegram chats, "Wer
 * sendet hierhin?", "Chats verwalten"); the region overview's Kanäle column. Seed: bieb manages S-06 Babu's Bakery and
 * S-01; Coop Winterthur Bahnhof (S-23) is in the region winterthur; S-06 has the live Telegram advert #1 and the
 * sign-out alert BELL+TELEGRAM and no store chat; S-01 has the store chat -1002.
 */
test.use({ role: 'bieb', seedReset: 'test' });

const S23 = seedStore('S-23');

test('AU1 picker links a store of another region one-way; both ways is locked with the reason @AU1 @page', async ({ page }) => {
  await openAutomation(page, 'S-06', 'policy');
  await page.getByTestId('automation.policy.addLink').click();
  await pickLinkedStore(page, 0, S23.name, 'winterthur');

  const link = page.getByTestId('automation.policy.link.0');
  await expect(link.getByTestId('automation.policy.link.0.bidiLocked')).toContainText(S23.name);
  await expect(link.getByTestId('automation.policy.link.0.bidirectional').locator('input')).toBeDisabled();
  // the member sentence of the rule card is the members' summary line
  await expect(link.getByTestId('automation.policy.link.member')).toContainText(S23.name);
  // Frist nach Austragung lives in "Erweitert"
  await expect(link.getByTestId('automation.policy.link.0.grace')).toBeHidden();
  await link.getByTestId('automation.policy.link.0.advanced').locator('summary').click();
  await expect(link.getByTestId('automation.policy.link.0.grace')).toBeVisible();
  await savePolicy(page);

  await page.reload();
  await expect(page.getByTestId('automation.policy.link.0.stores')).toContainText(S23.name);
  await expect(page.getByTestId('automation.policy.link.0.bidiLocked')).toBeVisible();
  await expect(page.getByTestId('automation.store.summary.rules')).not.toHaveAttribute('data-count', '0');
});

test('AU1 picker states: idle before two characters, empty for an unknown name @AU1', async ({ page }) => {
  await openAutomation(page, 'S-06', 'policy');
  await page.getByTestId('automation.policy.addLink').click();
  await page.getByTestId('automation.policy.link.0').getByTestId('automation.policy.pickStore').locator('button').click();
  await expect(page.getByTestId('automation.picker.state')).toHaveAttribute('data-state', 'idle');
  await page.getByTestId('automation.picker.search').fill('x');
  await expect(page.getByTestId('automation.picker.state')).toHaveAttribute('data-state', 'idle');
  await page.getByTestId('automation.picker.search').fill('Spar Oerlikon');
  await expect(page.getByTestId('automation.picker.state')).toHaveAttribute('data-state', 'empty');
  await page.getByTestId('automation.picker.cancel').locator('button').click();
  await expect(page.getByTestId('automation.policy.link.0.stores')).not.toContainText('Spar');
});

test('AU10 Kanäle: status strip, store chats from bot updates, test message, who sends here @AU10 @N5 @page', async ({ page, api }) => {
  const incoming = await api.fetch('POST', '/api/dev/telegram/incoming', { chatId: '-2006', text: 'Hallo Bot', title: 'Babu Team' });
  expect(incoming.status(), await incoming.text()).toBe(204);

  await openAutomation(page, 'S-06', 'channels');
  await expect(page.getByTestId('automation.store.summary.mode')).toHaveAttribute('data-mode', 'ON');
  await expect(page.getByTestId('automation.store.summary.channels')).toHaveAttribute('data-count', '0');
  await expectDataState(page, 'automation.channels.state', 'success');
  await expect(page.getByTestId('automation.channels.none')).toBeVisible();
  // the live Telegram advert #1 and the sign-out alert send here
  await expect(page.getByTestId('automation.channels.user')).toHaveCount(2);

  await page.getByTestId('automation.channels.pick').click();
  await page.locator('.p-select-overlay .p-select-option').filter({ hasText: 'Babu Team' }).click();
  await page.getByTestId('automation.channels.add').click();
  await page.getByTestId('automation.channels.save').click();
  await expect(page.getByTestId('automation.channels.save').locator('button')).toBeDisabled();
  await expect(page.getByTestId('automation.store.summary.channels')).toHaveAttribute('data-count', '1');
  await expect(page.getByTestId('automation.store.nav.channels')).toContainText('1');

  const added = page.locator('[data-testid="automation.channels.chat"][data-chat-id="-2006"]');
  await added.getByTestId('automation.channels.test').click();
  await expect
    .poll(async () => (await api.getJson<DevNotification[]>('/api/dev/notifications?channel=TELEGRAM')).some((n) => n.target === '-2006'), { timeout: 15_000 })
    .toBe(true);

  // the advert's Telegram select links back here
  await page.getByTestId('automation.store.nav.adverts').click();
  await page.getByTestId('automation.channels.manage').first().click();
  await expect(page).toHaveURL(/\/automation\/channels$/);
});

test('AU10 region overview: rules An/Aus and the Kanäle column @AU10 @AU1', async ({ as }) => {
  const bot = await as('bot');
  await bot.goto(`/regions/${seedRegion('zuerich-stadt').id}/automation/overview`);
  await expectDataState(bot, 'automation.region.overview', 'success');
  const row = (key: string) => bot.locator(`[data-testid="automation.region.overviewRow"][data-store="${seedStore(key).name}"]`);
  await expect(row('S-01').getByTestId('automation.region.overviewChannels')).toHaveAttribute('data-count', '1');
  await expect(row('S-06').getByTestId('automation.region.overviewChannels')).toHaveAttribute('data-count', '0');
  await expect(row('S-06').getByTestId('automation.region.overviewRules')).toHaveAttribute('data-enabled', 'true');
  await expect(row('S-01').getByTestId('automation.region.overviewRules')).toHaveAttribute('data-enabled', 'false');
});
