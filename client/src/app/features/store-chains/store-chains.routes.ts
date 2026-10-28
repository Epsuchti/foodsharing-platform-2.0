import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';

/** Mounted at `store-chains` in app.routes.ts (M3 S3): chain list and chain page. */
export const STORE_CHAINS_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard],
    data: { roles: [Role.Foodsaver] },
    title: 'storeChains.list.title',
    loadComponent: () => import('./pages/chain-list-page.component').then((m) => m.ChainListPageComponent),
  },
  {
    path: ':chainId',
    canActivate: [authGuard],
    data: { roles: [Role.Foodsaver] },
    title: 'storeChains.chain.pageTitle',
    loadComponent: () => import('./pages/chain-page.component').then((m) => m.ChainPageComponent),
  },
];
