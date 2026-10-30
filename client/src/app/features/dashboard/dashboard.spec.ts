import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { ActivityItem, ActivityType, ApiErrorCode, BasketStatus, Role } from '../../api';
import { FeatureToggleService } from '../../core/feature-toggles/feature-toggle.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { ActivityFeedCardComponent } from './components/activity-feed-card.component';
import { NearbyBasketsCardComponent } from './components/nearby-baskets-card.component';
import { mergeActivityPages, nearestBaskets, toggleExcluded } from './dashboard.util';

const tick = () => new Promise((resolve) => setTimeout(resolve));
const serverError = { status: 500, statusText: 'Server Error' };

let nextItemId = 0;

function item(overrides: Partial<ActivityItem> = {}): ActivityItem {
  return {
    id: `00000000-0000-4000-8000-${String(++nextItemId).padStart(12, '0')}`,
    type: ActivityType.Forum,
    at: '2026-11-01T10:00:00Z',
    title: 'Neues im Forum',
    excerpt: 'Wer hilft am Samstag?',
    link: '/forum/threads/t1',
    source: { id: 'r1', name: 'Zürich Stadt' },
    author: { id: 'u1', displayName: 'Reto Baumann', role: Role.Ambassador, verified: true },
    ...overrides,
  };
}

const page = (items: ActivityItem[], number: number, totalPages: number) => ({
  items,
  page: { page: number, size: 10, totalItems: totalPages * 10, totalPages },
});

describe('dashboard util', () => {
  it('toggles an activity type in the excluded list', () => {
    expect(toggleExcluded([], ActivityType.Mailbox)).toEqual([ActivityType.Mailbox]);
    expect(toggleExcluded([ActivityType.FspWall], ActivityType.Forum)).toEqual([ActivityType.Forum, ActivityType.FspWall]);
    expect(toggleExcluded([ActivityType.Forum, ActivityType.FspWall], ActivityType.Forum)).toEqual([ActivityType.FspWall]);
  });

  it('appends feed pages without repeating items', () => {
    const a = item();
    const b = item({ title: 'Zweiter', at: '2026-10-31T10:00:00Z' });
    const c = item({ title: 'Dritter', at: '2026-10-30T10:00:00Z' });
    expect(mergeActivityPages([a, b], [b, c]).map((entry) => entry.title)).toEqual(['Neues im Forum', 'Zweiter', 'Dritter']);
  });

  it('keeps different posts with the same time, link and title (tied store wall posts)', () => {
    const first = item({ type: ActivityType.StoreWall, title: 'Migros City', link: '/stores/s1' });
    const second = { ...first, id: '00000000-0000-4000-8000-999999999999' };
    expect(mergeActivityPages([first], [first, second]).map((entry) => entry.id)).toEqual([first.id, second.id]);
  });

  it('keeps the three nearest baskets', () => {
    const basket = (id: string) => ({ id, description: id, lat: 47, lon: 8, createdAt: '', expiresAt: '', owner: { id: 'o', displayName: 'O', role: Role.Foodsaver, verified: true }, status: BasketStatus.Active });
    expect(nearestBaskets(['a', 'b', 'c', 'd'].map(basket)).map((entry) => entry.id)).toEqual(['a', 'b', 'c']);
  });
});

describe('dashboard cards', () => {
  let http: HttpTestingController;
  const settle = async (fixture: ComponentFixture<unknown>) => {
    await tick();
    await fixture.whenStable();
  };
  const q = (fixture: ComponentFixture<unknown>, selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector);
  const all = (fixture: ComponentFixture<unknown>, selector: string) => (fixture.nativeElement as HTMLElement).querySelectorAll(selector);
  const state = (fixture: ComponentFixture<unknown>, testId: string) => q(fixture, `[data-testid="${testId}"]`)?.getAttribute('data-state');

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  describe('activity feed', () => {
    const feed = (pageNo: number) => (req: { url: string; params: { get(name: string): string | null } }) =>
      req.url === '/api/activity' && req.params.get('page') === String(pageNo);

    it('lists items, loads more and refreshes after a filter change', async () => {
      const fixture = TestBed.createComponent(ActivityFeedCardComponent);
      await settle(fixture);
      expect(state(fixture, 'dashboard.activity.state')).toBe('loading');
      http.expectOne('/api/users/me/activity-filters').flush({ excluded: [ActivityType.BuddyWall] });
      http.expectOne(feed(0)).flush(page([item(), item({ type: ActivityType.StoreWall, title: 'Pinnwand', link: '/stores/s1/wall' })], 0, 2));
      await settle(fixture);
      expect(state(fixture, 'dashboard.activity.state')).toBe('success');
      expect(all(fixture, '[data-testid="dashboard.activity.item"]').length).toBe(2);
      expect(q(fixture, '[data-testid="dashboard.activity.link"]')?.getAttribute('href')).toBe('/forum/threads/t1');
      expect(q(fixture, `[data-testid="dashboard.activity.filter.${ActivityType.BuddyWall}"]`)?.getAttribute('aria-pressed')).toBe('false');
      expect(q(fixture, `[data-testid="dashboard.activity.filter.${ActivityType.Forum}"]`)?.getAttribute('aria-pressed')).toBe('true');

      (q(fixture, '[data-testid="dashboard.activity.loadMore"] button') as HTMLButtonElement).click();
      await settle(fixture);
      http.expectOne(feed(1)).flush(page([item({ type: ActivityType.Mailbox, title: 'Mail', link: '/mailboxes/m1' })], 1, 2));
      await settle(fixture);
      expect(all(fixture, '[data-testid="dashboard.activity.item"]').length).toBe(3);
      expect(q(fixture, '[data-testid="dashboard.activity.loadMore"]')).toBeNull();

      (q(fixture, `[data-testid="dashboard.activity.filter.${ActivityType.Forum}"]`) as HTMLButtonElement).click();
      await settle(fixture);
      const save = http.expectOne((req) => req.method === 'PUT' && req.url === '/api/users/me/activity-filters');
      expect(save.request.body).toEqual({ excluded: [ActivityType.Forum, ActivityType.BuddyWall] });
      save.flush({ excluded: [ActivityType.Forum, ActivityType.BuddyWall] });
      await settle(fixture);
      http.expectOne(feed(0)).flush(page([item({ type: ActivityType.StoreWall, title: 'Pinnwand', link: '/stores/s1/wall' })], 0, 1));
      await settle(fixture);
      expect(all(fixture, '[data-testid="dashboard.activity.item"]').length).toBe(1);
      expect(q(fixture, `[data-testid="dashboard.activity.filter.${ActivityType.Forum}"]`)?.getAttribute('aria-pressed')).toBe('false');
    });

    it('shows the empty state and the error state with retry', async () => {
      const fixture = TestBed.createComponent(ActivityFeedCardComponent);
      await settle(fixture);
      http.expectOne('/api/users/me/activity-filters').flush({ excluded: [] });
      http.expectOne(feed(0)).flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
      await settle(fixture);
      expect(state(fixture, 'dashboard.activity.state')).toBe('error');
      (q(fixture, '[data-testid="dashboard.activity.state"] [data-testid$=".retry"] button') as HTMLButtonElement).click();
      await settle(fixture);
      http.expectOne(feed(0)).flush(page([], 0, 0));
      await settle(fixture);
      expect(state(fixture, 'dashboard.activity.state')).toBe('empty');
    });
  });

  describe('nearby baskets', () => {
    async function create(baskets: boolean) {
      void TestBed.inject(FeatureToggleService).load();
      http.expectOne('/api/public/feature-toggles').flush({ BASKETS: baskets });
      await tick();
      const fixture = TestBed.createComponent(NearbyBasketsCardComponent);
      await settle(fixture);
      return fixture;
    }

    const me = (lat?: number, lon?: number) => ({
      id: 'me',
      email: 'foodsaver@local',
      displayName: 'Sarah',
      language: 'de',
      theme: 'SYSTEM',
      role: Role.Foodsaver,
      verified: true,
      registeredAt: '2025-01-01T00:00:00Z',
      latitude: lat,
      longitude: lon,
    });

    it('shows the three nearest baskets around the home coordinates', async () => {
      const fixture = await create(true);
      http.expectOne('/api/users/me').flush(me(47.38, 8.53));
      await settle(fixture);
      const nearby = http.expectOne((req) => req.url === '/api/baskets/nearby');
      expect(nearby.request.params.get('lat')).toBe('47.38');
      expect(nearby.request.params.get('distanceKm')).toBe('30');
      const owner = { id: 'o', displayName: 'Nina', role: Role.Foodsaver, verified: true };
      nearby.flush(
        ['a', 'b', 'c', 'd'].map((id, index) => ({ id, description: `Korb ${id}`, lat: 47.38, lon: 8.53, distanceKm: index + 1, createdAt: '2026-11-01T08:00:00Z', expiresAt: '2099-01-01T00:00:00Z', owner, status: BasketStatus.Active })),
      );
      await settle(fixture);
      expect(all(fixture, 'fs-basket-card').length).toBe(3);
      expect(state(fixture, 'dashboard.baskets.state')).toBe('success');
    });

    it('links to the profile address without coordinates', async () => {
      const fixture = await create(true);
      http.expectOne('/api/users/me').flush(me());
      await settle(fixture);
      http.expectNone((req) => req.url === '/api/baskets/nearby');
      expect(q(fixture, '[data-testid="dashboard.baskets.addAddress"]')?.getAttribute('href')).toBe('/account/profile');
    });

    it('stays hidden while baskets are switched off', async () => {
      const fixture = await create(false);
      http.expectNone('/api/users/me');
      expect(q(fixture, '[data-testid="dashboard.baskets"]')).toBeNull();
    });
  });
});
