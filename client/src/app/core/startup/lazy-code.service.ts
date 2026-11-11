import { Injectable, Injector, Signal, effect, inject, signal, untracked } from '@angular/core';

import { OnlineStatusService } from '../online/online-status.service';
import { StartupService } from './startup.service';

/**
 * Loads the code of a deferred overlay (header popovers, menu drawers) after the first page, while online
 * (D11.5.3, decision 178 follow-up). `@defer (… prefetch when ready())` then finds the module in the browser's module
 * cache and cannot fail. Letting `@defer` prefetch on its own broke offline: a chunk request that started offline (or
 * was cut by the connection going away) failed for good, Angular reported NG0750 for the block without `@error` (the
 * shell's "Etwas ist schiefgelaufen" toast) and the menu never opened again until a reload. Here a failed import is
 * caught and tried again once the connection is back.
 */
@Injectable({ providedIn: 'root' })
export class LazyCodeService {
  private readonly injector = inject(Injector);
  private readonly pageShown = inject(StartupService).pageShown;
  private readonly online = inject(OnlineStatusService).online;
  private readonly entries = new Map<string, Signal<boolean>>();

  /** True once `load` has resolved; `key` names the module, so every caller of the same code shares one load. */
  ready(key: string, load: () => Promise<unknown>): Signal<boolean> {
    const known = this.entries.get(key);
    if (known) {
      return known;
    }
    const loaded = signal(false);
    let loading = false;
    effect(
      () => {
        if (!this.pageShown() || !this.online() || untracked(loaded) || loading) {
          return;
        }
        loading = true;
        load().then(
          () => loaded.set(true),
          () => {
            // offline or cut off: the next change of the connection state (back online) tries again
            loading = false;
          },
        );
      },
      { injector: this.injector },
    );
    const ready = loaded.asReadonly();
    this.entries.set(key, ready);
    return ready;
  }
}
