import { Routes } from '@angular/router';

import { devModeGuard } from '../../core/auth/dev-mode.guard';

export const DEV_ROUTES: Routes = [
  {
    path: '',
    canActivate: [devModeGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'gallery' },
      {
        path: 'gallery',
        title: 'dev.gallery.pageTitle',
        loadComponent: () =>
          import('./pages/gallery-page.component').then((m) => m.GalleryPageComponent),
      },
    ],
  },
];
