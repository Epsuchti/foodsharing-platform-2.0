import { Routes } from '@angular/router';

import { FeatureToggleKey, Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';
import { featureGuard } from '../../core/feature-toggles/feature.guard';

const oauthOn = featureGuard(FeatureToggleKey.OauthProvider);

/**
 * Mounted at '' in app.routes.ts (plan-m7 Q14, C13): the consent page `oauth/consent?request=` (minimal chrome; the
 * only Angular route below `/oauth/` — authorize, token and userinfo are server endpoints) and the ORGA client
 * administration `admin/oauth-clients`. The connections list lives in the account module (`/account/connections`).
 * Logged out, authGuard sends the consent page to the login and back (returnUrl keeps `?request=`).
 */
export const OAUTH_ROUTES: Routes = [
  {
    path: 'oauth/consent',
    canActivate: [authGuard, oauthOn],
    title: 'oauth.consent.pageTitle',
    data: { chrome: 'minimal' },
    loadComponent: () => import('./pages/consent-page.component').then((m) => m.ConsentPageComponent),
  },
  {
    path: 'admin/oauth-clients',
    canActivate: [authGuard, oauthOn],
    data: { roles: [Role.Orga] },
    title: 'oauth.clients.title',
    loadComponent: () => import('./pages/oauth-clients-page.component').then((m) => m.OAuthClientsPageComponent),
  },
];
