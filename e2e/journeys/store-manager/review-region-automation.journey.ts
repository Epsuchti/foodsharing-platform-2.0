import { type DevNotification, expectDataState, seedRegion, seedStore } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Ambassador Reto (bot@local, ambassador of Zürich Stadt) reviews how the stores of his region use automation: the
 * overview (rules on or off and the store chats per store, AU1/AU10), the decisions of every store with their reasons
 * (AU8, the seeded team-request decisions of Hiltl among them), people grouped by their future pickups (AU7), the
 * metrics per group (AU9), and he adds the region's new Telegram group as an alert chat and sends it a test message
 * (AU10/N5). Who sees which decisions and pages, the grouping and the metric arithmetic are unit tests
 * (AutomationAuditServiceTest, PickupCountGroupingAndMetricsTest, MetricKeyCoverageTest).
 */
test.use({ role: 'bot' });

const ZH = seedRegion('zuerich-stadt').id;

test('ambassador reviews the automation of the region and adds an alert chat @AU1 @AU7 @AU8 @AU9 @AU10 @N5 @D10.3', async ({
  page,
  api,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'ambassador');

  await walk.step('opens the region automation and its decisions', async () => {
    await page.goto(`/regions/${ZH}`);
    await walk.click(page.getByTestId('regions.page.tab.automation'));
    await expect(page).toHaveURL(new RegExp(`/regions/${ZH}/automation/audits$`));
    await expectDataState(page, 'automation.region.audits.state', 'success');
    const rows = page.getByTestId('automation.region.audits.row');
    await expect(rows.first()).toBeVisible();
    // decisions of several stores
    expect(new Set(await rows.locator('.audit__title strong').allTextContents()).size).toBeGreaterThan(1);
    await walk.click(page.getByTestId('automation.region.audits.kind.PICKUP'));
    await expect(rows.first()).toHaveAttribute('data-kind', 'PICKUP');
    await walk.click(rows.first().getByTestId('automation.region.audits.toggle'));
    await expect(rows.first().getByTestId('automation.region.audits.details')).toBeVisible();
    await checkpoint(page, 'region-decisions');
    // the seeded team-request decisions belong to Hiltl (S-07)
    await walk.click(page.getByTestId('automation.region.audits.kind.REQUEST'));
    await expect(rows.first()).toHaveAttribute('data-kind', 'REQUEST');
    await expect(rows.first().locator('.audit__title strong')).toHaveText(seedStore('S-07').name);
  });

  await walk.step('reads the overview of the stores', async () => {
    await walk.click(page.getByTestId('automation.region.nav.overview'));
    await expectDataState(page, 'automation.region.overview', 'success');
    const row = (key: string) => page.locator(`[data-testid="automation.region.overviewRow"][data-store="${seedStore(key).name}"]`);
    await expect(row('S-01').getByTestId('automation.region.overviewChannels')).toHaveAttribute('data-count', '1');
    await expect(row('S-06').getByTestId('automation.region.overviewChannels')).toHaveAttribute('data-count', '0');
    await expect(row('S-06').getByTestId('automation.region.overviewRules')).toHaveAttribute('data-enabled', 'true');
    await expect(row('S-01').getByTestId('automation.region.overviewRules')).toHaveAttribute('data-enabled', 'false');
    await checkpoint(page, 'region-overview');
  });

  await walk.step('looks at the grouping and the metrics', async () => {
    await walk.click(page.getByTestId('automation.region.nav.grouping'));
    await expectDataState(page, 'automation.grouping.state', 'success');
    const group = page.getByTestId('automation.grouping.group').first();
    await expect(group).toHaveAttribute('data-count', /\d+/);
    await expect(group.getByTestId('automation.grouping.user').first()).toBeVisible();
    await checkpoint(page, 'grouping');

    await walk.click(page.getByTestId('automation.region.nav.metrics'));
    await expectDataState(page, 'automation.metrics.state', 'success');
    // labels instead of raw keys, the chart's data as a hidden table
    await expect(page.locator('[data-testid="automation.metrics.totals.row"]').first()).toBeVisible();
    await expect(page.getByTestId('automation.metrics.state')).not.toContainText(/\b[A-Z_]+\.[A-Z_]+\b/);
    await expect(page.getByTestId('automation.metrics.chart.table')).toBeAttached();
    await checkpoint(page, 'metrics');
    for (const [group, key] of [
      ['PICKUP', 'SLOT_APPROVAL.CONFIRMED'],
      ['ADVERTS', 'ADVERT.FILLED_AFTER_ADVERT'],
      ['CANCELLATIONS', 'CANCELLATION.NOT_REPLACED'],
    ]) {
      await walk.click(page.getByTestId(`automation.metrics.group.${group}`));
      await expect(page.locator(`[data-testid="automation.metrics.totals.row"][data-key="${key}"]`)).not.toHaveAttribute('data-total', '0');
      await expect(page.locator(`[data-testid="automation.metrics.chart.legend"][data-key="${key}"]`)).not.toHaveAttribute('data-total', '0');
    }
    await walk.click(page.getByTestId('automation.metrics.group.DECLINE_REASONS'));
    await expect(page.locator('[data-testid="automation.metrics.totals.row"][data-key^="SLOT_DECLINE_RULE."]').first()).toBeVisible();
    await checkpoint(page, 'metrics-decline-reasons');
  });

  // the bot has seen the region's new group chat (dev: a simulated update)
  const incoming = await api.fetch('POST', '/api/dev/telegram/incoming', { chatId: '-2001', text: 'Hallo Bot', title: 'E2E Gruppe' });
  expect(incoming.status(), await incoming.text()).toBe(204);

  await walk.step('adds the group as an alert chat and tests it', async () => {
    await walk.click(page.getByTestId('automation.region.nav.telegram'));
    await expectDataState(page, 'automation.telegram.state', 'success');
    await expect(page.locator('[data-testid="automation.telegram.chat"][data-chat-id="-1001"]')).toBeVisible();
    await walk.click(page.getByTestId('automation.telegram.pick'));
    await walk.click(page.locator('.p-select-overlay .p-select-option').filter({ hasText: 'E2E Gruppe' }));
    await walk.click(page.getByTestId('automation.telegram.add'));
    const saved = page.waitForResponse((r) => r.request().method() === 'PUT' && r.url().endsWith('/telegram-chats'));
    await walk.click(page.getByTestId('automation.telegram.save'));
    expect((await saved).ok()).toBe(true);
    await expect(page.getByTestId('automation.telegram.save').locator('button')).toBeDisabled();
    const added = page.locator('[data-testid="automation.telegram.chat"][data-chat-id="-2001"]');
    const tested = page.waitForResponse((r) => r.request().method() === 'POST' && r.url().endsWith('/telegram-chats/test'));
    await walk.click(added.getByTestId('automation.telegram.test'));
    expect((await tested).ok()).toBe(true);
    await expect
      .poll(async () => (await api.getJson<DevNotification[]>('/api/dev/notifications?channel=TELEGRAM')).some((n) => n.target === '-2001'), {
        timeout: 15_000,
      })
      .toBe(true);
    await checkpoint(page, 'region-telegram');
  });

  walk.finish({ steps: 4, clicks: 17, fields: 0 });
});
