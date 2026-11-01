import type { Page } from '@playwright/test';
import { expect, expectDataState, test } from '../../fixtures';
import { NAMES, confirmWithMessage, memberAction, openStore } from './_stores';

/**
 * S14 statistics of S-01 (weight category KG_10_20): pickups per month and kg = pickups × the category's average;
 * per-member counts only for managers, paged with search, sort and status (incl. former members, resume item 14).
 * The page shows what `GET /api/stores/{id}/statistics` and `…/statistics/members` answer.
 */
interface Stats {
  totalPickups: number;
  totalKg: number;
  weightCategory?: { code: string; label: string; averageKg: number };
  months: { month: string; pickups: number; kg: number }[];
}

interface MemberPage {
  items: { user: { displayName: string }; pickups: number; status: string; isManager: boolean; lastPickupAt?: string }[];
  page: { totalItems: number };
}

/** Display names of the per-member rows, top to bottom. */
async function rowNames(page: Page): Promise<string[]> {
  return page
    .getByTestId('stores.statistics.member')
    .evaluateAll((rows) => rows.map((row) => row.getAttribute('data-user') ?? ''));
}

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('S14 statistics per month and kg per weight category @S14 @page', async ({ page, api, store, as }) => {
    const stats = await api.getJson<Stats>(`/api/stores/${store.id('S-01')}/statistics`);
    const members = await api.getJson<MemberPage>(`/api/stores/${store.id('S-01')}/statistics/members`);
    expect(stats.months).toHaveLength(12);
    expect(stats.totalPickups).toBeGreaterThan(0);
    expect(stats.weightCategory?.code).toBe('KG_10_20');
    for (const month of stats.months) {
      expect(month.kg).toBeCloseTo(month.pickups * stats.weightCategory!.averageKg, 5);
    }

    await openStore(page, 'S-01', 'statistics');
    await expectDataState(page, 'stores.statistics.state', 'success');
    await expect(page.getByTestId('stores.statistics.totalPickups')).toContainText(String(stats.totalPickups));
    const bars = page.getByTestId('stores.statistics.chart.bar');
    await expect(bars).toHaveCount(12);
    const pickupValues = await bars.evaluateAll((nodes) => nodes.map((node) => Number(node.getAttribute('data-value'))));
    expect(pickupValues).toEqual(stats.months.map((month) => month.pickups));

    await page.getByTestId('stores.statistics.metric.kg').click();
    await expect
      .poll(() => bars.evaluateAll((nodes) => nodes.map((node) => Number(node.getAttribute('data-value')))))
      .toEqual(stats.months.map((month) => month.kg));
    await expect(page.getByTestId('stores.statistics.weightHint')).toContainText(stats.weightCategory!.label);

    // manager: pickups per member, most pickups first
    await expectDataState(page, 'stores.statistics.members.state', 'success');
    await expect(page.getByTestId('stores.statistics.member')).toHaveCount(members.page.totalItems);
    expect(await rowNames(page)).toEqual(members.items.map((row) => row.user.displayName));
    await expect(page.getByTestId('stores.statistics.members.count')).toContainText(String(members.page.totalItems));

    // all time: at least as many pickups
    await page.getByTestId('stores.statistics.range.all').click();
    await expectDataState(page, 'stores.statistics.state', 'success');
    await expect(page.getByTestId('stores.statistics.chart.bar').first()).toBeVisible();

    // team member foodsaver: same chart, no per-member table
    const foodsaver = await as('foodsaver');
    await openStore(foodsaver, 'S-01', 'statistics');
    await expectDataState(foodsaver, 'stores.statistics.state', 'success');
    await expect(foodsaver.getByTestId('stores.statistics.chart.bar')).toHaveCount(12);
    await expect(foodsaver.getByTestId('stores.statistics.members')).toHaveCount(0);
  });

  test('S14 per member: search, sort by name and last pickup, empty filter @S14', async ({ page, api, store }) => {
    const s01 = store.id('S-01');
    const byName = await api.getJson<MemberPage>(`/api/stores/${s01}/statistics/members?sort=NAME`);
    const byLast = await api.getJson<MemberPage>(`/api/stores/${s01}/statistics/members?sort=LAST_PICKUP`);
    await openStore(page, 'S-01', 'statistics');
    await expectDataState(page, 'stores.statistics.members.state', 'success');

    await page.getByTestId('stores.statistics.members.sort').click();
    await page.getByRole('option', { name: 'Name', exact: true }).click();
    await expect.poll(() => rowNames(page)).toEqual(byName.items.map((row) => row.user.displayName));
    // NAME puts the managers first
    await expect(page.getByTestId('stores.statistics.member').first()).toHaveAttribute('data-user', NAMES.bieb);

    await page.getByTestId('stores.statistics.members.sort').click();
    await page.getByRole('option', { name: 'Letzte Abholung', exact: true }).click();
    await expect.poll(() => rowNames(page)).toEqual(byLast.items.map((row) => row.user.displayName));

    // accent- and case-insensitive name search
    await page.getByTestId('stores.statistics.members.search').fill('MULLER');
    await expect(page.getByTestId('stores.statistics.member')).toHaveCount(1);
    await expect(page.getByTestId('stores.statistics.member')).toHaveAttribute('data-user', NAMES.foodsaver);

    await page.getByTestId('stores.statistics.members.search').fill('niemand-hier');
    await expect(page.getByTestId('stores.statistics.members.emptyFilter')).toBeVisible();
    await page.getByTestId('stores.statistics.members.resetFilter').click();
    await expect(page.getByTestId('stores.statistics.member')).toHaveCount(byName.page.totalItems);
  });
});

test.describe('former members', () => {
  test.use({ role: 'bieb', seedReset: 'test' });

  test('S14 per member: someone removed from the team stays as former @S14', async ({ page }) => {
    // foodsaver picked up at S-01 in the last 12 months; after the removal the pickups count as a former member's
    await openStore(page, 'S-01', 'team');
    await memberAction(page, NAMES.foodsaver, 'remove');
    await confirmWithMessage(page, 'Wir danken dir für deine Abholungen und verabschieden dich aus dem Team.');

    await openStore(page, 'S-01', 'statistics');
    await expectDataState(page, 'stores.statistics.members.state', 'success');
    const row = page.locator(`[data-testid="stores.statistics.member"][data-user="${NAMES.foodsaver}"]`);
    await expect(row).toHaveAttribute('data-status', 'FORMER');
    await expect(row.getByTestId('stores.statistics.formerBadge')).toBeVisible();

    await page.getByTestId('stores.statistics.members.filter.former').click();
    await expect(page.getByTestId('stores.statistics.members.filter.former')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('stores.statistics.member')).toHaveCount(1);
    await expect(row).toBeVisible();

    await page.getByTestId('stores.statistics.members.filter.member').click();
    await expect(row).toHaveCount(0);
  });
});
