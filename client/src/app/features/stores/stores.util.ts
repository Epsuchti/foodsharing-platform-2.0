import {
  CooperationStatus,
  RegionDetail,
  SessionInfo,
  StoreAddress,
  StoresImportPreview,
  TeamSearchStatus,
  UnitType,
  WorkingGroupFunction,
} from '../../api';
import { isOrga } from '../../core/auth/permission.util';

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

export type RegionCoordinationInfo = Pick<RegionDetail, 'id' | 'parentId' | 'functions' | 'membership'>;

/**
 * UI mirror of the server's `mayCoordinateRegionStores` (S2 import): ORGA; else, when the region has a
 * STORES_COORDINATION group, only admins of that group; else ambassadors of the region or its parent. The server
 * also counts ambassadors of higher ancestors and re-checks every call.
 */
export function mayCoordinateRegionStores(
  session: SessionInfo | null | undefined,
  region: RegionCoordinationInfo,
): boolean {
  if (isOrga(session)) {
    return true;
  }
  const units = session?.units ?? [];
  const coordination = region.functions.find((f) => f.function === WorkingGroupFunction.StoresCoordination);
  if (coordination) {
    return units.some((unit) => unit.admin && unit.id === coordination.group.id);
  }
  const parentId = region.parentId;
  return (
    region.membership.admin ||
    (!!parentId && units.some((unit) => unit.admin && unit.type !== UnitType.WorkingGroup && unit.id === parentId))
  );
}

/** Cheap pre-check without the region: only ORGA and admins of some unit can coordinate stores anywhere. */
export function mightCoordinateStores(session: SessionInfo | null | undefined): boolean {
  return isOrga(session) || (session?.units ?? []).some((unit) => unit.admin);
}

/** The stores import may be confirmed after a dry run of the unchanged text that would write something. */
export function canConfirmStoresImport(
  preview: StoresImportPreview | null,
  previewedText: string | null,
  text: string,
): boolean {
  return !!preview && preview.dryRun && previewedText === text && preview.created + preview.updated > 0;
}
