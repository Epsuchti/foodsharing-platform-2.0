import { TestBed } from '@angular/core/testing';

import { AppInfo, Language, Role, SessionInfo, Theme } from '../../api';
import { AppInfoService } from '../../core/app-info.service';

/** Shared fixtures for the auth/account specs. */
export const APP_INFO: AppInfo = {
  version: '1.0.0-test',
  devMode: false,
  languages: [],
  loginMethods: { google: true, magicLink: true, passkeys: true },
  serverTime: '2026-11-02T09:00:00Z',
};

export function sessionInfo(overrides: Partial<SessionInfo['user']> = {}): SessionInfo {
  return {
    authenticated: true,
    permissions: [],
    sessionId: 'session-1',
    user: {
      id: '00000001-0001-4000-8000-000000000006',
      email: 'foodsaver@local',
      displayName: 'Sarah Müller',
      role: Role.Foodsaver,
      language: Language.De,
      theme: Theme.System,
      verified: true,
      ...overrides,
    },
  };
}

export function useAppInfo(overrides: Partial<AppInfo> = {}): void {
  TestBed.inject(AppInfoService).info.set({ ...APP_INFO, ...overrides });
}

/** Lets pending promises (mutation callbacks, router navigation) settle. */
export async function settle(fixture: { whenStable(): Promise<unknown> }): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve));
  await fixture.whenStable();
}
