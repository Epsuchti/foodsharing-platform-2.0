import type { UiLanguage } from '../../core/i18n/languages';

/**
 * Self-hosted vector basemap (MT1, decision 158): one PMTiles file in the Protomaps v4 schema, served by our own
 * server with range requests and drawn by protomaps-leaflet. No request leaves our origin for the map.
 */
export const BASEMAP_URL = '/tiles/basemap.pmtiles';

/** ODbL credit on every map, in both modes (D7). Protomaps itself needs no credit. */
export const OSM_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>';

export type BasemapFlavor = 'light' | 'dark';

/** The protomaps-leaflet flavor for the effective theme. */
export function basemapFlavor(isDark: boolean): BasemapFlavor {
  return isDark ? 'dark' : 'light';
}

/** Label language: the UI language; Swiss German labels come from the German names (`name:de`). */
export function basemapLang(lang: UiLanguage | string): string {
  return lang === 'gws' ? 'de' : lang;
}
