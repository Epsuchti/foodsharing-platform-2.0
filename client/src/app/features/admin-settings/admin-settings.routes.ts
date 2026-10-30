import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';

const orga = { canActivate: [authGuard], data: { roles: [Role.Orga] } };

/**
 * Mounted at '' in app.routes.ts (plan-m7 Q13, ORGA): platform settings (C11 feature toggles, C14 configuration
 * and donation settings, C12 email blocklist and IP blocks), C10 uploads administration and the AD1 admins list.
 * `/admin/settings` (the provisional nav route of Q1) opens the first settings tab.
 */
export const ADMIN_SETTINGS_ROUTES: Routes = [
  { path: 'admin/settings', pathMatch: 'full', redirectTo: 'admin/feature-toggles' },
  {
    path: 'admin/feature-toggles',
    ...orga,
    title: 'adminSettings.toggles.title',
    loadComponent: () => import('./pages/feature-toggles-page.component').then((m) => m.FeatureTogglesPageComponent),
  },
  {
    path: 'admin/configuration',
    ...orga,
    title: 'adminSettings.configuration.title',
    loadComponent: () => import('./pages/configuration-page.component').then((m) => m.ConfigurationPageComponent),
  },
  {
    path: 'admin/donations',
    ...orga,
    title: 'adminSettings.donations.title',
    loadComponent: () => import('./pages/donations-page.component').then((m) => m.DonationsPageComponent),
  },
  {
    path: 'admin/email-blocklist',
    ...orga,
    title: 'adminSettings.blocklist.title',
    loadComponent: () => import('./pages/email-blocklist-page.component').then((m) => m.EmailBlocklistPageComponent),
  },
  {
    path: 'admin/ip-blocks',
    ...orga,
    title: 'adminSettings.ipBlocks.title',
    loadComponent: () => import('./pages/ip-blocks-page.component').then((m) => m.IpBlocksPageComponent),
  },
  {
    path: 'admin/uploads',
    ...orga,
    title: 'adminSettings.uploads.title',
    loadComponent: () => import('./pages/uploads-page.component').then((m) => m.UploadsPageComponent),
  },
  {
    path: 'admin/admins',
    ...orga,
    title: 'adminSettings.admins.title',
    loadComponent: () => import('./pages/admins-page.component').then((m) => m.AdminsPageComponent),
  },
];
