import {
  expect,
  expectDataState,
  expectErrorState,
  expectSkeleton,
  expectNoMissingKeys,
  injectApiError,
  setLanguage,
  test,
} from '../../fixtures';

/** C3 (plan-m7 Q10): the public statistics page shows the totals of the latest aggregation as counting tiles. */

interface PublicStatistics {
  kgSaved: number;
  pickups: number;
  foodsavers: number;
  stores: number;
  cooperations: number;
  foodSharePoints: number;
  baskets: number;
  asOf: string;
}

const KEYS = ['kgSaved', 'pickups', 'foodsavers', 'stores', 'cooperations', 'foodSharePoints', 'baskets'] as const;

/** The counters' visible text without grouping characters (de-CH uses ’ as thousands separator). */
async function shownNumber(page: import('@playwright/test').Page, key: string): Promise<number> {
  const text = await page.getByTestId(`content.statistics.${key}`).locator('[aria-hidden="true"]').innerText();
  return Number(text.replace(/[^\d]/g, ''));
}

test('C3 statistics page counts up to the aggregated values @C3 @page', async ({ api, page }) => {
  // run the aggregation now (evicts the cached totals), then read what the page must show
  await api.loginAs('orga');
  expect((await api.fetch('POST', '/api/dev/statistics/aggregate')).status()).toBe(204);
  const stats = await api.getJson<PublicStatistics>('/api/public/statistics');
  expect(stats.kgSaved).toBeGreaterThan(0);

  await setLanguage(page, 'de');
  await page.goto('/statistics');
  await expectDataState(page, 'content.statistics.state', 'success');
  await expect(page.getByTestId('content.statistics.tile')).toHaveCount(7);
  for (const key of KEYS) {
    await expect(page.getByTestId(`content.statistics.${key}`)).toHaveAttribute('data-value', String(stats[key]));
  }
  // the counters animate from 0 and end exactly at the (rounded) totals
  for (const key of KEYS) {
    await expect.poll(() => shownNumber(page, key)).toBe(Math.round(stats[key]));
  }
  await expect(page.getByTestId('content.statistics.asOf')).toContainText(new Date(stats.asOf).getUTCFullYear().toString());
  await expectNoMissingKeys(page);
});

test.describe('reduced motion', () => {
  test.use({ reducedMotion: 'reduce' });

  test('C3 statistics show the final values at once with reduced motion @C3', async ({ api, page }) => {
    const stats = await api.getJson<PublicStatistics>('/api/public/statistics');
    await page.goto('/statistics');
    await expectDataState(page, 'content.statistics.state', 'success');
    // no animation frames: the first rendered text already is the total
    expect(await shownNumber(page, 'pickups')).toBe(stats.pickups);
    expect(await shownNumber(page, 'baskets')).toBe(stats.baskets);
  });
});

test('C3 statistics page: loading skeleton, error with retry @C3', async ({ page }) => {
  const slow = await injectApiError(page, { url: '/api/public/statistics', delayMs: 30_000 });
  await page.goto('/statistics');
  await expectSkeleton(page, 'content.statistics.state');
  await slow.remove();

  const failure = await injectApiError(page, { url: '/api/public/statistics', status: 500 });
  await page.goto('/statistics');
  const { retry } = await expectErrorState(page, 'content.statistics.state');
  await failure.remove();
  await retry.click();
  await expectDataState(page, 'content.statistics.state', 'success');
});
