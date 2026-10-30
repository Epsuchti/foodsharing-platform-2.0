import { HttpTestingController } from '@angular/common/http/testing';
import { DebugElement, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import {
  ApiErrorCode,
  BuddyRelation,
  Language,
  PublicProfile,
  Report,
  ReportReason,
  ReportStatus,
  ReportSummary,
  Role,
  SessionInfo,
  StoreListItem,
  Theme,
  UserTeamStatus,
} from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { ProfilePageComponent } from '../profiles/pages/profile-page.component';
import { RegionPageContext } from '../regions/region-page.context';
import { ReportPageComponent } from './pages/report-page.component';
import { RegionMediationTab } from './tabs/region-mediation.tab';
import { RegionReportLogTab } from './tabs/region-report-log.tab';
import { RegionReportsTab } from './tabs/region-reports.tab';

const ME = '00000001-0001-4000-8000-000000000006';
const JUMPER = '00000001-0001-4000-8000-000000000007';
const REPORTS = '00000001-0001-4000-8000-000000000013';
const ZH = '00000010-0001-4000-8000-000000000040';
const S01 = '00000020-0001-4000-8000-000000000001';
const R1 = '00000050-0001-4000-8000-000000000001';
const THREAD = '00000040-0001-4000-8000-000000000001';
const EMPTY_PAGE = { items: [], page: { page: 0, size: 50, totalItems: 0, totalPages: 0 } };
const serverError = { status: 500, statusText: 'Server Error' };

const SESSION: SessionInfo = {
  authenticated: true,
  permissions: [],
  user: { id: ME, email: 'foodsaver@local', displayName: 'Sarah', role: Role.Foodsaver, language: Language.De, theme: Theme.System, verified: true },
};

const jumper = { id: JUMPER, displayName: 'Jonas Weber', role: Role.Foodsaver, verified: true };

function summary(overrides: Partial<ReportSummary> = {}): ReportSummary {
  return {
    id: R1,
    regionId: ZH,
    reason: ReportReason.NoShow,
    reportedUser: jumper,
    reporter: { id: ME, displayName: 'Sarah Müller', role: Role.Foodsaver, verified: true },
    store: { id: S01, name: 'Bäckerei Kuhn' },
    status: ReportStatus.ToDo,
    createdAt: '2026-10-20T18:00:00Z',
    ...overrides,
  };
}

function report(overrides: Partial<Report> = {}): Report {
  return {
    ...summary(),
    message: 'Jumper ist ohne Abmeldung nicht erschienen.',
    wall: [],
    permissions: { mayEdit: true, mayDelete: false, mayPost: true },
    ...overrides,
  };
}

function profile(overrides: Partial<PublicProfile> = {}): PublicProfile {
  return {
    user: jumper,
    regions: [],
    groups: [],
    stats: { pickupCount: 1, kgSaved: 1, postCount: 0, bananaCount: 0, buddyCount: 0, basketsSharedCount: 0 },
    banana: { count: 0, givenByMe: false, mayGive: false },
    buddy: { status: BuddyRelation.None },
    achievements: [],
    kamChains: [],
    permissions: {
      mayEditProfile: false,
      maySeeNotes: false,
      mayReport: true,
      mayGiveBanana: false,
      maySeeHistory: false,
      maySeeCommitments: false,
      mayAdminister: false,
      maySeePickups: false,
    },
    ...overrides,
  };
}

function store(id: string, name: string, membership: UserTeamStatus): StoreListItem {
  return { id, name, membership } as StoreListItem;
}

describe('reports pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => document.querySelector(selector);
  const all = (selector: string) => document.querySelectorAll(selector);
  const settle = () => harness.fixture.whenStable();
  const tick = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await settle();
  };
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
  const click = (testId: string) => {
    const host = q(`[data-testid="${testId}"]`) as HTMLElement;
    ((host.querySelector('button') ?? host) as HTMLElement).click();
  };
  const type = (testId: string, value: string) => {
    const input = q(`[data-testid="${testId}"]`) as HTMLInputElement | HTMLTextAreaElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  /** Picks a p-select value through its (ngModelChange) binding. */
  const choose = (testId: string, value: unknown) => {
    const select = harness.fixture.debugElement.query(By.css(`[data-testid="${testId}"]`)) as DebugElement;
    select.triggerEventHandler('ngModelChange', value);
  };
  /** 403 answer; the error interceptor then refreshes the session once. */
  const forbid = async (path: string) => {
    http.expectOne((req) => req.url === path).flush(apiHttpError(403, ApiErrorCode.ReportsNotAccessible).error, { status: 403, statusText: 'Forbidden' });
    await settle();
    http.expectOne('/api/auth/session').flush(SESSION);
    await settle();
  };
  const retry = (testId: string) => (q(`[data-testid="${testId}"] [data-testid$=".retry"] button`) as HTMLButtonElement).click();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'reports.reason.violated': 'Regel {rule}' },
          routes: [
            { path: 'profile/:userId', component: ProfilePageComponent },
            { path: 'reports/:reportId', component: ReportPageComponent },
            {
              path: 'regions/:regionId',
              providers: [{ provide: RegionPageContext, useValue: { regionId: signal(ZH) } }],
              children: [
                { path: 'reports', component: RegionReportsTab },
                { path: 'report-log', component: RegionReportLogTab },
                { path: 'mediation', component: RegionMediationTab },
              ],
            },
          ],
        }),
        provideOptimus(),
      ],
    });
    TestBed.inject(SessionService).applySession(SESSION);
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  describe('report dialog from the profile', () => {
    async function openDialog(body: PublicProfile = profile()) {
      await harness.navigateByUrl(`/profile/${JUMPER}`);
      http.expectOne(`/api/users/${JUMPER}/profile`).flush(body);
      await settle();
      http.expectOne((req) => req.url === `/api/users/${JUMPER}/wall`).flush(EMPTY_PAGE);
      await settle();
      click('profiles.page.report');
      await settle();
      const stores = http.expectOne((req) => req.url === '/api/users/me/stores');
      expect(stores.request.params.get('excludeInactive')).toBe('true');
      stores.flush([store(S01, 'Bäckerei Kuhn', UserTeamStatus.Member), store('s9', 'Bioladen', UserTeamStatus.Applied)]);
      return http.expectOne((req) => req.url === `/api/users/${JUMPER}/report-options`);
    }

    it('offers exactly the region reasons, reloads them for a store and files the report', async () => {
      const options = await openDialog();
      expect(options.request.params.has('storeId')).toBe(false);
      options.flush({ regionId: ZH, reasons: [ReportReason.Late, ReportReason.NoShow], otherAllowed: false, buttonEnabled: true });
      await tick();
      expect(state('reports.dialog.options')).toBe('success');

      // the store decides the region: reasons reload with its id
      choose('reports.dialog.store', S01);
      await settle();
      const storeOptions = http.expectOne((req) => req.url === `/api/users/${JUMPER}/report-options`);
      expect(storeOptions.request.params.get('storeId')).toBe(S01);
      storeOptions.flush({ regionId: ZH, reasons: [ReportReason.NoShow, ReportReason.ViolatedB10], otherAllowed: true, buttonEnabled: true });
      await tick();

      // nothing is sent while the form is invalid
      click('reports.dialog.submit');
      await settle();
      http.expectNone((req) => req.method === 'POST');
      expect(q('[data-testid="reports.dialog.error.reason"]')).not.toBeNull();

      choose('reports.dialog.reason', ReportReason.Other);
      await settle();
      type('reports.dialog.reasonText', 'Unfreundlich');
      type('reports.dialog.message', 'Hat die Filialleitung beschimpft.');
      (q('[data-testid="reports.dialog.confirmation"] input') as HTMLInputElement).click();
      await settle();
      click('reports.dialog.submit');
      const create = http.expectOne((req) => req.method === 'POST' && req.url === `/api/users/${JUMPER}/reports`);
      expect(create.request.body).toMatchObject({
        reason: ReportReason.Other,
        reasonText: 'Unfreundlich',
        message: 'Hat die Filialleitung beschimpft.',
        storeId: S01,
        sendConfirmationMail: true,
      });
      create.flush(report());
      await settle();
      // the profile reloads (a handler's count may change)
      http.expectOne(`/api/users/${JUMPER}/profile`).flush(profile());
      await settle();
    });

    it('explains why a report cannot be filed and offers no submit', async () => {
      const options = await openDialog();
      options.flush({ regionId: ZH, reasons: [ReportReason.Late], otherAllowed: false, buttonEnabled: false, denialCode: ApiErrorCode.ReportButtonDisabled });
      await tick();
      expect(q('[data-testid="reports.dialog.denied"]')?.getAttribute('data-code')).toBe('REPORT_BUTTON_DISABLED');
      expect(q('[data-testid="reports.dialog.reason"]')).toBeNull();
      expect((q('[data-testid="reports.dialog.submit"] button') as HTMLButtonElement).disabled).toBe(true);
    });

    it('retries failed options', async () => {
      const options = await openDialog();
      options.flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
      await tick();
      expect(state('reports.dialog.options')).toBe('error');
      retry('reports.dialog.options');
      await settle();
      http.expectOne((req) => req.url === `/api/users/${JUMPER}/report-options`).flush({ reasons: [ReportReason.Late], otherAllowed: false, buttonEnabled: true });
      await tick();
      expect(state('reports.dialog.options')).toBe('success');
    });

    it('shows the reports block to handlers (violationCount) only', async () => {
      await harness.navigateByUrl(`/profile/${JUMPER}`);
      http.expectOne(`/api/users/${JUMPER}/profile`).flush(profile({ violationCount: 2 }));
      await settle();
      http.expectOne((req) => req.url === `/api/users/${JUMPER}/wall`).flush(EMPTY_PAGE);
      const list = http.expectOne(`/api/users/${JUMPER}/reports`);
      expect(state('reports.user.state')).toBe('loading');
      list.flush([summary(), summary({ id: 'r2', status: ReportStatus.Completed })]);
      await settle();
      expect(state('reports.user.state')).toBe('success');
      expect(all('[data-testid="reports.item"]').length).toBe(2);
      expect(q(`[data-testid="reports.item"][data-report-id="${R1}"] [data-testid="reports.item.open"]`)?.getAttribute('href')).toBe(`/reports/${R1}`);

      await harness.navigateByUrl(`/profile/${ME}`);
      http.expectOne(`/api/users/${ME}/profile`).flush(profile({ user: { ...jumper, id: ME } }));
      await settle();
      http.expectOne((req) => req.url === `/api/users/${ME}/wall`).flush(EMPTY_PAGE);
      await settle();
      expect(q('[data-testid="reports.user"]')).toBeNull();
      http.expectNone(`/api/users/${ME}/reports`);
    });
  });

  describe('region reports tab', () => {
    const list = (req: { url: string }) => req.url === `/api/regions/${ZH}/reports`;

    it('lists the reports, filters by status and pages', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/reports`);
      expect(state('reports.region.state')).toBe('loading');
      const first = http.expectOne(list);
      expect(first.request.params.has('status')).toBe(false);
      first.flush({ items: [summary(), summary({ id: 'r7', reason: ReportReason.ViolatedB10, reminderAt: '2026-11-03T08:00:00Z' })], page: { page: 0, size: 20, totalItems: 2, totalPages: 1 } });
      await settle();
      expect(state('reports.region.state')).toBe('success');
      expect(all('[data-testid="reports.item"]').length).toBe(2);
      expect(q('[data-report-id="r7"] [data-testid="reports.item.reason"]')?.textContent).toContain('Regel B1.0');
      expect(q('[data-report-id="r7"] [data-testid="reports.item.reminder"]')).not.toBeNull();

      choose('reports.region.status', ReportStatus.Completed);
      await settle();
      const filtered = http.expectOne(list);
      expect(filtered.request.params.get('status')).toBe('COMPLETED');
      expect(filtered.request.params.get('page')).toBe('0');
      filtered.flush({ items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } });
      await settle();
      expect(state('reports.region.state')).toBe('empty');
    });

    it('renders the forbidden state for non-handlers and retries errors', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/reports`);
      await forbid(`/api/regions/${ZH}/reports`);
      expect(state('reports.region.state')).toBe('forbidden');

      await harness.navigateByUrl(`/regions/${ZH}/mediation`);
      http.expectOne(`/api/regions/${ZH}/mediation`).flush({ enabled: true, team: [] });
      await harness.navigateByUrl(`/regions/${ZH}/reports`);
      http.expectOne(list).flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
      await settle();
      expect(state('reports.region.state')).toBe('error');
      retry('reports.region.state');
      await settle();
      http.expectOne(list).flush({ items: [summary()], page: { page: 0, size: 20, totalItems: 1, totalPages: 1 } });
      await settle();
      expect(state('reports.region.state')).toBe('success');
    });
  });

  describe('report log tab', () => {
    it('loads the report history with calendar days', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/report-log`);
      await settle();
      const log = http.expectOne((req) => req.url === `/api/regions/${ZH}/report-log`);
      expect(log.request.params.get('page')).toBe('0');
      log.flush({ items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } });
      await settle();
      expect(q('[data-testid="reports.log.reports"]')).not.toBeNull();
    });
  });

  describe('/reports/:id', () => {
    async function open(body: Report) {
      await harness.navigateByUrl(`/reports/${body.id}`);
      expect(state('reports.detail.state')).toBe('loading');
      http.expectOne(`/api/reports/${body.id}`).flush(body);
      await settle();
      http.expectOne((req) => req.url === `/api/reports/${body.id}/wall`).flush({
        items: [{ id: 'w1', author: { id: REPORTS, displayName: 'Céline Rochat', role: Role.Ambassador, verified: true }, body: 'Ich kläre das.', createdAt: '2026-10-21T08:00:00Z', mayDelete: false }],
        page: { page: 0, size: 50, totalItems: 1, totalPages: 1 },
      });
      await settle();
    }

    it('shows the report and saves only the changed handling fields', async () => {
      await open(report());
      expect(state('reports.detail.state')).toBe('success');
      expect(q('[data-testid="reports.detail.message"]')?.textContent).toContain('ohne Abmeldung');
      expect(q('[data-testid="reports.detail.store"]')?.textContent).toContain('Bäckerei Kuhn');
      expect(q('[data-testid="reports.detail.delete"]')).toBeNull();
      expect(state('reports.detail.wall.state')).toBe('success');
      expect(q('[data-testid="reports.detail.wall.composer"]')).not.toBeNull();
      expect((q('[data-testid="reports.detail.save"] button') as HTMLButtonElement).disabled).toBe(true);

      choose('reports.detail.statusSelect', ReportStatus.InProgress);
      type('reports.detail.consequence', 'Verwarnung ausgesprochen');
      type('reports.detail.thread', 'kein-uuid');
      await settle();
      expect(q('[data-testid="reports.detail.thread"]')?.getAttribute('aria-invalid')).toBe('true');
      expect((q('[data-testid="reports.detail.save"] button') as HTMLButtonElement).disabled).toBe(true);
      type('reports.detail.thread', THREAD);
      type('reports.detail.reminder', '2026-11-05T10:30');
      await settle();
      click('reports.detail.save');
      const patch = http.expectOne((req) => req.method === 'PATCH' && req.url === `/api/reports/${R1}`);
      expect(patch.request.body).toEqual({
        status: 'IN_PROGRESS',
        consequence: 'Verwarnung ausgesprochen',
        reminderAt: new Date('2026-11-05T10:30').toISOString(),
        forumThreadId: THREAD,
      });
      const saved = report({ status: ReportStatus.InProgress, consequence: 'Verwarnung ausgesprochen', forumThreadId: THREAD, reminderAt: new Date('2026-11-05T10:30').toISOString() });
      patch.flush(saved);
      await settle();
      http.expectOne(`/api/reports/${R1}`).flush(saved);
      await settle();
      expect(q('[data-testid="reports.detail.status"]')?.getAttribute('data-status')).toBe('IN_PROGRESS');
      expect(q('[data-testid="reports.detail.threadLink"]')?.getAttribute('href')).toBe(`/forum/threads/${THREAD}`);
      expect((q('[data-testid="reports.detail.save"] button') as HTMLButtonElement).disabled).toBe(true);
    });

    it('writes on the wall and lets ORGA delete the report', async () => {
      await open(report({ permissions: { mayEdit: true, mayDelete: true, mayPost: true } }));
      type('reports.detail.wall.text', 'Gespräch am Montag');
      await settle();
      (q('[data-testid="reports.detail.wall.composer"]') as HTMLFormElement).dispatchEvent(new Event('submit'));
      const post = http.expectOne((req) => req.method === 'POST' && req.url === `/api/reports/${R1}/wall`);
      expect(post.request.body).toEqual({ body: 'Gespräch am Montag' });
      post.flush({ id: 'w2', body: 'Gespräch am Montag', createdAt: '2026-11-02T09:00:00Z', mayDelete: true });
      await settle();
      http.expectOne((req) => req.url === `/api/reports/${R1}/wall`).flush(EMPTY_PAGE);
      await settle();

      const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm').mockImplementation((confirmation) => {
        confirmation.accept?.();
        return TestBed.inject(ConfirmationService);
      });
      click('reports.detail.delete');
      expect(confirm).toHaveBeenCalledTimes(1);
      http.expectOne((req) => req.method === 'DELETE' && req.url === `/api/reports/${R1}`).flush(null);
      await settle();
      expect(TestBed.inject(Router).url).toBe(`/regions/${ZH}/reports`);
      http.expectOne((req) => req.url === `/api/regions/${ZH}/reports`).flush({ items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } });
    });

    it('shows read-only handling without edit right, forbidden and not-found states', async () => {
      await open(report({ consequence: 'Verwarnung', permissions: { mayEdit: false, mayDelete: false, mayPost: false } }));
      expect(q('[data-testid="reports.detail.save"]')).toBeNull();
      expect(q('[data-testid="reports.detail.consequenceText"]')?.textContent).toContain('Verwarnung');
      expect(q('[data-testid="reports.detail.wall.composer"]')).toBeNull();

      await harness.navigateByUrl('/reports/r404');
      http.expectOne('/api/reports/r404').flush(apiHttpError(404, ApiErrorCode.ReportNotFound).error, { status: 404, statusText: 'Not Found' });
      await settle();
      expect(state('reports.detail.state')).toBe('notFound');

      await harness.navigateByUrl('/reports/r403');
      await forbid('/api/reports/r403');
      expect(state('reports.detail.state')).toBe('forbidden');
    });
  });

  describe('mediation tab', () => {
    const info = `/api/regions/${ZH}/mediation`;

    it('shows the team and sends a request with the other person', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/mediation`);
      expect(state('reports.mediation.state')).toBe('loading');
      http.expectOne(info).flush({
        enabled: true,
        team: [{ id: REPORTS, displayName: 'Céline Rochat', role: Role.Ambassador, verified: true }],
        mailboxAddress: 'mediation.zuerich@foodsharing.network',
      });
      await settle();
      expect(state('reports.mediation.state')).toBe('success');
      expect(all('[data-testid="reports.mediation.member"]').length).toBe(1);
      expect(q('[data-testid="reports.mediation.mail"]')?.getAttribute('href')).toBe('mailto:mediation.zuerich@foodsharing.network');

      type('reports.mediation.message', 'zu kurz');
      click('reports.mediation.send');
      await settle();
      http.expectNone((req) => req.method === 'POST');
      expect(q('[data-testid="reports.mediation.message"]')?.getAttribute('aria-invalid')).toBe('true');

      type('reports.mediation.picker.search', 'Jonas');
      await new Promise((resolve) => setTimeout(resolve, 0));
      await settle();
      const search = http.expectOne((req) => req.url === '/api/users/search');
      expect(search.request.params.get('q')).toBe('Jonas');
      search.flush([jumper]);
      await settle();
      click('reports.mediation.picker.add');
      await settle();
      expect(q('[data-testid="reports.mediation.counterpart"]')?.getAttribute('data-user-id')).toBe(JUMPER);

      type('reports.mediation.message', 'Wir streiten uns über die Abholzeiten.');
      click('reports.mediation.send');
      const request = http.expectOne((req) => req.method === 'POST' && req.url === `/api/regions/${ZH}/mediation-requests`);
      expect(request.request.body.message).toBe('Wir streiten uns über die Abholzeiten.');
      expect(request.request.body.counterpartUserId).toBe(JUMPER);
      request.flush(null, { status: 202, statusText: 'Accepted' });
      await settle();
      expect(q('[data-testid="reports.mediation.sent"]')).not.toBeNull();
    });

    it('offers no form when mediation is off and retries errors', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/mediation`);
      http.expectOne(info).flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
      await settle();
      expect(state('reports.mediation.state')).toBe('error');
      retry('reports.mediation.state');
      await settle();
      http.expectOne(info).flush({ enabled: false, team: [] });
      await settle();
      expect(q('[data-testid="reports.mediation.disabled"]')).not.toBeNull();
      expect(q('[data-testid="reports.mediation.noTeam"]')).not.toBeNull();
      expect(q('[data-testid="reports.mediation.send"]')).toBeNull();
    });
  });
});
