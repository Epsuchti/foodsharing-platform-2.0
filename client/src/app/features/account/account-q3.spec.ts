import { HttpTestingController } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { ApiErrorCode, Language, Role, SleepStatus, Theme, UserProfile, UsersService } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { DataState, loadResource } from '../../core/data/data-state';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { sessionInfo, settle } from '../auth/testing';
import {
  SleepForm,
  looksLikeEmail,
  sleepFormChanged,
  sleepFormError,
  sleepFormOf,
  sleepUpdate,
  todayIn,
} from './account.util';
import { EmailCardComponent } from './components/email-card.component';
import { PhotoCardComponent, photoFile } from './components/photo-card.component';
import { PreferencesCardComponent } from './components/preferences-card.component';
import { SleepModeCardComponent } from './components/sleep-mode-card.component';
import { EmailTokenPageComponent } from './pages/email-token-page.component';

const PROFILE: UserProfile = {
  id: '00000001-0001-4000-8000-000000000006',
  email: 'foodsaver@local',
  emailVerifiedAt: '2025-12-19T18:20:00Z',
  displayName: 'Sarah Müller',
  language: Language.De,
  theme: Theme.System,
  role: Role.Foodsaver,
  verified: true,
  registeredAt: '2025-12-19T18:18:00Z',
  infoMailMessage: true,
  mentionNotifications: true,
  sleepStatus: SleepStatus.None,
};

const form = (changes: Partial<SleepForm> = {}): SleepForm => ({
  status: SleepStatus.Temporary,
  from: '2026-11-05',
  until: '2026-11-20',
  message: '',
  ...changes,
});

describe('account util (Q3)', () => {
  it('sleep dates follow the server rule', () => {
    const today = '2026-11-02';
    expect(sleepFormError(form(), today)).toBeNull();
    expect(sleepFormError(form({ from: '' }), today)).toBe('account.sleep.datesRequired');
    expect(sleepFormError(form({ until: '' }), today)).toBe('account.sleep.datesRequired');
    expect(sleepFormError(form({ from: '2026-11-21' }), today)).toBe('account.sleep.datesOrder');
    expect(sleepFormError(form({ from: '2026-10-01', until: '2026-11-01' }), today)).toBe('account.sleep.datesPast');
    // a past start is fine while the end is today or later
    expect(sleepFormError(form({ from: '2026-10-01', until: today }), today)).toBeNull();
    expect(sleepFormError(form({ status: SleepStatus.Full, from: '', until: '' }), today)).toBeNull();
    expect(sleepFormError(form({ status: SleepStatus.None, message: 'x'.repeat(1001) }), today)).toBe(
      'account.sleep.messageTooLong',
    );
  });

  it('sleep update sends dates only for TEMPORARY and drops a blank message', () => {
    expect(sleepUpdate(form({ message: '  Ferien  ' }))).toEqual({
      status: SleepStatus.Temporary,
      from: '2026-11-05',
      until: '2026-11-20',
      message: 'Ferien',
    });
    expect(sleepUpdate(form({ status: SleepStatus.Full, message: ' ' }))).toEqual({ status: SleepStatus.Full });
  });

  it('sleep form change detection ignores dates outside TEMPORARY', () => {
    const stored = sleepFormOf(PROFILE);
    expect(stored).toEqual({ status: SleepStatus.None, from: '', until: '', message: '' });
    expect(sleepFormChanged(stored, stored)).toBe(false);
    expect(sleepFormChanged({ ...stored, from: '2026-11-05' }, stored)).toBe(false);
    expect(sleepFormChanged({ ...stored, status: SleepStatus.Full }, stored)).toBe(true);
    expect(sleepFormChanged(form(), { ...form(), until: '2026-11-21' })).toBe(true);
  });

  it('today in Zurich and email shape', () => {
    expect(todayIn('Europe/Zurich', new Date('2026-11-01T23:30:00Z'))).toBe('2026-11-02');
    expect(looksLikeEmail('noah@local')).toBe(true);
    expect(looksLikeEmail('noah')).toBe(false);
    expect(looksLikeEmail('a b@local')).toBe(false);
  });

  it('the cropped blob is uploaded as a named JPEG', () => {
    const file = photoFile(new Blob(['x'], { type: 'image/jpeg' }));
    expect(file.name).toBe('profile-photo.jpg');
    expect(file.type).toBe('image/jpeg');
  });
});

describe('account settings cards (Q3)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).applySession(sessionInfo());
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  function profileState(): DataState<UserProfile> {
    const usersApi = TestBed.inject(UsersService);
    return TestBed.runInInjectionContext(() =>
      loadResource<UserProfile>({ loader: () => usersApi.getCurrentUser(), isEmpty: () => false }),
    );
  }

  async function mount<T>(type: Type<T>): Promise<{ fixture: ComponentFixture<T>; host: HTMLElement; state: DataState<UserProfile> }> {
    const state = profileState();
    const fixture = TestBed.createComponent(type);
    fixture.componentRef.setInput('profile', state);
    await fixture.whenStable();
    return { fixture, host: fixture.nativeElement as HTMLElement, state };
  }

  const q = (host: HTMLElement, id: string) => host.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;

  describe('photo', () => {
    it('loading, then the initials and "Add photo"; error with retry', async () => {
      const { fixture, host } = await mount(PhotoCardComponent);
      expect(q(host, 'account.settings.photo.state')?.getAttribute('data-state')).toBe('loading');
      http.expectOne('/api/users/me').flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
      await settle(fixture);
      expect(q(host, 'account.settings.photo.state')?.getAttribute('data-state')).toBe('error');
      const retry = q(host, 'account.settings.photo.state.retry')!;
      (retry.querySelector('button') ?? retry).click();
      await settle(fixture);
      http.expectOne('/api/users/me').flush(PROFILE);
      await settle(fixture);
      expect(q(host, 'account.settings.photo.avatar')?.getAttribute('data-has-photo')).toBe('false');
      expect(q(host, 'account.settings.photo.remove')).toBeNull();
    });

    it('uploads the cropped picture, sets it as photo and updates the header avatar', async () => {
      const { fixture, host } = await mount(PhotoCardComponent);
      http.expectOne('/api/users/me').flush(PROFILE);
      await settle(fixture);
      const card = fixture.componentInstance as unknown as { upload: { run(blob: Blob): Promise<boolean> } };
      const done = card.upload.run(new Blob(['jpeg'], { type: 'image/jpeg' }));
      const upload = http.expectOne('/api/uploads');
      expect(upload.request.method).toBe('POST');
      expect((upload.request.body as FormData).get('file')).toBeInstanceOf(File);
      upload.flush({ id: 'up-1', fileName: 'profile-photo.jpg', mimeType: 'image/jpeg', size: 4, createdAt: '2026-11-02T09:00:00Z' });
      const set = http.expectOne('/api/users/me/photo');
      expect(set.request.method).toBe('PUT');
      expect(set.request.body).toEqual({ uploadId: 'up-1' });
      set.flush({ photoUrl: '/api/uploads/up-1?size=md' });
      expect(await done).toBe(true);
      http.expectOne('/api/users/me').flush({ ...PROFILE, avatarUrl: '/api/uploads/up-1?size=md' });
      await settle(fixture);
      expect(TestBed.inject(SessionService).user()?.avatarUrl).toBe('/api/uploads/up-1?size=md');
      expect(q(host, 'account.settings.photo.avatar')?.getAttribute('data-has-photo')).toBe('true');
      expect(q(host, 'account.settings.photo.remove')).not.toBeNull();
    });
  });

  describe('sleep mode', () => {
    it('loading, then the current state; TEMPORARY needs valid dates before saving', async () => {
      const { fixture, host } = await mount(SleepModeCardComponent);
      fixture.componentRef.setInput('now', new Date('2026-11-02T09:00:00Z'));
      expect(q(host, 'account.settings.sleep.state')?.getAttribute('data-state')).toBe('loading');
      http.expectOne('/api/users/me').flush(PROFILE);
      await settle(fixture);
      expect(q(host, 'account.settings.sleep.current')?.getAttribute('data-status')).toBe('NONE');
      const save = () => host.querySelector('[data-testid="account.settings.sleep.save"] button') as HTMLButtonElement;
      expect(save().disabled).toBe(true);

      q(host, 'account.settings.sleep.status.TEMPORARY')!.click();
      await settle(fixture);
      expect(q(host, 'account.settings.sleep.problem')?.textContent).toContain('account.sleep.datesRequired');
      expect(save().disabled).toBe(true);

      const set = (id: string, value: string) => {
        const input = q(host, id) as HTMLInputElement;
        input.value = value;
        input.dispatchEvent(new Event('input'));
      };
      set('account.settings.sleep.from', '2026-11-05');
      set('account.settings.sleep.until', '2026-11-20');
      set('account.settings.sleep.message', 'Ferien im Tessin');
      await settle(fixture);
      expect(q(host, 'account.settings.sleep.problem')).toBeNull();
      expect(save().disabled).toBe(false);
      save().click();
      const request = http.expectOne('/api/users/me/sleep-mode');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ status: 'TEMPORARY', from: '2026-11-05', until: '2026-11-20', message: 'Ferien im Tessin' });
      request.flush({ id: PROFILE.id, displayName: PROFILE.displayName, role: Role.Foodsaver, verified: true, sleepMode: false });
      await settle(fixture);
      http.expectOne('/api/users/me').flush({ ...PROFILE, sleepStatus: SleepStatus.Temporary, sleepFrom: '2026-11-05', sleepUntil: '2026-11-20' });
      await settle(fixture);
      expect(q(host, 'account.settings.sleep.current')?.getAttribute('data-status')).toBe('TEMPORARY');
      expect(save().disabled).toBe(true);
    });

    it('server date errors are shown as toast and the form keeps its values', async () => {
      const { fixture, host } = await mount(SleepModeCardComponent);
      http.expectOne('/api/users/me').flush(PROFILE);
      await settle(fixture);
      q(host, 'account.settings.sleep.status.FULL')!.click();
      await settle(fixture);
      (host.querySelector('[data-testid="account.settings.sleep.save"] button') as HTMLButtonElement).click();
      http
        .expectOne('/api/users/me/sleep-mode')
        .flush(apiHttpError(400, ApiErrorCode.SleepDatesInvalid).error, { status: 400, statusText: 'Bad Request' });
      await settle(fixture);
      expect(q(host, 'account.settings.sleep.status.FULL')?.getAttribute('aria-checked')).toBe('true');
    });
  });

  describe('email', () => {
    it('shows the current address and the pending change; cancelling it asks first', async () => {
      const { fixture, host } = await mount(EmailCardComponent);
      http.expectOne('/api/users/me').flush(PROFILE);
      http.expectOne('/api/users/me/email').flush({ pendingEmail: 'neu@local', requestedAt: '2026-11-02T08:00:00Z' });
      await settle(fixture);
      expect(q(host, 'account.settings.email.current')?.textContent?.trim()).toBe('foodsaver@local');
      expect(q(host, 'account.settings.email.pending')?.getAttribute('data-email')).toBe('neu@local');
      const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
      (host.querySelector('[data-testid="account.settings.email.abort"] button') as HTMLButtonElement).click();
      await settle(fixture);
      expect(confirm).toHaveBeenCalledTimes(1);
      expect(http.match('/api/users/me/email').length).toBe(0);
      confirm.mock.calls[0][0].accept!();
      const request = http.expectOne('/api/users/me/email');
      expect(request.request.method).toBe('DELETE');
      request.flush(null, { status: 204, statusText: 'No Content' });
      await settle(fixture);
      http.expectOne('/api/users/me/email').flush({});
      await settle(fixture);
      expect(q(host, 'account.settings.email.pending')).toBeNull();
    });

    it('requests a change; EMAIL_TAKEN is shown below the field', async () => {
      const { fixture, host } = await mount(EmailCardComponent);
      http.expectOne('/api/users/me').flush(PROFILE);
      http.expectOne('/api/users/me/email').flush({});
      await settle(fixture);
      expect(q(host, 'account.settings.email.pendingState')?.getAttribute('data-state')).toBe('empty');
      const input = q(host, 'account.settings.email.new') as HTMLInputElement;
      const submit = () => host.querySelector('[data-testid="account.settings.email.submit"] button') as HTMLButtonElement;
      input.value = 'kein-at';
      input.dispatchEvent(new Event('input'));
      await settle(fixture);
      expect(submit().disabled).toBe(true);

      input.value = 'jumper@local';
      input.dispatchEvent(new Event('input'));
      await settle(fixture);
      submit().click();
      const taken = http.expectOne('/api/users/me/email');
      expect(taken.request.method).toBe('POST');
      expect(taken.request.body).toEqual({ newEmail: 'jumper@local' });
      taken.flush(apiHttpError(409, ApiErrorCode.EmailTaken).error, { status: 409, statusText: 'Conflict' });
      await settle(fixture);
      expect(q(host, 'account.settings.email.error')?.getAttribute('data-code')).toBe('EMAIL_TAKEN');

      input.value = 'neu@local';
      input.dispatchEvent(new Event('input'));
      await settle(fixture);
      submit().click();
      http.expectOne((r) => r.method === 'POST' && r.url === '/api/users/me/email').flush(null, { status: 202, statusText: 'Accepted' });
      await settle(fixture);
      expect(q(host, 'account.settings.email.error')).toBeNull();
      expect(input.value).toBe('');
      http.expectOne('/api/users/me/email').flush({ pendingEmail: 'neu@local', requestedAt: '2026-11-02T09:00:00Z' });
      await settle(fixture);
      expect(q(host, 'account.settings.email.pending')).not.toBeNull();
    });
  });

  describe('preferences mail switches', () => {
    it('switching info mails off saves infoMailMessage=false and reloads the profile', async () => {
      const { fixture, host } = await mount(PreferencesCardComponent);
      http.expectOne('/api/users/me').flush(PROFILE);
      await settle(fixture);
      const checkbox = host.querySelector('[data-testid="account.settings.infoMail"] input') as HTMLInputElement;
      expect(checkbox.checked).toBe(true);
      checkbox.click();
      const request = await vi.waitFor(() => http.expectOne('/api/users/me/settings'), { timeout: 1000 });
      expect(request.request.body).toEqual({ infoMailMessage: false });
      request.flush({ ...PROFILE, infoMailMessage: false });
      await settle(fixture);
      http.expectOne('/api/users/me').flush({ ...PROFILE, infoMailMessage: false });
      await settle(fixture);
      expect((host.querySelector('[data-testid="account.settings.infoMail"] input') as HTMLInputElement).checked).toBe(false);
    });

    it('without a profile only language and theme are shown', async () => {
      const fixture = TestBed.createComponent(PreferencesCardComponent);
      await fixture.whenStable();
      expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="account.settings.mailSwitches"]')).toBeNull();
    });
  });
});

describe('email change links (Q3)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (id: string) => harness.routeNativeElement!.querySelector(`[data-testid="${id}"]`);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting(),
        provideRouter(
          [
            { path: 'account/email/confirm', component: EmailTokenPageComponent, data: { emailToken: 'confirm' } },
            { path: 'account/email/abort', component: EmailTokenPageComponent, data: { emailToken: 'abort' } },
          ],
          withComponentInputBinding(),
        ),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).applySession(sessionInfo());
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('confirm: changes the address and shows the new one', async () => {
    await harness.navigateByUrl('/account/email/confirm?token=abc');
    expect(q('account.email.confirm.state')?.getAttribute('data-state')).toBe('loading');
    const confirm = http.expectOne('/api/users/me/email/confirm');
    expect(confirm.request.body).toEqual({ token: 'abc' });
    confirm.flush({ id: PROFILE.id, displayName: PROFILE.displayName, role: Role.Foodsaver, verified: true });
    http.expectOne('/api/users/me').flush({ ...PROFILE, email: 'neu@local' });
    await harness.fixture.whenStable();
    expect(q('account.email.confirm.success')).not.toBeNull();
    expect(TestBed.inject(SessionService).user()?.email).toBe('neu@local');
  });

  it('confirm: an invalid token shows the link error without retry', async () => {
    await harness.navigateByUrl('/account/email/confirm?token=old');
    http
      .expectOne('/api/users/me/email/confirm')
      .flush(apiHttpError(400, ApiErrorCode.EmailChangeTokenInvalid).error, { status: 400, statusText: 'Bad Request' });
    await harness.fixture.whenStable();
    expect(q('account.email.confirm.tokenError')).not.toBeNull();
  });

  it('confirm: other errors offer a retry', async () => {
    await harness.navigateByUrl('/account/email/confirm?token=abc');
    http
      .expectOne('/api/users/me/email/confirm')
      .flush(apiHttpError(503, ApiErrorCode.UnexpectedError).error, { status: 503, statusText: 'Unavailable' });
    await harness.fixture.whenStable();
    expect(q('account.email.confirm.state')?.getAttribute('data-state')).toBe('error');
    expect(q('account.email.confirm.tokenError')).toBeNull();
  });

  it('abort: public call with the token', async () => {
    await harness.navigateByUrl('/account/email/abort?token=xyz');
    const abort = http.expectOne('/api/public/users/email-change/abort');
    expect(abort.request.body).toEqual({ token: 'xyz' });
    abort.flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();
    expect(q('account.email.abort.success')).not.toBeNull();
  });

  it('abort: a link without token shows the link error', async () => {
    await harness.navigateByUrl('/account/email/abort');
    await harness.fixture.whenStable();
    expect(q('account.email.abort.tokenError')).not.toBeNull();
  });
});
