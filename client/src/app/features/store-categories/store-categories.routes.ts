import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';

/** Mounted at `admin/store-categories` in app.routes.ts (M3 S4, ORGA): categories and groceries. */
export const STORE_CATEGORIES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    data: { roles: [Role.Orga] },
    title: 'storeCategories.title',
    loadComponent: () =>
      import('./pages/store-categories-admin-page.component').then((m) => m.StoreCategoriesAdminPageComponent),
  },
];
