import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, Injectable, PLATFORM_ID, effect, inject, signal, untracked } from '@angular/core';
import { Subscription, interval } from 'rxjs';

import { Bell, BellsService } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { StartupService } from '../../core/startup/startup.service';

/** Badge refresh interval without realtime events (N3). */
export const BELL_POLL_MS = 60_000;

/**
 * Bell badge state (N3): unread count polled every 60 s, on `visibilitychange` and on every realtime (re)connect;
 * a realtime `BELL` event carries the new unread count. `version` increments whenever bells changed (event, mark
 * read, delete) so open lists reload.
 */
@Injectable({ providedIn: 'root' })
export class BellStore {
  private readonly api = inject(BellsService);
  private readonly session = inject(SessionService);
  private readonly realtime = inject(RealtimeService);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private poll?: Subscription;
  private request?: Subscription;

  readonly unreadCount = signal(0);
  readonly version = signal(0);

  constructor() {
    if (!this.browser) {
      return;
    }
    const document = inject(DOCUMENT);
    const startup = inject(StartupService);
    effect(() => {
      // the badge is not part of the first page: count after it has rendered (D11.5.3)
      const authenticated = this.session.isAuthenticated() && startup.pageShown();
      this.realtime.connectCount();
      untracked(() => (authenticated ? this.start() : this.stopPolling()));
    });
    const onVisible = () => {
      if (document.visibilityState === 'visible' && this.session.isAuthenticated()) {
        this.refresh();
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    const events = this.realtime.on('BELL').subscribe((event) => {
      const count = Number((event.payload as { unreadCount?: unknown } | undefined)?.unreadCount);
      if (Number.isFinite(count)) {
        this.unreadCount.set(count);
      } else {
        this.refresh();
      }
      this.version.update((v) => v + 1);
    });
    inject(DestroyRef).onDestroy(() => {
      document.removeEventListener('visibilitychange', onVisible);
      events.unsubscribe();
      this.stopPolling();
    });
  }

  /** Re-reads the unread count (errors keep the last value). */
  refresh(): void {
    this.request?.unsubscribe();
    this.request = this.api.countUnreadBells().subscribe({
      next: (count) => this.unreadCount.set(count.count),
      error: () => undefined,
    });
  }

  /** Bells were read or deleted on this page: recount and let other lists reload. */
  changed(): void {
    this.refresh();
    this.version.update((v) => v + 1);
  }

  /** Optimistic local decrement for bells just marked read. */
  markedRead(bells: readonly Bell[]): void {
    const unread = bells.filter((bell) => !bell.readAt).length;
    if (unread) {
      this.unreadCount.update((count) => Math.max(0, count - unread));
    }
  }

  private start(): void {
    this.refresh();
    if (!this.poll) {
      this.poll = interval(BELL_POLL_MS).subscribe(() => this.refresh());
    }
  }

  private stopPolling(): void {
    this.poll?.unsubscribe();
    this.poll = undefined;
    this.unreadCount.set(0);
  }
}
