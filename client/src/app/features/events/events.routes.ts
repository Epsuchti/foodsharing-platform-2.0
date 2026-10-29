import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

const form = () => import('./pages/event-form-page.component').then((m) => m.EventFormPageComponent);

/**
 * Mounted at '' in app.routes.ts (full paths, M5 F5 / E1–E3): `regions/:regionId/events`, `events/mine`,
 * `events/new?regionId=`, `events/:eventId`, `events/:eventId/edit`. Public events are readable by every
 * logged-in user; the server decides (A36/A37b).
 */
export const EVENTS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: 'regions/:regionId/events',
        title: 'events.title',
        loadComponent: () => import('./pages/region-events-page.component').then((m) => m.RegionEventsPageComponent),
      },
      {
        path: 'events/mine',
        title: 'events.mine.title',
        loadComponent: () => import('./pages/my-events-page.component').then((m) => m.MyEventsPageComponent),
      },
      { path: 'events/new', title: 'events.form.newTitle', loadComponent: form },
      {
        path: 'events/:eventId',
        title: 'events.title',
        loadComponent: () => import('./pages/event-page.component').then((m) => m.EventPageComponent),
      },
      { path: 'events/:eventId/edit', title: 'events.form.editTitle', loadComponent: form },
    ],
  },
];
