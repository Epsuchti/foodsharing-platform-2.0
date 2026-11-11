import { distanceKm, isPostcode, nearestFsps } from './landing-geo';

const ZURICH_HB = { lat: 47.3779, lon: 8.5403 };

describe('landing geo (C17)', () => {
  it('measures great-circle distances in km', () => {
    expect(distanceKm(ZURICH_HB, ZURICH_HB)).toBe(0);
    // Zürich HB → Bern Bahnhof is about 95 km as the crow flies
    expect(distanceKm(ZURICH_HB, { lat: 46.949, lon: 7.4391 })).toBeGreaterThan(90);
    expect(distanceKm(ZURICH_HB, { lat: 46.949, lon: 7.4391 })).toBeLessThan(100);
    // symmetric
    const a = { lat: 52.52, lon: 13.405 };
    expect(distanceKm(a, ZURICH_HB)).toBeCloseTo(distanceKm(ZURICH_HB, a), 6);
  });

  it('picks the two nearest Fairteiler within 10 km, nearest first', () => {
    const markers = [
      { id: 'oerlikon', lat: 47.4111, lon: 8.5446 }, // ~3.7 km
      { id: 'winterthur', lat: 47.4999, lon: 8.7262 }, // ~19 km
      { id: 'langstrasse', lat: 47.3786, lon: 8.5292 }, // ~0.8 km
      { id: 'wiedikon', lat: 47.3695, lon: 8.5204 }, // ~1.8 km
    ];
    expect(nearestFsps(ZURICH_HB, markers).map((m) => m.id)).toEqual(['langstrasse', 'wiedikon']);
    expect(nearestFsps(ZURICH_HB, markers, 5).map((m) => m.id)).toEqual([
      'langstrasse',
      'wiedikon',
      'oerlikon',
    ]);
    expect(nearestFsps(ZURICH_HB, [markers[1]])).toEqual([]);
    expect(nearestFsps(ZURICH_HB, [])).toEqual([]);
  });

  it('accepts 4- and 5-digit postcodes only', () => {
    expect(isPostcode('8004')).toBe(true);
    expect(isPostcode('10115')).toBe(true);
    expect(isPostcode(' 1160 ')).toBe(true);
    expect(isPostcode('800')).toBe(false);
    expect(isPostcode('801234')).toBe(false);
    expect(isPostcode('Zürich')).toBe(false);
    expect(isPostcode('80 04')).toBe(false);
    expect(isPostcode('')).toBe(false);
    expect(isPostcode(undefined)).toBe(false);
  });
});
