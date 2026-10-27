import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

/** Mounted at `/me` (app.routes.ts). */
export const HISTORY_ROUTES: Routes = [
  {
    path: 'activity',
    canActivate: [authGuard],
    title: 'history.myActivity.pageTitle',
    loadComponent: () =>
      import('./pages/my-activity-page.component').then((m) => m.MyActivityPageComponent),
  },
];
