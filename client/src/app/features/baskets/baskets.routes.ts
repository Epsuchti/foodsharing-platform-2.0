import { Routes } from '@angular/router';

import { FeatureToggleKey } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';
import { featureGuard } from '../../core/feature-toggles/feature.guard';

/**
 * Mounted at `baskets` in app.routes.ts (plan-m7 Q5, B1/B2), behind the BASKETS feature toggle (A43; off →
 * /not-found): `/baskets` (nearby list + map), `/baskets/mine` (offered + requested), `/baskets/new`,
 * `/baskets/:basketId` (detail; the BASKET_REQUEST bells link here) and `/baskets/:basketId/edit`.
 */
export const BASKETS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard, featureGuard(FeatureToggleKey.Baskets)],
    children: [
      {
        path: '',
        title: 'baskets.nearby.title',
        loadComponent: () => import('./pages/baskets-page.component').then((m) => m.BasketsPageComponent),
      },
      {
        path: 'mine',
        title: 'baskets.mine.title',
        loadComponent: () => import('./pages/my-baskets-page.component').then((m) => m.MyBasketsPageComponent),
      },
      {
        path: 'new',
        title: 'baskets.form.createTitle',
        loadComponent: () => import('./pages/basket-form-page.component').then((m) => m.BasketFormPageComponent),
      },
      {
        path: ':basketId/edit',
        title: 'baskets.form.editTitle',
        loadComponent: () => import('./pages/basket-form-page.component').then((m) => m.BasketFormPageComponent),
      },
      {
        path: ':basketId',
        title: 'baskets.detail.title',
        loadComponent: () => import('./pages/basket-page.component').then((m) => m.BasketPageComponent),
      },
    ],
  },
];
