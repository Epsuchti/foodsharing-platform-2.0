import {
  Language,
  RegionDetail,
  Report,
  ReportReason,
  ReportStatus,
  Role,
  SessionInfo,
  StoreListItem,
  Theme,
  UnitType,
  UserTeamStatus,
  WorkingGroupFunction,
} from '../../api';
import {
  SETTABLE_STATUSES,
  fromLocalInput,
  handlingDraft,
  handlingErrors,
  handlingPatch,
  localDay,
  mayHandleReports,
  reasonChoices,
  reasonLabel,
  reportDraftErrors,
  reportableStores,
  toLocalInput,
} from './reports.util';

const ZH = '00000010-0001-4000-8000-000000000040';
const THREAD = '00000040-0001-4000-8000-000000000001';

function report(overrides: Partial<Report> = {}): Report {
  return {
    id: 'r1',
    regionId: ZH,
    reason: ReportReason.NoShow,
    reportedUser: { id: 'u7', displayName: 'Jonas Weber', role: Role.Foodsaver, verified: true },
    status: ReportStatus.ToDo,
    createdAt: '2026-10-20T18:00:00Z',
    message: 'Nicht erschienen.',
    wall: [],
    permissions: { mayEdit: true, mayDelete: false, mayPost: true },
    ...overrides,
  };
}

function session(units: SessionInfo['units'] = [], role: Role = Role.Ambassador): SessionInfo {
  return {
    authenticated: true,
    permissions: [],
    units,
    user: { id: 'me', email: 'me@local', displayName: 'Me', role, language: Language.De, theme: Theme.System, verified: true },
  };
}

function region(admin: boolean, reportGroup: boolean): RegionDetail {
  return {
    id: ZH,
    slug: 'zuerich-stadt',
    name: 'Zürich Stadt',
    type: UnitType.BigCity,
    hasChildren: false,
    memberCount: 10,
    ambassadors: [],
    functions: reportGroup
      ? [{ function: WorkingGroupFunction.Report, group: { id: 'g24', slug: 'meldungen', name: 'Meldungen' } }]
      : [],
    membership: { member: true, admin, homeRegion: true },
    timeZone: 'Europe/Zurich',
    joinMode: 'OPEN' as RegionDetail['joinMode'],
    moderated: false,
  };
}

describe('reports.util', () => {
  it('labels rule-book reasons with their rule number and the others by code', () => {
    expect(reasonLabel(ReportReason.ViolatedB10)).toEqual({ key: 'reports.reason.violated', params: { rule: 'B1.0' } });
    expect(reasonLabel(ReportReason.ViolatedB13)).toEqual({ key: 'reports.reason.violated', params: { rule: 'B1.3' } });
    expect(reasonLabel('VIOLATED_B13')).toEqual({ key: 'reports.reason.violated', params: { rule: 'B13' } });
    expect(reasonLabel(ReportReason.Late)).toEqual({ key: 'reports.reason.LATE' });
  });

  it('offers exactly the region reasons, OTHER only when allowed', () => {
    const reasons = [ReportReason.Late, ReportReason.NoShow];
    expect(reasonChoices({ reasons, otherAllowed: false, buttonEnabled: true })).toEqual(reasons);
    expect(reasonChoices({ reasons: [...reasons, ReportReason.Other], otherAllowed: false, buttonEnabled: true })).toEqual(reasons);
    expect(reasonChoices({ reasons, otherAllowed: true, buttonEnabled: true })).toEqual([...reasons, ReportReason.Other]);
    expect(reasonChoices(undefined)).toEqual([]);
  });

  it('lists the stores the reporter is in the team of, by name', () => {
    const store = (id: string, name: string, membership: UserTeamStatus) => ({ id, name, membership }) as StoreListItem;
    expect(
      reportableStores([
        store('s2', 'Migros', UserTeamStatus.Member),
        store('s1', 'Coop', UserTeamStatus.Coordinator),
        store('s3', 'Bäckerei', UserTeamStatus.Applied),
        store('s4', 'Aldi', UserTeamStatus.WaitingList),
      ]),
    ).toEqual([
      { id: 's4', name: 'Aldi' },
      { id: 's1', name: 'Coop' },
      { id: 's2', name: 'Migros' },
    ]);
  });

  it('validates the report draft like createReport', () => {
    const choices = [ReportReason.Late, ReportReason.Other];
    expect(reportDraftErrors({ reason: null, reasonText: '', message: 'kurz' }, choices)).toEqual({
      reason: 'reports.dialog.error.reason',
      message: 'reports.dialog.error.message',
    });
    // a reason outside the region's set is refused
    expect(reportDraftErrors({ reason: ReportReason.Sells, reasonText: '', message: 'genug Text hier' }, choices).reason).toBeDefined();
    expect(reportDraftErrors({ reason: ReportReason.Other, reasonText: '  ', message: 'genug Text hier' }, choices)).toEqual({
      reasonText: 'reports.dialog.error.reasonText',
    });
    expect(reportDraftErrors({ reason: ReportReason.Other, reasonText: 'x'.repeat(501), message: 'genug Text hier' }, choices)).toEqual({
      reasonText: 'reports.dialog.error.reasonTextLength',
    });
    // the minimum counts trimmed characters
    expect(reportDraftErrors({ reason: ReportReason.Late, reasonText: '', message: '   123456789   ' }, choices).message).toBeDefined();
    expect(reportDraftErrors({ reason: ReportReason.Late, reasonText: '', message: '1234567890' }, choices)).toEqual({});
  });

  it('sends only the changed handling fields', () => {
    const r = report({ consequence: 'Gespräch', reminderAt: '2026-11-03T08:00:00Z' });
    const draft = handlingDraft(r);
    expect(handlingPatch(r, draft)).toEqual({});
    expect(handlingPatch(r, { ...draft, status: ReportStatus.InProgress })).toEqual({ status: ReportStatus.InProgress });
    expect(handlingPatch(r, { ...draft, consequence: ' Verwarnung ' })).toEqual({ consequence: 'Verwarnung' });
    // emptied fields cannot be cleared through the contract
    expect(handlingPatch(r, { ...draft, consequence: '', reminderAt: '' })).toEqual({});
    const reminder = '2026-11-05T10:30';
    expect(handlingPatch(r, { ...draft, reminderAt: reminder, forumThreadId: THREAD })).toEqual({
      reminderAt: new Date(reminder).toISOString(),
      forumThreadId: THREAD,
    });
  });

  it('checks the handling form', () => {
    const draft = handlingDraft(report());
    expect(handlingErrors(draft)).toEqual({});
    expect(handlingErrors({ ...draft, forumThreadId: 'abc' })).toEqual({ forumThreadId: 'reports.detail.error.thread' });
    expect(handlingErrors({ ...draft, consequence: 'x'.repeat(4001) })).toEqual({ consequence: 'reports.detail.error.consequence' });
    expect(handlingErrors({ ...draft, reminderAt: 'nonsense' })).toEqual({ reminderAt: 'reports.detail.error.reminder' });
  });

  it('converts datetime-local values and days', () => {
    expect(toLocalInput(undefined)).toBe('');
    const iso = fromLocalInput('2026-11-03T09:15');
    expect(toLocalInput(iso)).toBe('2026-11-03T09:15');
    expect(localDay(iso)).toBe('2026-11-03');
    expect(localDay(undefined)).toBeUndefined();
  });

  it('never offers DELETED as a status', () => {
    expect(SETTABLE_STATUSES).not.toContain(ReportStatus.Deleted);
    expect(SETTABLE_STATUSES).toContain(ReportStatus.Completed);
  });

  it('shows the reports tabs to ORGA, report/arbitration group admins and ambassadors without a report group', () => {
    expect(mayHandleReports(session([], Role.Orga), region(false, true))).toBe(true);
    const reportAdmin = session([
      { id: 'g24', name: 'Meldungen', slug: 'meldungen', type: UnitType.WorkingGroup, member: true, admin: true, homeRegion: false, function: WorkingGroupFunction.Report },
    ]);
    expect(mayHandleReports(reportAdmin, region(false, true))).toBe(true);
    const arbitrationMember = session([
      { id: 'g26', name: 'Schiedsstelle', slug: 'schiedsstelle', type: UnitType.WorkingGroup, member: true, admin: false, homeRegion: false, function: WorkingGroupFunction.Arbitration },
    ]);
    expect(mayHandleReports(arbitrationMember, region(false, true))).toBe(false);
    // ambassadors handle only where no REPORT group exists
    expect(mayHandleReports(session(), region(true, false))).toBe(true);
    expect(mayHandleReports(session(), region(true, true))).toBe(false);
    expect(mayHandleReports(session(), region(false, false))).toBe(false);
    expect(mayHandleReports(null, undefined)).toBe(false);
  });
});
