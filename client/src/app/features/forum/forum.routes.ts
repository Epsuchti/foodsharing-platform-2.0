import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { homeRegionGuard } from '../../core/region/home-region.guard';

const list = () => import('./pages/forum-page.component').then((m) => m.ForumPageComponent);
const newThread = () => import('./pages/new-thread-page.component').then((m) => m.NewThreadPageComponent);

/**
 * Mounted at '' in app.routes.ts (full paths, M5 F4): `regions/:regionId/forum[/ambassadors][/new]`,
 * `forum/threads/:threadId` and `regions/:regionId/moderation-log`. Groups use their region id. The server decides
 * access (A36); the client only hides entries it knows are unavailable. `regions/home/…` resolves to the active or
 * home region (homeRegionGuard, the side menu's Forum item, decision 149).
 */
export const FORUM_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: 'regions/:regionId/forum', title: 'forum.title', canActivate: [homeRegionGuard], loadComponent: list },
      {
        path: 'regions/:regionId/forum/new',
        title: 'forum.new.title',
        canActivate: [homeRegionGuard],
        loadComponent: newThread,
      },
      {
        path: 'regions/:regionId/forum/ambassadors',
        title: 'forum.ambassadors',
        canActivate: [homeRegionGuard],
        loadComponent: list,
        data: { ambassadorForum: true },
      },
      {
        path: 'regions/:regionId/forum/ambassadors/new',
        title: 'forum.new.title',
        canActivate: [homeRegionGuard],
        loadComponent: newThread,
        data: { ambassadorForum: true },
      },
      {
        path: 'forum/threads/:threadId',
        title: 'forum.thread.title',
        loadComponent: () => import('./pages/thread-page.component').then((m) => m.ThreadPageComponent),
      },
      {
        path: 'regions/:regionId/moderation-log',
        title: 'forum.log.title',
        canActivate: [homeRegionGuard],
        loadComponent: () => import('./pages/moderation-log-page.component').then((m) => m.ModerationLogPageComponent),
      },
    ],
  },
];
