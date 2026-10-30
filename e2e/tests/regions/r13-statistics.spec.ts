import { expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';

/**
 * R13 region statistics tab (plan-m7 Q7): counters of the nightly statistics cache (seed `165` recomputed for Zürich,
 * incl. active FSPs and baskets of the last month), the monthly pickups/kg chart and the gender list / age bands of
 * all members or only those with Zürich Stadt as home region. foodsaver@local is a member of Zürich Stadt.
 */
const ZURICH = '00000010-0001-4000-8000-000000000040';

interface RegionStatisticsDto {
  activeFoodSharePoints: number;
  foodBasketsLastMonth: number;
}
interface GenderCountDto {
  gender: string;
  count: number;
}

test.use({ role: 'foodsaver' });

test('R13 region statistics show FSP and basket counts and gender list @R13 @R3', async ({ page, api }) => {
  const stats = await api.getJson<RegionStatisticsDto>(`/api/regions/${ZURICH}/statistics`);
  const genders = await api.getJson<GenderCountDto[]>(`/api/regions/${ZURICH}/statistics/gender`);
  const homeGenders = await api.getJson<GenderCountDto[]>(`/api/regions/${ZURICH}/statistics/gender?onlyHomeRegion=true`);
  const months = await api.getJson<{ month: string; pickups: number; kg: number }[]>(`/api/regions/${ZURICH}/statistics/pickups?months=12`);
  expect(genders.length).toBeGreaterThan(0);

  await page.goto(`/regions/${ZURICH}/statistics`);
  await expectDataState(page, 'regions.statistics.state', 'success');
  await expect(page.getByTestId('regions.statistics.fsps')).toContainText(String(stats.activeFoodSharePoints));
  await expect(page.getByTestId('regions.statistics.baskets')).toContainText(String(stats.foodBasketsLastMonth));

  // monthly chart: one bar per month, pickups or kilograms
  await expectDataState(page, 'regions.statistics.pickupState', months.length > 0 ? 'success' : 'empty');
  if (months.length > 0) {
    const bars = page.getByTestId('regions.statistics.chart.bar');
    await expect(bars).toHaveCount(months.length);
    await expect(bars.first()).toHaveAttribute('data-value', String(months[0].pickups));
    await page.getByTestId('regions.statistics.metric.kg').click();
    await expect(bars.first()).toHaveAttribute('data-value', String(months[0].kg));
  }

  // gender list of all members, then of home-region members only
  await expectDataState(page, 'regions.statistics.genderState', 'success');
  const rows = page.getByTestId('regions.statistics.genderRow');
  await expect(rows).toHaveCount(genders.length);
  for (const entry of genders) {
    await expect(page.locator(`[data-testid="regions.statistics.genderRow"][data-gender="${entry.gender}"]`)).toContainText(String(entry.count));
  }
  await page.getByTestId('regions.statistics.onlyHomeRegion').click();
  await expect(rows).toHaveCount(homeGenders.length);
  for (const entry of homeGenders) {
    await expect(page.locator(`[data-testid="regions.statistics.genderRow"][data-gender="${entry.gender}"]`)).toContainText(String(entry.count));
  }
  await expectDataState(page, 'regions.statistics.ageState', 'success');
});

test('R13 the pickup chart shows an error state and loads again on retry @R13', async ({ page }) => {
  // the retry interceptor repeats a failed GET twice: fail all three attempts
  await injectApiError(page, { url: `/api/regions/${ZURICH}/statistics/pickups`, status: 503, times: 3 });
  await page.goto(`/regions/${ZURICH}/statistics`);
  await expectDataState(page, 'regions.statistics.genderState', 'success');
  const { retry } = await expectErrorState(page, 'regions.statistics.pickupState');
  await retry.click();
  await expect(page.getByTestId('regions.statistics.pickupState')).toHaveAttribute('data-state', /success|empty/);
});
