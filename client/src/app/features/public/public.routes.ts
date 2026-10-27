import { Routes } from '@angular/router';

import { landingGuard } from '../../core/auth/landing.guard';

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
