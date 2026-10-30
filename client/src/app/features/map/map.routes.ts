import { Routes } from '@angular/router';

/** Mounted at `map` in app.routes.ts (plan-m7 Q7, M1): public; signed-in users get more layers. */
export const MAP_ROUTES: Routes = [
  {
    path: '',
    title: 'map.page.title',
    loadComponent: () => import('./pages/map-page.component').then((m) => m.MapPageComponent),
  },
];
