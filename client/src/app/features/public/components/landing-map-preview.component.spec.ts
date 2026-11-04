import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach } from 'vitest';

import { MapMarker, MapMarkerKind } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { LandingMarkersResource } from '../landing-markers.resource';
import { LandingMapPreviewComponent } from './landing-map-preview.component';

const REGIONS: MapMarker[] = [
  {
    id: '00000010-0001-4000-8000-000000000040',
    kind: MapMarkerKind.Region,
    lat: 47.3779,
    lon: 8.5403,
  },
  {
    id: '00000010-0001-4000-8000-000000000041',
    kind: MapMarkerKind.Region,
    lat: 47.4999,
    lon: 8.7262,
  },
  // Rome: outside the DACH frame, counted but not drawn
  { id: '00000010-0001-4000-8000-000000000099', kind: MapMarkerKind.Region, lat: 41.9, lon: 12.5 },
];
const FSPS: MapMarker[] = [
  {
    id: '00000049-0001-4000-8000-000000000001',
    kind: MapMarkerKind.Fsp,
    lat: 47.3695,
    lon: 8.5204,
  },
];

describe('LandingMapPreviewComponent (C17)', () => {
  let fixture: ComponentFixture<LandingMapPreviewComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'public.landing.near.previewNote': '{regions} Bezirke · {fsps} Fairteiler.',
            'public.landing.near.previewNoteNoCount':
              'Die echte Karte lädt erst auf der Kartenseite.',
          },
        }),
        LandingMarkersResource,
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LandingMapPreviewComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const pins = (kind: string) => host.querySelectorAll(`[data-pin="${kind}"]`).length;
  const note = () =>
    host.querySelector('[data-testid="public.landing.near.previewNote"]')!.textContent!.trim();

  it('draws the canvas at once, without a loading state and without pins', () => {
    const preview = host.querySelector('[data-testid="public.landing.near.preview"]')!;
    expect(preview.querySelector('[role="img"]')!.getAttribute('aria-label')).toBe(
      'public.landing.near.previewAlt',
    );
    expect(preview.querySelector('svg path')).not.toBeNull();
    expect(host.querySelector('fs-data-state, fs-skeleton, [aria-busy="true"]')).toBeNull();
    expect(pins('region') + pins('fsp')).toBe(0);
    expect(note()).toBe('Die echte Karte lädt erst auf der Kartenseite.');
    expect(
      host
        .querySelector('[data-testid="public.landing.near.preview.openMap"]')!
        .getAttribute('href'),
    ).toBe('/map');
    http.expectOne('/api/public/map/markers/regions').flush([]);
    http.expectOne('/api/public/map/markers/food-share-points').flush([]);
  });

  it('draws the markers inside the frame and counts all of them', async () => {
    http.expectOne('/api/public/map/markers/regions').flush(REGIONS);
    http.expectOne('/api/public/map/markers/food-share-points').flush(FSPS);
    await fixture.whenStable();
    expect(pins('region')).toBe(2);
    expect(pins('fsp')).toBe(1);
    expect(host.querySelector('.pin--fsp .pi-box')).not.toBeNull();
    expect(note()).toBe('3 Bezirke · 1 Fairteiler.');
  });

  it('leaves out the pins of a failing list silently', async () => {
    http.expectOne('/api/public/map/markers/regions').flush(REGIONS);
    http
      .expectOne('/api/public/map/markers/food-share-points')
      .flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    expect(pins('region')).toBe(2);
    expect(pins('fsp')).toBe(0);
    expect(host.querySelector('fs-error-state, [role="alert"]')).toBeNull();
    expect(note()).toBe('Die echte Karte lädt erst auf der Kartenseite.');
  });

  it('shows no pins at all when both lists fail', async () => {
    http
      .expectOne('/api/public/map/markers/regions')
      .flush({}, { status: 500, statusText: 'Error' });
    http
      .expectOne('/api/public/map/markers/food-share-points')
      .flush({}, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    expect(pins('region') + pins('fsp')).toBe(0);
    expect(host.querySelector('fs-error-state, [role="alert"]')).toBeNull();
  });
});
