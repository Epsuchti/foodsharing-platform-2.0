import { Routes } from '@angular/router';

import { FeatureToggleKey } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';
import { featureGuard } from '../../core/feature-toggles/feature.guard';
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
        // FS1 (plan-m7 Q6): the tab lives in features/food-share-points, behind its feature toggle
        path: 'food-share-points',
        title: 'regions.tab.foodSharePoints',
        canActivate: [featureGuard(FeatureToggleKey.FoodSharePoints)],
        loadComponent: () => import('../food-share-points/tabs/region-fsps.tab').then((m) => m.RegionFspsTab),
      },
      // AC1 (plan-m7 Q9): the tab lives in features/achievements
      tab('achievements', 'regions.tab.achievements', () =>
        import('../achievements/tabs/region-achievements.tab').then((m) => m.RegionAchievementsTab),
      ),
      // placeholders until their features exist (plan-m7 Q7): wall, resources (Q12)
      ...(['wall', 'resources'] as const).map((feature) => ({
        path: feature,
        title: 'regions.tab.later',
        data: { feature },
        loadComponent: () => import('./tabs/region-later.tab').then((m) => m.RegionLaterTab),
      })),
      // G1/G2/D10.3 (plan-m7 Q8): the tabs live in features/reports
      tab('reports', 'regions.tab.reports', () =>
        import('../reports/tabs/region-reports.tab').then((m) => m.RegionReportsTab),
      ),
      tab('report-log', 'regions.tab.reportLog', () =>
        import('../reports/tabs/region-report-log.tab').then((m) => m.RegionReportLogTab),
      ),
      tab('mediation', 'regions.tab.mediation', () =>
        import('../reports/tabs/region-mediation.tab').then((m) => m.RegionMediationTab),
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
