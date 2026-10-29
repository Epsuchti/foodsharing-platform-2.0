import { Injectable, inject, signal } from '@angular/core';

import { FeatureToggleService } from '../feature-toggles/feature-toggle.service';

/**
 * Start-up phase of the app (D11.5.3). `pageShown` turns true once the first page has rendered and the browser was
 * idle (set by ShellComponent). Background work that is not part of the page the visitor came for — the realtime
 * connection, bell badge polling, overlays, the web font, the feature toggles (A43) — waits for it, so it never
 * competes with the start-up requests (Lighthouse LCP on `/onboarding`).
 */
@Injectable({ providedIn: 'root' })
export class StartupService {
  private readonly featureToggles = inject(FeatureToggleService);
  private readonly shown = signal(false);
  readonly pageShown = this.shown.asReadonly();

  markPageShown(): void {
    if (this.shown()) {
      return;
    }
    this.shown.set(true);
    // toggled nav items and pages appear once loaded; routes behind featureGuard load them on their own
    void this.featureToggles.load();
  }
}
