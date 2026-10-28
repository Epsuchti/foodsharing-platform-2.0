import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { JourneyPageComponent } from './pages/journey-page.component';

/**
 * Mounted at 'onboarding' in app.routes.ts; paths inside are relative to that mount (M2 T22).
 * `/onboarding` journey page, `/onboarding/profile` step 2 (profile and home region).
 */
export const ONBOARDING_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    title: 'onboarding.journey.pageTitle',
    // bundled with this routes chunk: one lazy round trip less on the first paint of /onboarding (D11.5.3)
    component: JourneyPageComponent,
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    title: 'onboarding.profile.pageTitle',
    loadComponent: () =>
      import('./pages/profile-region-step-page.component').then(
        (m) => m.ProfileRegionStepPageComponent,
      ),
  },
];
