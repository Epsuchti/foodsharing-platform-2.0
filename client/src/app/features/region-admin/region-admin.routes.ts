import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard, RouteAccessData } from '../../core/auth/auth.guard';

/**
 * Mounted at 'admin/regions' in app.routes.ts; paths inside are relative to that mount.
 * Region administration for ORGA (R7): tree, create/edit/move/delete, functions, CSV import.
 */
export const REGION_ADMIN_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    data: { roles: [Role.Orga] } satisfies RouteAccessData,
    title: 'regionAdmin.pageTitle',
    loadComponent: () =>
      import('./pages/region-admin-page.component').then((m) => m.RegionAdminPageComponent),
  },
];
