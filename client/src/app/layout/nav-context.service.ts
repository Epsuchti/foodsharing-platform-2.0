import { Injectable, computed, inject } from '@angular/core';

import { AppInfoService } from '../core/app-info.service';
import { SessionService } from '../core/auth/session.service';
import { FeatureToggleService } from '../core/feature-toggles/feature-toggle.service';
import { NavContext, mobileTabs, visibleNavItems } from './nav.config';

/** Signals of the visible navigation, derived from session + app info. */
@Injectable({ providedIn: 'root' })
export class NavContextService {
  private readonly session = inject(SessionService);
  private readonly appInfo = inject(AppInfoService);
  private readonly features = inject(FeatureToggleService);

  readonly context = computed<NavContext>(() => {
    const session = this.session.session();
    const authenticated = this.session.isAuthenticated();
    // read the signals the helpers depend on, so the computed tracks them
    this.session.role();
    this.session.permissions();
    this.features.toggles();
    return {
      session,
      isAuthenticated: authenticated,
      hasRole: (role) => this.session.hasRole(role),
      hasPermission: (permission) => this.session.hasPermission(permission),
      devMode: this.appInfo.devMode(),
      featureEnabled: (key) => this.features.isEnabled(key),
    };
  });

  readonly main = computed(() => visibleNavItems(this.context(), 'main'));
  readonly user = computed(() => visibleNavItems(this.context(), 'user'));
  readonly footer = computed(() => visibleNavItems(this.context(), 'footer'));
  readonly tabs = computed(() => mobileTabs(this.context()));
}
