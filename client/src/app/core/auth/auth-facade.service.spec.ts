import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, vi } from 'vitest';

import { AuthService, Language, Role, SessionInfo, Theme } from '../../api';
import { provideDataStateTesting } from '../testing/data-state-testing';
import { AuthFacadeService, REFRESH_THROTTLE_MS } from './auth-facade.service';
import { SessionService } from './session.service';

function session(role: Role): SessionInfo {
  return {
    authenticated: true,
    permissions: [],
    user: {
      id: 'u1',
      email: 'newbie@local',
      displayName: 'Lea',
      role,
      language: Language.De,
      theme: Theme.Light,
      verified: false,
    },
  };
}

/**
 * A2 session refresh after a role change (moved from the e2e spec a2-login, decision 180): when the tab becomes
 * visible again after more than the throttle, the session is reloaded, so a role given meanwhile applies without
 * logging out; tab switches within the throttle do not ask the server again.
 */
describe('AuthFacadeService session refresh', () => {
  let getSession: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-02T09:00:00Z'));
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    TestBed.configureTestingModule({ providers: provideDataStateTesting() });
    TestBed.inject(SessionService).applySession(session(Role.Foodsharer));
    getSession = vi.fn(() => of(session(Role.Orga)));
    vi.spyOn(TestBed.inject(AuthService), 'getSession').mockImplementation(getSession as never);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  const tabShown = () => document.dispatchEvent(new Event('visibilitychange'));

  it('reloads the session when the tab is shown after the throttle and applies the new role', () => {
    const facade = TestBed.inject(AuthFacadeService);
    const sessions = TestBed.inject(SessionService);
    facade.startSessionRefresh();

    vi.setSystemTime(Date.now() + REFRESH_THROTTLE_MS / 2);
    tabShown();
    expect(getSession).not.toHaveBeenCalled();
    expect(sessions.role()).toBe(Role.Foodsharer);

    vi.setSystemTime(Date.now() + REFRESH_THROTTLE_MS);
    tabShown();
    expect(getSession).toHaveBeenCalledTimes(1);
    expect(sessions.role()).toBe(Role.Orga);

    // right after a refresh the next tab switch stays local
    tabShown();
    expect(getSession).toHaveBeenCalledTimes(1);
  });

  it('a forced refresh (after a 403) skips the throttle', () => {
    const facade = TestBed.inject(AuthFacadeService);
    facade.startSessionRefresh();
    facade.refreshSession(true);
    expect(getSession).toHaveBeenCalledTimes(1);
    expect(TestBed.inject(SessionService).role()).toBe(Role.Orga);
  });
});
