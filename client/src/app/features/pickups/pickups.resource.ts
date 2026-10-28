import { Injectable, inject, signal } from '@angular/core';

import { PublicService } from '../../api';

/**
 * The server's "now" (dev clock in local/e2e), as an offset to `Date.now()` measured once from
 * `getAppInfo().serverTime`. Until it is known, `now()` is the browser time.
 */
@Injectable({ providedIn: 'root' })
export class ServerClock {
  private readonly publicApi = inject(PublicService);
  private readonly offset = signal<number | null>(null);
  private requested = false;

  /** Server time in epoch milliseconds (reactive: changes once the offset is known). */
  now(): number {
    this.ensure();
    return Date.now() + (this.offset() ?? 0);
  }

  private ensure(): void {
    if (this.requested) return;
    this.requested = true;
    const sent = Date.now();
    this.publicApi.getAppInfo().subscribe({
      next: (info) => {
        const server = Date.parse(info.serverTime);
        if (!Number.isNaN(server)) {
          // the server stamped its time somewhere between sending and receiving: assume the middle
          this.offset.set(server - (sent + Date.now()) / 2);
        }
      },
      error: () => {
        this.requested = false;
      },
    });
  }
}
