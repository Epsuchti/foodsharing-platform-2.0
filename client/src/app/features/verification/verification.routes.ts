import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { homeRegionGuard } from '../../core/region/home-region.guard';

/**
 * Mounted at '' in app.routes.ts; paths inside are relative to that mount (M2 T23: Ve2, Ve3, A8).
 * `regions/:regionId/verification`, `regions/:regionId/passports`, `users/:userId/verification-history`,
 * `me/passport`. Permissions are decided by the server (403 → forbidden state).
 */
export const VERIFICATION_ROUTES: Routes = [
  {
    path: 'regions/:regionId/verification',
    canActivate: [authGuard, homeRegionGuard],
    title: 'verification.queue.pageTitle',
    loadComponent: () =>
      import('./pages/verification-queue-page.component').then((m) => m.VerificationQueuePageComponent),
  },
  {
    path: 'regions/:regionId/passports',
    canActivate: [authGuard, homeRegionGuard],
    title: 'verification.passports.pageTitle',
    loadComponent: () =>
      import('./pages/region-passports-page.component').then((m) => m.RegionPassportsPageComponent),
  },
  {
    path: 'users/:userId/verification-history',
    canActivate: [authGuard],
    title: 'verification.history.pageTitle',
    loadComponent: () =>
      import('./pages/user-verification-history-page.component').then(
        (m) => m.UserVerificationHistoryPageComponent,
      ),
  },
  {
    path: 'me/passport',
    canActivate: [authGuard],
    title: 'verification.passport.pageTitle',
    loadComponent: () => import('./pages/my-passport-page.component').then((m) => m.MyPassportPageComponent),
  },
];
