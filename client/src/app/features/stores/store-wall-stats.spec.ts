import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { StorePickupHistoryTab } from '../pickups/tabs/store-pickup-history.tab';
import { monthLabel } from './components/month-bar-chart.component';
import { StorePageComponent } from './pages/store-page.component';
import { STORE_LOG_PRESETS } from './stores.resource';
import { StoreStatisticsTab } from './tabs/store-statistics.tab';
import { StoreWallTab } from './tabs/store-wall.tab';

const PAGE = (items: unknown[]) => ({ items, page: { page: 0, size: 20, totalItems: items.length, totalPages: 1 } });
const USER = { id: 'u1', displayName: 'Sarah Müller', role: 'FOODSAVER', verified: true };

function detail(permissions: Record<string, boolean>): Record<string, unknown> {
  return {
    id: 's1',
    name: 'Coop',
    address: { street: 'Weg 1', postcode: '8000', city: 'Zürich' },
    cooperationStatus: 'COOPERATION_ESTABLISHED',
    teamStatus: 'OPEN',
    region: { id: 'r1', slug: 'zh', name: 'Zürich', type: 'CITY' },
    timeZone: 'Europe/Zurich',
    managers: [],
    teamCounts: { members: 2, jumpers: 1, applied: 0, invited: 0 },
    membership: 'MEMBER',
    prefetchDays: 14,
    permissions: { mayAccess: true, teamStatus: 'MEMBER', ...permissions },
  };
}

describe('store log presets and month labels', () => {
  it('action groups are disjoint', () => {
    const all = STORE_LOG_PRESETS.flatMap((preset) => preset.actions);
    expect(new Set(all).size).toBe(all.length);
    expect(STORE_LOG_PRESETS.map((preset) => preset.id)).toEqual(['team', 'pickups', 'wall', 'settings']);
  });

  it('formats months in the UI locale', () => {
    expect(monthLabel('2026-10', 'en-CH')).toMatch(/Oct/);
    expect(monthLabel('2026-01-01', 'de-CH')).toMatch(/Jan/);
  });
});

describe('store wall, statistics and pickup history tabs', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const qa = (selector: string) => [...harness.routeNativeElement!.querySelectorAll(selector)];
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            {
              path: 'stores/:storeId',
              component: StorePageComponent,
              children: [
                { path: 'wall', component: StoreWallTab },
                { path: 'statistics', component: StoreStatisticsTab },
                { path: 'pickups/history', component: StorePickupHistoryTab },
              ],
            },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const open = async (tab: string, permissions: Record<string, boolean>) => {
    await harness.navigateByUrl(`/stores/s1/${tab}`);
    http.expectOne('/api/stores/s1').flush(detail(permissions));
    await settle();
  };

  it('wall: milestones apart from posts, delete only where allowed, composer for posters, more pages', async () => {
    await open('wall', { mayReadWall: true, mayPostWall: true });
    const req = http.expectOne((r) => r.url === '/api/stores/s1/wall');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({
      items: [
        { id: 'p1', author: USER, text: 'Hallo Team', milestone: 'NONE', createdAt: '2026-11-01T10:00:00Z', mayDelete: true },
        { id: 'p2', author: USER, text: 'Fremd', milestone: 'NONE', createdAt: '2026-10-31T10:00:00Z', mayDelete: false },
        { id: 'm1', text: 'Coop', milestone: 'COOPERATION_STARTED', createdAt: '2025-01-01T10:00:00Z', mayDelete: false },
      ],
      page: { page: 0, size: 20, totalItems: 30, totalPages: 2 },
    });
    await settle();
    expect(qa('[data-testid="stores.wall.post"]')).toHaveLength(3);
    expect(qa('[data-testid="stores.wall.milestone"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.wall.delete"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.wall.composer"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.page.tab.wall"]')).toHaveLength(1);

    (qa('[data-testid="stores.wall.more"] button')[0] as HTMLButtonElement).click();
    await settle();
    const more = http.expectOne((r) => r.url === '/api/stores/s1/wall');
    expect(more.request.params.get('size')).toBe('40');
    more.flush(PAGE([]));
    await settle();
  });

  it('wall: readers without post right get no composer', async () => {
    await open('wall', { mayReadWall: true });
    http.expectOne((r) => r.url === '/api/stores/s1/wall').flush(PAGE([]));
    await settle();
    expect(qa('[data-testid="stores.wall.composer"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.wall.state"]')[0].getAttribute('data-state')).toBe('empty');
  });

  it('statistics: one bar per month, kg hint and per-member table', async () => {
    await open('statistics', { mayEditStore: true, maySeeStoreLog: true });
    http.expectOne((r) => r.url === '/api/stores/s1/statistics').flush({
      totalPickups: 5,
      totalKg: 75,
      weightCategory: { code: 'KG_10_20', label: '10–20 kg', averageKg: 15 },
      months: [
        { month: '2026-09', pickups: 2, kg: 30 },
        { month: '2026-10', pickups: 3, kg: 45 },
      ],
      perMember: [{ user: USER, pickups: 5 }],
    });
    await settle();
    const bars = qa('[data-testid="stores.statistics.chart.bar"]');
    expect(bars.map((bar) => bar.getAttribute('data-value'))).toEqual(['2', '3']);
    expect(qa('[data-testid="stores.statistics.member"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.statistics.weightHint"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.page.tab.log"]')).toHaveLength(1);

    (qa('[data-testid="stores.statistics.metric.kg"]')[0] as HTMLButtonElement).click();
    await settle();
    expect(qa('[data-testid="stores.statistics.chart.bar"]').map((bar) => bar.getAttribute('data-value'))).toEqual(['30', '45']);

    (qa('[data-testid="stores.statistics.range.all"]')[0] as HTMLButtonElement).click();
    await settle();
    const all = http.expectOne((r) => r.url === '/api/stores/s1/statistics');
    expect(all.request.params.get('from')).toBe('2012-01-01');
    all.flush({ totalPickups: 0, totalKg: 0, months: [] });
    await settle();
  });

  it('pickup history: KAM tab links to the history, range in the store time zone', async () => {
    await open('pickups/history', { maySeePickupHistory: true, isKam: true });
    expect(qa('[data-testid="stores.page.tab.pickups"]')[0].getAttribute('href')).toBe('/stores/s1/pickups/history');
    http.expectOne((r) => r.url === '/api/stores/s1/pickup-history').flush(
      PAGE([
        { storeId: 's1', pickupAt: '2026-10-01T17:00:00Z', slotCount: 2, occupiedSlots: [], isAvailable: false, isRegular: true },
        { storeId: 's1', pickupAt: '2026-10-29T18:00:00Z', slotCount: 2, occupiedSlots: [{ user: USER, confirmed: true }], isAvailable: false, isRegular: true },
      ]),
    );
    await settle();
    const rows = qa('[data-testid="stores.pickupHistory.row"]');
    expect(rows.map((row) => row.getAttribute('data-pickup-at'))).toEqual(['2026-10-29T18:00:00Z', '2026-10-01T17:00:00Z']);
    expect(qa('[data-testid="stores.pickupHistory.upcoming"]')).toHaveLength(0);
  });
});
