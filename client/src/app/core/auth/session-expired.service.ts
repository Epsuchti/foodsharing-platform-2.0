import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { safeReturnUrl } from './return-url';
import { SessionService } from './session.service';

/**
 * State of the "session expired" dialog (shared/session-expired-dialog). Opened by the error interceptor on
 * a 401 while the app believed to be logged in, and by AuthFacadeService when a refresh finds no session.
 */
@Injectable({ providedIn: 'root' })
export class SessionExpiredService {
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  readonly visible = signal(false);
  readonly returnUrl = signal<string | null>(null);

  open(): void {
    if (this.visible()) {
      return;
    }
    this.returnUrl.set(safeReturnUrl(this.router.url));
    this.session.clear();
    this.visible.set(true);
  }

  close(): void {
    this.visible.set(false);
  }

  /** "Log in again": to /login, coming back to the current page afterwards. */
  goToLogin(): void {
    const returnUrl = this.returnUrl();
    this.visible.set(false);
    void this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : {});
  }
}
