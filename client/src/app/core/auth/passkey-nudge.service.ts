import { Injectable, computed, inject, signal } from '@angular/core';

import { AppInfoService } from '../app-info.service';
import { readStorage, writeStorage } from '../storage';
import { SessionService } from './session.service';
import { WebAuthnClient } from './webauthn.client';

/** localStorage: 'dismissed' or 'accepted' once the nudge was answered on this device. */
export const PASSKEY_NUDGE_STORAGE_KEY = 'fs.passkeyNudge';
/** sessionStorage: set before the full-page Google redirect so the nudge can follow the round trip. */
export const LOGIN_PENDING_STORAGE_KEY = 'fs.loginPending';

export type LoginMethodName = 'GOOGLE' | 'EMAIL' | 'PASSKEY' | 'DEV';

function readSession(key: string): string | null {
  try {
    return globalThis.sessionStorage?.getItem(key) ?? null;
  } catch {
    return null;
  }
}

function writeSession(key: string, value: string | null): void {
  try {
    if (value === null) {
      globalThis.sessionStorage?.removeItem(key);
    } else {
      globalThis.sessionStorage?.setItem(key, value);
    }
  } catch {
    // storage unavailable: no nudge after the redirect
  }
}

/**
 * "Log in faster next time" (plan §8.1): after a login with Google or a magic link, offer once per device to
 * create a passkey. Not after passkey or dev-switcher logins, not when the browser has no WebAuthn, not when the
 * server has passkeys disabled.
 */
@Injectable({ providedIn: 'root' })
export class PasskeyNudgeService {
  private readonly session = inject(SessionService);
  private readonly appInfo = inject(AppInfoService);
  private readonly webauthn = inject(WebAuthnClient);

  private readonly pending = signal(readSession(LOGIN_PENDING_STORAGE_KEY) !== null);
  private readonly answered = signal(readStorage(PASSKEY_NUDGE_STORAGE_KEY) !== null);

  readonly visible = computed(
    () =>
      this.pending() &&
      !this.answered() &&
      this.session.isAuthenticated() &&
      (this.appInfo.info()?.loginMethods.passkeys ?? false) &&
      this.webauthn.isSupported(),
  );

  constructor() {
    writeSession(LOGIN_PENDING_STORAGE_KEY, null);
  }

  /** Called by every login flow once the session is applied. */
  loggedIn(method: LoginMethodName): void {
    this.pending.set(method === 'GOOGLE' || method === 'EMAIL');
  }

  /** Called right before leaving the app for the Google login. */
  leavingForLogin(method: LoginMethodName): void {
    writeSession(LOGIN_PENDING_STORAGE_KEY, method);
  }

  dismiss(): void {
    this.answer('dismissed');
  }

  accept(): void {
    this.answer('accepted');
  }

  private answer(value: string): void {
    writeStorage(PASSKEY_NUDGE_STORAGE_KEY, value);
    this.answered.set(true);
    this.pending.set(false);
  }
}
