import {
  RegionDetail,
  Report,
  ReportOptions,
  ReportReason,
  ReportStatus,
  ReportUpdate,
  SessionInfo,
  StoreListItem,
  UserTeamStatus,
  WorkingGroupFunction,
} from '../../api';
import { isOrga } from '../../core/auth/permission.util';

/** Server rules of `ReportCreate` / `ReportUpdate` / `MediationRequestCreate` (schemas/reports.yaml). */
export const REPORT_MESSAGE_MIN = 10;
export const REPORT_MESSAGE_MAX = 32600;
export const REASON_TEXT_MAX = 500;
export const CONSEQUENCE_MAX = 4000;
export const MEDIATION_MESSAGE_MIN = 10;
export const MEDIATION_MESSAGE_MAX = 4000;

/** Statuses a handler can set (DELETED is the result of `deleteReport`, ORGA only). */
export const SETTABLE_STATUSES: readonly ReportStatus[] = Object.values(ReportStatus).filter(
  (status) => status !== ReportStatus.Deleted,
);

/** Translation of a reason: the 23 rule-book codes share one text with the rule number (`VIOLATED_B1_0` → B1.0). */
export function reasonLabel(reason: ReportReason | string): { key: string; params?: Record<string, string> } {
  const rule = /^VIOLATED_B(\d+)(?:_(\d+))?$/.exec(reason);
  if (rule) {
    return { key: 'reports.reason.violated', params: { rule: `B${rule[1]}${rule[2] !== undefined ? '.' + rule[2] : ''}` } };
  }
  return { key: `reports.reason.${reason}` };
}

/**
 * Reasons the dialog offers: exactly the region's set from `getReportOptions` (the server rejects others), plus
 * OTHER when `otherAllowed`.
 */
export function reasonChoices(options: ReportOptions | undefined): ReportReason[] {
  if (!options) return [];
  const reasons = options.reasons.filter((reason) => reason !== ReportReason.Other);
  return options.otherAllowed ? [...reasons, ReportReason.Other] : reasons;
}

/** Stores a report can be about: the reporter's own stores where they are in the team (not applied or invited). */
export function reportableStores(stores: readonly StoreListItem[]): { id: string; name: string }[] {
  const inTeam: readonly UserTeamStatus[] = [UserTeamStatus.Member, UserTeamStatus.Coordinator, UserTeamStatus.WaitingList];
  return stores
    .filter((store) => inTeam.includes(store.membership))
    .map((store) => ({ id: store.id, name: store.name }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

export interface ReportDraft {
  reason: ReportReason | null;
  reasonText: string;
  message: string;
}

/** Field errors of the dialog (keys under `reports.dialog.error.*`), the rules of `createReport`. */
export function reportDraftErrors(draft: ReportDraft, choices: readonly ReportReason[]): Partial<Record<keyof ReportDraft, string>> {
  const errors: Partial<Record<keyof ReportDraft, string>> = {};
  if (!draft.reason || !choices.includes(draft.reason)) errors.reason = 'reports.dialog.error.reason';
  if (draft.reason === ReportReason.Other && !draft.reasonText.trim()) errors.reasonText = 'reports.dialog.error.reasonText';
  if (draft.reasonText.length > REASON_TEXT_MAX) errors.reasonText = 'reports.dialog.error.reasonTextLength';
  const message = draft.message.trim().length;
  if (message < REPORT_MESSAGE_MIN) errors.message = 'reports.dialog.error.message';
  if (draft.message.length > REPORT_MESSAGE_MAX) errors.message = 'reports.dialog.error.messageLength';
  return errors;
}

export interface ReportHandlingDraft {
  status: ReportStatus;
  consequence: string;
  /** `datetime-local` value or ''. */
  reminderAt: string;
  forumThreadId: string;
}

export function handlingDraft(report: Report): ReportHandlingDraft {
  return {
    status: report.status,
    consequence: report.consequence ?? '',
    reminderAt: toLocalInput(report.reminderAt),
    forumThreadId: report.forumThreadId ?? '',
  };
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Field errors of the handling form (keys under `reports.detail.error.*`). */
export function handlingErrors(draft: ReportHandlingDraft): Partial<Record<keyof ReportHandlingDraft, string>> {
  const errors: Partial<Record<keyof ReportHandlingDraft, string>> = {};
  if (draft.consequence.length > CONSEQUENCE_MAX) errors.consequence = 'reports.detail.error.consequence';
  if (draft.forumThreadId.trim() && !UUID.test(draft.forumThreadId.trim())) errors.forumThreadId = 'reports.detail.error.thread';
  if (draft.reminderAt && Number.isNaN(new Date(draft.reminderAt).getTime())) errors.reminderAt = 'reports.detail.error.reminder';
  return errors;
}

/**
 * `ReportUpdate` with only the changed fields (each one is its own history entry on the server). The contract
 * cannot clear a field, so an emptied consequence/reminder/thread is not sent.
 */
export function handlingPatch(report: Report, draft: ReportHandlingDraft): ReportUpdate {
  const patch: ReportUpdate = {};
  if (draft.status !== report.status) patch.status = draft.status;
  const consequence = draft.consequence.trim();
  if (consequence && consequence !== (report.consequence ?? '')) patch.consequence = consequence;
  if (draft.reminderAt && draft.reminderAt !== toLocalInput(report.reminderAt)) patch.reminderAt = fromLocalInput(draft.reminderAt);
  const thread = draft.forumThreadId.trim();
  if (thread && thread !== (report.forumThreadId ?? '')) patch.forumThreadId = thread;
  return patch;
}

/** `<input type="datetime-local">` value (local time, minutes) ↔ ISO instant. */
export function toLocalInput(iso: string | undefined | null): string {
  if (!iso) return '';
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}

/** Calendar day (`YYYY-MM-DD`, browser time zone) of an instant, for `format: date` query parameters. */
export function localDay(iso: string | undefined): string | undefined {
  return iso ? toLocalInput(iso).slice(0, 10) : undefined;
}

/**
 * Whether the region page offers the reports tabs (A51 copied client-side until the contract exposes the rule):
 * ORGA; admins of a REPORT or ARBITRATION working group (handlers of their region and the regions below; which
 * regions exactly is decided by the server, others see the forbidden state); ambassadors of a region without a
 * REPORT group. The server answers 403 REPORTS_NOT_ACCESSIBLE to everyone else.
 */
export function mayHandleReports(session: SessionInfo | null | undefined, region: RegionDetail | undefined): boolean {
  if (isOrga(session)) return true;
  const handlerFunctions: readonly WorkingGroupFunction[] = [WorkingGroupFunction.Report, WorkingGroupFunction.Arbitration];
  const groupAdmin = (session?.units ?? []).some(
    (unit) => unit.admin && unit.type === 'WORKING_GROUP' && !!unit.function && handlerFunctions.includes(unit.function),
  );
  if (groupAdmin) return true;
  return !!region?.membership.admin && !region.functions.some((assignment) => assignment.function === WorkingGroupFunction.Report);
}
