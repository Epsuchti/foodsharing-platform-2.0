import {
  CooperationStatus,
  RegionDetail,
  SessionInfo,
  StoresImportPreview,
  TeamSearchStatus,
  WallMilestone,
  WallPost,
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

/** What a wall milestone card shows below its title: an i18n key, a date-only value, the raw text, or nothing. */
export type MilestoneBody = { key: string } | { date: string } | { text: string } | null;

const COOPERATION_VALUES = new Set<string>(COOPERATION_STATUSES);
const TEAM_VALUES = new Set<string>(TEAM_STATUSES);
const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Body of a wall milestone (S11). The server stores enum names and ISO dates as the post text; they are translated
 * here so no raw codes reach the wall, also for old rows: status → `shared.store.cooperation.*`, team status →
 * `shared.store.team.*`, `yyyy-mm-dd` → a date, "cooperation started" with COOPERATION_ESTABLISHED → nothing (the
 * title already says it).
 */
export function milestoneText(item: Pick<WallPost, 'milestone' | 'text'>): MilestoneBody {
  const text = item.text?.trim() ?? '';
  if (!text) {
    return null;
  }
  if (item.milestone === WallMilestone.None) {
    return { text };
  }
  if (item.milestone === WallMilestone.CooperationStarted && text === CooperationStatus.CooperationEstablished) {
    return null;
  }
  const statusMilestone =
    item.milestone === WallMilestone.StatusChanged || item.milestone === WallMilestone.CooperationStarted;
  if (statusMilestone && COOPERATION_VALUES.has(text)) {
    return { key: `shared.store.cooperation.${text}` };
  }
  if (item.milestone === WallMilestone.TeamStatusChanged && TEAM_VALUES.has(text)) {
    return { key: `shared.store.team.${text}` };
  }
  if (DATE_ONLY.test(text)) {
    return { date: text };
  }
  return { text };
}

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
