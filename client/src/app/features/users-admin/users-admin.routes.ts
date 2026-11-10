import { inject } from '@angular/core';
import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { homeRegion } from '../../core/auth/permission.util';
import { SessionService } from '../../core/auth/session.service';

/**
 * Mounted at '' in app.routes.ts; paths inside are relative to that mount.
 * `users/:userId` and `admin/bounced-emails`, user management (AD1, A4 admin part, A18); `regions/:regionId/users`
 * redirects to the region's Members tab (D1). Access is decided by the
 * server (ambassadors, CAN_MANAGE_USERS for the region, ORGA); a 403 shows the forbidden state.
 */
export const USERS_ADMIN_ROUTES: Routes = [
  {
    // D1 (review 2026-11-10): the users page became the admin part of the region's Members tab; the old URL
    // redirects there (`home` resolved like homeRegionGuard does, redirects run no guards)
    path: 'regions/:regionId/users',
    redirectTo: ({ params }) => {
      const regionId = params['regionId'] === 'home' ? homeRegion(inject(SessionService).session())?.id : params['regionId'];
      return regionId ? `/regions/${regionId}/members` : '/find-region';
    },
  },
  {
    // A18: ORGA sees every bounced address; with ?regionId= the region's administrators see their members
    path: 'admin/bounced-emails',
    canActivate: [authGuard],
    title: 'usersAdmin.bounced.pageTitle',
    loadComponent: () =>
      import('./pages/bounced-users-page.component').then((m) => m.BouncedUsersPageComponent),
  },
  {
    path: 'users/:userId',
    canActivate: [authGuard],
    title: 'usersAdmin.user.pageTitle',
    loadComponent: () => import('./pages/user-admin-page.component').then((m) => m.UserAdminPageComponent),
  },
];
