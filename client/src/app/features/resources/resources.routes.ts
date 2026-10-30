import { Routes } from '@angular/router';

import { FeatureToggleKey } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';
import { featureGuard } from '../../core/feature-toggles/feature.guard';

/**
 * Mounted at `resources` in app.routes.ts (plan-m7 Q12, C6), behind the RESOURCES feature toggle (A43; off →
 * /not-found): `/resources` (backend deep link) → `/resources/mine`, `/resources/favourites`. The region tab
 * `/regions/:regionId/resources` (NEW_RESOURCE bells) is a child route of the region page (regions.routes.ts).
 */
export const RESOURCES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard, featureGuard(FeatureToggleKey.Resources)],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'mine' },
      {
        path: 'mine',
        title: 'resources.mine.title',
        loadComponent: () => import('./pages/my-resources-page.component').then((m) => m.MyResourcesPageComponent),
      },
      {
        path: 'favourites',
        title: 'resources.favourites.title',
        loadComponent: () =>
          import('./pages/favourite-resources-page.component').then((m) => m.FavouriteResourcesPageComponent),
      },
    ],
  },
];
