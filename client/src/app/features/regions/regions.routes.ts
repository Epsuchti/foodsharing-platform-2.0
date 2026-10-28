import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { homeRegionGuard } from '../../core/region/home-region.guard';

const tab = (path: string, title: string, load: () => Promise<unknown>) => ({
  path,
  title,
  loadComponent: load as never,
});

/**
 * Mounted at '' in app.routes.ts (full paths inside): `regions/**`, `groups/**`, public `r/:slug` and
 * `find-region` (M2 T18). Region sub-pages of other modules (`regions/:regionId/newcomers`, …) live in their own
 * route files; the router falls through to them when no tab here matches.
 */
export const REGIONS_ROUTES: Routes = [
  {
    path: 'regions/mine',
    canActivate: [authGuard],
    title: 'regions.mine.pageTitle',
    loadComponent: () => import('./pages/my-units-page.component').then((m) => m.MyUnitsPageComponent),
  },
  {
    path: 'regions/:regionId',
    canActivate: [authGuard, homeRegionGuard],
    loadComponent: () => import('./pages/region-page.component').then((m) => m.RegionPageComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'overview' },
      tab('overview', 'regions.tab.overview', () =>
        import('./tabs/region-overview.tab').then((m) => m.RegionOverviewTab),
      ),
      tab('members', 'regions.tab.members', () =>
        import('./tabs/region-members.tab').then((m) => m.RegionMembersTab),
      ),
      tab('groups', 'regions.tab.groups', () =>
        import('./tabs/region-groups.tab').then((m) => m.RegionGroupsTab),
      ),
      tab('statistics', 'regions.tab.statistics', () =>
        import('./tabs/region-statistics.tab').then((m) => m.RegionStatisticsTab),
      ),
      tab('options', 'regions.tab.options', () =>
        import('./tabs/region-options.tab').then((m) => m.RegionOptionsTab),
      ),
      tab('pin', 'regions.tab.pin', () => import('./tabs/region-pin.tab').then((m) => m.RegionPinTab)),
      tab('settings', 'regions.tab.settings', () =>
        import('./tabs/region-settings.tab').then((m) => m.RegionSettingsTab),
      ),
      tab('history', 'regions.tab.history', () =>
        import('./tabs/region-history.tab').then((m) => m.RegionHistoryTab),
      ),
      {
        path: 'later/:feature',
        title: 'regions.tab.later',
        loadComponent: () =>
          import('./tabs/region-later.tab').then((m) => m.RegionLaterTab),
      },
    ],
  },
  {
    path: 'groups/:groupId',
    canActivate: [authGuard],
    title: 'regions.group.pageTitle',
    loadComponent: () => import('./pages/group-page.component').then((m) => m.GroupPageComponent),
  },
  {
    path: 'r/:slug',
    title: 'regions.public.pageTitle',
    loadComponent: () =>
      import('./pages/public-region-page.component').then((m) => m.PublicRegionPageComponent),
  },
  {
    path: 'find-region',
    title: 'regions.find.pageTitle',
    loadComponent: () =>
      import('./pages/find-region-page.component').then((m) => m.FindRegionPageComponent),
  },
];
