import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import type * as Leaflet from 'leaflet';
import { afterEach, vi } from 'vitest';

import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { MapMarker, MapViewChange, MapViewComponent } from './map-view.component';

const STUB_TILES = '/stub/{z}/{x}/{y}.png';
const MARKERS: MapMarker[] = [
  { id: 'a', kind: 'fsp', lat: 47.37, lon: 8.51, label: 'Fairteiler' },
  { id: 'b', kind: 'region', lat: 47.5, lon: 8.72, label: 'Bezirk' },
];

/** `matchMedia` answering `true` for the given queries only. */
function mockMedia(...matching: string[]) {
  return vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        matches: matching.includes(query),
        media: query,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList,
  );
}

function leafletMap(fixture: ComponentFixture<MapViewComponent>): Leaflet.Map {
  return (fixture.componentInstance as unknown as { map: Leaflet.Map }).map;
}

async function create(inputs: Record<string, unknown> = {}): Promise<ComponentFixture<MapViewComponent>> {
  const fixture = TestBed.createComponent(MapViewComponent);
  fixture.componentRef.setInput('tileUrl', STUB_TILES);
  for (const [name, value] of Object.entries(inputs)) {
    fixture.componentRef.setInput(name, value);
  }
  await fixture.whenStable();
  await vi.waitFor(() => expect(leafletMap(fixture)).toBeTruthy());
  return fixture;
}

@Component({
  imports: [MapViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<fs-map-view tileUrl="${STUB_TILES}" testId="t.map"><a fsMapTilesAction href="/map">Open</a></fs-map-view>`,
})
class TilesActionHost {}

/** MapView opt-ins of decision 176 (C17 landing map, M1 deep link). */
describe('MapViewComponent options (decision 176)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
  });

  afterEach(() => vi.restoreAllMocks());

  it('cooperative: no one-finger dragging on touch, dragging with a mouse', async () => {
    mockMedia('(pointer: coarse)');
    const touch = await create({ cooperative: true });
    expect(leafletMap(touch).dragging.enabled()).toBe(false);
    expect(leafletMap(touch).touchZoom.enabled()).toBe(true);

    vi.restoreAllMocks();
    mockMedia();
    const mouse = await create({ cooperative: true });
    expect(leafletMap(mouse).dragging.enabled()).toBe(true);

    // without the input a touch map drags as before
    vi.restoreAllMocks();
    mockMedia('(pointer: coarse)');
    const plain = await create();
    expect(leafletMap(plain).dragging.enabled()).toBe(true);
  });

  it('cooperative: a one-finger move shows the two-finger hint for a moment', async () => {
    mockMedia('(pointer: coarse)');
    const fixture = await create({ cooperative: true, testId: 't.map' });
    const el = fixture.nativeElement as HTMLElement;
    const move = new Event('touchmove');
    Object.defineProperty(move, 'touches', { value: [{}] });
    el.querySelector('.map')!.dispatchEvent(move);
    await fixture.whenStable();
    const hint = el.querySelector('[data-testid="t.map.gestureHint"]');
    expect(hint?.textContent).toContain('shared.map.twoFingers');
    expect(hint?.getAttribute('aria-hidden')).toBe('true');
    // gone again after 1.5 s
    await vi.waitFor(
      async () => {
        await fixture.whenStable();
        expect(el.querySelector('[data-testid="t.map.gestureHint"]')).toBeNull();
      },
      { timeout: 3000, interval: 100 },
    );
  });

  it('markerTabStops=false: pins are no Tab stops and there is no hidden list', async () => {
    const fixture = await create({ markers: MARKERS, markerTabStops: false });
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(() => expect(el.querySelectorAll('.fs-map-marker')).toHaveLength(2));
    el.querySelectorAll('.fs-map-marker').forEach((pin) => expect(pin.hasAttribute('tabindex')).toBe(false));
    expect(el.querySelector('ul.fs-visually-hidden')).toBeNull();

    const tabbable = await create({ markers: MARKERS });
    const tel = tabbable.nativeElement as HTMLElement;
    await vi.waitFor(() => expect(tel.querySelectorAll('.fs-map-marker')).toHaveLength(2));
    expect(tel.querySelector('.fs-map-marker')!.getAttribute('tabindex')).toBe('0');
    expect(tel.querySelector('ul.fs-visually-hidden')).not.toBeNull();
  });

  it('reduced motion turns every animation off', async () => {
    mockMedia('(prefers-reduced-motion: reduce)');
    const fixture = await create();
    const map = leafletMap(fixture);
    expect(map.options.zoomAnimation).toBe(false);
    expect(map.options.fadeAnimation).toBe(false);
    expect(map.options.markerZoomAnimation).toBe(false);
    const setView = vi.spyOn(map, 'setView');
    fixture.componentRef.setInput('focus', { lat: 47.5, lon: 8.72, zoom: 11 });
    await fixture.whenStable();
    expect(setView).toHaveBeenCalledWith([47.5, 8.72], 11, expect.objectContaining({ animate: false }));

    vi.restoreAllMocks();
    mockMedia();
    const animated = await create();
    expect(leafletMap(animated).options.zoomAnimation).toBe(true);
  });

  it('emits viewChange after every move, byUser only for moves it did not make itself', async () => {
    const fixture = await create({ zoom: 8 });
    const changes: MapViewChange[] = [];
    fixture.componentInstance.viewChange.subscribe((change) => changes.push(change));

    fixture.componentRef.setInput('focus', { lat: 47.4996, lon: 8.7241, zoom: 13 });
    await fixture.whenStable();
    await vi.waitFor(() => expect(changes.length).toBeGreaterThan(0));
    expect(changes.at(-1)).toMatchObject({ zoom: 13, byUser: false });
    expect(changes.at(-1)!.lat).toBeCloseTo(47.4996, 4);
    expect(changes.at(-1)!.lon).toBeCloseTo(8.7241, 4);

    // a zoom like the +/− buttons or the keyboard do it
    leafletMap(fixture).setZoom(10, { animate: false });
    await vi.waitFor(() => expect(changes.at(-1)).toMatchObject({ zoom: 10, byUser: true }));
  });

  it('projects a tiles action into the tile error', async () => {
    const fixture = TestBed.createComponent(TilesActionHost);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const view = fixture.debugElement.children[0].componentInstance as MapViewComponent;
    await vi.waitFor(() => expect((view as unknown as { tiles: unknown }).tiles).toBeTruthy());
    (view as unknown as { tiles: { fire(event: string): void } }).tiles.fire('tileerror');
    await fixture.whenStable();
    const action = el.querySelector('[data-testid="t.map.tileError"] a[fsMapTilesAction]');
    expect(action?.textContent).toBe('Open');
    expect(action?.getAttribute('href')).toBe('/map');
  });
});
