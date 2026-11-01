import type { Locator } from '@playwright/test';
import { LANGUAGES, SEED_NOW, expect, expectDataState, expectNoSeriousViolations, test, zurichPickupAt } from '../../fixtures';
import { dateField, fillDate, fillDateTime, fillTime } from '../../support/date-inputs';
import { openPickups, pickupRow } from '../pickups/_pickups';

/**
 * AD1 date inputs (decision 150): the shared date/datetime/time inputs show `dd.mm.yyyy` and 24h time in every UI
 * language, also on an en-US browser where native inputs showed mm/dd/yyyy and AM/PM. The calendar names follow the
 * UI language; a typed and a picked date store the same ISO value. Seed clock 2026-11-02.
 */
test.use({ locale: 'en-US', seedReset: 'test' });

const NOVEMBER: Record<string, RegExp> = { de: /November/, gws: /November/, en: /November/, fr: /novembre/, it: /novembre/ };

function dayCell(panel: Locator, day: number): Locator {
  return panel
    .locator('td:not(.p-datepicker-other-month) > span, td:not(.p-datepicker-other-month) [data-pc-section="day"]')
    .filter({ hasText: new RegExp(`^${day}$`) })
    .first();
}

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  for (const lang of LANGUAGES) {
    test(`AD1 sleep-mode dates show dd.mm.yyyy, the calendar follows the UI language, typed and picked dates save as ISO (${lang}) @AD1`, async ({
      page,
      apiOf,
    }) => {
      await page.clock.setFixedTime(new Date(SEED_NOW));
      const api = apiOf(page);
      const language = await api.patch('/api/users/me/settings', { language: lang });
      expect(language.status(), await language.text()).toBe(200);
      await page.goto('/account/settings');
      await expectDataState(page, 'account.settings.sleep.state', 'success');
      await page.getByTestId('account.settings.sleep.status.TEMPORARY').click();

      // typed
      const from = page.getByTestId('account.settings.sleep.from');
      await expect(dateField(from)).toHaveAttribute('placeholder', /^[A-Za-z]{2}\.[A-Za-z]{2}\.[A-Za-z]{4}$/);
      await fillDate(from, '2026-11-05');
      await expect(dateField(from)).toHaveValue('05.11.2026');

      // picked from the calendar behind the icon; Escape closes it, the icon opens it again
      const until = page.getByTestId('account.settings.sleep.until');
      const trigger = until.locator('button.fs-date-input-trigger');
      const panel = page.locator('.p-datepicker-panel');
      await trigger.click();
      await expect(panel).toBeVisible();
      await expect(panel.locator('.p-datepicker-select-month')).toHaveText(NOVEMBER[lang]);
      await page.keyboard.press('Escape');
      await expect(panel).toHaveCount(0);
      await trigger.click();
      await dayCell(panel, 20).click();
      await expect(panel).toHaveCount(0);
      await expect(dateField(until)).toHaveValue('20.11.2026');

      await page.getByTestId('account.settings.sleep.save').click();
      await expect(page.getByTestId('account.settings.sleep.current')).toHaveAttribute('data-status', 'TEMPORARY');
      const me = await api.get('/api/users/me');
      expect(me.status()).toBe(200);
      expect(await me.json()).toMatchObject({ sleepFrom: '2026-11-05', sleepUntil: '2026-11-20' });
    });
  }

  test('AD1 unreadable text shows an inline error and keeps the last value @AD1', async ({ page }) => {
    await page.clock.setFixedTime(new Date(SEED_NOW));
    await page.goto('/account/settings');
    await expectDataState(page, 'account.settings.sleep.state', 'success');
    await page.getByTestId('account.settings.sleep.status.TEMPORARY').click();
    const from = page.getByTestId('account.settings.sleep.from');
    await fillDate(from, '2026-11-05');
    await dateField(from).fill('31.02.2026');
    await dateField(from).press('Tab');
    await expect(from.getByRole('alert')).toBeVisible();
    await expect(dateField(from)).toHaveAttribute('aria-invalid', 'true');
    await expectNoSeriousViolations(page);
    // lenient formats are read and shown in the display format
    await dateField(from).fill('6.11.26');
    await dateField(from).press('Tab');
    await expect(from.getByRole('alert')).toHaveCount(0);
    await expect(dateField(from)).toHaveValue('06.11.2026');
  });
});

test.describe('bot', () => {
  test.use({ role: 'bot' });

  test('AD1 event form takes date and 24h time (datetime) @AD1', async ({ page, apiOf, region }) => {
    await page.clock.setFixedTime(new Date(SEED_NOW));
    await page.goto(`/events/new?regionId=${region.id('zuerich-stadt')}`);
    await page.getByTestId('events.form.type.ONLINE').click();
    await page.getByTestId('events.form.name').fill('Austausch am Abend');
    const startsAt = page.getByTestId('events.form.startsAt');
    await fillDateTime(startsAt, '2026-11-21T19:00');
    await expect(dateField(startsAt)).toHaveValue('21.11.2026 19:00');
    await fillDateTime(page.getByTestId('events.form.endsAt'), '2026-11-21T21:30');
    await expect(dateField(page.getByTestId('events.form.endsAt'))).toHaveValue('21.11.2026 21:30');
    await page.getByTestId('events.form.link').fill('https://meet.example.org/fs');
    await page.getByTestId('events.form.submit').click();
    await expect(page).toHaveURL(/\/events\/[0-9a-f-]{36}$/);
    const eventId = page.url().split('/').pop()!;
    const event = await apiOf(page).get(`/api/events/${eventId}`);
    expect(event.status()).toBe(200);
    // Zürich wall time, UTC+1 in November
    const body = (await event.json()) as { startsAt: string; endsAt: string };
    expect(new Date(body.startsAt).toISOString()).toBe('2026-11-21T18:00:00.000Z');
    expect(new Date(body.endsAt).toISOString()).toBe('2026-11-21T20:30:00.000Z');
  });
});

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('AD1 store pickup edit takes a 24h time (time) @AD1', async ({ page }) => {
    await page.clock.setFixedTime(new Date(SEED_NOW));
    await openPickups(page, 'S-01');
    await page.getByTestId('stores.pickups.add').click();
    const time = page.getByTestId('stores.pickups.edit.time');
    await expect(dateField(time)).toHaveValue('18:00');
    await fillDate(page.getByTestId('stores.pickups.edit.date'), '2026-11-10');
    await fillTime(time, '21:15');
    await expect(dateField(time)).toHaveValue('21:15');
    await page.getByTestId('stores.pickups.edit.save').click();
    await expect(pickupRow(page, zurichPickupAt(8, '21:15'))).toBeVisible();
  });
});
