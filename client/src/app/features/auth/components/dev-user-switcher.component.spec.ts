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
});
