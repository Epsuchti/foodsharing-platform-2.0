import { Routes } from '@angular/router';

import { landingGuard } from '../../core/auth/landing.guard';

/**
 * Mounted at `''`. M2 adds `r/:slug` (public region page, T18) and `find-region` (postcode/map region finder,
 * T22) here; until those pages exist the URLs fall through to not-found.
 */
export const PUBLIC_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [landingGuard],
    title: 'public.landing.pageTitle',
    loadComponent: () =>
      import('./pages/landing-page.component').then((m) => m.LandingPageComponent),
  },
];
