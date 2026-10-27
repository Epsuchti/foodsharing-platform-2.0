import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { Observable, finalize, tap } from 'rxjs';

import { AuthService, SessionInfo } from '../../api';
import { I18nService } from '../i18n/i18n.service';
import { isUiLanguage } from '../i18n/languages';
import { ThemeService, themeFromApi } from '../theme/theme.service';
import { authGuard, routeAccess } from './auth.guard';
import { SessionExpiredService } from './session-expired.service';
import { SessionService } from './session.service';

/** Tab switches within this time do not ask the server again. */
export const REFRESH_THROTTLE_MS = 60_000;

/**
 * Session lifecycle on top of SessionService: refresh on tab focus and after 403 (so role changes apply
 * without logging out), logout, and applying the account's language/theme after login.
 */
@Injectable({ providedIn: 'root' })
export class AuthFacadeService {
  private readonly authApi = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly i18n = inject(I18nService);
  private readonly theme = inject(ThemeService);
  private readonly sessionExpired = inject(SessionExpiredService);

  private lastRefresh = 0;
  private refreshing = false;
  private listening = false;

  /** Refreshes the session whenever the tab becomes visible again (throttled). */
  startSessionRefresh(): void {
    this.lastRefresh = Date.now();
    if (this.listening) {
      return;
    }
    this.listening = true;
    this.document.addEventListener('visibilitychange', () => {
      if (this.document.visibilityState === 'visible') {
        this.refreshSession();
      }
    });
  }

  /** Reloads the session; `force` skips the throttle (e.g. after the server answered 403). */
  refreshSession(force = false): void {
    if (this.refreshing || (!force && Date.now() - this.lastRefresh < REFRESH_THROTTLE_MS)) {
      return;
    }
    const wasAuthenticated = this.session.isAuthenticated();
    if (!wasAuthenticated && !force) {
      return;
    }
    this.refreshing = true;
    this.lastRefresh = Date.now();
    this.authApi.getSession().subscribe({
      next: (info) => {
        this.refreshing = false;
        this.onSessionLoaded(info, wasAuthenticated);
      },
      // network errors keep the current session; 401 is handled by the error interceptor
      error: () => {
        this.refreshing = false;
      },
    });
  }

  /** Takes over a session returned by a login endpoint and applies the account's preferences. */
  completeLogin(info: SessionInfo): void {
    this.session.applySession(info);
    this.applyUserPreferences();
  }

  /**
   * When logged in, the account's language and theme win over the device defaults (they are the user's
   * saved settings); they are also remembered on this device for the next logged-out visit.
   */
  applyUserPreferences(): void {
    const user = this.session.user();
    if (!user) {
      return;
    }
    if (isUiLanguage(user.language) && user.language !== this.i18n.language()) {
      void this.i18n.setLanguage(user.language, { persist: false });
    }
    const theme = themeFromApi(user.theme);
    if (theme !== this.theme.preference()) {
      this.theme.setPreference(theme, { persist: false });
    }
  }

  logout(): Observable<unknown> {
    return this.authApi.logout().pipe(
      tap({ error: () => undefined }),
      finalize(() => {
        this.session.clear();
        void this.router.navigateByUrl('/');
      }),
    );
  }

  private onSessionLoaded(info: SessionInfo, wasAuthenticated: boolean): void {
    const changed = this.session.applySession(info);
    if (wasAuthenticated && !this.session.isAuthenticated()) {
      if (this.currentRouteRequiresLogin()) {
        this.sessionExpired.open();
      }
      return;
    }
    if (changed) {
      this.leavePageIfNoLongerAllowed();
    }
  }

  private currentRouteRequiresLogin(): boolean {
    return this.routeChain().some((route) => route.routeConfig?.canActivate?.includes(authGuard));
  }

  private leavePageIfNoLongerAllowed(): void {
    for (const route of this.routeChain()) {
      if (!routeAccess(this.session, route.data)) {
        void this.router.navigateByUrl('/forbidden');
        return;
      }
    }
  }

  private routeChain(): ActivatedRouteSnapshot[] {
    const chain: ActivatedRouteSnapshot[] = [];
    let route: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    while (route) {
      chain.push(route);
      route = route.firstChild;
    }
    return chain;
  }
}
