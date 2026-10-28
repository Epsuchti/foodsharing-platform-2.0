import { expect, expectDataState, test } from '../../fixtures';
import { openStore } from './_stores';

/**
 * S14 statistics of S-01 (weight category KG_10_20): pickups per month and kg = pickups × the category's average;
 * per-member counts only for managers. The page shows what `GET /api/stores/{id}/statistics` answers.
 */
interface Stats {
  totalPickups: number;
  totalKg: number;
  weightCategory?: { code: string; label: string; averageKg: number };
  months: { month: string; pickups: number; kg: number }[];
  perMember?: { user: { displayName: string }; pickups: number }[];
}

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('S14 statistics per month and kg per weight category @S14 @page', async ({ page, api, store, as }) => {
    const stats = await api.getJson<Stats>(`/api/stores/${store.id('S-01')}/statistics`);
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

    // manager: pickups per member
    await expect(page.getByTestId('stores.statistics.member').first()).toBeVisible();
    await expect(page.getByTestId('stores.statistics.member')).toHaveCount(stats.perMember?.length ?? 0);

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
});
