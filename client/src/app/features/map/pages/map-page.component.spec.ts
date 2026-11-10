import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { MapFocus, MapViewChange } from '../../../shared/map-view/map-view.component';
import { MapPageComponent } from './map-page.component';

interface PageInternals {
  focus(): MapFocus | null;
  syncUrl(change: MapViewChange): void;
  locate(): void;
}

@Component({ template: '' })
class OtherPage {}

const ZURICH_DEFAULT: MapViewChange = { lat: 47.3769, lon: 8.5417, zoom: 8, byUser: true };

/** M1 deep link and URL sync of /map (decision 176, D12/D13). */
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
