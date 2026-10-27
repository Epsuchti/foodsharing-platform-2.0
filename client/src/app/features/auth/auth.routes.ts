import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';

import { safeReturnUrl } from '../../core/auth/return-url';
import { SessionService } from '../../core/auth/session.service';
import { DEFAULT_AFTER_LOGIN } from './login-flow.service';

/** Login pages are for visitors: a logged-in user goes on to the return URL (or the dashboard). */
export const guestGuard: CanActivateFn = (route) => {
  if (!inject(SessionService).isAuthenticated()) {
    return true;
  }
  const target = safeReturnUrl(route.queryParamMap.get('returnUrl')) ?? DEFAULT_AFTER_LOGIN;
  return inject(Router).parseUrl(target);
};

/** /login, /login/sent, /login/verify — minimal chrome (no navigation, no footer). */
export const AUTH_ROUTES: Routes = [
  {
    path: '',
    data: { chrome: 'minimal' },
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [guestGuard],
        title: 'auth.login.pageTitle',
        loadComponent: () =>
          import('./pages/login-page.component').then((m) => m.LoginPageComponent),
      },
      {
        path: 'sent',
        canActivate: [guestGuard],
        title: 'auth.sent.pageTitle',
        loadComponent: () =>
          import('./pages/magic-link-sent-page.component').then(
            (m) => m.MagicLinkSentPageComponent,
          ),
      },
      {
        // the magic link may also be opened while another account is logged in: it switches accounts
        path: 'verify',
        title: 'auth.verify.pageTitle',
        loadComponent: () =>
          import('./pages/magic-link-verify-page.component').then(
            (m) => m.MagicLinkVerifyPageComponent,
          ),
      },
    ],
  },
];
