import { computed, Signal } from '@angular/core';

import {
  MapBubble,
  MapMarker as ApiMapMarker,
  MapMarkerKind,
  StoreMarker,
  StoreMarkerHelp,
  StoreMarkerScope,
  StoreMarkerStatus,
  UnitMembership,
  UnitType,
  UserMarkerActivity,
  UserMarkerMember,
  UserMarkerRole,
} from '../../api';
import { DataState, DataStatus } from '../../core/data/data-state';
import { readStorage, writeStorage } from '../../core/storage';
import { MapMarker, MapMarkerKind as ViewKind } from '../../shared/map-view/map-view.component';

/** Layers of /map: the first three are public, the others need a session (users also MAP_USERS + region admin). */
export type MapLayer = 'fsps' | 'regions' | 'baskets' | 'stores' | 'events' | 'users';
export const PUBLIC_LAYERS: readonly MapLayer[] = ['fsps', 'regions', 'baskets'];
export const MEMBER_LAYERS: readonly MapLayer[] = ['stores', 'events', 'users'];
export const ALL_LAYERS: readonly MapLayer[] = [...PUBLIC_LAYERS, ...MEMBER_LAYERS];
/** Shown on the first visit (nothing stored yet). */
export const DEFAULT_LAYERS: readonly MapLayer[] = ['fsps', 'regions', 'baskets', 'stores'];
export const LAYERS_STORAGE_KEY = 'fs.map.layers';

/** The remembered layers (`localStorage['fs.map.layers']`, a JSON array); unknown entries are dropped. */
export function readLayers(): MapLayer[] {
  const raw = readStorage(LAYERS_STORAGE_KEY);
  if (raw === null) return [...DEFAULT_LAYERS];
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [...DEFAULT_LAYERS];
    return ALL_LAYERS.filter((layer) => parsed.includes(layer));
  } catch {
    return [...DEFAULT_LAYERS];
  }
}

export function writeLayers(layers: readonly MapLayer[]): void {
  writeStorage(LAYERS_STORAGE_KEY, JSON.stringify(ALL_LAYERS.filter((layer) => layers.includes(layer))));
}

export function toggleLayer(layers: readonly MapLayer[], layer: MapLayer): MapLayer[] {
  return layers.includes(layer) ? layers.filter((entry) => entry !== layer) : ALL_LAYERS.filter((entry) => entry === layer || layers.includes(entry));
}

const VIEW_KIND: Record<MapMarkerKind, ViewKind> = {
  [MapMarkerKind.Store]: 'store',
  [MapMarkerKind.Fsp]: 'fsp',
  [MapMarkerKind.Basket]: 'basket',
  [MapMarkerKind.Region]: 'region',
  [MapMarkerKind.RegionPin]: 'region-pin',
  [MapMarkerKind.Event]: 'event',
  [MapMarkerKind.User]: 'user',
};
const API_KIND = Object.fromEntries(Object.entries(VIEW_KIND).map(([api, view]) => [view, api])) as Record<ViewKind, MapMarkerKind | undefined>;

/** Map pins of API markers; `label` names the kind (the API sends no names except for stores). */
export function toViewMarkers(markers: readonly ApiMapMarker[], label: (kind: MapMarkerKind) => string): MapMarker[] {
  return markers.map((marker) => ({ id: marker.id, kind: VIEW_KIND[marker.kind], lat: marker.lat, lon: marker.lon, label: label(marker.kind) }));
}

export function storeViewMarkers(stores: readonly StoreMarker[]): MapMarker[] {
  return stores.map((store) => ({ id: store.id, kind: 'store', lat: store.lat, lon: store.lon, label: store.name }));
}

/** The API kind of a pin (for the bubble request, lower case as in `/bubbles/{kind}/{id}`); none for own pins. */
export function bubbleKind(kind: ViewKind): MapMarkerKind | null {
  return API_KIND[kind] ?? null;
}

/** Kinds an anonymous visitor may open (server `BubbleService.PUBLIC`). */
export const PUBLIC_BUBBLE_KINDS: readonly MapMarkerKind[] = [MapMarkerKind.Fsp, MapMarkerKind.Basket, MapMarkerKind.Region, MapMarkerKind.RegionPin];

/**
 * A piece of a bubble: an i18n key with params (`date`: an ISO instant formatted into the `date` param), a date, or
 * plain text from the server.
 */
export type BubbleText = { key: string; params?: Record<string, unknown>; date?: string } | { date: string } | { text: string };

export interface BubbleView {
  title: string;
  subtitle: BubbleText | null;
  lines: BubbleText[];
  imageUrl?: string;
  link: string;
}

const text = (value: string | undefined | null): BubbleText | null => (value ? { text: value } : null);

/**
 * How a bubble reads (server `BubbleService`): the lines are data, the client adds the words. Store: subtitle =
 * cooperation status, lines = address + team status (last); FSP: address + follower count (last); basket: subtitle =
 * owner, line = expiry; region: subtitle = type, line = member count; event: line 1 = start, then the address; user:
 * subtitle = role.
 */
export function bubbleView(kind: MapMarkerKind, bubble: MapBubble): BubbleView {
  const lines = [...bubble.lines];
  const base = { title: bubble.title, imageUrl: bubble.imageUrl, link: bubble.link };
  switch (kind) {
    case MapMarkerKind.Store: {
      const team = lines.pop();
      return {
        ...base,
        subtitle: bubble.subtitle ? { key: `map.cooperation.${bubble.subtitle}` } : null,
        lines: [...lines.map((line) => ({ text: line })), ...(team ? [{ key: `map.team.${team}` }] : [])],
      };
    }
    case MapMarkerKind.Fsp: {
      const followers = lines.pop();
      return {
        ...base,
        subtitle: text(bubble.subtitle),
        lines: [...lines.map((line) => ({ text: line })), ...(followers ? [{ key: 'map.bubble.followers', params: { count: Number(followers) } }] : [])],
      };
    }
    case MapMarkerKind.Basket:
      return {
        ...base,
        subtitle: bubble.subtitle ? { key: 'map.bubble.offeredBy', params: { name: bubble.subtitle } } : null,
        lines: lines.slice(0, 1).map((until) => ({ key: 'map.bubble.until', date: until })),
      };
    case MapMarkerKind.Region:
    case MapMarkerKind.RegionPin:
      return {
        ...base,
        subtitle: bubble.subtitle ? { key: `map.regionType.${bubble.subtitle}` } : null,
        lines: lines.slice(0, 1).map((count) => ({ key: 'map.bubble.members', params: { count: Number(count) } })),
      };
    case MapMarkerKind.Event: {
      const [start, ...address] = lines;
      return {
        ...base,
        subtitle: text(bubble.subtitle),
        lines: [...(start ? [{ date: start }] : []), ...address.map((line) => ({ text: line }))],
      };
    }
    case MapMarkerKind.User:
      return { ...base, subtitle: bubble.subtitle ? { key: `common.role.${bubble.subtitle}` } : null, lines: [] };
  }
}

/** Store filter values of the filter bar ('' = all / any category). */
export interface StoreFilterValues {
  status: StoreMarkerStatus;
  help: StoreMarkerHelp;
  scope: StoreMarkerScope;
  categoryId: string;
}
export const DEFAULT_STORE_FILTER: StoreFilterValues = {
  status: StoreMarkerStatus.All,
  help: StoreMarkerHelp.All,
  scope: StoreMarkerScope.All,
  categoryId: '',
};

/** Parameters of `listStoreMarkers` (ALL and no category are left out). */
export function storeQuery(values: StoreFilterValues): { status?: StoreMarkerStatus; help?: StoreMarkerHelp; scope?: StoreMarkerScope; categoryId?: string } {
  return {
    ...(values.status !== StoreMarkerStatus.All ? { status: values.status } : {}),
    ...(values.help !== StoreMarkerHelp.All ? { help: values.help } : {}),
    ...(values.scope !== StoreMarkerScope.All ? { scope: values.scope } : {}),
    ...(values.categoryId ? { categoryId: values.categoryId } : {}),
  };
}

export interface UserFilterValues {
  regionId: string;
  role: UserMarkerRole;
  activity: UserMarkerActivity;
  member: UserMarkerMember;
}

/** Regions whose members the user may see on the map: administered regions (not working groups). */
export function adminRegions(units: readonly UnitMembership[] | null | undefined): UnitMembership[] {
  return (units ?? []).filter((unit) => unit.admin && unit.type !== UnitType.WorkingGroup);
}

const SEVERITY: DataStatus[] = ['offline', 'error', 'forbidden', 'notFound', 'loading', 'refreshing', 'success', 'empty', 'idle'];

/**
 * One DataState for several layers (the map shows one status line): the most severe status wins (offline > error >
 * … > loading > refreshing > success); `reload()` reloads the layers that failed (all when none failed), `refresh()`
 * refreshes the layers that show data.
 */
export function combineStates(states: Signal<readonly DataState<unknown>[]>): DataState<true> {
  const status = computed<DataStatus>(() => {
    const all = states().map((state) => state.status());
    if (all.length === 0) return 'success';
    const worst = SEVERITY.find((candidate) => all.includes(candidate)) ?? 'success';
    // layers without data are fine: an empty layer is not an empty map
    return worst === 'empty' || worst === 'idle' ? 'success' : worst;
  });
  const failed = (state: DataState<unknown>) => ['offline', 'error', 'forbidden', 'notFound'].includes(state.status());
  return {
    status,
    data: computed(() => (status() === 'success' || status() === 'refreshing' ? true : undefined)),
    error: computed(() => states().find(failed)?.error()),
    isStale: computed(() => states().some((state) => state.isStale())),
    isLoading: computed(() => status() === 'loading' || status() === 'refreshing'),
    reload: () => {
      const broken = states().filter(failed);
      (broken.length > 0 ? broken : states()).forEach((state) => state.reload());
    },
    refresh: () => states().forEach((state) => state.refresh()),
    set: () => undefined,
    update: () => undefined,
  };
}
