import type { BrowserContext, Page, WebSocketRoute } from '@playwright/test';
import { expect } from '../index';

/** Whether the page's realtime client is connected (dev hook `window.__fsRealtime`, A32). */
export function realtimeConnected(page: Page): Promise<boolean> {
  return page.evaluate(() => (window as unknown as { __fsRealtime: { connected(): boolean } }).__fsRealtime.connected());
}

/**
 * Deterministic "offline" for the realtime socket. Chromium's offline emulation does not stop new WebSocket
 * connections, so the client's 1 s reconnect could succeed again and `connected()` was false only for a moment
 * (the poll missed it under load), and inside the Playwright container the open socket even survived the offline
 * emulation. Install before `page.goto`: going offline closes the open sockets itself, and while offline new
 * handshakes are closed at once.
 */
export async function gateRealtime(page: Page, context: BrowserContext) {
  let blocked = false;
  const open = new Set<WebSocketRoute>();
  await page.routeWebSocket('**/ws', (ws) => {
    if (blocked) {
      void ws.close();
    } else {
      open.add(ws);
      const server = ws.connectToServer();
      // a handler replaces Playwright's own forwarding of the page's close: pass it on to the server
      ws.onClose((code, reason) => {
        open.delete(ws);
        void server.close({ code, reason });
      });
    }
  });
  return {
    async goOffline() {
      blocked = true;
      await context.setOffline(true);
      for (const ws of open) await ws.close();
      open.clear();
      await expect.poll(() => realtimeConnected(page)).toBe(false);
    },
    async goOnline() {
      blocked = false;
      await context.setOffline(false);
    },
  };
}
