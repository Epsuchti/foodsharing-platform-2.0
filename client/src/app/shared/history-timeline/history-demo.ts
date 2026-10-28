import { Observable, NEVER, of, throwError } from 'rxjs';

import {
  ApiErrorCode,
  HistoryActionInfo,
  HistoryActorType,
  HistoryEntry,
  HistoryEntryPage,
  HistorySubjectType,
} from '../../api';
import { ApiErrorView } from '../../core/data/api-error';
import { HistorySource } from './history-timeline.types';

/**
 * Fixed demo history for the dev gallery and component specs (no backend). Dates are relative to
 * 2026-11-02 (SEED_NOW), so screenshots with the browser clock at SEED_NOW show "today"/"yesterday".
 */
export const DEMO_USER_ID = '00000001-0001-4000-8000-000000000006';
const ORGA_ID = '00000001-0001-4000-8000-000000000014';

const me = { id: DEMO_USER_ID, displayName: 'Sarah Müller', type: HistoryActorType.User };
const orga = { id: ORGA_ID, displayName: 'Marco Rossi', type: HistoryActorType.User };
const system = { displayName: 'System', type: HistoryActorType.System };

function entry(partial: Partial<HistoryEntry> & Pick<HistoryEntry, 'id' | 'occurredAt' | 'action'>): HistoryEntry {
  return {
    actorType: partial.actor?.type ?? HistoryActorType.User,
    subjectType: HistorySubjectType.User,
    subjectId: DEMO_USER_ID,
    actor: me,
    ...partial,
  };
}

export const DEMO_HISTORY: HistoryEntry[] = [
  entry({
    id: 'd0000000-0000-4000-8000-000000000001',
    occurredAt: '2026-11-02T08:41:00Z',
    action: 'LOGIN',
    metadata: { method: 'PASSKEY' },
  }),
  entry({
    id: 'd0000000-0000-4000-8000-000000000002',
    occurredAt: '2026-11-02T07:15:00Z',
    action: 'SETTINGS_CHANGED',
    field: 'theme',
    oldValue: 'SYSTEM',
    newValue: 'DARK',
  }),
  entry({
    id: 'd0000000-0000-4000-8000-000000000003',
    occurredAt: '2026-11-01T18:02:00Z',
    action: 'PASSKEY_REGISTERED',
    metadata: { label: 'MacBook Touch ID' },
  }),
  entry({
    id: 'd0000000-0000-4000-8000-000000000004',
    occurredAt: '2026-11-01T17:55:00Z',
    action: 'LOGIN_FAILED',
    actor: undefined,
    actorType: HistoryActorType.System,
    metadata: { method: 'EMAIL', reason: ApiErrorCode.LoginTokenExpiredOrUsed },
    traceId: '5f3a9c1e7b20',
  }),
  entry({
    id: 'd0000000-0000-4000-8000-000000000005',
    occurredAt: '2026-10-21T10:30:00Z',
    action: 'ROLE_CHANGED',
    actor: orga,
    field: 'role',
    oldValue: 'FOODSHARER',
    newValue: 'FOODSAVER',
    reason: 'Ausweis von der Botschafterin geprüft',
  }),
  entry({
    id: 'd0000000-0000-4000-8000-000000000006',
    occurredAt: '2026-10-21T10:29:00Z',
    action: 'FIELD_CHANGED',
    actor: orga,
    field: 'displayName',
    oldValue: 'Sarah M.',
    newValue: 'Sarah Müller',
  }),
  entry({
    id: 'd0000000-0000-4000-8000-000000000007',
    occurredAt: '2025-12-03T09:12:00Z',
    action: 'MAIL_SENT',
    subjectType: HistorySubjectType.System,
    actor: system,
    actorType: HistoryActorType.System,
    metadata: { mailId: 'b2a7', template: 'magic-link' },
  }),
];

export const DEMO_ACTIONS: HistoryActionInfo[] = [
  'ACCOUNT_CREATED',
  'SETTINGS_CHANGED',
  'FIELD_CHANGED',
  'ROLE_CHANGED',
  'LOGIN',
  'LOGOUT',
  'LOGIN_FAILED',
  'PASSKEY_REGISTERED',
].map((action) => ({
  subjectType: HistorySubjectType.User,
  action,
  labelKey: `history.action.USER.${action}`,
}));

export function demoPage(items: HistoryEntry[], page = 0, size = 20, total = items.length): HistoryEntryPage {
  return {
    items,
    page: { page, size, totalItems: total, totalPages: Math.max(1, Math.ceil(total / size)) },
  };
}

/**
 * Pages through DEMO_HISTORY (filters by action). `reason` replaces the free-text reasons (user input, so the
 * gallery passes one in the viewer's language).
 */
export function createDemoHistorySource(reason?: () => string): HistorySource {
  return (filter, { page, size }) => {
    const items = DEMO_HISTORY.filter(
      (item) => filter.actions.length === 0 || filter.actions.includes(item.action),
    ).map((item) => (item.reason && reason ? { ...item, reason: reason() } : item));
    return of(demoPage(items.slice(page * size, (page + 1) * size), page, size, items.length));
  };
}

export const demoHistorySource: HistorySource = createDemoHistorySource();

export const emptyHistorySource: HistorySource = () => of(demoPage([]));

export const loadingHistorySource: HistorySource = (): Observable<HistoryEntryPage> => NEVER;

export function failingHistorySource(error: ApiErrorView): HistorySource {
  return () => throwError(() => error);
}

export const demoActionsSource = () => of(DEMO_ACTIONS);
