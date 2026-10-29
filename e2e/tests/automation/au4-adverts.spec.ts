import { type ApiClient, type DevNotification, expect, expectDataState, test } from '../../fixtures';
import { openAutomation, setSwitch, typeNumber } from './_automation';

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
  await card.getByTestId('automation.adverts.save').click();
  await expect(card.getByTestId('automation.adverts.save').locator('button')).toBeDisabled();

  await page.getByTestId('automation.adverts.run').click();
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
