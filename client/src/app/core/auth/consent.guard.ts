import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';

import { safeReturnUrl } from './return-url';
import { SessionService } from './session.service';

/** URL prefixes reachable while consents are pending (the consent page itself, the documents, login/logout, …). */
export const CONSENT_EXEMPT_PREFIXES: readonly string[] = [
  '/consent',
  '/legal',
  '/login',
  '/pages',
  '/forbidden',
  '/dev',
  // Ve1: the legal agreement is accepted in the foodsaver quiz confirmation, so its results page stays reachable
  '/quizzes/foodsaver/results',
];

export function isConsentExempt(url: string): boolean {
  const path = url.split(/[?#]/)[0];
  return (
    path === '/' ||
    CONSENT_EXEMPT_PREFIXES.some((prefix) => path === prefix || path.startsWith(prefix + '/'))
  );
}

/**
 * EA4: a signed-in user with pending legal consents (`SessionInfo.pendingConsents`, e.g. a new privacy policy
 * version) is sent to `/consent?returnUrl=…` before any other page. Wired once on the root route
 * (`canActivateChild` in app.routes.ts); the consent page arrives in M2 T20.
 */
export const consentGuard: CanActivateChildFn = (_route, state) => {
  const session = inject(SessionService);
  if (!session.isAuthenticated() || isConsentExempt(state.url)) {
    return true;
  }
  const pending = session.session().pendingConsents ?? [];
  if (pending.length === 0) {
    return true;
  }
  const returnUrl = safeReturnUrl(state.url);
  return inject(Router).createUrlTree(['/consent'], returnUrl ? { queryParams: { returnUrl } } : {});
};
