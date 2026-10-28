import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

/**
 * Mounted at '' in app.routes.ts (M4 F5, N7): `/newsletters`, `/newsletters/:newsletterId`,
 * `/regions/:regionId/newsletters` (ORGA or region admins; the API decides), public
 * `/newsletter/unsubscribe/:token` without app menu.
 */
export const NEWSLETTERS_ROUTES: Routes = [
  {
    path: 'newsletters',
    canActivate: [authGuard],
    title: 'newsletters.list.title',
    loadComponent: () => import('./pages/newsletter-list-page.component').then((m) => m.NewsletterListPageComponent),
  },
  {
    path: 'newsletters/:newsletterId',
    canActivate: [authGuard],
    title: 'newsletters.editor.title',
    loadComponent: () => import('./pages/newsletter-editor-page.component').then((m) => m.NewsletterEditorPageComponent),
  },
  {
    path: 'regions/:regionId/newsletters',
    canActivate: [authGuard],
    title: 'newsletters.list.title',
    loadComponent: () => import('./pages/newsletter-list-page.component').then((m) => m.NewsletterListPageComponent),
  },
  {
    path: 'newsletter/unsubscribe/:token',
    title: 'newsletters.unsubscribe.title',
    data: { chrome: 'minimal' },
    loadComponent: () => import('./pages/unsubscribe-page.component').then((m) => m.UnsubscribePageComponent),
  },
];
