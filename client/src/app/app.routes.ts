import { Routes } from '@angular/router';

/**
 * One lazy entry per module (docs/conventions.md A7). Feature route files export `<MODULE>_ROUTES`.
 * The public module (path '') comes last so it does not load for every other URL.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadChildren: () => import('./features/auth/auth.routes').then((m) => m.AUTH_ROUTES),
  },
  {
    path: 'account',
    loadChildren: () => import('./features/account/account.routes').then((m) => m.ACCOUNT_ROUTES),
  },
  {
    path: 'me',
    loadChildren: () => import('./features/history/history.routes').then((m) => m.HISTORY_ROUTES),
  },
  {
    path: 'dashboard',
    loadChildren: () =>
      import('./features/dashboard/dashboard.routes').then((m) => m.DASHBOARD_ROUTES),
  },
  {
    path: 'dev',
    loadChildren: () => import('./features/dev/dev.routes').then((m) => m.DEV_ROUTES),
  },
  {
    path: 'forbidden',
    title: 'common.pageTitle.forbidden',
    loadComponent: () =>
      import('./features/errors/pages/forbidden-page.component').then(
        (m) => m.ForbiddenPageComponent,
      ),
  },
  {
    path: '',
    loadChildren: () => import('./features/public/public.routes').then((m) => m.PUBLIC_ROUTES),
  },
  {
    path: '**',
    title: 'common.pageTitle.notFound',
    loadComponent: () =>
      import('./features/errors/pages/not-found-page.component').then(
        (m) => m.NotFoundPageComponent,
      ),
  },
];
