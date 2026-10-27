import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';

import { Theme, UsersService } from '../../api';
import { SessionService } from '../auth/session.service';
import { readStorage, writeStorage } from '../storage';

export type ThemePreference = 'light' | 'dark' | 'system';
export const THEME_PREFERENCES: readonly ThemePreference[] = ['light', 'dark', 'system'];
export const THEME_STORAGE_KEY = 'fs.theme';
/** Class on <html> that switches Optimus (darkModeSelector) and the app tokens to dark. */
export const DARK_CLASS = 'fs-dark';

export function isThemePreference(value: unknown): value is ThemePreference {
  return value === 'light' || value === 'dark' || value === 'system';
}

export function themeFromApi(theme: Theme | undefined | null): ThemePreference {
  return theme === Theme.Dark ? 'dark' : theme === Theme.Light ? 'light' : 'system';
}

export function themeToApi(preference: ThemePreference): Theme {
  return preference === 'dark' ? Theme.Dark : preference === 'light' ? Theme.Light : Theme.System;
}

/**
 * Light / dark / system theme. Stored per device in localStorage['fs.theme'] and, when logged in, as the
 * account setting. `system` follows `prefers-color-scheme` live.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly session = inject(SessionService);
  private readonly usersApi = inject(UsersService);

  readonly preference = signal<ThemePreference>(this.initialPreference());
  private readonly systemDark = signal(false);
  readonly effective = computed<'light' | 'dark'>(() =>
    this.preference() === 'system'
      ? this.systemDark()
        ? 'dark'
        : 'light'
      : (this.preference() as 'light' | 'dark'),
  );
  readonly isDark = computed(() => this.effective() === 'dark');

  constructor() {
    const media = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
    if (media) {
      this.systemDark.set(media.matches);
      const listener = (event: MediaQueryListEvent) => this.systemDark.set(event.matches);
      media.addEventListener?.('change', listener);
      inject(DestroyRef).onDestroy(() => media.removeEventListener?.('change', listener));
    }
    effect(() => this.apply(this.effective()));
    this.apply(this.effective());
  }

  setPreference(
    preference: ThemePreference,
    options: { persist?: boolean; remember?: boolean } = {},
  ): void {
    const { persist = true, remember = true } = options;
    this.preference.set(preference);
    this.apply(this.effective());
    if (remember) {
      writeStorage(THEME_STORAGE_KEY, preference);
    }
    const user = this.session.user();
    const theme = themeToApi(preference);
    if (persist && user && user.theme !== theme) {
      this.session.patchUser({ theme });
      this.usersApi
        .updateMySettings({ userSettingsUpdate: { theme } })
        .subscribe({ error: () => undefined });
    }
  }

  private apply(mode: 'light' | 'dark'): void {
    const root = this.document.documentElement;
    root.classList.toggle(DARK_CLASS, mode === 'dark');
    root.style.colorScheme = mode;
    const meta = this.document.querySelector('meta[name="theme-color"]');
    meta?.setAttribute('content', mode === 'dark' ? '#121712' : '#25834a');
  }

  private initialPreference(): ThemePreference {
    const stored = readStorage(THEME_STORAGE_KEY);
    return isThemePreference(stored) ? stored : 'system';
  }
}
