import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

/** Mounted at 'bells' in app.routes.ts (M4 F1, N3). */
export const BELLS_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    title: 'bell.ui.page.pageTitle',
    loadComponent: () => import('./pages/bells-page.component').then((m) => m.BellsPageComponent),
  },
];
