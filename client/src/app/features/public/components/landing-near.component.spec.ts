import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import {
  MapMarker,
  MapMarkerKind,
  PublicRegion,
  RegionMatch,
  RegionSuggestion,
  UnitType,
} from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { LandingMarkersResource } from '../landing-markers.resource';
import { LandingNearComponent } from './landing-near.component';

const REGION_ID = '00000010-0001-4000-8000-000000000040';
const FSP = {
  wiedikon: '00000049-0001-4000-8000-000000000001',
  langstrasse: '00000049-0001-4000-8000-000000000002',
  winterthur: '00000049-0001-4000-8000-000000000007',
};

const FSP_MARKERS: MapMarker[] = [
  { id: FSP.wiedikon, kind: MapMarkerKind.Fsp, lat: 47.3695, lon: 8.5204 },
  { id: FSP.langstrasse, kind: MapMarkerKind.Fsp, lat: 47.3786, lon: 8.5292 },
  { id: FSP.winterthur, kind: MapMarkerKind.Fsp, lat: 47.4999, lon: 8.7262 },
];

const SUGGESTION: RegionSuggestion = {
  region: { id: REGION_ID, slug: 'zuerich-stadt', name: 'Zürich Stadt', type: UnitType.City },
  matchedBy: RegionMatch.Postcode,
};

function region(overrides: Partial<PublicRegion> = {}): PublicRegion {
  return {
    id: REGION_ID,
    slug: 'zuerich-stadt',
    name: 'Zürich Stadt',
    type: UnitType.City,
    children: [],
    memberCount: 1240,
    storeCount: 38,
    nextIntroPickups: [],
    ambassadorNames: [],
    pin: { latitude: 47.3779, longitude: 8.5403, active: true },
    ...overrides,
  } as PublicRegion;
}

describe('LandingNearComponent (C17)', () => {
  let fixture: ComponentFixture<LandingNearComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'public.landing.near.result.cooperations':
              '{count, plural, other {# aktive Kooperationen}}',
            'public.landing.near.result.members': '{count, plural, other {# Mitglieder}}',
            'public.landing.near.result.fsps': 'Fairteiler in der Nähe: {names}',
            'public.landing.near.result.noFsp': 'Noch kein Fairteiler in der Nähe.',
            'public.landing.near.result.notFound': 'Bezirk nicht gefunden',
            'public.landing.near.search.unavailable': 'Suche gerade nicht möglich',
            'public.landing.near.search.noMatch': 'Noch kein Bezirk in deiner Nähe – gründe einen',
          },
        }),
        provideOptimus(),
        LandingMarkersResource,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LandingNearComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const byTestId = (id: string) => host.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const state = () => byTestId('public.landing.near.state')!.getAttribute('data-state');

  async function flushMarkers(fsps: MapMarker[] | 'fail' = FSP_MARKERS): Promise<void> {
    http.expectOne('/api/public/map/markers/regions').flush([]);
    const fspReq = http.expectOne('/api/public/map/markers/food-share-points');
    if (fsps === 'fail') {
      fspReq.flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Error' });
    } else {
      fspReq.flush(fsps);
    }
    await fixture.whenStable();
  }

  async function search(postcode: string): Promise<void> {
    const input = byTestId('public.landing.near.search') as HTMLInputElement;
    input.value = postcode;
    input.closest('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  }

  function expectSuggest(postcode: string) {
    return http.expectOne(
      (req) => req.url === '/api/public/regions/suggest' && req.params.get('postcode') === postcode,
    );
  }

  it('shows the hint card before any search', async () => {
    await flushMarkers();
    expect(byTestId('public.landing.near.idle')).not.toBeNull();
    expect(byTestId('public.landing.near.result')).toBeNull();
  });

  it('rejects an invalid postcode without a request', async () => {
    await flushMarkers();
    await search('80');
    expect(byTestId('public.landing.near.search.hint')!.textContent).toContain(
      'public.landing.near.search.invalid',
    );
    expect(byTestId('public.landing.near.search')!.getAttribute('aria-invalid')).toBe('true');
    await search('Zürich');
    http.expectNone('/api/public/regions/suggest');
  });

  it('shows the empty state when no region matches', async () => {
    await flushMarkers();
    await search('9999');
    expectSuggest('9999').flush([]);
    await fixture.whenStable();
    expect(state()).toBe('empty');
    expect(byTestId('public.landing.near.state')!.textContent).toContain('gründe einen');
  });

  it('shows the result card with counts and the two nearest Fairteiler', async () => {
    await flushMarkers();
    await search('8004');
    expect(state()).toBe('loading');
    expectSuggest('8004').flush([SUGGESTION]);
    http.expectOne('/api/public/regions/zuerich-stadt').flush(region());
    http
      .expectOne(`/api/public/map/bubbles/fsp/${FSP.langstrasse}`)
      .flush({ title: 'Fairteiler Langstrasse', link: '/', lines: [] });
    http
      .expectOne(`/api/public/map/bubbles/fsp/${FSP.wiedikon}`)
      .flush({ title: 'Fairteiler Wiedikon', link: '/', lines: [] });
    await fixture.whenStable();
    expect(state()).toBe('success');
    const card = byTestId('public.landing.near.result')!;
    expect(card.getAttribute('aria-live')).toBe('polite');
    expect(byTestId('public.landing.near.result.name')!.textContent).toContain('Zürich Stadt');
    expect(byTestId('public.landing.near.result.cooperations')!.textContent).toContain(
      '38 aktive Kooperationen',
    );
    expect(byTestId('public.landing.near.result.members')!.textContent).toContain('Mitglieder');
    expect(byTestId('public.landing.near.result.fsps')!.textContent).toContain(
      'Fairteiler in der Nähe: Fairteiler Langstrasse, Fairteiler Wiedikon',
    );
    expect(byTestId('public.landing.near.regionLink')!.getAttribute('href')).toBe(
      '/r/zuerich-stadt',
    );
    // decision 179: "Karte öffnen" opens /map at the region's pin, zoom 11, and the landing map moves there
    expect(byTestId('public.landing.near.openMap')!.getAttribute('href')).toBe(
      '/map?lat=47.3779&lon=8.5403&z=11',
    );
    expect(TestBed.inject(LandingMarkersResource).focus()).toEqual({
      lat: 47.3779,
      lon: 8.5403,
      zoom: 11,
    });
    expect(byTestId('public.landing.near.idle')).toBeNull();
  });

  it('keeps the map and links plain /map for a region without a pin', async () => {
    await flushMarkers();
    await search('8004');
    expectSuggest('8004').flush([SUGGESTION]);
    http.expectOne('/api/public/regions/zuerich-stadt').flush(region({ pin: undefined }));
    await fixture.whenStable();
    expect(state()).toBe('success');
    expect(byTestId('public.landing.near.openMap')!.getAttribute('href')).toBe('/map');
    expect(TestBed.inject(LandingMarkersResource).focus()).toBeNull();
  });

  it('leaves the map where it is when the search finds nothing', async () => {
    await flushMarkers();
    await search('9999');
    expectSuggest('9999').flush([]);
    await fixture.whenStable();
    expect(state()).toBe('empty');
    expect(TestBed.inject(LandingMarkersResource).focus()).toBeNull();
  });

  it('leaves out a name whose bubble fails', async () => {
    await flushMarkers();
    await search('8004');
    expectSuggest('8004').flush([SUGGESTION]);
    http.expectOne('/api/public/regions/zuerich-stadt').flush(region());
    http
      .expectOne(`/api/public/map/bubbles/fsp/${FSP.langstrasse}`)
      .flush({ code: 'MARKER_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    http
      .expectOne(`/api/public/map/bubbles/fsp/${FSP.wiedikon}`)
      .flush({ title: 'Fairteiler Wiedikon', link: '/', lines: [] });
    await fixture.whenStable();
    expect(state()).toBe('success');
    expect(byTestId('public.landing.near.result.fsps')!.textContent).toContain(
      'Fairteiler in der Nähe: Fairteiler Wiedikon',
    );
    expect(byTestId('public.landing.near.result.fsps')!.textContent).not.toContain('Langstrasse');
  });

  it('says "no Fairteiler nearby" when the markers failed or none is within 10 km', async () => {
    await flushMarkers('fail');
    await search('8004');
    expectSuggest('8004').flush([SUGGESTION]);
    http.expectOne('/api/public/regions/zuerich-stadt').flush(region());
    await fixture.whenStable();
    expect(state()).toBe('success');
    expect(byTestId('public.landing.near.result.fsps')!.textContent).toContain(
      'Noch kein Fairteiler in der Nähe.',
    );
  });

  it('shows "Bezirk nicht gefunden" when the region is gone (404)', async () => {
    await flushMarkers();
    await search('8004');
    expectSuggest('8004').flush([SUGGESTION]);
    http
      .expectOne('/api/public/regions/zuerich-stadt')
      .flush({ code: 'REGION_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(state()).toBe('notFound');
    expect(byTestId('public.landing.near.state')!.textContent).toContain('Bezirk nicht gefunden');
    expect(byTestId('public.landing.near.state.retry')).toBeNull();
  });

  it('shows "Suche gerade nicht möglich" with retry on 503 GEOCODING_UNAVAILABLE', async () => {
    await flushMarkers();
    await search('8004');
    expectSuggest('8004').flush(
      {
        code: 'GEOCODING_UNAVAILABLE',
        message: 'x',
        traceId: 't',
        retryable: true,
        fieldErrors: [],
      },
      { status: 503, statusText: 'Unavailable' },
    );
    await fixture.whenStable();
    expect(state()).toBe('error');
    expect(byTestId('public.landing.near.state')!.textContent).toContain(
      'Suche gerade nicht möglich',
    );
    byTestId('public.landing.near.state.retry')!.querySelector('button')!.click();
    await fixture.whenStable();
    expectSuggest('8004').flush([]);
    await fixture.whenStable();
    expect(state()).toBe('empty');
  });
});
