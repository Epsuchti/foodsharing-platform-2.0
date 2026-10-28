import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

const section = (path: string, title: string, load: () => Promise<unknown>) => ({
  path,
  title,
  loadComponent: load as never,
});

/** Children of the store tab `/stores/:storeId/automation` (mounted by stores.routes.ts, M4 F3/F4). */
export const STORE_AUTOMATION_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'policy' },
  section('policy', 'automation.section.policy', () => import('./sections/pickup-policy.section').then((m) => m.PickupPolicySection)),
  section('auto-accept', 'automation.section.auto-accept', () =>
    import('./sections/request-policy.section').then((m) => m.RequestPolicySection),
  ),
  section('adverts', 'automation.section.adverts', () => import('./sections/adverts.section').then((m) => m.AdvertsSection)),
  section('signout-alerts', 'automation.section.signout-alerts', () =>
    import('./sections/signout-alert.section').then((m) => m.SignoutAlertSection),
  ),
  section('extra-slot', 'automation.section.extra-slot', () =>
    import('./sections/extra-slot.section').then((m) => m.ExtraSlotSection),
  ),
  section('audits', 'automation.section.audits', () => import('./sections/store-audits.section').then((m) => m.StoreAuditsSection)),
];

const region = () => import('./pages/region-sections');

/** Mounted at '' in app.routes.ts: `/regions/:regionId/automation/**` (AU1, AU5, AU7–AU10). */
export const AUTOMATION_ROUTES: Routes = [
  {
    path: 'regions/:regionId/automation',
    canActivate: [authGuard],
    title: 'automation.region.title',
    loadComponent: () => import('./pages/region-automation-page.component').then((m) => m.RegionAutomationPageComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'audits' },
      section('overview', 'automation.region.section.overview', () => region().then((m) => m.RegionOverviewSection)),
      section('audits', 'automation.region.section.audits', () => region().then((m) => m.RegionAuditsSection)),
      section('cleaning-exemptions', 'automation.region.section.cleaning-exemptions', () =>
        region().then((m) => m.RegionExemptionsSection),
      ),
      section('grouping', 'automation.region.section.grouping', () => region().then((m) => m.RegionGroupingSection)),
      section('metrics', 'automation.region.section.metrics', () => region().then((m) => m.RegionMetricsSection)),
      section('telegram', 'automation.region.section.telegram', () => region().then((m) => m.RegionTelegramSection)),
      section('holidays', 'automation.region.section.holidays', () => region().then((m) => m.RegionHolidaysSection)),
    ],
  },
];
