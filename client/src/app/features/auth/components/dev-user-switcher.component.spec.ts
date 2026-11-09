import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { DevUser, Role } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { sessionInfo, settle } from '../testing';
import { DevUserSwitcherComponent } from './dev-user-switcher.component';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class BlankComponent {}

const USERS: DevUser[] = [
  {
    id: '00000001-0001-4000-8000-000000000001',
    email: 'newbie@local',
    displayName: 'Lea Brunner',
    role: Role.Foodsharer,
    descriptionKey: 'dev.users.newbie',
  },
  {
    id: '00000001-0001-4000-8000-000000000014',
    email: 'orga@local',
    displayName: 'Daniel Huber',
    role: Role.Orga,
    descriptionKey: 'dev.users.orga',
  },
];

describe('DevUserSwitcherComponent', () => {
  let fixture: ComponentFixture<DevUserSwitcherComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'dev.users.newbie': 'Gerade registriert' },
          routes: [
            { path: 'dashboard', component: BlankComponent },
            { path: 'account/sessions', component: BlankComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DevUserSwitcherComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const state = () => host.querySelector('[data-testid="auth.login.devSwitcher"]')!;

  it('shows a skeleton while loading, then one button per seed user', async () => {
    expect(state().getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/dev/users').flush(USERS);
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
    expect(
      host.querySelector('[data-testid="auth.login.devSwitcher.newbie@local"]')?.textContent,
    ).toContain('Gerade registriert');
    expect(host.querySelector('[data-testid="auth.login.devSwitcher.orga@local"]')).not.toBeNull();
  });

  it('shows the empty state', async () => {
    http.expectOne('/api/dev/users').flush([]);
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('empty');
  });

  it('shows the error with retry and recovers', async () => {
    http
      .expectOne('/api/dev/users')
      .flush(
        { code: 'UNEXPECTED_ERROR', message: 'boom', traceId: 'abc', retryable: true },
        { status: 500, statusText: 'Server Error' },
      );
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('error');
    (
      state().querySelector('[data-testid="auth.login.devSwitcher.retry"] button') as HTMLElement
    ).click();
    http.expectOne('/api/dev/users').flush(USERS);
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
  });

  it('logs in as the clicked user and goes to the return URL', async () => {
    fixture.componentRef.setInput('returnUrl', '/account/sessions');
    http.expectOne('/api/dev/users').flush(USERS);
    await fixture.whenStable();
    (
      host.querySelector('[data-testid="auth.login.devSwitcher.orga@local"]') as HTMLElement
    ).click();
    const login = http.expectOne('/api/dev/login-as');
    expect(login.request.body).toEqual({ userId: USERS[1].id });
    login.flush(sessionInfo({ id: USERS[1].id, email: 'orga@local', role: Role.Orga }));
    await settle(fixture);
    expect(TestBed.inject(SessionService).user()?.email).toBe('orga@local');
    expect(TestBed.inject(Router).url).toBe('/account/sessions');
  });

  it('stays on the dev API and shows no password field in dev mode', async () => {
    expect(host.querySelector('[data-testid="auth.login.personaPanel.password"]')).toBeNull();
    expect(host.querySelector('.dev__badge')?.textContent).toContain('Dev');
    http.expectOne('/api/dev/users').flush(USERS);
    await fixture.whenStable();
  });
});

describe('DevUserSwitcherComponent in persona mode (test environment)', () => {
  let fixture: ComponentFixture<DevUserSwitcherComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'auth.personaPanel.badge': 'Testkonten',
            'auth.personaPanel.notice': 'Testseite: keine echten Daten',
            'errors.PERSONA_PANEL_PASSWORD_INVALID': 'Das Passwort stimmt nicht.',
            'errors.TOO_MANY_REQUESTS': 'Zu viele Anfragen ({seconds})',
          },
          routes: [
            { path: 'dashboard', component: BlankComponent },
            { path: 'account/sessions', component: BlankComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DevUserSwitcherComponent);
    fixture.componentRef.setInput('mode', 'persona');
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const byTestId = (id: string) => host.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  function stored(storage: Storage): string {
    let all = '';
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i)!;
      all += `${key}=${storage.getItem(key)};`;
    }
    return all;
  }

  async function unlockWith(password: string): Promise<void> {
    const input = byTestId('auth.login.personaPanel.password') as HTMLInputElement;
    input.value = password;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('asks for the password first and loads nothing before', async () => {
    http.expectNone('/api/dev/users');
    http.expectNone('/api/personas/list');
    expect(byTestId('auth.login.personaPanel.password')).not.toBeNull();
    expect(byTestId('auth.login.personaPanel')).toBeNull();
    expect(host.querySelector('.dev__badge')?.textContent).toContain('Testkonten');
    expect(byTestId('auth.login.personaPanel.notice')?.textContent).toContain(
      'Testseite: keine echten Daten',
    );
    const unlock = byTestId('auth.login.personaPanel.unlock')?.querySelector('button');
    expect(unlock?.disabled).toBe(true);
  });

  it('unlocks with the password, lists the personas and logs in with the password', async () => {
    fixture.componentRef.setInput('returnUrl', '/account/sessions');
    await unlockWith('foobar');
    const list = http.expectOne('/api/personas/list');
    expect(list.request.method).toBe('POST');
    expect(list.request.body).toEqual({ password: 'foobar' });
    expect(byTestId('auth.login.personaPanel')?.getAttribute('data-state')).toBe('loading');
    list.flush(USERS);
    await fixture.whenStable();
    expect(byTestId('auth.login.personaPanel')?.getAttribute('data-state')).toBe('success');
    expect(byTestId('auth.login.personaPanel.password')).toBeNull();

    byTestId('auth.login.personaPanel.orga@local')!.click();
    const login = http.expectOne('/api/personas/login');
    expect(login.request.body).toEqual({ password: 'foobar', userId: USERS[1].id });
    login.flush(sessionInfo({ id: USERS[1].id, email: 'orga@local', role: Role.Orga }));
    await settle(fixture);
    expect(TestBed.inject(SessionService).user()?.email).toBe('orga@local');
    expect(TestBed.inject(Router).url).toBe('/account/sessions');
    // the password is never stored in the browser
    expect(stored(localStorage)).not.toContain('foobar');
    expect(stored(sessionStorage)).not.toContain('foobar');
  });

  it('shows a wrong password at the field and lets the user try again', async () => {
    await unlockWith('nope');
    http
      .expectOne('/api/personas/list')
      .flush(
        { code: 'PERSONA_PANEL_PASSWORD_INVALID', message: 'wrong', traceId: 'abc', retryable: false },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();
    expect(byTestId('auth.login.personaPanel.error')?.textContent).toContain(
      'Das Passwort stimmt nicht.',
    );
    expect(byTestId('auth.login.personaPanel')).toBeNull();

    // the same password again is sent again
    await unlockWith('nope');
    http
      .expectOne('/api/personas/list')
      .flush(
        { code: 'PERSONA_PANEL_PASSWORD_INVALID', message: 'wrong', traceId: 'abc', retryable: false },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();

    await unlockWith('foobar');
    http.expectOne('/api/personas/list').flush(USERS);
    await fixture.whenStable();
    expect(byTestId('auth.login.personaPanel.error')).toBeNull();
    expect(byTestId('auth.login.personaPanel')?.getAttribute('data-state')).toBe('success');
  });

  it('shows the lock-out with its wait time', async () => {
    await unlockWith('nope');
    http.expectOne('/api/personas/list').flush(
      { code: 'TOO_MANY_REQUESTS', message: 'slow down', traceId: 'abc', retryable: true },
      {
        status: 429,
        statusText: 'Too Many Requests',
        headers: { 'Retry-After': '600' },
      },
    );
    await fixture.whenStable();
    expect(byTestId('auth.login.personaPanel.error')?.textContent).toContain(
      'Zu viele Anfragen (600)',
    );
  });

  it('shows other failures in the list with retry', async () => {
    await unlockWith('foobar');
    http
      .expectOne('/api/personas/list')
      .flush(
        { code: 'UNEXPECTED_ERROR', message: 'boom', traceId: 'abc', retryable: true },
        { status: 500, statusText: 'Server Error' },
      );
    await fixture.whenStable();
    const state = byTestId('auth.login.personaPanel')!;
    expect(state.getAttribute('data-state')).toBe('error');
    (state.querySelector('[data-testid="auth.login.personaPanel.retry"] button') as HTMLElement).click();
    const retry = http.expectOne('/api/personas/list');
    expect(retry.request.body).toEqual({ password: 'foobar' });
    retry.flush([]);
    await fixture.whenStable();
    expect(state.getAttribute('data-state')).toBe('empty');
  });
});
