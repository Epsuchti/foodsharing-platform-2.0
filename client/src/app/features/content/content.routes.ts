import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';
import { ABOUT_PAGES, contentPath } from './content-paths';

const loadContentPage = () =>
  import('./pages/content-page.component').then((m) => m.ContentPageComponent);

/**
 * Mounted at '' in app.routes.ts; paths inside are full paths.
 * Public CMS pages: `about` (overview), `about/<page>`, `imprint`, `for-companies` (fixed slugs, C1), `pages/:slug`
 * (any published page) and `content/:slug` (links inside page bodies, redirected to the canonical path); `faq`,
 * `team` (C16), `statistics` (C3), `release-notes` (C4), `petition` (C8); `learn` + `learn/:key` (learning chapters,
 * onboarding step 3); `admin/content/**` (editor for ORGA and PR admins; the server answers 403 for everybody else)
 * and `admin/release-notes` (ORGA).
 */
export const CONTENT_ROUTES: Routes = [
  {
    path: 'about',
    pathMatch: 'full',
    title: 'content.about.pageTitle',
    loadComponent: () => import('./pages/about-page.component').then((m) => m.AboutPageComponent),
  },
  ...ABOUT_PAGES.filter((page) => page.slug).map((page) => ({
    path: page.path.slice(1),
    title: `content.about.page.${page.id}`,
    data: { slug: page.slug },
    loadComponent: loadContentPage,
  })),
  {
    path: 'pages/:slug',
    title: 'content.page.pageTitle',
    loadComponent: loadContentPage,
  },
  {
    path: 'content/:slug',
    redirectTo: ({ params }) => contentPath(params['slug'] ?? ''),
  },
  {
    path: 'faq',
    title: 'content.faq.pageTitle',
    loadComponent: () => import('./pages/faq-page.component').then((m) => m.FaqPageComponent),
  },
  {
    path: 'team',
    title: 'content.team.pageTitle',
    loadComponent: () => import('./pages/team-page.component').then((m) => m.TeamPageComponent),
  },
  {
    path: 'statistics',
    title: 'content.statistics.pageTitle',
    loadComponent: () =>
      import('./pages/statistics-page.component').then((m) => m.StatisticsPageComponent),
  },
  {
    path: 'release-notes',
    title: 'content.releaseNotes.pageTitle',
    loadComponent: () =>
      import('./pages/release-notes-page.component').then((m) => m.ReleaseNotesPageComponent),
  },
  {
    path: 'petition',
    title: 'content.petition.pageTitle',
    loadComponent: () =>
      import('./pages/petition-page.component').then((m) => m.PetitionPageComponent),
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
  {
    path: 'admin/release-notes',
    canActivate: [authGuard],
    data: { roles: [Role.Orga] },
    title: 'content.releaseAdmin.pageTitle',
    loadComponent: () =>
      import('./pages/release-notes-admin-page.component').then(
        (m) => m.ReleaseNotesAdminPageComponent,
      ),
  },
];
