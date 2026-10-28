import type { Locator, Page } from '@playwright/test';
import { S01_THURSDAY, SEED_NOW, expect, expectDataState, test } from '../../fixtures';
import { openStore } from './_stores';

/**
 * S12 store log (A21): bieb (manager of S-01) moves jumper into the team and confirms foodsaver's slot of Thursday
 * 2026-11-05 19:00; both actions appear in the log; the action groups and the date range filter narrow it.
 */
const LIST = 'stores.log.list';
const ENTRY = 'stores.log.entry';

const entries = (page: Page) => page.getByTestId(ENTRY);
const entry = (page: Page, action: string) => page.locator(`[data-testid="${ENTRY}"][data-action="STORE.${action}"]`);

function dayCell(panel: Locator, day: number): Locator {
  return panel
    .locator('td:not(.p-datepicker-other-month) > span, td:not(.p-datepicker-other-month) [data-pc-section="day"]')
    .filter({ hasText: new RegExp(`^${day}$`) })
    .first();
}

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('S12 store log lists team and slot actions with date and action filter @S12 @D10.3 @page', async ({ page, api, dev, store, as }) => {
    const s01 = store.id('S-01');
    const jumper = await dev.user('jumper');
    const foodsaver = await dev.user('foodsaver');
    expect((await api.post(`/api/stores/${s01}/team/${jumper.id}/regular`)).ok()).toBe(true);
    expect((await api.post(`/api/stores/${s01}/pickups/${encodeURIComponent(S01_THURSDAY)}/signups/${foodsaver.id}/confirm`)).ok()).toBe(true);

    await page.clock.setFixedTime(new Date(SEED_NOW));
    await openStore(page, 'S-01', 'log');
    await expectDataState(page, LIST, 'success');
    await expect(entry(page, 'MOVED_TO_TEAM').first()).toBeVisible();
    await expect(entry(page, 'SLOT_CONFIRMED').first()).toBeVisible();

    // action groups
    await page.getByTestId('stores.log.preset.pickups').click();
    await expectDataState(page, LIST, 'success');
    await expect(entry(page, 'SLOT_CONFIRMED').first()).toBeVisible();
    await expect(entry(page, 'MOVED_TO_TEAM')).toHaveCount(0);
    await page.getByTestId('stores.log.preset.team').click();
    await expectDataState(page, LIST, 'success');
    await expect(entry(page, 'MOVED_TO_TEAM').first()).toBeVisible();
    await expect(entry(page, 'SLOT_CONFIRMED')).toHaveCount(0);
    await page.getByTestId('stores.log.preset.all').click();
    await expectDataState(page, LIST, 'success');

    // date range: 1–2 November 2026 (today of the dev clock) keeps today's actions, everything is inside the range
    const rangeRequest = page.waitForRequest((request) => request.url().includes(`/api/stores/${s01}/log?`) && request.url().includes('from='));
    await page.getByTestId('stores.log.filter.dateRange').locator('input').click();
    const panel = page.locator('.p-datepicker-panel, .p-datepicker-overlay, [role="dialog"]').filter({ has: page.locator('table') }).first();
    await expect(panel).toBeVisible();
    await dayCell(panel, 1).click();
    await dayCell(panel, 2).click();
    const url = new URL((await rangeRequest).url());
    await page.getByTestId('stores.page.name').click();
    await expectDataState(page, LIST, 'success');
    const from = Date.parse(url.searchParams.get('from')!);
    const to = Date.parse(url.searchParams.get('to')!);
    await expect(entry(page, 'SLOT_CONFIRMED').first()).toBeVisible();
    const occurred = await entries(page).evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-occurred-at')));
    for (const value of occurred) {
      expect(Date.parse(value!)).toBeGreaterThanOrEqual(from);
      expect(Date.parse(value!)).toBeLessThanOrEqual(to);
    }

    // foodsaver (no maySeeStoreLog): no tab, direct link is forbidden
    const foodsaverPage = await as('foodsaver');
    await openStore(foodsaverPage, 'S-01', 'overview');
    await expect(foodsaverPage.getByTestId('stores.page.tab.log')).toHaveCount(0);
    await foodsaverPage.goto(`/stores/${s01}/log`);
    await expectDataState(foodsaverPage, LIST, 'forbidden');
  });
});
