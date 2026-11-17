import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { FeatureToggleKey, PublicSettingsService } from '../../api';

export type FeatureToggles = Partial<Record<FeatureToggleKey, boolean>>;

/**
 * Feature toggles (A43) from `GET /api/public/feature-toggles`, loaded once after the first page
 * (StartupService.markPageShown) or earlier by `featureGuard`. Until loaded — and when loading fails — every toggle
 * counts as off, so toggled features never flash in; a failed load is retried on the next `load()`. Once loaded,
 * `<html data-feature-toggles>` is set: the toggled nav items and tabs are in place (journeys wait for it before they
 * navigate, so a tab or menu item never moves under the click).
 */
@Injectable({ providedIn: 'root' })
export class FeatureToggleService {
  private readonly api = inject(PublicSettingsService);
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private pending: Promise<FeatureToggles> | null = null;

  private readonly state = signal<FeatureToggles | null>(null);
  /** The loaded toggles (empty until loaded). */
  readonly toggles = computed<FeatureToggles>(() => this.state() ?? {});
  readonly loaded = computed(() => this.state() !== null);

  isEnabled(key: FeatureToggleKey): boolean {
    return this.toggles()[key] === true;
  }

  /**
   * Applies a toggle changed in this tab (admin settings, C11) so nav entries and guards follow at once; other
   * tabs and users see the change on their next start (the server caches toggles for 30 s).
   */
  apply(key: FeatureToggleKey, enabled: boolean): void {
    const current = this.state();
    if (current) {
      this.state.set({ ...current, [key]: enabled });
    }
  }

  /** Loads once; resolves to the toggles (empty on failure, retried next call). */
  load(): Promise<FeatureToggles> {
    const current = this.state();
    if (current) {
      return Promise.resolve(current);
    }
    if (!this.pending) {
      this.pending = firstValueFrom(this.api.getPublicFeatureToggles())
        .then((body) => {
          const toggles = body as FeatureToggles;
          this.state.set(toggles);
          if (this.browser) {
            this.document.documentElement.setAttribute('data-feature-toggles', '');
          }
          return toggles;
        })
        .catch(() => ({}) as FeatureToggles)
        .finally(() => {
          this.pending = null;
        });
    }
    return this.pending;
  }
}
