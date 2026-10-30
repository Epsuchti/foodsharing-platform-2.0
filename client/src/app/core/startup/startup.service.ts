import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable, filter, map, take } from 'rxjs';

import { FeatureToggleService } from '../feature-toggles/feature-toggle.service';
import { whenIdle } from '../theme/web-font';

/** Quiet time after the first page before the service worker registers (its install downloads every chunk). */
export const SERVICE_WORKER_DELAY_MS = 10_000;

/**
 * Start-up phase of the app (D11.5.3). `pageShown` turns true once the first page has rendered and the browser was
 * idle (set by ShellComponent). Background work that is not part of the page the visitor came for — the realtime
 * connection, bell badge polling, overlays, the web font, the feature toggles (A43) — waits for it, so it never
 * competes with the start-up requests (Lighthouse LCP on `/onboarding`).
 *
 * The service worker comes last (`serviceWorkerReady`, app.config.ts): its install prefetches every script and
 * catalog, which held back the requests of the first page's deferred blocks by 3–4 s when it registered as soon as
 * the app was stable. It registers once the browser is idle {@link SERVICE_WORKER_DELAY_MS} after the first page,
 * or right away when a feature needs it (`requestServiceWorker`, push sign-up).
 */
@Injectable({ providedIn: 'root' })
export class StartupService {
  private readonly featureToggles = inject(FeatureToggleService);
  private readonly view = inject(DOCUMENT).defaultView;
  private readonly shown = signal(false);
  private readonly serviceWorker = signal(false);
  readonly pageShown = this.shown.asReadonly();
  /** Emits once (then completes) when the service worker may register. */
  readonly serviceWorkerReady: Observable<void> = toObservable(this.serviceWorker).pipe(
    filter(Boolean),
    take(1),
    map(() => undefined),
  );

  markPageShown(): void {
    if (this.shown()) {
      return;
    }
    this.shown.set(true);
    // toggled nav items and pages appear once loaded; routes behind featureGuard load them on their own
    void this.featureToggles.load();
    const view = this.view;
    if (view) {
      whenIdle(view, () =>
        setTimeout(() => whenIdle(view, () => this.requestServiceWorker()), SERVICE_WORKER_DELAY_MS),
      );
    }
  }

  /** Lets the service worker register now (a feature needs its registration, e.g. push). */
  requestServiceWorker(): void {
    this.serviceWorker.set(true);
  }
}
