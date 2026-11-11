/**
 * Geometry of the landing page (C17, decisions 160 and 176): distances for "Fairteiler in der Nähe" and the
 * postcode rule of the search. Pure functions, no Angular.
 */

export interface LatLon {
  readonly lat: number;
  readonly lon: number;
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
