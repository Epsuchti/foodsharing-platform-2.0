import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

/**
 * Mounted at '' in app.routes.ts; paths inside are full paths.
 * `consent` (pending legal consents, target of consentGuard, EA4). `legal/:kind` documents follow in M2 T20.
 */
export const LEGAL_ROUTES: Routes = [
  {
    path: 'consent',
    canActivate: [authGuard],
    title: 'legal.consent.pageTitle',
    loadComponent: () =>
      import('./pages/consent-page.component').then((m) => m.ConsentPageComponent),
  },
];
