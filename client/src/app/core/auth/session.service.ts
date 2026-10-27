import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AuthService, Role, SessionInfo, SessionUser } from '../../api';

/** Journey roles in ascending order; `hasRole(r)` is true for r and every higher role. */
export const ROLE_ORDER: readonly Role[] = [
  Role.Foodsharer,
  Role.Foodsaver,
  Role.StoreManager,
  Role.Ambassador,
  Role.Orga,
];

const ANONYMOUS: SessionInfo = { authenticated: false, permissions: [] };

/**
 * The current session as reported by GET /api/auth/session (the only bootstrap source; nothing is kept in
 * localStorage). The session cookie is HttpOnly, so the client never sees a token.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly authApi = inject(AuthService);

  private readonly state = signal<SessionInfo>(ANONYMOUS);
  /** True once bootstrap() finished (successfully or not). */
  readonly loaded = signal(false);

  readonly session = this.state.asReadonly();
  readonly user = computed<SessionUser | null>(() =>
    this.state().authenticated ? (this.state().user ?? null) : null,
  );
  readonly isAuthenticated = computed(() => this.state().authenticated && !!this.state().user);
  readonly role = computed<Role | null>(() => this.user()?.role ?? null);
  readonly permissions = computed(() => this.state().permissions ?? []);

  /** Loads the session; network or server errors leave the app anonymous instead of blocking the start. */
  async bootstrap(): Promise<SessionInfo> {
    try {
      const info = await firstValueFrom(this.authApi.getSession());
      this.applySession(info);
    } catch {
      this.state.set(ANONYMOUS);
    } finally {
      this.loaded.set(true);
    }
    return this.state();
  }

  /**
   * Takes over a session from the server (bootstrap, login, refresh). Returns true when the user, role or
   * permissions changed, so callers can re-check the current route.
   */
  applySession(info: SessionInfo): boolean {
    const before = this.fingerprint(this.state());
    this.state.set({ ...info, permissions: info.permissions ?? [] });
    this.loaded.set(true);
    return before !== this.fingerprint(this.state());
  }

  /** Updates fields of the current user locally (e.g. after PATCH /api/users/me/settings). */
  patchUser(changes: Partial<SessionUser>): void {
    const current = this.state();
    if (current.user) {
      this.state.set({ ...current, user: { ...current.user, ...changes } });
    }
  }

  clear(): void {
    this.state.set(ANONYMOUS);
  }

  /** True when the user has at least the given role (role hierarchy, ORGA has everything). */
  hasRole(minimum: Role): boolean {
    const role = this.role();
    return !!role && ROLE_ORDER.indexOf(role) >= ROLE_ORDER.indexOf(minimum);
  }

  /** True when the user has at least one of the roles (each compared hierarchically). */
  hasAnyRole(roles: readonly Role[] | undefined): boolean {
    return !roles?.length || roles.some((role) => this.hasRole(role));
  }

  hasPermission(permission: string): boolean {
    return this.permissions().includes(permission);
  }

  hasAnyPermission(permissions: readonly string[] | undefined): boolean {
    return !permissions?.length || permissions.some((permission) => this.hasPermission(permission));
  }

  private fingerprint(info: SessionInfo): string {
    return [
      info.authenticated,
      info.user?.id,
      info.user?.role,
      [...(info.permissions ?? [])].sort().join(','),
    ].join('|');
  }
}
