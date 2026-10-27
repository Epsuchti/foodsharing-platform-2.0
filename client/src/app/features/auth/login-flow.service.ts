import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

import { SessionInfo } from '../../api';
import { AuthFacadeService } from '../../core/auth/auth-facade.service';
import { LoginMethodName, PasskeyNudgeService } from '../../core/auth/passkey-nudge.service';
import { safeReturnUrl } from '../../core/auth/return-url';

export const DEFAULT_AFTER_LOGIN = '/dashboard';
export const GOOGLE_AUTHORIZATION_PATH = '/oauth2/authorization/google';

/** The common end of every login flow (Google, magic link, passkey, dev switcher). */
@Injectable({ providedIn: 'root' })
export class LoginFlowService {
  private readonly auth = inject(AuthFacadeService);
  private readonly router = inject(Router);
  private readonly nudge = inject(PasskeyNudgeService);
  private readonly document = inject(DOCUMENT);

  /** Applies the new session and goes to the (validated) return URL or the dashboard. */
  finish(info: SessionInfo, returnUrl: string | null | undefined, method: LoginMethodName): void {
    this.auth.completeLogin(info);
    this.nudge.loggedIn(method);
    void this.router.navigateByUrl(this.target(returnUrl), { replaceUrl: true });
  }

  target(returnUrl: string | null | undefined): string {
    return safeReturnUrl(returnUrl) ?? DEFAULT_AFTER_LOGIN;
  }

  /** `/oauth2/authorization/google?returnUrl=…` (the backend validates the return URL again). */
  googleUrl(returnUrl: string | null | undefined): string {
    const safe = safeReturnUrl(returnUrl);
    return safe
      ? `${GOOGLE_AUTHORIZATION_PATH}?returnUrl=${encodeURIComponent(safe)}`
      : GOOGLE_AUTHORIZATION_PATH;
  }

  /** Full-page navigation to Google (the OAuth2 handshake is not an XHR). */
  startGoogle(returnUrl: string | null | undefined): void {
    this.nudge.leavingForLogin('GOOGLE');
    this.redirect(this.googleUrl(returnUrl));
  }

  /** Separate method so specs can replace the real page navigation. */
  redirect(url: string): void {
    this.document.location.assign(url);
  }
}
