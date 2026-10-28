import { Routes } from '@angular/router';

import { landingGuard } from '../../core/auth/landing.guard';

/** Mounted at `''`: the landing page. `r/:slug` and `find-region` live in the regions routes. */
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
