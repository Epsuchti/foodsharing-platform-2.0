import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

/**
 * Mounted at `reports` in app.routes.ts (plan-m7 Q8, G1): `/reports/:reportId` (the REPORT_NEW and REPORT_REMINDER
 * bells link here). The region pages `/regions/:regionId/{reports,report-log,mediation}` are child routes of the
 * region page (regions.routes.ts); the report dialog opens from the profile.
 */
export const REPORTS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: '/dashboard' },
      {
        path: ':reportId',
        title: 'reports.detail.pageTitle',
        loadComponent: () => import('./pages/report-page.component').then((m) => m.ReportPageComponent),
      },
    ],
  },
];
