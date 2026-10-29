import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

const form = () => import('./pages/poll-form-page.component').then((m) => m.PollFormPageComponent);

/**
 * Mounted at '' in app.routes.ts (full paths, M5 F6 / V1): `regions/:regionId/polls`, `polls/mine`,
 * `polls/new?regionId=`, `polls/:pollId`, `polls/:pollId/edit`. The server decides access (A36/A37c).
 */
export const POLLS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: 'regions/:regionId/polls',
        title: 'polls.title',
        loadComponent: () => import('./pages/polls-list-page.component').then((m) => m.PollsListPageComponent),
      },
      {
        path: 'polls/mine',
        title: 'polls.mine.title',
        loadComponent: () => import('./pages/polls-list-page.component').then((m) => m.PollsListPageComponent),
        data: { mine: true },
      },
      { path: 'polls/new', title: 'polls.form.newTitle', loadComponent: form },
      {
        path: 'polls/:pollId',
        title: 'polls.title',
        loadComponent: () => import('./pages/poll-page.component').then((m) => m.PollPageComponent),
      },
      { path: 'polls/:pollId/edit', title: 'polls.form.editTitle', loadComponent: form },
    ],
  },
];
