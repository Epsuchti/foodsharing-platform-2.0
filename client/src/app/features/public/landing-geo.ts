/**
 * Geometry of the landing page (C17, decision 160): distances for "Fairteiler in der Nähe", the projection of
 * marker positions onto the stylised DACH frame of the static preview, and the postcode rule of the search.
 * Pure functions, no Angular.
 */

export interface LatLon {
  readonly lat: number;
  readonly lon: number;
}

export interface FramePoint {
  readonly x: number;
  readonly y: number;
}

/** The SVG view box of the preview (mockup `OVERVIEW`). */
export const FRAME = { x: 170, y: 20, w: 410, h: 420 } as const;

/** Land shapes of the preview in frame units (mockup `LAND`): Germany, Austria, Switzerland. */
export const LAND_SHAPES: Readonly<Record<'de' | 'at' | 'ch', readonly FramePoint[]>> = {
  de: points(
    '250 40 300 28 340 34 380 58 402 108 440 148 432 222 470 282 452 318 430 332 380 342 330 334 300 352 252 330 230 282 198 250 212 182 190 122 232 80',
  ),
  at: points(
    '330 350 380 346 430 334 482 328 542 344 566 368 526 398 462 404 400 398 358 390 326 378',
  ),
  ch: points('232 344 282 350 330 354 326 384 300 410 262 422 220 408 198 382 206 360'),
};

function points(list: string): FramePoint[] {
  const n = list.split(' ').map(Number);
  const out: FramePoint[] = [];
  for (let i = 0; i < n.length; i += 2) {
    out.push({ x: n[i], y: n[i + 1] });
  }
  return out;
}

/** `M x y L x y … Z` of a shape. */
export function shapePath(shape: readonly FramePoint[]): string {
  return 'M' + shape.map((p) => `${p.x} ${p.y}`).join(' L') + 'Z';
}

/**
 * Linear fit of lon → x and lat → y through the mockup positions of Zürich, Berlin, Wien, Hamburg and Genf
 * (least squares; a few frame units off at the edges, which is fine for a schematic).
 */
const X_PER_LON = 30.9121;
const X_AT_LON0 = 18.9216;
const Y_PER_LAT = -48.6326;
const Y_AT_LAT0 = 2672.8193;

/** Position in the preview frame, or `null` outside it. */
export function projectToFrame(lat: number, lon: number): FramePoint | null {
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
    return null;
  }
  const x = X_PER_LON * lon + X_AT_LON0;
  const y = Y_PER_LAT * lat + Y_AT_LAT0;
  if (x < FRAME.x || x > FRAME.x + FRAME.w || y < FRAME.y || y > FRAME.y + FRAME.h) {
    return null;
  }
  return { x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 };
}

/** Position in percent of the frame (for absolutely positioned pins). */
export function framePercent(point: FramePoint): { left: number; top: number } {
  return {
    left: ((point.x - FRAME.x) / FRAME.w) * 100,
    top: ((point.y - FRAME.y) / FRAME.h) * 100,
  };
}

/** Ray-casting point-in-polygon test (frame units). */
export function insideShape(point: FramePoint, shape: readonly FramePoint[]): boolean {
  let inside = false;
  for (let i = 0, j = shape.length - 1; i < shape.length; j = i++) {
    const a = shape[i];
    const b = shape[j];
    if (
      a.y > point.y !== b.y > point.y &&
      point.x < ((b.x - a.x) * (point.y - a.y)) / (b.y - a.y) + a.x
    ) {
      inside = !inside;
    }
  }
  return inside;
}

const EARTH_RADIUS_KM = 6371;
const rad = (deg: number) => (deg * Math.PI) / 180;

/** Great-circle distance (haversine) in km. */
export function distanceKm(a: LatLon, b: LatLon): number {
  const dLat = rad(b.lat - a.lat);
  const dLon = rad(b.lon - a.lon);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** The `max` markers closest to `pin` within `km`, nearest first. */
export function nearestFsps<T extends LatLon>(
  pin: LatLon,
  markers: readonly T[],
  max = 2,
  km = 10,
): T[] {
  return markers
    .map((marker) => ({ marker, distance: distanceKm(pin, marker) }))
    .filter((entry) => entry.distance <= km)
    .sort((a, b) => a.distance - b.distance)
    .slice(0, max)
    .map((entry) => entry.marker);
}

/** Swiss and Austrian postcodes have 4 digits, German ones 5. */
export function isPostcode(value: string | null | undefined): boolean {
  return /^\d{4,5}$/.test((value ?? '').trim());
}
