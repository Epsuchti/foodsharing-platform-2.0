import { Routes } from '@angular/router';

import { FeatureToggleKey } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';
import { featureGuard } from '../../core/feature-toggles/feature.guard';

const loadEditor = () =>
  import('./pages/blog-editor-page.component').then((m) => m.BlogEditorPageComponent);

/**
 * Mounted at `blog` (C2, BLOG toggle): the public list (`?region=`, `?page=`) and post (`/blog/<id>`, a backend deep
 * link), the management list and the editor (`/blog/new`, `/blog/<id>/edit` — the BLOG_NEW_CHECK bell links there).
 */
export const BLOG_ROUTES: Routes = [
  {
    path: '',
    canActivate: [featureGuard(FeatureToggleKey.Blog)],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'blog.list.pageTitle',
        loadComponent: () =>
          import('./pages/blog-list-page.component').then((m) => m.BlogListPageComponent),
      },
      {
        path: 'manage',
        canActivate: [authGuard],
        title: 'blog.manage.pageTitle',
        loadComponent: () =>
          import('./pages/blog-manage-page.component').then((m) => m.BlogManagePageComponent),
      },
      {
        path: 'new',
        canActivate: [authGuard],
        data: { id: 'new' },
        title: 'blog.editor.newPageTitle',
        loadComponent: loadEditor,
      },
      {
        path: ':id/edit',
        canActivate: [authGuard],
        title: 'blog.editor.pageTitle',
        loadComponent: loadEditor,
      },
      {
        path: ':id',
        title: 'blog.post.pageTitle',
        loadComponent: () =>
          import('./pages/blog-post-page.component').then((m) => m.BlogPostPageComponent),
      },
    ],
  },
];
