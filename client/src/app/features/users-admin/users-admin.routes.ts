import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { homeRegionGuard } from '../../core/region/home-region.guard';

/**
 * Mounted at '' in app.routes.ts; paths inside are relative to that mount.
 * `regions/:regionId/users`, `users/:userId` and `admin/bounced-emails`, user management (AD1, A4 admin part, A18). Access is decided by the
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
