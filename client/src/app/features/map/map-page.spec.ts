import { HttpTestingController } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { ApiErrorCode, Language, MapMarkerKind, Role, Theme, UnitType } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { MapFocus, MapViewChange } from '../../shared/map-view/map-view.component';
import { LAYERS_STORAGE_KEY } from './map.util';
import { MapPageComponent } from './pages/map-page.component';

const ME = '00000001-0001-4000-8000-000000000006';
const ZH = '00000010-0001-4000-8000-000000000040';
const F1 = '00000049-0001-4000-8000-000000000001';

const TOGGLES = '/api/public/feature-toggles';
const PUBLIC = '/api/public/map/markers';
const AREAS = '/api/public/map/store-areas';
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

  function login(admin = false, role: Role = Role.Foodsaver, verified = true): void {
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: { id: ME, email: 'foodsaver@local', displayName: 'Sarah', role, language: Language.De, theme: Theme.System, verified, homeRegionId: ZH },
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
    http.expectOne(AREAS).flush([{ name: 'Zürich Stadt', latitude: 47.37, longitude: 8.54, count: 16 }]);
    await settle();
    http.expectNone((req) => req.url.startsWith('/api/map/'));
    expect(state('map.page.map.state')).toBe('success');
    expect(all('[data-testid="map.page.layers"] button').length).toBe(4);
    expect(q('[data-testid="map.page.layer.storeAreas"]')).not.toBeNull();
    expect(q('[data-testid="map.page.layer.stores"]')).toBeNull();
    expect(q('[data-testid="map.page.search"]')).toBeNull();
    expect(q('[data-testid="map.page.loginHint"]')).toBeNull();
    expect(all('fs-map-view .fs-visually-hidden li').length).toBe(3);

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
    expect(JSON.parse(localStorage.getItem(LAYERS_STORAGE_KEY)!)).toEqual(['fsps', 'regions', 'storeAreas', 'stores']);
  });

  it('an unverified member gets the count bubbles, whose popup has no link, and never asks for store markers', async () => {
    login(false, Role.Foodsaver, false);
    localStorage.setItem(LAYERS_STORAGE_KEY, '["stores","storeAreas"]');
    await harness.navigateByUrl('/map');
    http.expectOne(TOGGLES).flush({});
    await settle();
    http.expectOne(AREAS).flush([{ name: 'Winterthur', latitude: 47.5, longitude: 8.72, count: 2 }]);
    await settle();
    http.expectNone((req) => req.url.includes('/markers/stores'));
    expect(q('[data-testid="map.page.layer.stores"]')).toBeNull();
    expect(q('[data-testid="map.page.layer.events"]')).not.toBeNull();

    page().open({ id: 'area:Winterthur', kind: 'store-area', lat: 47.5, lon: 8.72, label: '2 Betriebe · Winterthur' });
    await settle();
    expect(q('[data-testid="map.area.text"]')?.textContent).toContain('2 Betriebe · Winterthur');
    expect(q('[data-testid="map.area"] a')).toBeNull();
    http.expectNone((req) => req.url.includes('/bubbles/'));
  });

  it('a verified member gets the full store layer and no count bubbles', async () => {
    login();
    localStorage.setItem(LAYERS_STORAGE_KEY, '["stores","storeAreas"]');
    await harness.navigateByUrl('/map');
    http.expectOne(TOGGLES).flush({});
    await settle();
    http.expectNone(AREAS);
    expect(q('[data-testid="map.page.layer.storeAreas"]')).toBeNull();
    http.expectOne((req) => req.url === '/api/map/markers/stores').flush([]);
    http.match((req) => req.url.includes('categories')).forEach((req) => req.flush([]));
    await settle();
  });

  it('loads stores with the filter bar for signed-in users and searches a place', async () => {
    // a foodsharer: the category filter needs only a session (listStoreCategories), not the foodsaver store metadata
    login(false, Role.Foodsharer);
    localStorage.setItem(LAYERS_STORAGE_KEY, '["stores","events"]');
    await harness.navigateByUrl('/map');
    http.expectOne(TOGGLES).flush({ FOOD_SHARE_POINTS: true, BASKETS: true, MAP_USERS: true });
    await settle();
    http.expectOne((req) => req.url === '/api/map/markers/stores' && req.params.keys().length === 0).flush([
      { id: 's1', lat: 47.36, lon: 8.54, name: 'Bäckerei', cooperationStatus: 'COOPERATION_ESTABLISHED', teamStatus: 'OPEN' },
    ]);
    http.expectOne((req) => req.url === '/api/map/markers/events').flush([]);
    http.expectOne('/api/store-categories').flush([{ id: 'c1', name: 'Bäckerei' }]);
    http.expectNone('/api/store-metadata');
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

interface PageInternals {
  focus(): MapFocus | null;
  syncUrl(change: MapViewChange): void;
  locate(): void;
}

@Component({ template: '' })
class OtherPage {}

const ZURICH_DEFAULT: MapViewChange = { lat: 47.3769, lon: 8.5417, zoom: 8, byUser: true };

/** M1 deep link and URL sync of /map (decision 179, D12/D13). */
describe('MapPageComponent deep link (M1)', () => {
  let router: Router;
  let harness: RouterTestingHarness | undefined;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({ routes: [
            { path: 'map', component: MapPageComponent },
            { path: 'other', component: OtherPage },
          ], }),
        provideOptimus(),
      ],
    });
    // the logic only: no MapView, no Leaflet (the layer requests stay unanswered)
    TestBed.overrideComponent(MapPageComponent, { set: { template: '' } });
    router = TestBed.inject(Router);
    harness = undefined;
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    delete (navigator as { geolocation?: Geolocation }).geolocation;
  });

  /** A fresh map page at `url` (via another route, so the page is created anew and parses its link). */
  async function open(url: string): Promise<PageInternals> {
    harness ??= await RouterTestingHarness.create();
    await harness.navigateByUrl('/other');
    return (await harness.navigateByUrl(url, MapPageComponent)) as unknown as PageInternals;
  }

  it('opens at the view of a valid link, rounded and clamped', async () => {
    expect((await open('/map?lat=47.3769&lon=8.5417&z=13')).focus()).toEqual({ lat: 47.3769, lon: 8.5417, zoom: 13 });
    expect((await open('/map?lat=47.5&lon=8.7&z=50')).focus()).toEqual({ lat: 47.5, lon: 8.7, zoom: 19 });
    expect((await open('/map?lat=47.5&lon=8.7')).focus()).toEqual({ lat: 47.5, lon: 8.7, zoom: 12 });
  });

  it('keeps the default view for an invalid link or none', async () => {
    expect((await open('/map?lat=999&lon=x&z=50')).focus()).toBeNull();
    expect((await open('/map?lat=47.5')).focus()).toBeNull();
    expect((await open('/map')).focus()).toBeNull();
  });

  it('writes the view after a move, debounced and with replaceUrl', async () => {
    const page = await open('/map');
    const navigate = vi.spyOn(router, 'navigate');
    vi.useFakeTimers();
    page.syncUrl({ lat: 47.4, lon: 8.6, zoom: 10, byUser: true });
    page.syncUrl({ lat: 47.5, lon: 8.7123456, zoom: 11, byUser: true });
    await vi.advanceTimersByTimeAsync(299);
    expect(navigate).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate.mock.calls[0][1]).toMatchObject({
      queryParams: { lat: '47.5000', lon: '8.7123', z: 11 },
      replaceUrl: true,
    });
    await vi.advanceTimersByTimeAsync(0);
    expect(router.url).toBe('/map?lat=47.5000&lon=8.7123&z=11');

    // the same view again: no navigation
    page.syncUrl({ lat: 47.5, lon: 8.7123, zoom: 11, byUser: false });
    await vi.advanceTimersByTimeAsync(300);
    expect(navigate).toHaveBeenCalledTimes(1);

    // back at the default view: plain /map again
    page.syncUrl(ZURICH_DEFAULT);
    await vi.advanceTimersByTimeAsync(300);
    expect(router.url).toBe('/map');
  });

  it('writes nothing for the default view', async () => {
    const page = await open('/map');
    const navigate = vi.spyOn(router, 'navigate');
    vi.useFakeTimers();
    page.syncUrl(ZURICH_DEFAULT);
    page.syncUrl({ ...ZURICH_DEFAULT, byUser: false });
    await vi.advanceTimersByTimeAsync(600);
    expect(navigate).not.toHaveBeenCalled();
    expect(router.url).toBe('/map');
  });

  it('never writes a "near me" position, until the user moves the map by hand', async () => {
    Object.defineProperty(navigator, 'geolocation', {
      configurable: true,
      value: {
        getCurrentPosition: (success: PositionCallback) =>
          success({ coords: { latitude: 47.41, longitude: 8.55 } } as GeolocationPosition),
      },
    });
    const page = await open('/map?lat=47.5000&lon=8.7000&z=11');
    vi.useFakeTimers();
    page.locate();
    // MapView reports the move to the browser location (not by the user): the URL drops its params
    page.syncUrl({ lat: 47.41, lon: 8.55, zoom: 13, byUser: false });
    await vi.advanceTimersByTimeAsync(300);
    expect(router.url).toBe('/map');
    page.syncUrl({ lat: 47.41, lon: 8.55, zoom: 13, byUser: false });
    await vi.advanceTimersByTimeAsync(300);
    expect(router.url).toBe('/map');

    // a pan by hand writes again
    page.syncUrl({ lat: 47.42, lon: 8.56, zoom: 13, byUser: true });
    await vi.advanceTimersByTimeAsync(300);
    expect(router.url).toBe('/map?lat=47.4200&lon=8.5600&z=13');
  });

  it('never moves the map again on its own URL changes', async () => {
    const page = await open('/map?lat=47.5000&lon=8.7000&z=11');
    const first = page.focus();
    vi.useFakeTimers();
    page.syncUrl({ lat: 47.6, lon: 8.8, zoom: 12, byUser: true });
    await vi.advanceTimersByTimeAsync(300);
    expect(router.url).toBe('/map?lat=47.6000&lon=8.8000&z=12');
    expect(page.focus()).toBe(first);
  });
});
