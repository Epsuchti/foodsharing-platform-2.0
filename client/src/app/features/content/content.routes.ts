import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

/**
 * Mounted at '' in app.routes.ts; paths inside are full paths.
 * `pages/:slug` (public CMS pages), `learn` + `learn/:key` (learning chapters, onboarding step 3) and
 * `admin/content/**` (editor for ORGA and PR admins; the server answers 403 for everybody else). C1.
 */
export const CONTENT_ROUTES: Routes = [
  {
    path: 'pages/:slug',
    title: 'content.page.pageTitle',
    loadComponent: () =>
      import('./pages/content-page.component').then((m) => m.ContentPageComponent),
  },
  {
    path: 'learn',
    title: 'content.learn.pageTitle',
    loadComponent: () =>
      import('./pages/learning-overview-page.component').then(
        (m) => m.LearningOverviewPageComponent,
      ),
  },
  {
    path: 'learn/:key',
    title: 'content.chapter.pageTitle',
    loadComponent: () =>
      import('./pages/learning-chapter-page.component').then((m) => m.LearningChapterPageComponent),
  },
  {
    path: 'admin/content',
    canActivate: [authGuard],
    title: 'content.admin.pageTitle',
    loadComponent: () =>
      import('./pages/content-admin-page.component').then((m) => m.ContentAdminPageComponent),
  },
  {
    path: 'admin/content/:slug',
    canActivate: [authGuard],
    title: 'content.editor.pageTitle',
    loadComponent: () =>
      import('./pages/content-editor-page.component').then((m) => m.ContentEditorPageComponent),
  },
];
