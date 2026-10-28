import { CooperationStatus, StoreAddress, TeamSearchStatus } from '../../api';

/** Default filter of the region store list (S1): stores that are or may become active. */
export const DEFAULT_COOPERATION_FILTER: readonly CooperationStatus[] = [
  CooperationStatus.Unclear,
  CooperationStatus.NoContact,
  CooperationStatus.InNegotiation,
  CooperationStatus.CooperationEstablished,
];

export const COOPERATION_STATUSES = Object.values(CooperationStatus);
export const TEAM_STATUSES = Object.values(TeamSearchStatus);

/** Cooperation statuses of stores that no longer take part (shown as "inactive" on /stores/mine). */
export const INACTIVE_COOPERATION: readonly CooperationStatus[] = [
  CooperationStatus.DoesNotWantToWorkWithUs,
  CooperationStatus.GivesToOtherCharity,
  CooperationStatus.PermanentlyClosed,
];

export interface MapLinks {
  google: string;
  apple: string;
}

/** Navigation deep links (S15): coordinates when known, otherwise the address. */
export function mapLinks(store: { latitude?: number; longitude?: number; address: StoreAddress; name: string }): MapLinks | null {
  const { latitude: lat, longitude: lon, address } = store;
  if (lat != null && lon != null) {
    return {
      google: `https://www.google.com/maps/search/?api=1&query=${lat},${lon}`,
      apple: `https://maps.apple.com/?ll=${lat},${lon}&q=${encodeURIComponent(store.name)}`,
    };
  }
  const query = [address.street, address.postcode, address.city].filter(Boolean).join(' ');
  if (!query) return null;
  return {
    google: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`,
    apple: `https://maps.apple.com/?q=${encodeURIComponent(query)}`,
  };
}
