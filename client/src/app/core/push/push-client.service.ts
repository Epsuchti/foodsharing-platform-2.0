import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';

import { StartupService } from '../startup/startup.service';

/** Push state of this browser (N4): no API / blocked by the user / not asked yet / allowed. */
export type PushPermission = 'unsupported' | 'denied' | 'default' | 'granted';

/** Subscription data the backend stores (`PushSubscriptionCreate`). */
export interface PushSubscriptionData {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

const READY_TIMEOUT_MS = 10_000;

/** VAPID public key (URL-safe base64) → bytes for `applicationServerKey`. */
export function vapidKeyBytes(base64: string): Uint8Array<ArrayBuffer> {
  const padded = (base64 + '='.repeat((4 - (base64.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

/** `PushSubscription` → backend payload (null when the browser omits the keys). */
export function subscriptionData(subscription: PushSubscription): PushSubscriptionData | null {
  const json = subscription.toJSON() as { endpoint?: string; keys?: { p256dh?: string; auth?: string } };
  const endpoint = json.endpoint ?? subscription.endpoint;
  if (!endpoint || !json.keys?.p256dh || !json.keys.auth) return null;
  return { endpoint, keys: { p256dh: json.keys.p256dh, auth: json.keys.auth } };
}

/**
 * Thin wrapper over the browser Push API on the Angular service worker's registration (N4). Notifications
 * themselves are shown by `ngsw-worker.js` (payload `{notification: {title, body, data: {onActionClick}}}`).
 */
@Injectable({ providedIn: 'root' })
export class PushClient {
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly startup = inject(StartupService);

  permission(): PushPermission {
    if (!this.browser || !('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
      return 'unsupported';
    }
    return Notification.permission as PushPermission;
  }

  /** This device's current subscription, if any. */
  async current(): Promise<PushSubscription | null> {
    const registration = await this.registration();
    return registration ? registration.pushManager.getSubscription() : null;
  }

  /** Asks for permission (when needed) and subscribes with the server's VAPID key. */
  async subscribe(publicKey: string): Promise<PushSubscriptionData> {
    if (this.permission() === 'unsupported') throw new Error('PUSH_UNSUPPORTED');
    const permission = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission();
    if (permission !== 'granted') throw new Error('PUSH_DENIED');
    // the worker registers a while after start-up (StartupService); subscribing needs it now
    this.startup.requestServiceWorker();
    const registration = await this.registration();
    if (!registration) throw new Error('PUSH_UNSUPPORTED');
    const subscription =
      (await registration.pushManager.getSubscription()) ??
      (await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: vapidKeyBytes(publicKey) }));
    const data = subscriptionData(subscription);
    if (!data) throw new Error('PUSH_UNSUPPORTED');
    return data;
  }

  /** Unsubscribes this device; returns the endpoint to remove on the server (null when there was none). */
  async unsubscribe(): Promise<string | null> {
    const subscription = await this.current();
    if (!subscription) return null;
    const endpoint = subscription.endpoint;
    await subscription.unsubscribe();
    return endpoint;
  }

  private async registration(): Promise<ServiceWorkerRegistration | null> {
    if (this.permission() === 'unsupported') return null;
    const ready = navigator.serviceWorker.ready;
    const timeout = new Promise<null>((resolve) => setTimeout(() => resolve(null), READY_TIMEOUT_MS));
    return Promise.race([ready, timeout]);
  }
}
