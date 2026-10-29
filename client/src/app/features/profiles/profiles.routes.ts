import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { SessionService } from '../../core/auth/session.service';

/** `/profile/me[?tab=…]` → `/profile/<own id>[?tab=…]`; without a session authGuard (listed first) wins. */
export const myProfileRedirect: CanActivateFn = (route) => {
  const user = inject(SessionService).user();
  return user ? inject(Router).createUrlTree(['/profile', user.id], { queryParams: route.queryParams }) : true;
};

/**
 * Mounted at `profile` in app.routes.ts (plan-m7 Q2, A6/A15/A16): `/profile/me` (redirect to the own profile),
 * `/profile/me/buddies` (requests + buddies), `/profile/:userId[?tab=wall|bananas|commitments|notes|history]`.
 * The bells of bananas and buddy requests link to `/profile/<id>` (ProfileNotifier).
 */
export const PROFILES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: 'me/buddies',
        title: 'profiles.buddies.title',
        loadComponent: () => import('./pages/buddies-page.component').then((m) => m.BuddiesPageComponent),
      },
      { path: 'me', canActivate: [myProfileRedirect], children: [] },
      {
        path: ':userId',
        title: 'profiles.title',
        loadComponent: () => import('./pages/profile-page.component').then((m) => m.ProfilePageComponent),
      },
    ],
  },
];
