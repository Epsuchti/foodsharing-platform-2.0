import { HttpContext } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

import { PublicService } from '../../api';
import { NO_RETRY } from '../http/http-context';

export const PROBE_INTERVAL_MS = 15_000;

/**
 * Online state = browser online AND the server answered the last request. A request failing with status 0
 * (reported by the error interceptor) switches to offline and starts probing GET /api/public/app-info every
 * 15 s; the first success (or any later successful request) switches back. DataState auto-retries then.
 */
@Injectable({ providedIn: 'root' })
export class OnlineStatusService {
  private readonly document = inject(DOCUMENT);
  private readonly publicApi = inject(PublicService);

  private readonly browserOnline = signal(this.document.defaultView?.navigator?.onLine ?? true);
  private readonly serverReachable = signal(true);
  private probeTimer: ReturnType<typeof setInterval> | null = null;
  private probing = false;

  readonly online = computed(() => this.browserOnline() && this.serverReachable());
  /** True when the browser is online but the server did not answer. */
  readonly serverUnreachable = computed(() => this.browserOnline() && !this.serverReachable());

  constructor() {
    const view = this.document.defaultView;
    if (view) {
      const goOnline = () => {
        this.browserOnline.set(true);
        this.probe();
      };
      const goOffline = () => {
        this.browserOnline.set(false);
        this.startProbing();
      };
      view.addEventListener('online', goOnline);
      view.addEventListener('offline', goOffline);
      inject(DestroyRef).onDestroy(() => {
        view.removeEventListener('online', goOnline);
        view.removeEventListener('offline', goOffline);
        this.stopProbing();
      });
    }
  }

  /** A request failed without any HTTP response. */
  reportFailure(): void {
    if (this.serverReachable()) {
      this.serverReachable.set(false);
    }
    this.startProbing();
  }

  /** A request succeeded: the server is reachable. */
  reportSuccess(): void {
    if (!this.serverReachable()) {
      this.serverReachable.set(true);
    }
    if (this.online()) {
      this.stopProbing();
    }
  }

  /** Checks the connection now (offline banner "retry" button, browser `online` event, timer). */
  probe(): void {
    if (this.probing) {
      return;
    }
    this.probing = true;
    this.publicApi
      .getAppInfo('body', false, { context: new HttpContext().set(NO_RETRY, true) })
      .subscribe({
        next: () => {
          this.probing = false;
          this.browserOnline.set(true);
          this.reportSuccess();
        },
        error: () => {
          this.probing = false;
          this.serverReachable.set(false);
          this.startProbing();
        },
      });
  }

  private startProbing(): void {
    if (this.probeTimer === null) {
      this.probeTimer = setInterval(() => this.probe(), PROBE_INTERVAL_MS);
    }
  }

  private stopProbing(): void {
    if (this.probeTimer !== null) {
      clearInterval(this.probeTimer);
      this.probeTimer = null;
    }
  }
}
