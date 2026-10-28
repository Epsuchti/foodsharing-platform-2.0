import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';

const tab = (path: string, load: () => Promise<unknown>) => ({
  path,
  title: `stores.tab.${path}`,
  loadComponent: load as never,
});

/**
 * Mounted at '' in app.routes.ts (full paths inside, M3): `stores/mine`, `stores/new`, `stores/:storeId/**`,
 * `regions/:regionId/stores` (falls through from the regions module, which has no `stores` tab).
 */
export const STORES_ROUTES: Routes = [
  {
    path: 'stores/mine',
    canActivate: [authGuard],
    data: { roles: [Role.Foodsaver] },
    title: 'stores.mine.pageTitle',
    loadComponent: () => import('./pages/my-stores-page.component').then((m) => m.MyStoresPageComponent),
  },
  {
    path: 'stores/new',
    canActivate: [authGuard],
    data: { roles: [Role.StoreManager] },
    title: 'stores.form.createTitle',
    loadComponent: () => import('./pages/store-form-page.component').then((m) => m.StoreFormPageComponent),
  },
  {
    path: 'stores/:storeId/edit',
    canActivate: [authGuard],
    title: 'stores.form.editTitle',
    loadComponent: () => import('./pages/store-form-page.component').then((m) => m.StoreFormPageComponent),
  },
  {
    path: 'stores/:storeId',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/store-page.component').then((m) => m.StorePageComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      tab('overview', () => import('./tabs/store-overview.tab').then((m) => m.StoreOverviewTab)),
    ],
  },
  {
    path: 'regions/:regionId/stores',
    canActivate: [authGuard],
    data: { roles: [Role.Foodsaver] },
    title: 'stores.region.pageTitle',
    loadComponent: () =>
      import('./pages/region-stores-page.component').then((m) => m.RegionStoresPageComponent),
  },
];
