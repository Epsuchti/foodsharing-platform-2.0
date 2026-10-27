import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AppInfo, PublicService } from '../api';

/** GET /api/public/app-info, loaded once at start (version, dev mode, enabled login methods). */
@Injectable({ providedIn: 'root' })
export class AppInfoService {
  private readonly publicApi = inject(PublicService);
  private pending: Promise<AppInfo | null> | null = null;

  readonly info = signal<AppInfo | null>(null);
  readonly devMode = computed(() => this.info()?.devMode ?? false);
  readonly version = computed(() => this.info()?.version ?? null);

  /** Loads once; failures resolve to null and are retried on the next call. */
  load(): Promise<AppInfo | null> {
    if (this.info()) {
      return Promise.resolve(this.info());
    }
    if (!this.pending) {
      this.pending = firstValueFrom(this.publicApi.getAppInfo())
        .then((info) => {
          this.info.set(info);
          return info;
        })
        .catch(() => null)
        .finally(() => {
          this.pending = null;
        });
    }
    return this.pending;
  }
}
