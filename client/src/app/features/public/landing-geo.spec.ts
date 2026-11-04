import {
  FRAME,
  LAND_SHAPES,
  distanceKm,
  framePercent,
  insideShape,
  isPostcode,
  nearestFsps,
  projectToFrame,
  shapePath,
} from './landing-geo';

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

  it('projects Zürich into the Swiss shape; other DACH cities stay inside the frame', () => {
    const zurich = projectToFrame(47.3769, 8.5417)!;
    expect(zurich).not.toBeNull();
    expect(insideShape(zurich, LAND_SHAPES.ch)).toBe(true);
    expect(insideShape(zurich, LAND_SHAPES.de)).toBe(false);
    // the schematic shapes are rough; the big cities only have to land in the frame, near the mockup positions
    const berlin = projectToFrame(52.52, 13.405)!;
    expect(Math.abs(berlin.x - 420)).toBeLessThan(15);
    expect(Math.abs(berlin.y - 118)).toBeLessThan(15);
    const vienna = projectToFrame(48.2082, 16.3738)!;
    expect(Math.abs(vienna.x - 532)).toBeLessThan(30);
    expect(Math.abs(vienna.y - 354)).toBeLessThan(30);
  });

  it('returns null for points outside the frame', () => {
    expect(projectToFrame(41.9, 12.5)).toBeNull(); // Rome
    expect(projectToFrame(59.33, 18.07)).toBeNull(); // Stockholm
    expect(projectToFrame(48.86, 2.35)).toBeNull(); // Paris
    expect(projectToFrame(Number.NaN, 8)).toBeNull();
  });

  it('turns frame points into percentages and shapes into paths', () => {
    expect(framePercent({ x: FRAME.x, y: FRAME.y })).toEqual({ left: 0, top: 0 });
    expect(framePercent({ x: FRAME.x + FRAME.w, y: FRAME.y + FRAME.h })).toEqual({
      left: 100,
      top: 100,
    });
    expect(
      shapePath([
        { x: 1, y: 2 },
        { x: 3, y: 4 },
      ]),
    ).toBe('M1 2 L3 4Z');
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
