import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { Language, Role, Theme } from '../../api';
import { provideApi } from '../../api/provide-api';
import { SessionService } from '../auth/session.service';
import {
  DARK_CLASS,
  THEME_STORAGE_KEY,
  ThemeService,
  themeFromApi,
  themeToApi,
} from './theme.service';

describe('ThemeService', () => {
  let listeners: ((event: MediaQueryListEvent) => void)[];
  let systemDark: boolean;

  function setup(): ThemeService {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi(''),
      ],
    });
    return TestBed.inject(ThemeService);
  }

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove(DARK_CLASS);
    listeners = [];
    systemDark = false;
    vi.spyOn(window, 'matchMedia').mockImplementation(
      (query: string) =>
        ({
          get matches() {
            return systemDark;
          },
          media: query,
          addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) =>
            listeners.push(listener),
          removeEventListener: () => undefined,
        }) as unknown as MediaQueryList,
    );
  });

  afterEach(() => vi.restoreAllMocks());

  it('defaults to system and follows prefers-color-scheme live', () => {
    systemDark = true;
    const theme = setup();
    expect(theme.preference()).toBe('system');
    expect(theme.effective()).toBe('dark');
    expect(document.documentElement.classList.contains(DARK_CLASS)).toBe(true);

    listeners.forEach((listener) => listener({ matches: false } as MediaQueryListEvent));
    TestBed.tick();
    expect(theme.effective()).toBe('light');
    expect(document.documentElement.classList.contains(DARK_CLASS)).toBe(false);
  });

  it('applies and remembers an explicit preference', () => {
    const theme = setup();
    theme.setPreference('dark');
    expect(document.documentElement.classList.contains(DARK_CLASS)).toBe(true);
    expect(document.documentElement.style.colorScheme).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');

    theme.setPreference('light');
    expect(document.documentElement.classList.contains(DARK_CLASS)).toBe(false);
  });

  it('restores the stored preference', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    expect(setup().preference()).toBe('dark');
  });

  it('saves the preference to the account when logged in', () => {
    const theme = setup();
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: 'u1',
        email: 'a@b.ch',
        displayName: 'Anna',
        role: Role.Foodsaver,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });
    theme.setPreference('dark');
    const backend = TestBed.inject(HttpTestingController);
    const patch = backend.expectOne('/api/users/me/settings');
    expect(patch.request.body).toEqual({ theme: 'DARK' });
    patch.flush({});

    theme.setPreference('dark'); // unchanged → no second request
    theme.setPreference('light', { persist: false });
    backend.verify();
  });

  it('maps between API and UI values', () => {
    expect(themeFromApi(Theme.Dark)).toBe('dark');
    expect(themeFromApi(undefined)).toBe('system');
    expect(themeToApi('light')).toBe(Theme.Light);
  });
});
