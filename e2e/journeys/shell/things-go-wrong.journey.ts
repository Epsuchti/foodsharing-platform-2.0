import type { Page } from '@playwright/test';
import {
  expectDataState,
  expectErrorState,
  goOffline,
  goOnline,
  injectApiError,
  injectNetworkFailure,
  seedStore,
} from '../../fixtures';
import { checkpoint, expect, settle, journey as test } from '../../fixtures/checkpoint';
import { ZURICH } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';

/**
 * Things go wrong (decision 180 #5): foodsaver Sarah (foodsaver@local) meets every kind of failure once, on a few
 * representative pages, and gets back on track each time. Logged out, an old bookmark shows the forbidden page with a
 * login that comes back. Logged in: a server error with retry (my stores), a store outside her regions (forbidden), a
 * store that does not exist (not found), the router's forbidden and not-found pages, the browser going offline and the
 * server becoming unreachable (shell banner, DataStates switch to offline and reload by themselves).
 *
 * The per-page error states are not repeated page by page: every data-loading element uses `<fs-data-state>`
 * (client/scripts/check-data-state.mjs), whose states are asserted once in
 * client/src/app/shared/data-state/data-state.component.spec.ts and shown once in the gallery journey.
 * Rules behind it: `safeReturnUrl` (return-url.spec.ts, forbidden-page.component.spec.ts), `statusForError` and the
 * automatic reload after an offline failure (core/data/data-state.spec.ts), retry backoff (error.interceptor.spec.ts).
 */

const S23 = seedStore('S-23');
const UNKNOWN_STORE = '00000020-0001-4000-8000-0000000009ff';
const APP_INFO = '/api/public/app-info';

async function catalogText(page: Page, key: string): Promise<string> {
  const catalog = (await (await page.request.get('/i18n/de.json')).json()) as Record<string, string>;
  expect(catalog[key], `i18n key ${key}`).toBeTruthy();
  return catalog[key];
}

test('foodsaver meets a server error, forbidden and missing pages, going offline and an unreachable server @D4.3 @AU12', async ({
  page,
  as,
}, testInfo) => {
  test.setTimeout(150_000);

  // logged out (the anonymous `page`): an old bookmark of her activity page
  await page.goto('/forbidden?returnUrl=%2Fme%2Factivity');
  await expect(page.getByTestId('errorPages.forbidden.primary')).toHaveAttribute('href', '/login?returnUrl=%2Fme%2Factivity');
  await expect(page.getByTestId('errorPages.forbidden.back')).toBeVisible();
  await checkpoint(page, 'forbidden-logged-out');

  const sarah = await as('foodsaver');
  const walk = new Walk(sarah, testInfo, 'foodsaver');

  await walk.step('opens her stores while the server fails, retries', async () => {
    const failure = await injectApiError(sarah, { url: '/api/users/me/stores', status: 500 });
    await sarah.goto('/dashboard');
    await expect(sarah.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('stores');
    await expect(sarah).toHaveURL(/\/stores\/mine$/);
    const { retry } = await expectErrorState(sarah, 'stores.mine.state');
    await checkpoint(sarah, 'server-error');
    await failure.remove();
    await walk.click(retry);
    await expectDataState(sarah, 'stores.mine.state', 'success');
  });

  await walk.step('follows links to a store outside her regions and to a deleted store', async () => {
    // Winterthur (S-23) is outside Sarah's regions: the server answers 403
    await sarah.goto(`/stores/${S23.id}`);
    await expectErrorState(sarah, 'stores.page.state', { status: 'forbidden', retry: false });
    await checkpoint(sarah, 'forbidden-state');
    await sarah.goto(`/stores/${UNKNOWN_STORE}`);
    await expectErrorState(sarah, 'stores.page.state', { status: 'notFound', retry: false });
    await checkpoint(sarah, 'not-found-state');
  });

  await walk.step('opens an admin link and a broken deep link, goes back', async () => {
    // the route is for the orga team only: the router sends her to the forbidden page
    await sarah.goto('/admin/feature-toggles');
    await expect(sarah).toHaveURL(/\/forbidden$/);
    await expect(sarah.getByTestId('errorPages.forbidden.primary')).toHaveAttribute('href', '/dashboard');
    await expect(sarah.getByTestId('errorPages.forbidden.back')).toBeVisible();
    await checkpoint(sarah, 'forbidden-page');

    await sarah.goto('/some/deep/link');
    await expect(sarah.getByTestId('errorPages.notFound')).toBeVisible();
    await expect(sarah.getByTestId('errorPages.notFound.primary')).toHaveAttribute('href', '/dashboard');
    await checkpoint(sarah, 'not-found-page');
    await walk.click(sarah.getByTestId('errorPages.notFound.back'));
    await expect.poll(() => new URL(sarah.url()).pathname).toBe('/forbidden');
  });

  await walk.step('loses the connection while searching the stores of her region', async () => {
    const context = sarah.context();
    await sarah.goto(`/regions/${ZURICH}/stores`);
    await expectDataState(sarah, 'stores.region.state', 'success');
    // the shell's after-first-page loads are done before the connection goes (otherwise they fail and retry too)
    await settle(sarah);
    const banner = sarah.getByTestId('shell.offlineBanner');
    await expect(banner).toBeHidden();

    await goOffline(context);
    await expect(banner).toBeVisible();
    await expect(banner).toHaveAttribute('role', 'status');
    await expect(banner).toContainText(await catalogText(sarah, 'shared.offline.title'));
    await walk.fill(sarah.getByTestId('stores.region.search'), 'Migros');
    const state = await expectDataState(sarah, 'stores.region.state', 'offline');
    // a new search is new data: the offline message replaces the list (a reload of the same data keeps it, stale:
    // data-state.component.spec.ts, gallery `data-state-offline`)
    await expect(state.getByRole('alert')).toBeVisible();
    await checkpoint(sarah, 'offline', { waitForData: false });

    // back online: the banner goes and the search runs by itself, without a click
    await goOnline(context);
    await expect(banner).toBeHidden();
    await expectDataState(sarah, 'stores.region.state', 'success');
    await expect(sarah.getByTestId('stores.region.row')).toHaveCount(2);
  });

  await walk.step('the server cannot be reached, she retries from the banner', async () => {
    const banner = sarah.getByTestId('shell.offlineBanner');
    // browser online, but every API request dies without an HTTP response (server down, proxy gone)
    const outage = await injectNetworkFailure(sarah, { url: '/api/*' });
    await walk.fill(sarah.getByTestId('stores.region.search'), 'Coop');
    await expectDataState(sarah, 'stores.region.state', 'offline');
    await expect(banner).toBeVisible();
    await expect(banner).toContainText(await catalogText(sarah, 'shared.offline.serverTitle'));
    expect(outage.calls()).toBeGreaterThan(0);
    await checkpoint(sarah, 'server-unreachable', { waitForData: false });

    // retrying while the server is still down keeps the banner; wait for that probe to fail (still in flight when the
    // outage is lifted, it would reach the server and hide the banner before the second click)
    const probeFailed = sarah.waitForEvent('requestfailed', (request) => request.url().includes(APP_INFO));
    await walk.click(sarah.getByTestId('shell.offlineBanner.retry'));
    await probeFailed;
    await expect(banner).toBeVisible();

    await outage.remove();
    await walk.click(sarah.getByTestId('shell.offlineBanner.retry'));
    await expect(banner).toBeHidden();
    await expectDataState(sarah, 'stores.region.state', 'success');
    await expect(sarah.getByTestId('stores.region.row').first()).toBeVisible();
  });

  walk.finish({ steps: 5, clicks: 5, fields: 2 });
});
