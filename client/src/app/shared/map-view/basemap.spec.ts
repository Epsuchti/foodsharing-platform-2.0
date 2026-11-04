import { BASEMAP_URL, OSM_ATTRIBUTION, basemapFlavor, basemapLang } from './basemap';

/** MT1: flavor and label language of the self-hosted basemap. */
describe('basemap', () => {
  it('follows the theme', () => {
    expect(basemapFlavor(false)).toBe('light');
    expect(basemapFlavor(true)).toBe('dark');
  });

  it('labels Swiss German maps with the German names and every other language with its own', () => {
    expect(basemapLang('gws')).toBe('de');
    expect(basemapLang('de')).toBe('de');
    expect(basemapLang('fr')).toBe('fr');
    expect(basemapLang('it')).toBe('it');
    expect(basemapLang('en')).toBe('en');
  });

  it('is served from our own origin and credits OpenStreetMap', () => {
    expect(BASEMAP_URL).toBe('/tiles/basemap.pmtiles');
    expect(OSM_ATTRIBUTION).toContain('href="https://www.openstreetmap.org/copyright"');
    expect(OSM_ATTRIBUTION).toContain('OpenStreetMap contributors');
  });
});
