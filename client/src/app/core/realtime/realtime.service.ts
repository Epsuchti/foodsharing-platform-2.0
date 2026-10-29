import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, Injectable, PLATFORM_ID, effect, inject, signal } from '@angular/core';
import { Observable, Subject, filter } from 'rxjs';

import { RealtimeEvent, RealtimeEventType } from '../../api';
import { AppInfoService } from '../app-info.service';
import { SessionService } from '../auth/session.service';
import { StartupService } from '../startup/startup.service';

/** Minimal surface of `@stomp/stompjs` `Client` used here (the library is loaded lazily). */
interface StompClientLike {
  activate(): void;
  deactivate(): Promise<void>;
  forceDisconnect(): void;
  subscribe(destination: string, callback: (message: { body: string }) => void): unknown;
  reconnectDelay: number;
}

export const REALTIME_MIN_DELAY_MS = 1_000;
export const REALTIME_MAX_DELAY_MS = 30_000;
/** The "reconnecting…" chip shows after this long without a connection. */
const RECONNECTING_CHIP_MS = 5_000;

/** Next reconnect delay: doubles from 1 s up to 30 s (A32). */
export function nextReconnectDelay(current: number): number {
  return Math.min(Math.max(current * 2, REALTIME_MIN_DELAY_MS), REALTIME_MAX_DELAY_MS);
}

/** Dev/e2e hook (`window.__fsRealtime`, only when the backend runs in dev mode). */
export interface RealtimeDevHook {
  connected(): boolean;
  /** Events received so far (last 100). */
  events: RealtimeEvent[];
  /** Simulates a dropped connection (the client reconnects with backoff). */
  drop(): void;
}

/**
 * A32 realtime client: one STOMP connection to `/ws` while the user is signed in, subscribed to
 * `/user/queue/events`. `connected` turns true on every (re)connect; consumers refetch then (`connectCount` in an
 * effect, or `onReconnect`), because missed events are never assumed delivered. `@stomp/stompjs` is imported lazily
 * so public pages never load it. Going offline drops the socket; coming back online reconnects at once.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly session = inject(SessionService);
  private readonly appInfo = inject(AppInfoService);
  private readonly startup = inject(StartupService);
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly events = new Subject<RealtimeEvent>();
  private readonly reconnectCallbacks = new Set<() => void>();
  private readonly devEvents: RealtimeEvent[] = [];
  private client: StompClientLike | null = null;
  private generation = 0;
  private chipTimer: ReturnType<typeof setTimeout> | null = null;

  /** True while the STOMP session is up. */
  readonly connected = signal(false);
  /** Increments on every successful (re)connect: effects reading it refetch. */
  readonly connectCount = signal(0);
  /** Disconnected for more than 5 s while signed in (the shell shows a "reconnecting…" chip). */
  readonly reconnecting = signal(false);

  constructor() {
    if (!this.browser) {
      return;
    }
    effect(() => {
      // connect only after the first page has rendered (D11.5.3): the socket is not part of what the visitor came for
      if (this.session.isAuthenticated()) {
        if (this.startup.pageShown()) void this.start();
      } else {
        void this.stop();
      }
    });
    const view = this.document.defaultView;
    effect(() => {
      if (view && this.appInfo.devMode()) {
        const hook: RealtimeDevHook = {
          connected: () => this.connected(),
          events: this.devEvents,
          drop: () => this.client?.forceDisconnect(),
        };
        (view as unknown as { __fsRealtime?: RealtimeDevHook }).__fsRealtime = hook;
      }
    });
    const offline = () => this.client?.forceDisconnect();
    const online = () => {
      // skip the pending backoff: reconnect now
      if (this.client && !this.connected()) {
        const client = this.client;
        client.reconnectDelay = REALTIME_MIN_DELAY_MS;
        void client.deactivate().then(() => {
          if (this.client === client) {
            client.activate();
          }
        });
      }
    };
    view?.addEventListener('offline', offline);
    view?.addEventListener('online', online);
    inject(DestroyRef).onDestroy(() => {
      view?.removeEventListener('offline', offline);
      view?.removeEventListener('online', online);
      void this.stop();
    });
  }

  /** Events of the given types (`BELL`, `CONVERSATION_MESSAGE`, …). */
  on(...types: (RealtimeEventType | `${RealtimeEventType}`)[]): Observable<RealtimeEvent> {
    return this.events.pipe(filter((event) => (types as string[]).includes(event.type)));
  }

  /** Runs `callback` after every (re)connect, the first one included; returns the unregister function. */
  onReconnect(callback: () => void): () => void {
    this.reconnectCallbacks.add(callback);
    return () => this.reconnectCallbacks.delete(callback);
  }

  private async start(): Promise<void> {
    if (this.client) {
      return;
    }
    const generation = ++this.generation;
    const { Client } = await import('@stomp/stompjs');
    if (generation !== this.generation || this.client) {
      return;
    }
    const location = this.document.location;
    const client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
      reconnectDelay: REALTIME_MIN_DELAY_MS,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      debug: () => undefined,
    });
    client.onConnect = () => {
      client.reconnectDelay = REALTIME_MIN_DELAY_MS;
      client.subscribe('/user/queue/events', (message) => this.dispatch(message.body));
      this.clearChip();
      this.reconnecting.set(false);
      this.connected.set(true);
      this.connectCount.update((count) => count + 1);
      // also on the first connect: it starts only after the first page (D11.5.3), so pages opened before it
      // may have missed events and refetch like after a reconnect
      for (const callback of [...this.reconnectCallbacks]) {
        callback();
      }
    };
    client.onWebSocketClose = () => {
      this.connected.set(false);
      client.reconnectDelay = nextReconnectDelay(client.reconnectDelay);
      this.startChip();
    };
    this.client = client;
    client.activate();
  }

  private dispatch(body: string): void {
    let event: RealtimeEvent;
    try {
      event = JSON.parse(body) as RealtimeEvent;
    } catch {
      // malformed frame: ignore, the next refetch repairs the state
      return;
    }
    if (this.appInfo.devMode()) {
      this.devEvents.push(event);
      this.devEvents.splice(0, Math.max(0, this.devEvents.length - 100));
    }
    this.events.next(event);
  }

  private async stop(): Promise<void> {
    this.generation++;
    const client = this.client;
    this.client = null;
    this.clearChip();
    this.reconnecting.set(false);
    this.connected.set(false);
    if (client) {
      await client.deactivate();
    }
  }

  private startChip(): void {
    if (!this.chipTimer && !this.reconnecting()) {
      this.chipTimer = setTimeout(() => {
        this.chipTimer = null;
        if (this.client && !this.connected()) {
          this.reconnecting.set(true);
        }
      }, RECONNECTING_CHIP_MS);
    }
  }

  private clearChip(): void {
    if (this.chipTimer) {
      clearTimeout(this.chipTimer);
      this.chipTimer = null;
    }
  }
}
