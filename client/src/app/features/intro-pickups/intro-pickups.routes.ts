import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { homeRegionGuard } from '../../core/region/home-region.guard';

/**
 * Mounted at '' in app.routes.ts; paths inside are relative to that mount.
 * `intro-pickups/**`, `me/intro-pickups`, `r/:slug/intro-pickups`, `guide/**`, `regions/:regionId/newcomers`,
 * `regions/:regionId/intro-pickups/**` (M2 T21). Permissions are decided by the server (403 → forbidden state).
 */
export const INTRO_PICKUPS_ROUTES: Routes = [
  {
    path: 'intro-pickups',
    pathMatch: 'full',
    canActivate: [authGuard],
    title: 'introPickups.bookable.pageTitle',
    loadComponent: () =>
      import('./pages/bookable-list-page.component').then((m) => m.BookableListPageComponent),
  },
  {
    path: 'intro-pickups/unsubscribe',
    title: 'introPickups.unsubscribe.pageTitle',
    loadComponent: () => import('./pages/unsubscribe-page.component').then((m) => m.UnsubscribePageComponent),
  },
  {
    path: 'r/:slug/intro-pickups',
    title: 'introPickups.public.pageTitle',
    loadComponent: () => import('./pages/public-slots-page.component').then((m) => m.PublicSlotsPageComponent),
  },
  {
    path: 'me/intro-pickups',
    canActivate: [authGuard],
    title: 'introPickups.my.pageTitle',
    loadComponent: () => import('./pages/my-bookings-page.component').then((m) => m.MyBookingsPageComponent),
  },
  {
    path: 'guide',
    canActivate: [authGuard],
    title: 'introPickups.guide.pageTitle',
    loadComponent: () =>
      import('./pages/guide-dashboard-page.component').then((m) => m.GuideDashboardPageComponent),
  },
  {
    path: 'guide/settings',
    canActivate: [authGuard],
    title: 'introPickups.settings.pageTitle',
    loadComponent: () =>
      import('./pages/guide-settings-page.component').then((m) => m.GuideSettingsPageComponent),
  },
  {
    path: 'regions/:regionId/newcomers',
    canActivate: [authGuard, homeRegionGuard],
    title: 'introPickups.funnel.pageTitle',
    loadComponent: () =>
      import('./pages/newcomer-funnel-page.component').then((m) => m.NewcomerFunnelPageComponent),
  },
  {
    path: 'regions/:regionId/intro-pickups/admin',
    canActivate: [authGuard, homeRegionGuard],
    title: 'introPickups.admin.pageTitle',
    loadComponent: () =>
      import('./pages/admin-intro-pickups-page.component').then((m) => m.AdminIntroPickupsPageComponent),
  },
  {
    path: 'regions/:regionId/intro-pickups/import',
    canActivate: [authGuard, homeRegionGuard],
    title: 'introPickups.import.pageTitle',
    loadComponent: () => import('./pages/past-import-page.component').then((m) => m.PastImportPageComponent),
  },
];
