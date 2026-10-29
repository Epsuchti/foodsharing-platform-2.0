import { Routes } from '@angular/router';

import { FeatureToggleKey } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';
import { featureGuard } from '../../core/feature-toggles/feature.guard';

/**
 * Mounted at `food-share-points` in app.routes.ts (plan-m7 Q6, FS1), behind the FOOD_SHARE_POINTS feature toggle
 * (A43; off → /not-found): `/food-share-points/mine`, `/food-share-points/new?regionId=`,
 * `/food-share-points/:fspId` (detail; the FSP_ACTIVATE / FSP_NEW_POST bells link here) and `…/:fspId/edit`. The
 * region tab `/regions/:regionId/food-share-points` is a child route of the region page (regions.routes.ts).
 */
export const FOOD_SHARE_POINTS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard, featureGuard(FeatureToggleKey.FoodSharePoints)],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'mine' },
      {
        path: 'mine',
        title: 'foodSharePoints.mine.title',
        loadComponent: () => import('./pages/my-fsps-page.component').then((m) => m.MyFspsPageComponent),
      },
      {
        path: 'new',
        title: 'foodSharePoints.form.createTitle',
        loadComponent: () => import('./pages/fsp-form-page.component').then((m) => m.FspFormPageComponent),
      },
      {
        path: ':fspId/edit',
        title: 'foodSharePoints.form.editTitle',
        loadComponent: () => import('./pages/fsp-form-page.component').then((m) => m.FspFormPageComponent),
      },
      {
        path: ':fspId',
        title: 'foodSharePoints.detail.title',
        loadComponent: () => import('./pages/fsp-page.component').then((m) => m.FspPageComponent),
      },
    ],
  },
];
