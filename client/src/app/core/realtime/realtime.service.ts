import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, Injectable, PLATFORM_ID, effect, inject, signal } from '@angular/core';
import { Observable, Subject, filter } from 'rxjs';

import { RealtimeEvent, RealtimeEventType } from '../../api';
import { SessionService } from '../auth/session.service';

/** Minimal surface of `@stomp/stompjs` `Client` used here (the library is loaded lazily). */
interface StompClientLike {
  activate(): void;
  deactivate(): Promise<void>;
  subscribe(destination: string, callback: (message: { body: string }) => void): unknown;
  reconnectDelay: number;
}

export const REALTIME_MIN_DELAY_MS = 1_000;
export const REALTIME_MAX_DELAY_MS = 30_000;

/** Next reconnect delay: doubles from 1 s up to 30 s (A32). */
export function nextReconnectDelay(current: number): number {
  return Math.min(Math.max(current * 2, REALTIME_MIN_DELAY_MS), REALTIME_MAX_DELAY_MS);
}

/**
 * A32 realtime client: one STOMP connection to `/ws` while the user is signed in, subscribed to
 * `/user/queue/events`. `connected` turns true on every (re)connect; consumers refetch then, because missed events
 * are never assumed delivered. `@stomp/stompjs` is imported lazily so public pages never load it.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly session = inject(SessionService);
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly events = new Subject<RealtimeEvent>();
  private client: StompClientLike | null = null;
  private generation = 0;

  /** True while the STOMP session is up. */
  readonly connected = signal(false);
  /** Increments on every successful (re)connect: effects reading it refetch. */
  readonly connectCount = signal(0);

  constructor() {
    if (!this.browser) {
      return;
    }
    effect(() => {
      if (this.session.isAuthenticated()) {
        void this.start();
      } else {
        void this.stop();
      }
    });
    inject(DestroyRef).onDestroy(() => void this.stop());
  }

  /** Events of one type (`BELL`, `CONVERSATION_MESSAGE`, …). */
  on(type: RealtimeEventType | `${RealtimeEventType}`): Observable<RealtimeEvent> {
    return this.events.pipe(filter((event) => event.type === type));
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
      client.subscribe('/user/queue/events', (message) => {
        try {
          this.events.next(JSON.parse(message.body) as RealtimeEvent);
        } catch {
          // malformed frame: ignore, the next refetch repairs the state
        }
      });
      this.connected.set(true);
      this.connectCount.update((count) => count + 1);
    };
    client.onWebSocketClose = () => {
      this.connected.set(false);
      client.reconnectDelay = nextReconnectDelay(client.reconnectDelay);
    };
    this.client = client;
    client.activate();
  }

  private async stop(): Promise<void> {
    this.generation++;
    const client = this.client;
    this.client = null;
    this.connected.set(false);
    if (client) {
      await client.deactivate();
    }
  }
}
