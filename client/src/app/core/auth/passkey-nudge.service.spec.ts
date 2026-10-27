import { TestBed } from '@angular/core/testing';
import { afterEach, vi } from 'vitest';

import { Language, Role, Theme } from '../../api';
import { AppInfoService } from '../app-info.service';
import { provideDataStateTesting } from '../testing/data-state-testing';
import {
  LOGIN_PENDING_STORAGE_KEY,
  PASSKEY_NUDGE_STORAGE_KEY,
  PasskeyNudgeService,
} from './passkey-nudge.service';
import { SessionService } from './session.service';
import { WebAuthnClient } from './webauthn.client';

describe('PasskeyNudgeService', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: provideDataStateTesting() });
    vi.spyOn(TestBed.inject(WebAuthnClient), 'isSupported').mockReturnValue(true);
    TestBed.inject(AppInfoService).info.set({
      version: 't',
      devMode: false,
      languages: [],
      loginMethods: { google: true, magicLink: true, passkeys: true },
      serverTime: '2026-11-02T09:00:00Z',
    });
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: 'u1',
        email: 'a@local',
        displayName: 'A',
        role: Role.Foodsharer,
        language: Language.De,
        theme: Theme.System,
        verified: false,
      },
    });
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('shows after an email or Google login, not after passkey or dev logins', () => {
    const nudge = TestBed.inject(PasskeyNudgeService);
    expect(nudge.visible()).toBe(false);
    nudge.loggedIn('DEV');
    expect(nudge.visible()).toBe(false);
    nudge.loggedIn('PASSKEY');
    expect(nudge.visible()).toBe(false);
    nudge.loggedIn('EMAIL');
    expect(nudge.visible()).toBe(true);
  });

  it('is answered once per device', () => {
    const nudge = TestBed.inject(PasskeyNudgeService);
    nudge.loggedIn('GOOGLE');
    nudge.dismiss();
    expect(nudge.visible()).toBe(false);
    expect(localStorage.getItem(PASSKEY_NUDGE_STORAGE_KEY)).toBe('dismissed');
    nudge.loggedIn('EMAIL');
    expect(nudge.visible()).toBe(false);
  });

  it('survives the Google redirect via sessionStorage', () => {
    sessionStorage.setItem(LOGIN_PENDING_STORAGE_KEY, 'GOOGLE');
    const nudge = TestBed.inject(PasskeyNudgeService);
    expect(nudge.visible()).toBe(true);
    expect(sessionStorage.getItem(LOGIN_PENDING_STORAGE_KEY)).toBeNull();
  });
});
