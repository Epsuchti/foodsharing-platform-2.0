import { inject } from '@angular/core';
import { CanActivateFn, Data, Router } from '@angular/router';

import { Role } from '../../api';
import { safeReturnUrl } from './return-url';
import { SessionService } from './session.service';

/** Route `data` understood by authGuard: `{ roles: [Role.Ambassador] }` = at least that role. */
export interface RouteAccessData {
  roles?: Role[];
  permissions?: string[];
}

/** True when the session satisfies the `roles` / `permissions` of a route's data. */
export function routeAccess(session: SessionService, data: Data | undefined): boolean {
  const access = (data ?? {}) as RouteAccessData;
  return session.hasAnyRole(access.roles) && session.hasAnyPermission(access.permissions);
}

/**
 * Logged out → /login?returnUrl=<target>; logged in without the required role/permission → /forbidden.
 * The session is bootstrapped by the app initializer before the first navigation.
 */
export const authGuard: CanActivateFn = (route, state) => {
  const session = inject(SessionService);
  const router = inject(Router);
  if (!session.isAuthenticated()) {
    const returnUrl = safeReturnUrl(state.url);
    return router.createUrlTree(['/login'], returnUrl ? { queryParams: { returnUrl } } : {});
  }
  if (!routeAccess(session, route.data)) {
    return router.createUrlTree(['/forbidden']);
  }
  return true;
};
