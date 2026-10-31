import { type ApiClient, type DevNotification, expect, expectDataState, seedRegion, test, zurichPickupAt } from '../../fixtures';
import { S06, openAutomation, setSwitch, typeNumber } from './_automation';

/**
 * AU4: open-slot adverts. Seed: S-06 has the live advert #1 (24 h, Telegram only, region chat -1001); its next
 * pickup (Tuesday 3 Nov 19:00, SEED_NOW Monday 09:00) has two free slots.
 */
test.use({ role: 'bieb', seedReset: 'test' });

async function telegramTo(api: ApiClient, chatId: string): Promise<DevNotification[]> {
  const all = await api.getJson<DevNotification[]>('/api/dev/notifications?channel=TELEGRAM');
  return all.filter((n) => n.target === chatId);
}

test('AU4 advert 48 h before posts a rotating Telegram message to the region chat @AU4 @page', async ({ page, api, notifications }) => {
  await notifications.clearNotifications();
  await openAutomation(page, 'S-06', 'adverts');
  const card = page.locator('[data-testid="automation.adverts.card"][data-number="1"]');
  await expect(card).toHaveAttribute('data-enabled', 'true');
  await typeNumber(card.getByTestId('automation.adverts.trigger'), 48);
  await card.getByTestId('automation.adverts.addMessage.TELEGRAM').click();
  await card.getByTestId('automation.adverts.message.TELEGRAM.1.text').fill('Noch Platz bei ');
  await card.getByTestId('automation.adverts.message.TELEGRAM.1.placeholder.storeName').click();
  await expect(card.getByTestId('automation.adverts.message.TELEGRAM.1.preview')).toContainText("Babu's Bakery");
  // "run now" evaluates the saved adverts: blocked while the form has unsaved changes or the save is in flight
  const run = page.getByTestId('automation.adverts.run').locator('button');
  await expect(run).toBeDisabled();
  await expect(page.getByTestId('automation.adverts.saveFirst')).toBeVisible();
  await card.getByTestId('automation.adverts.save').click();
  await expect(card.getByTestId('automation.adverts.save').locator('button')).toBeDisabled();
  await expect(run).toBeEnabled();
  await expect(page.getByTestId('automation.adverts.saveFirst')).toBeHidden();

  await run.click();
  await expect(page.getByTestId('automation.adverts.runResult')).toBeVisible();
  await expect.poll(async () => (await telegramTo(api, '-1001')).length, { timeout: 15_000 }).toBeGreaterThan(0);
  const sent = await telegramTo(api, '-1001');
  expect(sent.some((n) => (n.body ?? '').includes("Babu's Bakery"))).toBe(true);
});

test('AU4 planned notifications preview @AU4', async ({ page }) => {
  await openAutomation(page, 'S-06', 'adverts');
  await expectDataState(page, 'automation.adverts.plannedState', 'success');
  await expect(page.getByTestId('automation.adverts.plannedRow').first()).toBeVisible();

  // a second advert: unsaved until saved, then listed
  await page.getByTestId('automation.adverts.add').click();
  const second = page.locator('[data-testid="automation.adverts.card"][data-number="2"]');
  await setSwitch(second.getByTestId('automation.adverts.enabled'), true);
  await second.getByTestId('automation.adverts.save').click();
  await expect(second.getByTestId('automation.adverts.save').locator('button')).toBeDisabled();
});

/** One-time S-06 pickups on Tuesday 3 Nov (inside the 24 h trigger window from Monday evening, under two days ahead). */
const FILL_PICKUP = zurichPickupAt(1, '20:00');
const LATE_PICKUP = zurichPickupAt(1, '21:00');
const after = (iso: string, hours: number) => new Date(Date.parse(iso) + hours * 3_600_000).toISOString();

async function onePlacePickup(bieb: ApiClient, pickupAt: string): Promise<void> {
  const response = await bieb.put(`/api/stores/${S06.id}/pickups/${encodeURIComponent(pickupAt)}`, { slotCount: 1 });
  expect(response.ok(), await response.text()).toBe(true);
}

async function hasAudit(api: ApiClient, pickupAt: string, status: string): Promise<boolean> {
  const page = await api.getJson<{ items: { pickupAt?: string; status: string }[] }>(`/api/stores/${S06.id}/automation/audits?kind=ADVERT&size=100`);
  return page.items.some((a) => !!a.pickupAt && Date.parse(a.pickupAt) === Date.parse(pickupAt) && a.status === status);
}

async function filledTotal(api: ApiClient): Promise<number> {
  const series = await api.getJson<{ key: string; points: { value: number }[] }[]>(
    `/api/regions/${seedRegion('zuerich-stadt').id}/automation/metrics?from=2026-11-01&to=2026-11-03`,
  );
  return (series.find((s) => s.key === 'ADVERT.FILLED_AFTER_ADVERT')?.points ?? []).reduce((n, p) => n + p.value, 0);
}

test('AU4 filled slot after the advert counts in the filled-after-advert metric @AU4 @AU9 @page', async ({ page, api, clock, notifications }) => {
  await onePlacePickup(api, FILL_PICKUP);
  const before = await filledTotal(api);
  // 23.5 h before the pickup: the 24 h trigger has passed and the slot is free → advert SENT
  await clock.fixServer(after(FILL_PICKUP, -23.5));
  await notifications.runJob('open-slot-adverts');
  await expect.poll(() => hasAudit(api, FILL_PICKUP, 'SENT')).toBe(true);
  // bieb takes the slot; the next run marks the advert as filled
  const signup = await api.post(`/api/stores/${S06.id}/pickups/${encodeURIComponent(FILL_PICKUP)}/signups`);
  expect(signup.ok(), await signup.text()).toBe(true);
  await notifications.runJob('open-slot-adverts');
  await expect.poll(() => filledTotal(api)).toBe(before + 1);

  await page.goto(`/regions/${seedRegion('zuerich-stadt').id}/automation/metrics`);
  await expectDataState(page, 'automation.metrics.state', 'success');
  await expect(page.locator('[data-testid="automation.metrics.chart.legend"][data-key="ADVERT.FILLED_AFTER_ADVERT"]')).not.toHaveAttribute('data-total', '0');
  await clock.fixServer();
});

test('AU4 late sign-out sends reminder to canceller and resends advert @AU4', async ({ api, clock, notifications }) => {
  const advert = await api.getJson<Record<string, unknown>>(`/api/stores/${S06.id}/automation/adverts/1`);
  const saved = await api.put(`/api/stores/${S06.id}/automation/adverts/1`, {
    ...advert, sendLateCancellationMessage: true, lateCancellationMaxHoursBefore: 72, resendLatestAdvertAfterLateCancellation: true,
  });
  expect(saved.ok(), await saved.text()).toBe(true);
  await onePlacePickup(api, LATE_PICKUP);
  const me = await api.session();
  const signup = await api.post(`/api/stores/${S06.id}/pickups/${encodeURIComponent(LATE_PICKUP)}/signups`);
  expect(signup.ok(), await signup.text()).toBe(true);
  await notifications.clearNotifications();

  // at the trigger the pickup is full → OCCUPIED_AT_TRIGGER with bieb as the first sign-up, no advert
  await clock.fixServer(after(LATE_PICKUP, -23.5));
  await notifications.runJob('open-slot-adverts');
  await expect.poll(() => hasAudit(api, LATE_PICKUP, 'OCCUPIED_AT_TRIGGER')).toBe(true);

  // bieb signs out late → reminder to bieb and the advert again in the region chat
  const out = await api.fetch('DELETE', `/api/stores/${S06.id}/pickups/${encodeURIComponent(LATE_PICKUP)}/signups/${me.user!.id}`);
  expect(out.ok(), await out.text()).toBe(true);
  await notifications.runJob('open-slot-adverts');
  await expect.poll(() => hasAudit(api, LATE_PICKUP, 'LATE_CANCELLATION_REMINDER_AND_ADVERTISEMENT_SENT')).toBe(true);
  await expect.poll(async () => (await notifications.bells('bieb', 'PICKUP_LATE_CANCELLATION')).length).toBeGreaterThan(0);
  await expect.poll(async () => (await telegramTo(api, '-1001')).length).toBeGreaterThan(0);
  await clock.fixServer();
});
