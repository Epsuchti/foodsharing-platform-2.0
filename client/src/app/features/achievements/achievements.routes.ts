import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';

/**
 * Mounted at `admin/achievements` in app.routes.ts (plan-m7 Q9, AC1, ORGA): the global achievements. The region tab
 * `/regions/:regionId/achievements` is a child route of the region page (regions.routes.ts).
 */
export const ACHIEVEMENTS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    data: { roles: [Role.Orga] },
    title: 'achievements.admin.title',
    loadComponent: () =>
      import('./pages/admin-achievements-page.component').then((m) => m.AdminAchievementsPageComponent),
  },
];
