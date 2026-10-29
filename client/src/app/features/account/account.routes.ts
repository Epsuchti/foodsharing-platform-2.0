import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

/**
 * /account/* — own settings (photo, sleep mode, email, preferences, login methods), signed-in devices, security log,
 * the own profile form and the email-change links (`email/confirm` logged in, `email/abort` public).
 */
export const ACCOUNT_ROUTES: Routes = [
  // link in the notice to the old address (A3): works logged out, so it sits outside the guarded children
  {
    path: 'email/abort',
    title: 'account.email.abort.pageTitle',
    data: { chrome: 'minimal', emailToken: 'abort' },
    loadComponent: () =>
      import('./pages/email-token-page.component').then((m) => m.EmailTokenPageComponent),
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'settings' },
      {
        path: 'settings',
        title: 'account.settings.pageTitle',
        loadComponent: () =>
          import('./pages/settings-page.component').then((m) => m.SettingsPageComponent),
      },
      {
        path: 'sessions',
        title: 'account.sessions.pageTitle',
        loadComponent: () =>
          import('./pages/sessions-page.component').then((m) => m.SessionsPageComponent),
      },
      {
        path: 'security-log',
        title: 'account.securityLog.pageTitle',
        loadComponent: () =>
          import('./pages/security-log-page.component').then((m) => m.SecurityLogPageComponent),
      },
      {
        path: 'email/confirm',
        title: 'account.email.confirm.pageTitle',
        data: { emailToken: 'confirm' },
        loadComponent: () =>
          import('./pages/email-token-page.component').then((m) => m.EmailTokenPageComponent),
      },
      {
        path: 'profile',
        title: 'account.profile.pageTitle',
        loadComponent: () =>
          import('./pages/profile-page.component').then((m) => m.ProfilePageComponent),
      },
    ],
  },
];
