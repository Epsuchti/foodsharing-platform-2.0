import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { MapMarker as ApiMapMarker, MapMarkerKind } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { MapViewComponent } from '../../../shared/map-view/map-view.component';
import { LandingMarkersResource } from '../landing-markers.resource';
import { LandingMapComponent } from './landing-map.component';

const REGIONS: ApiMapMarker[] = [
  { id: '00000010-0001-4000-8000-000000000040', kind: MapMarkerKind.RegionPin, lat: 47.3779, lon: 8.5403 },
  { id: '00000010-0001-4000-8000-000000000041', kind: MapMarkerKind.Region, lat: 47.4999, lon: 8.7262 },
];
const FSP_ID = '00000049-0001-4000-8000-000000000001';
const FSPS: ApiMapMarker[] = [{ id: FSP_ID, kind: MapMarkerKind.Fsp, lat: 47.3695, lon: 8.5204 }];

const REGIONS_URL = '/api/public/map/markers/regions';
const FSPS_URL = '/api/public/map/markers/food-share-points';

/** C17 landing map (decision 176): pins, counts, popup, focus and the `/map` deep link. */
describe('LandingMapComponent (C17)', () => {
  let fixture: ComponentFixture<LandingMapComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(async () => {
    // a raster stub instead of the PMTiles probe (as the screenshots do)
    (globalThis as { __fsTileUrl?: string }).__fsTileUrl = '/stub/{z}/{x}/{y}.png';
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'public.landing.near.map.note': '{regions} Bezirke · {fsps} Fairteiler',
            'public.landing.near.map.noteNoCount': 'Bezirke und Fairteiler in deiner Nähe',
            'public.landing.near.map.label': 'Karte',
            'public.landing.near.map.kind.region': 'Bezirk',
            'public.landing.near.map.kind.fsp': 'Fairteiler',
            'public.landing.near.map.bubble.gone': 'Dieser Ort ist nicht mehr verfügbar.',
          },
        }),
        provideOptimus(),
        LandingMarkersResource,
      ],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LandingMapComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    delete (globalThis as { __fsTileUrl?: string }).__fsTileUrl;
  });

  const byTestId = (id: string) => host.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const note = () => byTestId('public.landing.near.map.note')!.textContent!.trim();
  const mapView = () => fixture.debugElement.query(By.directive(MapViewComponent))?.componentInstance as MapViewComponent;

  async function flush(fsps: ApiMapMarker[] | 'fail' = FSPS): Promise<void> {
    http.expectOne(REGIONS_URL).flush(REGIONS);
    const fspReq = http.expectOne(FSPS_URL);
    if (fsps === 'fail') {
      fspReq.flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Error' });
    } else {
      fspReq.flush(fsps);
    }
    await fixture.whenStable();
  }

  async function renderMap(): Promise<void> {
    const [inner] = await fixture.getDeferBlocks();
    await inner.render(DeferBlockState.Complete);
    await fixture.whenStable();
  }

  it('shows a plain frame and the deep link of the DACH view before the map is in view', async () => {
    await flush();
    expect(byTestId('public.landing.near.map.placeholder')).not.toBeNull();
    expect(host.querySelector('fs-map-view')).toBeNull();
    // jsdom matches no media query: the phone zoom
    expect(byTestId('public.landing.near.map.openMap')!.getAttribute('href')).toBe('/map?lat=47.9000&lon=10.4000&z=5');
    expect(note()).toBe('2 Bezirke · 1 Fairteiler');
  });

  it('maps region and Fairteiler markers to labelled pins without Tab stops', async () => {
    await flush();
    await renderMap();
    await vi.waitFor(() => expect(host.querySelectorAll('.fs-map-marker')).toHaveLength(3));
    expect(host.querySelector('.fs-map-marker[data-kind="region-pin"]')!.getAttribute('title')).toBe('Bezirk');
    expect(host.querySelector('.fs-map-marker[data-kind="region"]')!.getAttribute('title')).toBe('Bezirk');
    const fsp = host.querySelector('.fs-map-marker[data-kind="fsp"]')!;
    expect(fsp.getAttribute('title')).toBe('Fairteiler');
    expect(fsp.querySelector('.pi-box')).not.toBeNull();
    expect(fsp.hasAttribute('tabindex')).toBe(false);
    expect(mapView().cooperative()).toBe(true);
    expect(mapView().fitMarkers()).toBe(false);
    expect(mapView().ariaLabel()).toBe('Karte: 2 Bezirke · 1 Fairteiler');
  });

  it('drops the pins and the counts of a failing list', async () => {
    await flush('fail');
    await renderMap();
    await vi.waitFor(() => expect(host.querySelectorAll('.fs-map-marker')).toHaveLength(2));
    expect(host.querySelector('.fs-map-marker[data-kind="fsp"]')).toBeNull();
    expect(note()).toBe('Bezirke und Fairteiler in deiner Nähe');
    expect(mapView().ariaLabel()).toBe('Karte');
  });

  it('opens the popup of a clicked pin with its kind, title, address and link', async () => {
    await flush();
    await renderMap();
    fixture.componentInstance.open({ id: FSP_ID, kind: 'fsp', lat: 47.3695, lon: 8.5204, label: 'Fairteiler' });
    await fixture.whenStable();
    expect(byTestId('public.landing.near.map.bubble.state')!.getAttribute('data-state')).toBe('loading');
    http
      .expectOne(`/api/public/map/bubbles/fsp/${FSP_ID}`)
      .flush({ title: 'Fairteiler Wiedikon', lines: ['Birmensdorferstrasse 1', '8003 Zürich', '12'], link: '/fsp/wiedikon' });
    await fixture.whenStable();
    const popup = byTestId('public.landing.near.map.bubble')!;
    expect(popup.getAttribute('data-kind')).toBe(MapMarkerKind.Fsp);
    expect(popup.textContent).toContain('Fairteiler');
    expect(byTestId('public.landing.near.map.bubble.title')!.textContent!.trim()).toBe('Fairteiler Wiedikon');
    expect(byTestId('public.landing.near.map.bubble.address')!.textContent!.trim()).toBe('Birmensdorferstrasse 1, 8003 Zürich');
    expect(byTestId('public.landing.near.map.bubble.open')!.getAttribute('href')).toBe('/fsp/wiedikon');
    expect(document.activeElement).toBe(byTestId('public.landing.near.map.bubble.title'));

    byTestId('public.landing.near.map.bubble.close')!.click();
    await fixture.whenStable();
    expect(byTestId('public.landing.near.map.bubble')).toBeNull();
  });

  it('asks for the region pin bubble and closes on Escape', async () => {
    await flush();
    await renderMap();
    fixture.componentInstance.open({ id: REGIONS[0].id, kind: 'region-pin', lat: 47.3779, lon: 8.5403, label: 'Bezirk' });
    await fixture.whenStable();
    http
      .expectOne(`/api/public/map/bubbles/region_pin/${REGIONS[0].id}`)
      .flush({ title: 'Zürich Stadt', subtitle: 'CITY', lines: ['1240'], link: '/r/zuerich-stadt' });
    await fixture.whenStable();
    expect(byTestId('public.landing.near.map.bubble.address')).toBeNull();
    expect(byTestId('public.landing.near.map.bubble.open')!.getAttribute('href')).toBe('/r/zuerich-stadt');
    byTestId('public.landing.near.map.bubble')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await fixture.whenStable();
    expect(byTestId('public.landing.near.map.bubble')).toBeNull();
  });

  it('shows "no longer available" for a marker that is gone', async () => {
    await flush();
    await renderMap();
    fixture.componentInstance.open({ id: FSP_ID, kind: 'fsp', lat: 47.3695, lon: 8.5204, label: 'Fairteiler' });
    await fixture.whenStable();
    http
      .expectOne(`/api/public/map/bubbles/fsp/${FSP_ID}`)
      .flush({ code: 'MARKER_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    const state = byTestId('public.landing.near.map.bubble.state')!;
    expect(state.getAttribute('data-state')).toBe('notFound');
    expect(state.textContent).toContain('Dieser Ort ist nicht mehr verfügbar.');
  });

  it('follows the focus of the resource and links "Karte öffnen" to the current view', async () => {
    await flush();
    await renderMap();
    const markers = TestBed.inject(LandingMarkersResource);
    markers.focus.set({ lat: 47.3779, lon: 8.5403, zoom: 11 });
    await fixture.whenStable();
    expect(mapView().focus()).toEqual({ lat: 47.3779, lon: 8.5403, zoom: 11 });

    mapView().viewChange.emit({ lat: 47.37791234, lon: 8.54031234, zoom: 11, byUser: false });
    await fixture.whenStable();
    expect(byTestId('public.landing.near.map.openMap')!.getAttribute('href')).toBe('/map?lat=47.3779&lon=8.5403&z=11');
  });
});
