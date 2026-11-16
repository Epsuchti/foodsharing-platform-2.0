import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { afterEach } from 'vitest';

import { Language, Role, Theme } from '../../api';
import { provideDataStateTesting } from '../testing/data-state-testing';
import { authGuard } from './auth.guard';
import { SessionService } from './session.service';

/**
 * A2: a failed session bootstrap (server error, no connection) must not block the start. The app comes up logged
 * out, a protected deep link asks for the login with its return URL, and the next start (reload) loads the session
 * again. Moved from the removed e2e spec auth-error-states (decision 180 #5).
 */
describe('SessionService.bootstrap (A2)', () => {
  let http: HttpTestingController;
  let session: SessionService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideDataStateTesting() });
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
  });

  afterEach(() => http.verify());

  const sessionRequest = () => http.expectOne((request) => request.url.endsWith('/api/auth/session'));

  function guard(url: string): boolean | UrlTree {
    return TestBed.runInInjectionContext(
      () => authGuard({ data: {} } as unknown as ActivatedRouteSnapshot, { url } as RouterStateSnapshot) as boolean | UrlTree,
    );
  }

  it('falls back to logged out on a server error; the guard asks for the login with the return URL', async () => {
    const started = session.bootstrap();
    sessionRequest().flush({ status: 500, code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Server Error' });
    expect(await started).toEqual({ authenticated: false, permissions: [] });
    expect(session.loaded()).toBe(true);
    expect(session.isAuthenticated()).toBe(false);

    const redirect = guard('/account/sessions') as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(redirect)).toBe('/login?returnUrl=%2Faccount%2Fsessions');
  });

  it('falls back to logged out without a connection and loads the session on the next bootstrap', async () => {
    const offline = session.bootstrap();
    sessionRequest().error(new ProgressEvent('error'));
    await offline;
    expect(session.isAuthenticated()).toBe(false);

    const reload = session.bootstrap();
    sessionRequest().flush({
      authenticated: true,
      permissions: [],
      user: {
        id: 'u1',
        email: 'a@local',
        displayName: 'Anna',
        role: Role.Foodsaver,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });
    await reload;
    expect(session.isAuthenticated()).toBe(true);
    expect(guard('/account/sessions')).toBe(true);
  });
});
