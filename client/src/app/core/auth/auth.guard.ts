import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Data, Router } from '@angular/router';

import { Role } from '../../api';
import { safeReturnUrl } from './return-url';
import { SessionService } from './session.service';

/** Scope of every region (ORGA gets `<PERMISSION>@*`, conventions A10). */
export const ANY_SCOPE = '*';

/**
 * Route `data` understood by authGuard:
 * - `roles: [Role.Ambassador]`: at least that role (any of the listed).
 * - `permissions: [Permission.CanManageUsers, Permission.CanGiveIntroPickups]`: any of them (reference "anyOf").
 * - `scope: 'route:regionId'`: the permissions must hold for the region in that route param
 *   (`CAN_MANAGE_USERS@<regionId>` or `@*`); without `scope` a grant for any region is enough.
 */
export interface RouteAccessData {
  roles?: Role[];
  permissions?: string[];
  scope?: `route:${string}`;
}

/** `CAN_MANAGE_USERS@<uuid>` → { permission, scope }; an unscoped entry has scope undefined. */
export function parseScopedPermission(entry: string): { permission: string; scope?: string } {
  const at = entry.lastIndexOf('@');
  return at < 0
    ? { permission: entry }
    : { permission: entry.slice(0, at), scope: entry.slice(at + 1) };
}

/**
 * True when `granted` (SessionInfo.permissions, A10 format) contains `permission` for `scopeId`
 * (`@<scopeId>` or `@*`); without `scopeId` any grant of that permission counts.
 */
export function hasScopedPermission(
  granted: readonly string[],
  permission: string,
  scopeId?: string | null,
): boolean {
  return granted.some((entry) => {
    const parsed = parseScopedPermission(entry);
    if (parsed.permission !== permission) {
      return false;
    }
    if (!scopeId || parsed.scope === undefined) {
      return true;
    }
    return parsed.scope === ANY_SCOPE || parsed.scope === scopeId;
  });
}

/** Region id a route's `data.scope` points to (`route:regionId` → the `regionId` param, inherited). */
export function scopeIdOf(route: ActivatedRouteSnapshot | undefined, data: RouteAccessData): string | null {
  if (!data.scope || !route) {
    return null;
  }
  const param = data.scope.slice('route:'.length);
  for (let current: ActivatedRouteSnapshot | null = route; current; current = current.parent) {
    const value = current.paramMap.get(param);
    if (value) {
      return value;
    }
  }
  return null;
}

/** True when the session satisfies the `roles` / `permissions` of a route's data. */
export function routeAccess(
  session: SessionService,
  data: Data | undefined,
  route?: ActivatedRouteSnapshot,
): boolean {
  const access = (data ?? {}) as RouteAccessData;
  if (!session.hasAnyRole(access.roles)) {
    return false;
  }
  if (!access.permissions?.length) {
    return true;
  }
  const scopeId = scopeIdOf(route, access);
  if (access.scope && !scopeId) {
    return false;
  }
  const granted = session.permissions();
  return access.permissions.some((permission) => hasScopedPermission(granted, permission, scopeId));
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
  if (!routeAccess(session, route.data, route)) {
    return router.createUrlTree(['/forbidden']);
  }
  return true;
};
