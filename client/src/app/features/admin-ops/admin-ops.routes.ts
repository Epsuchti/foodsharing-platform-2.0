import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';

/** Mounted at 'admin' in app.routes.ts (M4 F6, ORGA): integrations, jobs, mail outbox. */
export const ADMIN_OPS_ROUTES: Routes = [
  {
    path: 'integrations',
    canActivate: [authGuard],
    data: { roles: [Role.Orga] },
    title: 'adminOps.integrations.title',
    loadComponent: () => import('./pages/integrations-page.component').then((m) => m.IntegrationsPageComponent),
  },
  {
    path: 'jobs',
    canActivate: [authGuard],
    data: { roles: [Role.Orga] },
    title: 'adminOps.jobs.title',
    loadComponent: () => import('./pages/jobs-page.component').then((m) => m.JobsPageComponent),
  },
  {
    path: 'mail-outbox',
    canActivate: [authGuard],
    data: { roles: [Role.Orga] },
    title: 'adminOps.outbox.title',
    loadComponent: () => import('./pages/mail-outbox-page.component').then((m) => m.MailOutboxPageComponent),
  },
];
