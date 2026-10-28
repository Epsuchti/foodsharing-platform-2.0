import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, Injectable, PLATFORM_ID, effect, inject, signal, untracked } from '@angular/core';
import { Observable, Subject, filter } from 'rxjs';

import { RealtimeEvent, RealtimeEventType } from '../../api';
import { AppInfoService } from '../app-info.service';
import { SessionService } from '../auth/session.service';
import { backoffDelay, encodeFrame, parseFrames } from './stomp';

/** Heart-beat interval both ways (A32). */
const HEARTBEAT_MS = 10_000;
/** No frame (not even a heart-beat) for this long: the connection is dead. */
const SILENCE_MS = 25_000;
/** The "reconnecting…" chip shows after this long without a connection. */
const RECONNECTING_CHIP_MS = 5_000;
const DESTINATION = '/user/queue/events';

/** Dev/e2e hook (`window.__fsRealtime`, only when the backend runs in dev mode). */
export interface RealtimeDevHook {
  connected(): boolean;
  events: RealtimeEvent[];
  /** Simulates a dropped connection (the client reconnects with backoff). */
  drop(): void;
}

/**
 * The single realtime connection (A32): STOMP over a plain WebSocket at `/ws`, cookie-authenticated, subscribed to
 * `/user/queue/events`. Connects while a session exists, reconnects with exponential backoff (1 s → 30 s) and
 * immediately when the browser comes back online. Missed events are never assumed delivered: consumers refetch in
 * `onReconnect` (or by watching `connection`). STOMP callbacks run outside change detection, so state is signals.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly session = inject(SessionService);
  private readonly appInfo = inject(AppInfoService);
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));

  /** True while subscribed. */
  readonly connected = signal(false);
  /** Increments on every successful connect; consumers may refetch in an effect when it changes. */
  readonly connection = signal(0);
  /** Disconnected for more than 5 s while logged in (the shell shows a "reconnecting…" chip). */
  readonly reconnecting = signal(false);

  private readonly events = new Subject<RealtimeEvent>();
  readonly events$: Observable<RealtimeEvent> = this.events.asObservable();
  private readonly reconnectCallbacks = new Set<() => void>();

  private socket: WebSocket | null = null;
  private wanted = false;
  private attempt = 0;
  private buffer = '';
  private lastFrameAt = 0;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private chipTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private readonly devEvents: RealtimeEvent[] = [];

  constructor() {
    const view = this.document.defaultView;
    if (!this.browser || !view || typeof view.WebSocket === 'undefined') {
      return;
    }
    effect(() => {
      const authenticated = this.session.isAuthenticated();
      untracked(() => (authenticated ? this.start() : this.stop()));
    });
    effect(() => {
      if (this.appInfo.devMode()) {
        const hook: RealtimeDevHook = {
          connected: () => this.connected(),
          events: this.devEvents,
          drop: () => this.socket?.close(),
        };
        (view as unknown as { __fsRealtime?: RealtimeDevHook }).__fsRealtime = hook;
      }
    });
    const online = () => {
      if (this.wanted && !this.socket) {
        this.clearRetry();
        this.open();
      }
    };
    const offline = () => this.socket?.close();
    view.addEventListener('online', online);
    view.addEventListener('offline', offline);
    inject(DestroyRef).onDestroy(() => {
      view.removeEventListener('online', online);
      view.removeEventListener('offline', offline);
      this.stop();
    });
  }

  /** Events of the given types. */
  on(...types: RealtimeEventType[]): Observable<RealtimeEvent> {
    return this.events$.pipe(filter((event) => types.includes(event.type)));
  }

  /** Runs `callback` after every reconnect (not the first connect); returns the unregister function. */
  onReconnect(callback: () => void): () => void {
    this.reconnectCallbacks.add(callback);
    return () => this.reconnectCallbacks.delete(callback);
  }

  private start(): void {
    if (this.wanted) {
      return;
    }
    this.wanted = true;
    this.attempt = 0;
    this.open();
  }

  private stop(): void {
    this.wanted = false;
    this.clearRetry();
    this.clearChip();
    this.reconnecting.set(false);
    this.socket?.close();
    this.socket = null;
    this.connected.set(false);
  }

  private open(): void {
    const view = this.document.defaultView!;
    const scheme = view.location.protocol === 'https:' ? 'wss' : 'ws';
    let socket: WebSocket;
    try {
      socket = new view.WebSocket(`${scheme}://${view.location.host}/ws`);
    } catch {
      this.scheduleRetry();
      return;
    }
    this.socket = socket;
    this.buffer = '';
    socket.onopen = () => {
      this.lastFrameAt = Date.now();
      socket.send(
        encodeFrame('CONNECT', {
          'accept-version': '1.2',
          host: view.location.hostname,
          'heart-beat': `${HEARTBEAT_MS},${HEARTBEAT_MS}`,
        }),
      );
    };
    socket.onmessage = (message: MessageEvent) => {
      this.lastFrameAt = Date.now();
      const { frames, rest } = parseFrames(this.buffer + String(message.data));
      this.buffer = rest;
      for (const frame of frames) {
        if (frame.command === 'CONNECTED') {
          socket.send(encodeFrame('SUBSCRIBE', { id: 'events', destination: DESTINATION }));
          this.onConnected(socket);
        } else if (frame.command === 'MESSAGE') {
          this.dispatch(frame.body);
        } else if (frame.command === 'ERROR') {
          socket.close();
        }
      }
    };
    socket.onclose = () => {
      if (this.socket !== socket) {
        return;
      }
      this.socket = null;
      this.stopHeartbeat();
      this.connected.set(false);
      if (this.wanted) {
        this.startChip();
        this.scheduleRetry();
      }
    };
  }

  private onConnected(socket: WebSocket): void {
    const reconnect = this.connection() > 0;
    this.attempt = 0;
    this.clearChip();
    this.reconnecting.set(false);
    this.connected.set(true);
    this.connection.update((count) => count + 1);
    this.startHeartbeat(socket);
    if (reconnect) {
      for (const callback of [...this.reconnectCallbacks]) {
        callback();
      }
    }
  }

  private dispatch(body: string): void {
    let event: RealtimeEvent;
    try {
      event = JSON.parse(body) as RealtimeEvent;
    } catch {
      return;
    }
    if (event.type === RealtimeEventType.Ping) {
      return;
    }
    if (this.appInfo.devMode()) {
      this.devEvents.push(event);
      this.devEvents.splice(0, Math.max(0, this.devEvents.length - 100));
    }
    this.events.next(event);
  }

  private startHeartbeat(socket: WebSocket): void {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (Date.now() - this.lastFrameAt > SILENCE_MS) {
        socket.close();
        return;
      }
      if (socket.readyState === socket.OPEN) {
        socket.send('\n');
      }
    }, HEARTBEAT_MS);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private scheduleRetry(): void {
    this.clearRetry();
    const delay = backoffDelay(this.attempt++);
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      if (this.wanted && !this.socket) {
        this.open();
      }
    }, delay);
  }

  private clearRetry(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  private startChip(): void {
    if (!this.chipTimer && !this.reconnecting()) {
      this.chipTimer = setTimeout(() => {
        this.chipTimer = null;
        if (this.wanted && !this.connected()) {
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
