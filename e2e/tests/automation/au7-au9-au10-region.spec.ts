import { type DevNotification, expect, expectDataState, seedRegion, test } from '../../fixtures';

/** AU7 grouping, AU9 metrics, AU10 region Telegram chats — region automation pages as bot (ambassador, all six permissions). */
test.use({ role: 'bot', seedReset: 'test' });
const ZH = seedRegion('zuerich-stadt').id;

test('AU7 users grouped by future pickup count for permission holder @AU7 @page', async ({ page, as }) => {
  await page.goto(`/regions/${ZH}/automation/grouping`);
  await expectDataState(page, 'automation.grouping.state', 'success');
  const group = page.getByTestId('automation.grouping.group').first();
  await expect(group).toHaveAttribute('data-count', /\d+/);
  await expect(group.getByTestId('automation.grouping.user').first()).toBeVisible();

  // a plain member has no access
  const jumper = await as('jumper');
  await jumper.goto(`/regions/${ZH}/automation/grouping`);
  await expectDataState(jumper, 'automation.grouping.state', 'forbidden');
});

test('AU9 metrics show decisions, adverts filled and cancellations per day @AU9 @page', async ({ page }) => {
  await page.goto(`/regions/${ZH}/automation/metrics`);
  await expectDataState(page, 'automation.metrics.state', 'success');
  for (const key of ['SLOT_APPROVAL.CONFIRMED', 'ADVERT.FILLED_AFTER_ADVERT', 'CANCELLATION.NOT_REPLACED']) {
    await expect(page.locator(`[data-testid="automation.metrics.chart.legend"][data-key="${key}"]`)).not.toHaveAttribute('data-total', '0');
  }
});

test('AU10 region picks chat from bot updates and sends test @AU10 @N5', async ({ page, api }) => {
  // a group chat the bot has seen (dev: simulated update)
  const incoming = await api.fetch('POST', '/api/dev/telegram/incoming', { chatId: '-2001', text: 'Hallo Bot', title: 'E2E Gruppe' });
  expect(incoming.status(), await incoming.text()).toBe(204);

  await page.goto(`/regions/${ZH}/automation/telegram`);
  await expectDataState(page, 'automation.telegram.state', 'success');
  await expect(page.locator('[data-testid="automation.telegram.chat"][data-chat-id="-1001"]')).toBeVisible();
  await page.getByTestId('automation.telegram.pick').click();
  await page.locator('.p-select-overlay .p-select-option').filter({ hasText: 'E2E Gruppe' }).click();
  await page.getByTestId('automation.telegram.add').click();
  await page.getByTestId('automation.telegram.save').click();
  await expect(page.getByTestId('automation.telegram.save').locator('button')).toBeDisabled();

  const added = page.locator('[data-testid="automation.telegram.chat"][data-chat-id="-2001"]');
  await added.getByTestId('automation.telegram.test').click();
  await expect
    .poll(async () => (await api.getJson<DevNotification[]>('/api/dev/notifications?channel=TELEGRAM')).some((n) => n.target === '-2001'), { timeout: 15_000 })
    .toBe(true);
});
