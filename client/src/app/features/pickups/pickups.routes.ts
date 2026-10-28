import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';

/**
 * Mounted at '' in app.routes.ts (full paths inside, M3): `pickups` (where can I help), `pickups/mine`,
 * `pickups/agenda/:date`, `me/pickups/history` and `users/:userId/pickups` (admins).
 */
export const PICKUPS_ROUTES: Routes = [
  {
    path: 'pickups',
    pathMatch: 'full',
    canActivate: [authGuard],
    data: { roles: [Role.Foodsaver] },
    title: 'pickups.options.title',
    loadComponent: () => import('./pages/pickup-options-page.component').then((m) => m.PickupOptionsPageComponent),
  },
  {
    path: 'pickups/mine',
    canActivate: [authGuard],
    data: { roles: [Role.Foodsaver] },
    title: 'pickups.mine.title',
    loadComponent: () => import('./pages/my-pickups-page.component').then((m) => m.MyPickupsPageComponent),
  },
  {
    path: 'pickups/agenda/:date',
    canActivate: [authGuard],
    data: { roles: [Role.Foodsaver] },
    title: 'pickups.agenda.title',
    loadComponent: () => import('./pages/agenda-page.component').then((m) => m.AgendaPageComponent),
  },
];
