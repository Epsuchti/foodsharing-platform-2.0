import type { MapFocus } from './map-view.component';

/**
 * Deep link of a map view (M1, decision 179): `/map?lat=<deg>&lon=<deg>&z=<int>`, written with 4 decimals
 * (about 11 m). Shared by the landing map ("Karte öffnen") and `/map`, so neither feature imports the other.
 * Pure functions, no Angular.
 */

/** Query params of a map view, as `routerLink` / `router.navigate` take them. */
export interface MapViewQuery {
  readonly lat: string;
  readonly lon: string;
  readonly z: number;
}

/** What `parseMapView` reads: Angular's `ParamMap` (its `get` returns the first of duplicated values). */
export interface MapViewParams {
  get(name: string): string | null;
}

export const MAP_VIEW_MIN_ZOOM = 3;
export const MAP_VIEW_MAX_ZOOM = 19;
/** Zoom of a link with a valid position but no (or no numeric) `z`. */
export const MAP_VIEW_DEFAULT_ZOOM = 12;

/** A plain decimal (no exponent, no `Infinity`, no hex). */
const DECIMAL = /^[+-]?(\d+(\.\d*)?|\.\d+)$/;

function decimal(value: string | null | undefined): number | null {
  const text = value?.trim() ?? '';
  if (!DECIMAL.test(text)) {
    return null;
  }
  const number = Number(text);
  return Number.isFinite(number) ? number : null;
}

function clampZoom(zoom: number): number {
  return Math.min(MAP_VIEW_MAX_ZOOM, Math.max(MAP_VIEW_MIN_ZOOM, Math.round(zoom)));
}

/**
 * The view of a deep link, or `null` when `lat`/`lon` are missing, not plain decimals or out of range (the whole
 * link is then ignored). `z` is rounded and clamped to [3, 19]; missing or not a number → 12. Never throws.
 */
export function parseMapView(params: MapViewParams): MapFocus | null {
  const lat = decimal(params.get('lat'));
  const lon = decimal(params.get('lon'));
  if (lat === null || lon === null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
    return null;
  }
  const z = decimal(params.get('z'));
  return { lat, lon, zoom: z === null ? MAP_VIEW_DEFAULT_ZOOM : clampZoom(z) };
}

/** The query params of a view: 4 decimals, zoom rounded and clamped like `parseMapView`. */
export function mapViewQuery(view: MapFocus): MapViewQuery {
  return {
    lat: view.lat.toFixed(4),
    lon: view.lon.toFixed(4),
    z: clampZoom(view.zoom ?? MAP_VIEW_DEFAULT_ZOOM),
  };
}
