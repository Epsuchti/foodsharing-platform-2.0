import { animationsDone } from '../a11y/animations';
import { expect, goOffline, goOnline, injectNetworkFailure, test } from '../../fixtures';
import type { Page } from '@playwright/test';

/**
 * AU12 (offline banner part): the shell shows a banner while the browser is offline or our own API cannot be reached,
 * DataStates switch to `offline` (cached data stays visible) and reload by themselves once the connection is back.
 * The gallery's live specimen (`gallery.live.state`) is a real `loadResource` on GET /api/public/app-info.
 */

test.use({ seedReset: 'off' });

const APP_INFO = '/api/public/app-info';

async function openLiveSpecimen(page: Page) {
  await page.goto('/dev/gallery');
  const live = page.getByTestId('gallery.live.state');
  await expect(live).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('gallery.live.languages')).toBeVisible();
  return live;
}

/**
 * Resolves once the shell's after-first-page loads (StartupService.markPageShown: feature toggles, then the deferred
 * release-notes dot and petition banner) are over and no request has been in flight for 500 ms. Started during a
 * simulated outage, such a GET is retried 300/900 ms later (retry interceptor); once the outage is lifted that retry
 * succeeds and rightly ends the offline state by itself, before the banner's retry button can be clicked.
 * Call before `page.goto`.
 */
function startupRequestsDone(page: Page): () => Promise<void> {
  let inFlight = 0;
  let lastChange = Date.now();
  const change = (delta: number) => {
    inFlight += delta;
    lastChange = Date.now();
  };
  page.on('request', () => change(1));
  page.on('requestfinished', () => change(-1));
  page.on('requestfailed', () => change(-1));
  const toggles = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/public/feature-toggles');
  return async () => {
    await toggles;
    await expect.poll(() => inFlight === 0 && Date.now() - lastChange >= 500, { intervals: [100] }).toBe(true);
  };
}

async function catalogText(page: Page, key: string): Promise<string> {
  const catalog = (await (await page.request.get('/i18n/de.json')).json()) as Record<string, string>;
  expect(catalog[key], `i18n key ${key}`).toBeTruthy();
  return catalog[key];
}

test('AU12 offline banner appears on network loss and data reloads when back online @AU12 @page', async ({ page, context }) => {
  const live = await openLiveSpecimen(page);
  const title = await catalogText(page, 'shared.offline.title');
  const banner = page.getByTestId('shell.offlineBanner');
  await expect(banner).toBeHidden();

  await goOffline(context);
  await expect(banner).toBeVisible();
  await expect(banner).toHaveAttribute('role', 'status');
  await expect(banner).toContainText(title);

  // A load while offline: the DataState reports `offline` and keeps the cached data (marked stale).
  await page.getByTestId('gallery.live.reload').click();
  await expect(live).toHaveAttribute('data-state', 'offline');
  await expect(page.getByTestId('gallery.live.languages')).toBeVisible();
  await expect(live.getByRole('alert')).toBeVisible();

  // Back online: banner disappears and the DataState reloads without any click.
  const reloaded = page.waitForResponse((response) => new URL(response.url()).pathname === APP_INFO && response.ok());
  await goOnline(context);
  await reloaded;
  await expect(banner).toBeHidden();
  await expect(live).toHaveAttribute('data-state', 'success');
  await expect(live.getByRole('alert')).toHaveCount(0);
  // the stale content fades back to full opacity (200 ms); the automatic axe check must see the final colours
  await animationsDone(page);
});

test('AU12 banner reports an unreachable server and recovers via its retry button @AU12', async ({ page }) => {
  const startupDone = startupRequestsDone(page);
  const live = await openLiveSpecimen(page);
  await startupDone();
  const serverTitle = await catalogText(page, 'shared.offline.serverTitle');
  const banner = page.getByTestId('shell.offlineBanner');

  // Browser online, but every API request dies without an HTTP response (server down / proxy gone).
  const outage = await injectNetworkFailure(page, { url: '/api/*' });
  await page.getByTestId('gallery.live.reload').click();
  await expect(live).toHaveAttribute('data-state', 'offline');
  await expect(banner).toBeVisible();
  await expect(banner).toContainText(serverTitle);
  expect(outage.calls()).toBeGreaterThan(0);

  // Retrying while the server is still down keeps the banner. Wait for that probe to fail: still in flight when the
  // outage is removed, it would reach the server, succeed and hide the banner before the second click.
  const probeFailed = page.waitForEvent('requestfailed', (request) => request.url().includes('/api/public/app-info'));
  await page.getByTestId('shell.offlineBanner.retry').click();
  await probeFailed;
  await expect(banner).toBeVisible();

  await outage.remove();
  await page.getByTestId('shell.offlineBanner.retry').click();
  await expect(banner).toBeHidden();
  // Auto-retry once the probe succeeded.
  await expect(live).toHaveAttribute('data-state', 'success');
});
