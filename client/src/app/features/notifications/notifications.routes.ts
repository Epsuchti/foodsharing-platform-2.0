import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

/** Mounted at 'account/notifications' in app.routes.ts (M4 F2: N4, N5 user side, PU9). */
export const NOTIFICATIONS_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    title: 'notifications.settings.pageTitle',
    loadComponent: () =>
      import('./pages/notification-settings-page.component').then((m) => m.NotificationSettingsPageComponent),
  },
];
