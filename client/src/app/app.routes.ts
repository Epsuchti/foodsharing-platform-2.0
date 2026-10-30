import { Routes } from '@angular/router';

import { consentGuard } from './core/auth/consent.guard';
import { catalogGuard } from './core/i18n/catalog.guard';

/**
 * One lazy entry per module (docs/conventions.md A7). Feature route files export `<MODULE>_ROUTES`.
 *
 * Modules with one URL prefix are mounted at it; modules spanning several prefixes (regions + groups,
 * intro pickups under `regions/:regionId/…`, …) are mounted at `''` with full paths inside. The router tries
 * the entries in order and falls through when a module's routes do not match, so the `''` mounts sit after the
 * public module (which only matches `/`) and before not-found.
 *
 * Everything runs below one parent route so `consentGuard` (pending legal consents, EA4) sees every
 * navigation.
 */
const MODULE_ROUTES: Routes = [
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
    path: 'admin/regions',
    loadChildren: () =>
      import('./features/region-admin/region-admin.routes').then((m) => m.REGION_ADMIN_ROUTES),
  },
  {
    path: 'quizzes',
    loadChildren: () => import('./features/quiz/quiz.routes').then((m) => m.QUIZ_ROUTES),
  },
  {
    path: 'onboarding',
    loadChildren: () =>
      import('./features/onboarding/onboarding.routes').then((m) => m.ONBOARDING_ROUTES),
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
    // `/`, `/r/:slug`, `/find-region`
    path: '',
    loadChildren: () => import('./features/public/public.routes').then((m) => m.PUBLIC_ROUTES),
  },
  // --- modules with several URL prefixes (full paths inside) ---
  {
    path: '',
    loadChildren: () => import('./features/regions/regions.routes').then((m) => m.REGIONS_ROUTES),
  },
  {
    path: '',
    loadChildren: () =>
      import('./features/intro-pickups/intro-pickups.routes').then((m) => m.INTRO_PICKUPS_ROUTES),
  },
  {
    path: '',
    loadChildren: () =>
      import('./features/verification/verification.routes').then((m) => m.VERIFICATION_ROUTES),
  },
  {
    path: '',
    loadChildren: () =>
      import('./features/users-admin/users-admin.routes').then((m) => m.USERS_ADMIN_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/legal/legal.routes').then((m) => m.LEGAL_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/content/content.routes').then((m) => m.CONTENT_ROUTES),
  },
  // --- M3 stores & pickups ---
  {
    path: '',
    loadChildren: () => import('./features/stores/stores.routes').then((m) => m.STORES_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/pickups/pickups.routes').then((m) => m.PICKUPS_ROUTES),
  },
  {
    path: 'store-chains',
    loadChildren: () =>
      import('./features/store-chains/store-chains.routes').then((m) => m.STORE_CHAINS_ROUTES),
  },
  {
    path: 'admin/store-categories',
    loadChildren: () =>
      import('./features/store-categories/store-categories.routes').then(
        (m) => m.STORE_CATEGORIES_ROUTES,
      ),
  },
  // --- M4 automations & notifications ---
  {
    path: 'bells',
    loadChildren: () => import('./features/bells/bells.routes').then((m) => m.BELLS_ROUTES),
  },
  {
    path: 'messages',
    loadChildren: () => import('./features/messaging/messaging.routes').then((m) => m.MESSAGING_ROUTES),
  },
  {
    path: 'mailboxes',
    loadChildren: () => import('./features/mailboxes/mailboxes.routes').then((m) => m.MAILBOXES_ROUTES),
  },
  {
    path: 'account/notifications',
    loadChildren: () =>
      import('./features/notifications/notifications.routes').then((m) => m.NOTIFICATIONS_ROUTES),
  },
  {
    path: 'admin/achievements',
    loadChildren: () =>
      import('./features/achievements/achievements.routes').then((m) => m.ACHIEVEMENTS_ROUTES),
  },
  {
    path: 'admin',
    loadChildren: () => import('./features/admin-ops/admin-ops.routes').then((m) => m.ADMIN_OPS_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/newsletters/newsletters.routes').then((m) => m.NEWSLETTERS_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/automation/automation.routes').then((m) => m.AUTOMATION_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/forum/forum.routes').then((m) => m.FORUM_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/events/events.routes').then((m) => m.EVENTS_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/polls/polls.routes').then((m) => m.POLLS_ROUTES),
  },
  {
    path: '',
    loadChildren: () => import('./features/surveys/surveys.routes').then((m) => m.SURVEYS_ROUTES),
  },
  // --- M6 community ---
  {
    path: 'profile',
    loadChildren: () => import('./features/profiles/profiles.routes').then((m) => m.PROFILES_ROUTES),
  },
  {
    path: 'baskets',
    loadChildren: () => import('./features/baskets/baskets.routes').then((m) => m.BASKETS_ROUTES),
  },
  {
    path: 'map',
    loadChildren: () => import('./features/map/map.routes').then((m) => m.MAP_ROUTES),
  },
  {
    path: 'food-share-points',
    loadChildren: () =>
      import('./features/food-share-points/food-share-points.routes').then((m) => m.FOOD_SHARE_POINTS_ROUTES),
  },
  {
    path: 'reports',
    loadChildren: () => import('./features/reports/reports.routes').then((m) => m.REPORTS_ROUTES),
  },
  {
    path: 'search',
    loadChildren: () => import('./features/search/search.routes').then((m) => m.SEARCH_ROUTES),
  },
  {
    path: 'resources',
    loadChildren: () => import('./features/resources/resources.routes').then((m) => m.RESOURCES_ROUTES),
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

export const routes: Routes = [
  {
    path: '',
    canActivateChild: [consentGuard, catalogGuard],
    children: MODULE_ROUTES,
  },
];

/**
 * Starts downloading the lazy module of the URL the app is opened on while the session and translations load
 * (start-up runs before the first navigation, so the router would only request it afterwards). The router's own
 * `import()` later resolves from the module cache. Only modules mounted at their own prefix are warmed. D11.5.3.
 */
export function warmInitialRoute(url: string): void {
  const path = url.split(/[?#]/)[0].replace(/^\/+/, '');
  const match = MODULE_ROUTES.find(
    (route) => route.path && (path === route.path || path.startsWith(`${route.path}/`)),
  );
  void match?.loadChildren?.();
}
