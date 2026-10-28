import type { BrowserContext, Page } from '@playwright/test';

/**
 * Fake Web Push for every browser project (headless browsers have no push service): `Notification.permission` and
 * `requestPermission`, `navigator.serviceWorker.ready` and a `PushManager` whose subscription lives in
 * localStorage (`e2e.push`). The app code under test is the real `PushClient`; the server stores the fake
 * endpoint and, in mode LOG, captures the push (`/api/dev/notifications?channel=PUSH`).
 * `permission`: what the user answers when asked.
 */
export async function stubPush(target: Page | BrowserContext, permission: 'granted' | 'denied' = 'granted'): Promise<void> {
  await target.addInitScript((answer: string) => {
    const P256DH = 'BAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0-P0A';
    const AUTH = 'AAECAwQFBgcICQoLDA0ODw';
    const w = window as unknown as Record<string, unknown>;
    const fakeSubscription = (endpoint: string) => ({
      endpoint,
      toJSON: () => ({ endpoint, keys: { p256dh: P256DH, auth: AUTH } }),
      unsubscribe: async () => {
        localStorage.removeItem('e2e.push');
        return true;
      },
    });
    const pushManager = {
      getSubscription: async () => {
        const endpoint = localStorage.getItem('e2e.push');
        return endpoint ? fakeSubscription(endpoint) : null;
      },
      subscribe: async () => {
        const endpoint = `https://push.e2e.invalid/${Math.random().toString(36).slice(2)}`;
        localStorage.setItem('e2e.push', endpoint);
        return fakeSubscription(endpoint);
      },
    };
    if (!('PushManager' in window)) w['PushManager'] = function PushManager() {};
    const permission = () => localStorage.getItem('e2e.permission') ?? 'default';
    const FakeNotification = function Notification() {} as unknown as Record<string, unknown>;
    Object.defineProperty(FakeNotification, 'permission', { get: permission });
    FakeNotification['requestPermission'] = async () => {
      localStorage.setItem('e2e.permission', answer);
      return answer;
    };
    w['Notification'] = FakeNotification;
    if ('serviceWorker' in navigator) {
      Object.defineProperty(navigator.serviceWorker, 'ready', { get: () => Promise.resolve({ pushManager }) });
    } else {
      Object.defineProperty(navigator, 'serviceWorker', {
        value: {
          ready: Promise.resolve({ pushManager }),
          register: () => new Promise(() => undefined),
          addEventListener: () => undefined,
          controller: null,
        },
      });
    }
  }, permission);
}
