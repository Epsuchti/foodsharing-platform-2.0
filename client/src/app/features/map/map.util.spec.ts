import { signal } from '@angular/core';

import { MapMarkerKind, StoreMarkerHelp, StoreMarkerScope, StoreMarkerStatus, UnitType } from '../../api';
import { DataState, DataStatus } from '../../core/data/data-state';
import {
  DEFAULT_LAYERS,
  DEFAULT_STORE_FILTER,
  LAYERS_STORAGE_KEY,
  adminRegions,
  bubbleKind,
  bubbleView,
  combineStates,
  readLayers,
  storeQuery,
  toViewMarkers,
  toggleLayer,
  writeLayers,
} from './map.util';

function fakeState(status: DataStatus): DataState<unknown> & { reloads: number } {
  const state = {
    reloads: 0,
    status: signal(status),
    data: signal(undefined),
    error: signal(status === 'error' ? { status: 500, code: 'UNEXPECTED_ERROR', message: 'x', retryable: true } : undefined),
    isStale: signal(false),
    isLoading: signal(status === 'loading'),
    reload: () => {
      state.reloads++;
    },
    refresh: () => undefined,
    set: () => undefined,
    update: () => undefined,
  };
  return state as unknown as DataState<unknown> & { reloads: number };
}

describe('map util', () => {
  afterEach(() => localStorage.removeItem(LAYERS_STORAGE_KEY));

  it('remembers the layers in localStorage and falls back to the defaults', () => {
    expect(readLayers()).toEqual([...DEFAULT_LAYERS]);
    writeLayers(['events', 'fsps']);
    expect(localStorage.getItem(LAYERS_STORAGE_KEY)).toBe('["fsps","events"]');
    expect(readLayers()).toEqual(['fsps', 'events']);
    localStorage.setItem(LAYERS_STORAGE_KEY, '["users","nonsense"]');
    expect(readLayers()).toEqual(['users']);
    localStorage.setItem(LAYERS_STORAGE_KEY, '{broken');
    expect(readLayers()).toEqual([...DEFAULT_LAYERS]);
    localStorage.setItem(LAYERS_STORAGE_KEY, '[]');
    expect(readLayers()).toEqual([]);
  });

  it('toggles a layer keeping the canonical order', () => {
    expect(toggleLayer(['stores'], 'fsps')).toEqual(['fsps', 'stores']);
    expect(toggleLayer(['fsps', 'stores'], 'fsps')).toEqual(['stores']);
  });

  it('maps API markers to pins and back to bubble kinds', () => {
    const pins = toViewMarkers(
      [
        { id: 'a', kind: MapMarkerKind.RegionPin, lat: 47.1, lon: 8.1 },
        { id: 'b', kind: MapMarkerKind.Fsp, lat: 47.2, lon: 8.2 },
      ],
      (kind) => `label ${kind}`,
    );
    expect(pins).toEqual([
      { id: 'a', kind: 'region-pin', lat: 47.1, lon: 8.1, label: 'label REGION_PIN' },
      { id: 'b', kind: 'fsp', lat: 47.2, lon: 8.2, label: 'label FSP' },
    ]);
    expect(bubbleKind('region-pin')).toBe(MapMarkerKind.RegionPin);
    expect(bubbleKind('user')).toBe(MapMarkerKind.User);
    expect(bubbleKind('home')).toBeNull();
    expect(bubbleKind('pickup')).toBeNull();
  });

  it('turns bubble data lines into labelled lines per kind', () => {
    const store = bubbleView(MapMarkerKind.Store, {
      title: 'Bäckerei',
      subtitle: 'COOPERATION_ESTABLISHED',
      link: '/stores/s',
      lines: ['Langstrasse 1', '8004 Zürich', 'OPEN_SEARCHING'],
    });
    expect(store.subtitle).toEqual({ key: 'map.cooperation.COOPERATION_ESTABLISHED' });
    expect(store.lines).toEqual([{ text: 'Langstrasse 1' }, { text: '8004 Zürich' }, { key: 'map.team.OPEN_SEARCHING' }]);

    const fsp = bubbleView(MapMarkerKind.Fsp, { title: 'Fairteiler', subtitle: 'Zürich', link: '/f', lines: ['8003 Zürich', '3'] });
    expect(fsp.subtitle).toEqual({ text: 'Zürich' });
    expect(fsp.lines).toEqual([{ text: '8003 Zürich' }, { key: 'map.bubble.followers', params: { count: 3 } }]);

    const basket = bubbleView(MapMarkerKind.Basket, { title: 'Brot', subtitle: 'Sarah', link: '/b', lines: ['2026-11-09T10:00:00Z'] });
    expect(basket.subtitle).toEqual({ key: 'map.bubble.offeredBy', params: { name: 'Sarah' } });
    expect(basket.lines).toEqual([{ key: 'map.bubble.until', date: '2026-11-09T10:00:00Z' }]);

    const region = bubbleView(MapMarkerKind.RegionPin, { title: 'Zürich', subtitle: 'BIG_CITY', link: '/regions/r', lines: ['42'] });
    expect(region.subtitle).toEqual({ key: 'map.regionType.BIG_CITY' });
    expect(region.lines).toEqual([{ key: 'map.bubble.members', params: { count: 42 } }]);

    const event = bubbleView(MapMarkerKind.Event, { title: 'Stammtisch', link: '/events/e', lines: ['2026-11-05T18:00:00Z', 'Bahnhofplatz 1'] });
    expect(event.subtitle).toBeNull();
    expect(event.lines).toEqual([{ date: '2026-11-05T18:00:00Z' }, { text: 'Bahnhofplatz 1' }]);

    const user = bubbleView(MapMarkerKind.User, { title: 'Max', subtitle: 'FOODSAVER', link: '/profile/u', lines: [] });
    expect(user.subtitle).toEqual({ key: 'common.role.FOODSAVER' });
    expect(user.link).toBe('/profile/u');
  });

  it('leaves ALL filters out of the store query', () => {
    expect(storeQuery(DEFAULT_STORE_FILTER)).toEqual({});
    expect(
      storeQuery({ status: StoreMarkerStatus.Cooperating, help: StoreMarkerHelp.Searching, scope: StoreMarkerScope.Member, categoryId: 'c1' }),
    ).toEqual({ status: StoreMarkerStatus.Cooperating, help: StoreMarkerHelp.Searching, scope: StoreMarkerScope.Member, categoryId: 'c1' });
  });

  it('offers the user layer for administered regions only', () => {
    const unit = (id: string, type: UnitType, admin: boolean) => ({ id, name: id, slug: id, type, member: true, admin, homeRegion: false });
    expect(
      adminRegions([unit('zh', UnitType.BigCity, true), unit('wg', UnitType.WorkingGroup, true), unit('wi', UnitType.City, false)]).map((u) => u.id),
    ).toEqual(['zh']);
    expect(adminRegions(undefined)).toEqual([]);
  });

  it('combines layer states: the worst status wins, reload retries the failed layers', () => {
    const ok = fakeState('success');
    const empty = fakeState('empty');
    const failed = fakeState('error');
    const states = signal<DataState<unknown>[]>([ok, empty]);
    const combined = combineStates(states);
    expect(combined.status()).toBe('success');
    expect(combined.data()).toBe(true);

    states.set([ok, fakeState('loading')]);
    expect(combined.status()).toBe('loading');
    expect(combined.isLoading()).toBe(true);

    states.set([ok, failed]);
    expect(combined.status()).toBe('error');
    expect(combined.error()?.status).toBe(500);
    combined.reload();
    expect(failed.reloads).toBe(1);
    expect(ok.reloads).toBe(0);

    states.set([]);
    expect(combined.status()).toBe('success');
  });
});
