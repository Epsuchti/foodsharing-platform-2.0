import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { homeRegionGuard } from '../../core/region/home-region.guard';

/**
 * Mounted at '' in app.routes.ts; paths inside are relative to that mount.
 * `regions/:regionId/users` and `users/:userId`, user management (AD1, A4 admin part). Access is decided by the
 * server (ambassadors, CAN_MANAGE_USERS for the region, ORGA); a 403 shows the forbidden state.
 */
export const USERS_ADMIN_ROUTES: Routes = [
  {
    path: 'regions/:regionId/users',
    canActivate: [authGuard, homeRegionGuard],
    title: 'usersAdmin.list.pageTitle',
    loadComponent: () =>
      import('./pages/region-users-page.component').then((m) => m.RegionUsersPageComponent),
  },
  {
    path: 'users/:userId',
    canActivate: [authGuard],
    title: 'usersAdmin.user.pageTitle',
    loadComponent: () => import('./pages/user-admin-page.component').then((m) => m.UserAdminPageComponent),
  },
];
