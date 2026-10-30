import { Routes } from '@angular/router';

import { FeatureToggleKey } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';
import { featureGuard } from '../../core/feature-toggles/feature.guard';

/**
 * Mounted at `search` in app.routes.ts (plan-m7 Q12, C5), behind GLOBAL_SEARCH (off → /not-found): `/search?q=`.
 * The header search box (`fs-global-search`) lives in components/ and is placed by layout/header.component.ts.
 */
export const SEARCH_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [authGuard, featureGuard(FeatureToggleKey.GlobalSearch)],
    title: 'search.page.title',
    loadComponent: () => import('./pages/search-page.component').then((m) => m.SearchPageComponent),
  },
];
