import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

/** /account/* — own settings, signed-in devices, security log and the profile placeholder (A4 arrives in M6). */
export const ACCOUNT_ROUTES: Routes = [
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
        path: 'profile',
        title: 'account.profile.pageTitle',
        loadComponent: () =>
          import('./pages/profile-page.component').then((m) => m.ProfilePageComponent),
      },
    ],
  },
];
