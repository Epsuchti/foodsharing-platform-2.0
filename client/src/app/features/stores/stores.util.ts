import {
  CooperationStatus,
  RegionDetail,
  SessionInfo,
  StoresImportPreview,
  TeamSearchStatus,
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

/**
 * Whether the stores CSV import (S2) is offered: ORGA, or the server's `RegionDetail.mayCoordinateStores` (the
 * store-coordination rule lives only on the server; it re-checks every call).
 */
export function mayImportStores(
  session: SessionInfo | null | undefined,
  region: Pick<RegionDetail, 'mayCoordinateStores'> | null | undefined,
): boolean {
  return isOrga(session) || region?.mayCoordinateStores === true;
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
