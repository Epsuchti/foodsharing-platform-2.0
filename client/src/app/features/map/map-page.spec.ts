import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { ApiErrorCode, Language, MapMarkerKind, Role, Theme, UnitType } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { LAYERS_STORAGE_KEY } from './map.util';
import { MapPageComponent } from './pages/map-page.component';

const ME = '00000001-0001-4000-8000-000000000006';
const ZH = '00000010-0001-4000-8000-000000000040';
const F1 = '00000049-0001-4000-8000-000000000001';

const TOGGLES = '/api/public/feature-toggles';
const PUBLIC = '/api/public/map/markers';
const tick = () => new Promise((resolve) => setTimeout(resolve));

interface MapPageApi {
  open(marker: { id: string; kind: string; lat: number; lon: number; label: string }): void;
  storeFilter: { update(fn: (value: Record<string, string>) => Record<string, string>): void };
}

describe('map page', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const all = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
  const settle = async () => {
    await tick();
    await harness.fixture.whenStable();
  };
  const page = () => harness.routeDebugElement!.componentInstance as unknown as MapPageApi;
  const serverError = { status: 500, statusText: 'Server Error' };

  beforeEach(async () => {
    localStorage.removeItem(LAYERS_STORAGE_KEY);
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting({ routes: [{ path: 'map', component: MapPageComponent }] }), provideOptimus()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    localStorage.removeItem(LAYERS_STORAGE_KEY);
  });

  function login(admin = false): void {
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: { id: ME, email: 'foodsaver@local', displayName: 'Sarah', role: Role.Foodsaver, language: Language.De, theme: Theme.System, verified: true, homeRegionId: ZH },
      units: [{ id: ZH, name: 'Zürich Stadt', slug: 'zuerich-stadt', type: UnitType.BigCity, member: true, admin, homeRegion: true }],
    });
  }

  it('shows the public layers to visitors and opens a public bubble', async () => {
    await harness.navigateByUrl('/map');
    http.expectOne(TOGGLES).flush({ FOOD_SHARE_POINTS: true, BASKETS: true, MAP_USERS: true });
    await settle();
    http.expectOne(`${PUBLIC}/food-share-points`).flush([{ id: F1, kind: MapMarkerKind.Fsp, lat: 47.37, lon: 8.51 }]);
    http.expectOne(`${PUBLIC}/regions`).flush([{ id: ZH, kind: MapMarkerKind.RegionPin, lat: 47.37, lon: 8.54, status: 'ACTIVE' }]);
    http.expectOne(`${PUBLIC}/baskets`).flush([]);
    await settle();
    http.expectNone((req) => req.url.startsWith('/api/map/'));
    expect(state('map.page.map.state')).toBe('success');
    expect(all('[data-testid="map.page.layers"] button').length).toBe(3);
    expect(q('[data-testid="map.page.layer.stores"]')).toBeNull();
    expect(q('[data-testid="map.page.search"]')).toBeNull();
    expect(q('[data-testid="map.page.loginHint"]')).not.toBeNull();
    expect(all('fs-map-view .fs-visually-hidden li').length).toBe(2);

    page().open({ id: F1, kind: 'fsp', lat: 47.37, lon: 8.51, label: 'FSP' });
    await settle();
    http.expectOne(`/api/public/map/bubbles/fsp/${F1}`).flush({ title: 'Fairteiler Wiedikon', subtitle: 'Zürich', link: `/food-share-points/${F1}`, lines: ['Schlossgasse 10', '8003 Zürich', '3'] });
    await settle();
    expect(q('[data-testid="map.bubble.title"]')?.textContent).toContain('Fairteiler Wiedikon');
    expect(all('[data-testid="map.bubble.lines"] li').length).toBe(3);
    expect(q('[data-testid="map.bubble.link"]')?.getAttribute('href')).toBe(`/food-share-points/${F1}`);
    // a store pin is not public: no request
    page().open({ id: 's', kind: 'store', lat: 1, lon: 1, label: 's' });
    await settle();
    http.expectNone((req) => req.url.includes('/bubbles/store/'));

    // switching a layer off is remembered
    (q('[data-testid="map.page.layer.baskets"]') as HTMLButtonElement).click();
    await settle();
    expect(JSON.parse(localStorage.getItem(LAYERS_STORAGE_KEY)!)).toEqual(['fsps', 'regions', 'stores']);
  });

  it('loads stores with the filter bar for signed-in users and searches a place', async () => {
    login();
    localStorage.setItem(LAYERS_STORAGE_KEY, '["stores","events"]');
    await harness.navigateByUrl('/map');
    http.expectOne(TOGGLES).flush({ FOOD_SHARE_POINTS: true, BASKETS: true, MAP_USERS: true });
    await settle();
    http.expectOne((req) => req.url === '/api/map/markers/stores' && req.params.keys().length === 0).flush([
      { id: 's1', lat: 47.36, lon: 8.54, name: 'Bäckerei', cooperationStatus: 'COOPERATION_ESTABLISHED', teamStatus: 'OPEN' },
    ]);
    http.expectOne((req) => req.url === '/api/map/markers/events').flush([]);
    http.expectOne('/api/store-metadata').flush({ categories: [{ id: 'c1', name: 'Bäckerei' }], groceries: [], weightCategories: [], chains: [] });
    await settle();
    // not an administrator: no member layer
    expect(q('[data-testid="map.page.layer.users"]')).toBeNull();
    expect(q('[data-testid="map.page.storeFilter.categoryId"]')).not.toBeNull();
    expect(all('fs-map-view .fs-visually-hidden li')[0]?.textContent).toContain('Bäckerei');

    page().storeFilter.update((value) => ({ ...value, status: 'COOPERATING', help: 'SEARCHING' }));
    await settle();
    const filtered = http.expectOne((req) => req.url === '/api/map/markers/stores');
    expect(filtered.request.params.get('status')).toBe('COOPERATING');
    expect(filtered.request.params.get('help')).toBe('SEARCHING');
    expect(filtered.request.params.has('scope')).toBe(false);
    filtered.flush([]);
    await settle();

    page().open({ id: 's1', kind: 'store', lat: 47.36, lon: 8.54, label: 'Bäckerei' });
    await settle();
    http.expectOne('/api/map/bubbles/store/s1').flush({ title: 'Bäckerei', subtitle: 'COOPERATION_ESTABLISHED', link: '/stores/s1', lines: ['8004 Zürich', 'OPEN'] });
    await settle();
    expect(q('[data-testid="map.bubble"]')?.getAttribute('data-kind')).toBe('STORE');

    const input = q('[data-testid="map.page.search"]') as HTMLInputElement;
    input.value = 'Winterthur';
    input.dispatchEvent(new Event('input'));
    await settle();
    (q('[data-testid="map.page.searchSubmit"] button') as HTMLButtonElement).click();
    await settle();
    http.expectOne((req) => req.url === '/api/map/search' && req.params.get('q') === 'Winterthur').flush([{ label: 'Winterthur', lat: 47.5, lon: 8.72 }]);
    await settle();
    expect(all('[data-testid="map.page.searchResult"]').length).toBe(1);
    expect(q('[data-testid="map.page.searchResult"]')?.classList).toContain('hit--on');
  });

  it('offers the member layer to region administrators when MAP_USERS is on', async () => {
    login(true);
    localStorage.setItem(LAYERS_STORAGE_KEY, '["users"]');
    await harness.navigateByUrl('/map');
    http.expectOne(TOGGLES).flush({ MAP_USERS: true });
    await settle();
    const users = http.expectOne((req) => req.url === '/api/map/markers/users');
    expect(users.request.params.get('regionId')).toBe(ZH);
    users.flush([{ id: 'u1', kind: MapMarkerKind.User, lat: 47.389, lon: 8.548, status: 'FOODSAVER' }]);
    await settle();
    expect(q('[data-testid="map.page.layer.users"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(q('[data-testid="map.page.userFilter.regionId"]')).not.toBeNull();
  });

  it('hides the member layer when MAP_USERS is off', async () => {
    login(true);
    localStorage.setItem(LAYERS_STORAGE_KEY, '["users"]');
    await harness.navigateByUrl('/map');
    http.expectOne(TOGGLES).flush({ MAP_USERS: false });
    await settle();
    http.expectNone((req) => req.url === '/api/map/markers/users');
    expect(q('[data-testid="map.page.layer.users"]')).toBeNull();
  });

  it('shows the error state of a failed layer and retries it', async () => {
    localStorage.setItem(LAYERS_STORAGE_KEY, '["regions"]');
    await harness.navigateByUrl('/map');
    http.expectOne(TOGGLES).flush({});
    await settle();
    expect(state('map.page.map.state')).toBe('loading');
    http.expectOne(`${PUBLIC}/regions`).flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
    await settle();
    expect(state('map.page.map.state')).toBe('error');
    (q('[data-testid="map.page.map.state"] [data-testid$=".retry"] button') as HTMLButtonElement).click();
    await settle();
    http.expectOne(`${PUBLIC}/regions`).flush([]);
    await settle();
    expect(state('map.page.map.state')).toBe('success');
  });
});
