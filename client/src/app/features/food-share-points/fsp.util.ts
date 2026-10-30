import { FspAddress, FspCreate, FspSummary, FspUpdate } from '../../api';
import { MapMarker } from '../../shared/map-view/map-view.component';

/** Server limits (`schemas/food-share-points.yaml`). */
export const NAME_MAX = 200;
export const STREET_MAX = 200;
export const POSTCODE_MAX = 20;
export const CITY_MAX = 200;
export const DESCRIPTION_MAX = 32600;
export const MANAGERS_MAX = 20;

export interface MapLinks {
  google: string;
  apple: string;
}

/** Navigation deep links (as `stores.util.ts` `mapLinks`): coordinates when known, otherwise the address. */
export function fspMapLinks(fsp: { lat?: number | null; lon?: number | null; address: FspAddress; name: string }): MapLinks | null {
  const { lat, lon, address } = fsp;
  if (lat != null && lon != null) {
    return {
      google: `https://www.google.com/maps/search/?api=1&query=${lat},${lon}`,
      apple: `https://maps.apple.com/?ll=${lat},${lon}&q=${encodeURIComponent(fsp.name)}`,
    };
  }
  const query = [address.street, address.postcode, address.city].filter(Boolean).join(' ');
  if (!query) return null;
  return {
    google: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`,
    apple: `https://maps.apple.com/?q=${encodeURIComponent(query)}`,
  };
}

/** Photo URL in another variant size (the server returns `…?size=md`; A41 sizes sm|md|lg). */
export function photoVariant(url: string | null | undefined, size: 'sm' | 'md' | 'lg'): string | undefined {
  if (!url) return undefined;
  if (!url.startsWith('/api/uploads/')) return url;
  return /[?&]size=/.test(url) ? url.replace(/([?&]size=)[a-z]+/, `$1${size}`) : `${url}${url.includes('?') ? '&' : '?'}size=${size}`;
}

/**
 * Map pins for a region's FSP list from the coordinates of `FspSummary` (inactive FSPs included), labelled with
 * their names; FSPs without a position get no pin.
 */
export function fspMarkers(fsps: readonly FspSummary[]): MapMarker[] {
  return fsps.flatMap((fsp) =>
    fsp.lat != null && fsp.lon != null ? [{ id: fsp.id, kind: 'fsp' as const, lat: fsp.lat, lon: fsp.lon, label: fsp.name }] : [],
  );
}

/** Values of the FSP form (see FspFormPageComponent). */
export interface FspFormValue {
  name: string;
  description: string;
  street: string;
  postcode: string;
  city: string;
  position: [number, number] | null;
  photoUploadId: string | null;
}

/**
 * `PATCH /api/food-share-points/{id}` body. Texts are trimmed; without a pin the server geocodes the address, so
 * lat/lon only travel together.
 */
export function fspUpdateBody(value: FspFormValue): FspUpdate {
  const description = value.description.trim();
  return {
    name: value.name.trim(),
    street: value.street.trim(),
    postcode: value.postcode.trim(),
    city: value.city.trim(),
    description,
    lat: value.position ? value.position[0] : undefined,
    lon: value.position ? value.position[1] : undefined,
    photoUploadId: value.photoUploadId ?? undefined,
  };
}

/** `POST /api/regions/{regionId}/food-share-points` body (the creator becomes the first manager on the server). */
export function fspCreateBody(value: FspFormValue): FspCreate {
  const body = fspUpdateBody(value);
  return {
    ...body,
    name: body.name!,
    street: body.street!,
    postcode: body.postcode!,
    city: body.city!,
    description: body.description || undefined,
  };
}

/**
 * Manager list edits (`setFoodSharePointManagers` replaces the whole list): adds a user once, at most
 * {@link MANAGERS_MAX}; removing keeps at least one manager (the server refuses an empty list).
 */
export function addManager(ids: readonly string[], userId: string): string[] {
  if (ids.includes(userId) || ids.length >= MANAGERS_MAX) return [...ids];
  return [...ids, userId];
}

export function removeManager(ids: readonly string[], userId: string): string[] {
  if (ids.length <= 1) return [...ids];
  return ids.filter((id) => id !== userId);
}
