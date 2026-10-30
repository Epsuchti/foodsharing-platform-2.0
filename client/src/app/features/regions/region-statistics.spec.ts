import { HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { ApiErrorCode } from '../../api';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { RegionPageContext } from './region-page.context';
import { RegionLaterTab } from './tabs/region-later.tab';
import { RegionStatisticsTab, bars, pickupBars } from './tabs/region-statistics.tab';

const ZH = '00000010-0001-4000-8000-000000000040';
const tick = () => new Promise((resolve) => setTimeout(resolve));
const serverError = { status: 500, statusText: 'Server Error' };

const STATS = {
  activeHomeRegionFoodsavers: 12,
  activeCooperations: 4,
  pickupsLastMonth: 30,
  savedFoodWeightLastMonth: 120.4,
  activeFoodSharePoints: 3,
  foodBasketsLastMonth: 9,
  memberCount: 40,
  genderCounts: {},
  ageBands: [],
};

describe('region statistics', () => {
  it('scales bars to the largest value and picks the chart metric', () => {
    expect(bars([['a', 2], ['b', 4]], (key) => key.toUpperCase())).toEqual([
      { key: 'a', label: 'A', count: 2, percent: 50 },
      { key: 'b', label: 'B', count: 4, percent: 100 },
    ]);
    const months = [{ month: '2026-09', pickups: 5, kg: 12.5 }, { month: '2026-10', pickups: 7, kg: 20 }];
    expect(pickupBars(months, 'pickups')).toEqual([{ month: '2026-09', value: 5 }, { month: '2026-10', value: 7 }]);
    expect(pickupBars(months, 'kg')).toEqual([{ month: '2026-09', value: 12.5 }, { month: '2026-10', value: 20 }]);
  });

  describe('tabs', () => {
    let http: HttpTestingController;
    let harness: RouterTestingHarness;
    const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
    const all = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
    const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
    const settle = async () => {
      await tick();
      await harness.fixture.whenStable();
    };
    const context = { provide: RegionPageContext, useValue: { regionId: signal(ZH) } };

    beforeEach(async () => {
      TestBed.configureTestingModule({
        providers: [
          ...provideDataStateTesting({
            translations: { 'regions.gender.FEMALE': 'weiblich', 'regions.gender.MALE': 'männlich', 'regions.later.title': '{feature} kommt bald', 'regions.later.wall': 'Pinnwand' },
            routes: [
              { path: 'regions/:regionId/statistics', component: RegionStatisticsTab, providers: [context] },
              { path: 'regions/:regionId/wall', component: RegionLaterTab, data: { feature: 'wall' } },
            ],
          }),
          provideOptimus(),
        ],
      });
      http = TestBed.inject(HttpTestingController);
      harness = await RouterTestingHarness.create();
    });

    afterEach(() => http.verify({ ignoreCancelled: true }));

    const gender = (only: string) => (req: { url: string; params: { get(name: string): string | null } }) =>
      req.url === `/api/regions/${ZH}/statistics/gender` && req.params.get('onlyHomeRegion') === only;
    const ages = (only: string) => (req: { url: string; params: { get(name: string): string | null } }) =>
      req.url === `/api/regions/${ZH}/statistics/age-bands` && req.params.get('onlyHomeRegion') === only;

    it('shows counters, the monthly chart and gender/age lists; the home-region switch reloads the lists', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/statistics`);
      http.expectOne(`/api/regions/${ZH}/statistics`).flush(STATS);
      const pickups = http.expectOne((req) => req.url === `/api/regions/${ZH}/statistics/pickups`);
      expect(pickups.request.params.get('months')).toBe('12');
      pickups.flush([{ month: '2026-09', pickups: 5, kg: 12.5 }, { month: '2026-10', pickups: 7, kg: 20 }]);
      http.expectOne(gender('false')).flush([{ gender: 'FEMALE', count: 20 }, { gender: 'MALE', count: 10 }]);
      http.expectOne(ages('false')).flush([{ label: '18-25', count: 8 }]);
      await settle();
      expect(q('[data-testid="regions.statistics.fsps"]')?.textContent).toContain('3');
      expect(q('[data-testid="regions.statistics.baskets"]')?.textContent).toContain('9');
      expect(all('[data-testid="regions.statistics.chart.bar"]').length).toBe(2);
      expect(q('[data-testid="regions.statistics.chart.bar"]')?.getAttribute('data-value')).toBe('5');
      expect(all('[data-testid="regions.statistics.genderRow"]').length).toBe(2);
      expect(q('[data-testid="regions.statistics.genderRow"]')?.textContent).toContain('weiblich');

      (q('[data-testid="regions.statistics.metric.kg"]') as HTMLButtonElement).click();
      await settle();
      expect(q('[data-testid="regions.statistics.chart.bar"]')?.getAttribute('data-value')).toBe('12.5');

      (q('[data-testid="regions.statistics.onlyHomeRegion"] input') as HTMLInputElement).click();
      await settle();
      http.expectOne(gender('true')).flush([{ gender: 'FEMALE', count: 5 }]);
      http.expectOne(ages('true')).flush([]);
      await settle();
      expect(all('[data-testid="regions.statistics.genderRow"]').length).toBe(1);
      expect(state('regions.statistics.ageState')).toBe('empty');
    });

    it('shows an error state for the chart and retries it', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/statistics`);
      http.expectOne(`/api/regions/${ZH}/statistics`).flush(STATS);
      http.expectOne((req) => req.url === `/api/regions/${ZH}/statistics/pickups`).flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
      http.expectOne(gender('false')).flush([]);
      http.expectOne(ages('false')).flush([]);
      await settle();
      expect(state('regions.statistics.pickupState')).toBe('error');
      (q('[data-testid="regions.statistics.pickupState"] [data-testid$=".retry"] button') as HTMLButtonElement).click();
      await settle();
      http.expectOne((req) => req.url === `/api/regions/${ZH}/statistics/pickups`).flush([]);
      await settle();
      expect(state('regions.statistics.pickupState')).toBe('empty');
    });

    it('renders a placeholder tab registered with route data', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/wall`);
      await settle();
      expect(q('[data-testid="regions.later.empty"]')?.textContent).toContain('Pinnwand kommt bald');
    });
  });
});
