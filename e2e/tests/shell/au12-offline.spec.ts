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
});

test('AU12 banner reports an unreachable server and recovers via its retry button @AU12', async ({ page }) => {
  const live = await openLiveSpecimen(page);
  const serverTitle = await catalogText(page, 'shared.offline.serverTitle');
  const banner = page.getByTestId('shell.offlineBanner');

  // Browser online, but every API request dies without an HTTP response (server down / proxy gone).
  const outage = await injectNetworkFailure(page, { url: '/api/*' });
  await page.getByTestId('gallery.live.reload').click();
  await expect(live).toHaveAttribute('data-state', 'offline');
  await expect(banner).toBeVisible();
  await expect(banner).toContainText(serverTitle);
  expect(outage.calls()).toBeGreaterThan(0);

  // Retrying while the server is still down keeps the banner.
  await page.getByTestId('shell.offlineBanner.retry').click();
  await expect(banner).toBeVisible();

  await outage.remove();
  await page.getByTestId('shell.offlineBanner.retry').click();
  await expect(banner).toBeHidden();
  // Auto-retry once the probe succeeded.
  await expect(live).toHaveAttribute('data-state', 'success');
});
