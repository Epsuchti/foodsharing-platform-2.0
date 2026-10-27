import { Injectable, computed, signal } from '@angular/core';

/** Number of HTTP requests in flight, fed by the loading interceptor and shown as the request bar. */
@Injectable({ providedIn: 'root' })
export class HttpActivityService {
  private readonly pending = signal(0);
  readonly count = this.pending.asReadonly();
  readonly busy = computed(() => this.pending() > 0);

  start(): void {
    this.pending.update((count) => count + 1);
  }

  end(): void {
    this.pending.update((count) => Math.max(0, count - 1));
  }
}
