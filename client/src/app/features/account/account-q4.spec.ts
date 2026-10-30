import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { firstValueFrom, throwError } from 'rxjs';
import { afterEach, vi } from 'vitest';

import { ApiErrorCode, Language, Role, SessionInfo, Theme, UnitMembership, UnitType, UserProfile, UsersService } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { readBlobErrors, toApiErrorView } from '../../core/data/api-error';
import { DataState, loadResource } from '../../core/data/data-state';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { sessionInfo, settle } from '../auth/testing';
import {
  CALENDAR_FEED_DEFAULTS,
  businessCardFileName,
  businessCardRegions,
  businessCardRoles,
  calendarFeedUrl,
  deletionConfirmed,
  webcalUrl,
} from './account.util';
import { BounceBannerComponent } from './components/bounce-banner.component';
import { BusinessCardComponent } from './components/business-card.component';
import { CalendarCardComponent } from './components/calendar-card.component';
import { DangerZoneCardComponent } from './components/danger-zone-card.component';
import { DataExportCardComponent, jobResultUrl } from './components/data-export-card.component';

const USER_ID = '00000001-0001-4000-8000-000000000006';
const FEED = 'https://app.test/api/public/calendar/abc.ics';

const PROFILE: UserProfile = {
  id: USER_ID,
  email: 'foodsaver@local',
  displayName: 'Sarah Müller',
  language: Language.De,
  theme: Theme.System,
  role: Role.Foodsaver,
  verified: true,
  registeredAt: '2025-12-19T18:18:00Z',
};

const unit = (id: string, changes: Partial<UnitMembership> = {}): UnitMembership => ({
  id,
  name: id,
  slug: id,
  type: UnitType.City,
  member: true,
  admin: false,
  homeRegion: false,
  ...changes,
});

describe('account util (Q4)', () => {
  it('feed URL carries only the filters that differ from the server defaults', () => {
    expect(calendarFeedUrl(FEED, CALENDAR_FEED_DEFAULTS)).toBe(FEED);
    expect(
      calendarFeedUrl(FEED, { events: 'accepted', pickups: false, introPickups: true, history: true, reminders: [60, 15, 60] }),
    ).toBe(`${FEED}?events=accepted&pickups=false&history=true&reminders=15,60`);
    // at most five reminders (server limit)
    expect(calendarFeedUrl(FEED, { ...CALENDAR_FEED_DEFAULTS, reminders: [1, 2, 3, 4, 5, 6] })).toBe(`${FEED}?reminders=1,2,3,4,5`);
    expect(webcalUrl(`${FEED}?events=none`)).toBe('webcal://app.test/api/public/calendar/abc.ics?events=none');
    expect(webcalUrl('http://localhost:8080/x.ics')).toBe('webcal://localhost:8080/x.ics');
  });

  it('business card roles follow the own role, never above AMBASSADOR', () => {
    expect(businessCardRoles(Role.Foodsharer)).toEqual([]);
    expect(businessCardRoles(null)).toEqual([]);
    expect(businessCardRoles(Role.Foodsaver)).toEqual([Role.Foodsaver]);
    expect(businessCardRoles(Role.StoreManager)).toEqual([Role.Foodsaver, Role.StoreManager]);
    expect(businessCardRoles(Role.Orga)).toEqual([Role.Foodsaver, Role.StoreManager, Role.Ambassador]);
  });

  it('business card regions: member regions, home first, ambassador cards only for led regions', () => {
    const units = [
      unit('winterthur'),
      unit('zuerich', { homeRegion: true, admin: true }),
      unit('ag', { type: UnitType.WorkingGroup }),
      unit('basel', { member: false }),
    ];
    expect(businessCardRegions(units, Role.Foodsaver).map((u) => u.id)).toEqual(['zuerich', 'winterthur']);
    expect(businessCardRegions(units, Role.Ambassador).map((u) => u.id)).toEqual(['zuerich']);
    expect(businessCardRegions(undefined, Role.Foodsaver)).toEqual([]);
    expect(businessCardFileName('zuerich')).toBe('business-card-zuerich.pdf');
    expect(businessCardFileName(undefined)).toBe('business-card-foodsharing.pdf');
  });

  it('deletion needs the re-typed address', () => {
    expect(deletionConfirmed(' Foodsaver@Local ', 'foodsaver@local')).toBe(true);
    expect(deletionConfirmed('foodsaver@loca', 'foodsaver@local')).toBe(false);
    expect(deletionConfirmed('', undefined)).toBe(false);
  });

  it('blob error bodies are read as ApiError', async () => {
    const body = new Blob([JSON.stringify(apiHttpError(403, ApiErrorCode.BusinessCardNotAllowed).error)], {
      type: 'application/json',
    });
    const failed = throwError(() => new HttpErrorResponse({ error: body, status: 403, statusText: 'Forbidden' }));
    const error = await firstValueFrom(failed.pipe(readBlobErrors())).catch((e: unknown) => e);
    expect(toApiErrorView(error).code).toBe(ApiErrorCode.BusinessCardNotAllowed);
    // other errors pass unchanged
    const plain = await firstValueFrom(throwError(() => new Error('x')).pipe(readBlobErrors())).catch((e: unknown) => e);
    expect((plain as Error).message).toBe('x');
  });
});

describe('account settings cards (Q4)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).applySession(sessionInfo());
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const q = (host: HTMLElement, id: string) => host.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const button = (host: HTMLElement, id: string) =>
    (host.querySelector(`[data-testid="${id}"] button`) ?? host.querySelector(`[data-testid="${id}"]`)) as HTMLButtonElement;

  async function mount<T>(type: Type<T>, inputs: Record<string, unknown> = {}): Promise<{ fixture: ComponentFixture<T>; host: HTMLElement }> {
    const fixture = TestBed.createComponent(type);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
    return { fixture, host: fixture.nativeElement as HTMLElement };
  }

  function profileState(): DataState<UserProfile> {
    const usersApi = TestBed.inject(UsersService);
    return TestBed.runInInjectionContext(() =>
      loadResource<UserProfile>({ loader: () => usersApi.getCurrentUser(), isEmpty: () => false }),
    );
  }

  describe('calendar', () => {
    it('no token is the empty state; creating one shows the link, filters change it', async () => {
      const { fixture, host } = await mount(CalendarCardComponent);
      expect(q(host, 'account.settings.calendar.state')?.getAttribute('data-state')).toBe('loading');
      http
        .expectOne('/api/calendar/token')
        .flush(apiHttpError(404, ApiErrorCode.CalendarTokenNotFound).error, { status: 404, statusText: 'Not Found' });
      await settle(fixture);
      expect(q(host, 'account.settings.calendar.state')?.getAttribute('data-state')).toBe('empty');

      button(host, 'account.settings.calendar.create').click();
      const create = http.expectOne('/api/calendar/token');
      expect(create.request.method).toBe('PUT');
      create.flush({ token: 'abc', url: FEED });
      await settle(fixture);
      const url = () => (q(host, 'account.settings.calendar.url') as HTMLInputElement).value;
      expect(url()).toBe(FEED);
      expect(q(host, 'account.settings.calendar.subscribe')?.getAttribute('href')).toBe(
        'webcal://app.test/api/public/calendar/abc.ics',
      );

      const card = fixture.componentInstance as unknown as {
        setSwitch(key: string, value: boolean): void;
        toggleReminder(minutes: number, on: boolean): void;
        patch(changes: object): void;
      };
      card.setSwitch('history', true);
      card.toggleReminder(60, true);
      card.toggleReminder(15, true);
      card.patch({ events: 'none' });
      await settle(fixture);
      expect(url()).toBe(`${FEED}?events=none&history=true&reminders=15,60`);
    });

    it('regenerate and delete ask first', async () => {
      const { fixture, host } = await mount(CalendarCardComponent);
      http.expectOne('/api/calendar/token').flush({ token: 'abc', url: FEED });
      await settle(fixture);
      const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');

      button(host, 'account.settings.calendar.regenerate').click();
      expect(http.match('/api/calendar/token').length).toBe(0);
      confirm.mock.calls[0][0].accept!();
      http.expectOne((r) => r.method === 'PUT' && r.url === '/api/calendar/token').flush({ token: 'def', url: FEED.replace('abc', 'def') });
      await settle(fixture);
      expect((q(host, 'account.settings.calendar.url') as HTMLInputElement).value).toContain('def.ics');

      button(host, 'account.settings.calendar.delete').click();
      confirm.mock.calls[1][0].accept!();
      http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/calendar/token').flush(null, { status: 204, statusText: 'No Content' });
      await settle(fixture);
      expect(q(host, 'account.settings.calendar.state')?.getAttribute('data-state')).toBe('empty');
    });

    it('other errors show the error state with retry', async () => {
      const { fixture, host } = await mount(CalendarCardComponent);
      http.expectOne('/api/calendar/token').flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
      await settle(fixture);
      expect(q(host, 'account.settings.calendar.state')?.getAttribute('data-state')).toBe('error');
    });
  });

  describe('business card', () => {
    function session(changes: Partial<SessionInfo> = {}, user: Partial<NonNullable<SessionInfo['user']>> = {}): void {
      TestBed.inject(SessionService).applySession({
        ...sessionInfo(user),
        units: [unit('r-zuerich', { name: 'Zürich', slug: 'zuerich', homeRegion: true }), unit('r-winti', { name: 'Winterthur' })],
        ...changes,
      });
    }

    it('unverified users get the hint, no form', async () => {
      session({}, { verified: false });
      const { host } = await mount(BusinessCardComponent);
      expect(q(host, 'account.settings.businessCard.notAllowed')).not.toBeNull();
      expect(q(host, 'account.settings.businessCard.download')).toBeNull();
    });

    it('downloads the card for the home region; a refusal is shown with its code', async () => {
      session();
      const { fixture, host } = await mount(BusinessCardComponent);
      button(host, 'account.settings.businessCard.download').click();
      const request = http.expectOne((r) => r.url === '/api/users/me/business-card');
      expect(request.request.params.get('role')).toBe('FOODSAVER');
      expect(request.request.params.get('regionId')).toBe('r-zuerich');
      expect(request.request.responseType).toBe('blob');
      request.flush(new Blob(['%PDF-1.4'], { type: 'application/pdf' }));
      await settle(fixture);
      expect(q(host, 'account.settings.businessCard.error')).toBeNull();

      button(host, 'account.settings.businessCard.download').click();
      http
        .expectOne((r) => r.url === '/api/users/me/business-card')
        .flush(new Blob([JSON.stringify(apiHttpError(400, ApiErrorCode.NotRegionMember).error)], { type: 'application/json' }), {
          status: 400,
          statusText: 'Bad Request',
        });
      await vi.waitFor(async () => {
        await settle(fixture);
        expect(q(host, 'account.settings.businessCard.error')?.getAttribute('data-code')).toBe('NOT_REGION_MEMBER');
      });
    });
  });

  describe('data export', () => {
    it('starts the job, follows its progress and offers the zip', async () => {
      const { fixture, host } = await mount(DataExportCardComponent);
      button(host, 'account.settings.export.start').click();
      const start = http.expectOne('/api/users/me/export');
      expect(start.request.method).toBe('POST');
      start.flush({ jobId: 'job-1' });
      await settle(fixture);
      await vi.waitFor(() => http.expectOne('/api/jobs/job-1').flush({ id: 'job-1', status: 'DONE', done: 4, total: 4 }));
      await settle(fixture);
      expect(q(host, 'account.settings.export.download')).not.toBeNull();
      expect(jobResultUrl('job-1')).toBe('/api/jobs/job-1/result');
    });

    it('the 10-minute limit is shown below the button', async () => {
      const { fixture, host } = await mount(DataExportCardComponent);
      button(host, 'account.settings.export.start').click();
      http
        .expectOne('/api/users/me/export')
        .flush(apiHttpError(429, ApiErrorCode.TooManyRequests).error, { status: 429, statusText: 'Too Many Requests' });
      await settle(fixture);
      expect(q(host, 'account.settings.export.error')?.getAttribute('data-code')).toBe('TOO_MANY_REQUESTS');
      expect(q(host, 'account.settings.export.job')).toBeNull();
    });
  });

  describe('danger zone', () => {
    it('deleting needs the re-typed address, sends the reason and ends the session', async () => {
      const state = profileState();
      const { fixture, host } = await mount(DangerZoneCardComponent, { profile: state });
      http.expectOne('/api/users/me').flush(PROFILE);
      await settle(fixture);
      button(host, 'account.settings.danger.open').click();
      await settle(fixture);
      const confirm = () => document.querySelector('[data-testid="account.settings.danger.confirm"] button') as HTMLButtonElement;
      expect(confirm().disabled).toBe(true);
      const type = (id: string, value: string) => {
        const field = document.querySelector(`[data-testid="${id}"]`) as HTMLInputElement | HTMLTextAreaElement;
        field.value = value;
        field.dispatchEvent(new Event('input'));
      };
      type('account.settings.danger.reason', '  Ich ziehe weg  ');
      type('account.settings.danger.retype', 'foodsaver@loca');
      await settle(fixture);
      expect(confirm().disabled).toBe(true);
      type('account.settings.danger.retype', 'FOODSAVER@local');
      await settle(fixture);
      expect(confirm().disabled).toBe(false);
      confirm().click();
      const request = http.expectOne('/api/users/me');
      expect(request.request.method).toBe('DELETE');
      expect(request.request.body).toEqual({ reason: 'Ich ziehe weg' });
      request.flush(null, { status: 204, statusText: 'No Content' });
      await settle(fixture);
      expect(TestBed.inject(SessionService).isAuthenticated()).toBe(false);
    });
  });

  describe('bounce banner', () => {
    it('on its own (dashboard): hidden while the address is fine', async () => {
      const { fixture, host } = await mount(BounceBannerComponent);
      http.expectOne('/api/users/me').flush(PROFILE);
      await settle(fixture);
      expect(q(host, 'account.bounce.state')?.getAttribute('data-state')).toBe('empty');
      expect(host.classList.contains('shown')).toBe(false);
    });

    it('on its own: a bounced address shows the warning; clearing reloads the profile', async () => {
      const { fixture, host } = await mount(BounceBannerComponent);
      http.expectOne('/api/users/me').flush({ ...PROFILE, emailBouncedAt: '2026-11-01T10:00:00Z' });
      await settle(fixture);
      expect(host.classList.contains('shown')).toBe(true);
      expect(q(host, 'account.bounce.change')).not.toBeNull();
      button(host, 'account.bounce.clear').click();
      const clear = http.expectOne(`/api/users/${USER_ID}/email-bounce`);
      expect(clear.request.method).toBe('DELETE');
      clear.flush(null, { status: 204, statusText: 'No Content' });
      await settle(fixture);
      http.expectOne('/api/users/me').flush(PROFILE);
      await settle(fixture);
      expect(host.classList.contains('shown')).toBe(false);
    });

    it('on its own: a failed load offers a retry', async () => {
      const { fixture, host } = await mount(BounceBannerComponent);
      http.expectOne('/api/users/me').flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
      await settle(fixture);
      expect(q(host, 'account.bounce.state')?.getAttribute('data-state')).toBe('error');
      expect(host.classList.contains('shown')).toBe(true);
    });

    it("with the settings page's profile: no own request, no change link, loading and errors stay with the cards", async () => {
      const state = profileState();
      const { fixture, host } = await mount(BounceBannerComponent, { profile: state, showChangeLink: false });
      expect(host.classList.contains('shown')).toBe(false);
      http.expectOne('/api/users/me').flush({ ...PROFILE, emailBouncedAt: '2026-11-01T10:00:00Z' });
      await settle(fixture);
      expect(q(host, 'account.bounce.banner')).not.toBeNull();
      expect(q(host, 'account.bounce.change')).toBeNull();
      state.reload();
      http.expectOne('/api/users/me').flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
      await settle(fixture);
      expect(q(host, 'account.bounce.state')?.getAttribute('data-state')).toBe('empty');
    });
  });
});
