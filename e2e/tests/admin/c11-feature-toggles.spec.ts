import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';
import { acceptConfirm } from '../auth/_auth';

/**
 * C11 feature toggles (plan-m7 Q13, A43/A53): ORGA switches features on `/admin/feature-toggles`; a disabled
 * feature loses its nav entry and route (featureGuard → not found) and its API answers 404 FEATURE_DISABLED.
 * Seed: every toggle on except those whose default is off.
 */
test.use({ seedReset: 'test', role: 'orga' });

const toggleRow = (page: Page, key: string) => page.locator(`[data-testid="adminSettings.toggles.row"][data-key="${key}"]`);

/** Opens a page and waits until the client loaded the feature toggles (they count as off before). */
async function gotoWithToggles(page: Page, path: string): Promise<void> {
  const loaded = page.waitForResponse((response) => response.url().endsWith('/api/public/feature-toggles') && response.ok());
  await page.goto(path);
  await loaded;
}

test('C11 orga disables baskets: nav entry and route disappear, API answers 404 FEATURE_DISABLED @C11 @page', async ({ page, as, apiOf }) => {
  const member = await as('foodsaver');
  await gotoWithToggles(member, '/dashboard');
  await expect(member.getByTestId('nav.baskets').first()).toBeAttached();

  // the provisional nav route of Q1 opens the toggles tab
  await page.goto('/admin/settings');
  await expect(page).toHaveURL(/\/admin\/feature-toggles$/);
  await expectDataState(page, 'adminSettings.toggles.state', 'success');
  await expect(toggleRow(page, 'BASKETS')).toHaveAttribute('data-enabled', 'true');
  await expect(page.getByTestId('nav.baskets').first()).toBeAttached();

  // switching off asks first; the orga's own nav follows at once
  await toggleRow(page, 'BASKETS').getByTestId('adminSettings.toggles.switch').click();
  await acceptConfirm(page);
  await expect(toggleRow(page, 'BASKETS')).toHaveAttribute('data-enabled', 'false');
  await expect(toggleRow(page, 'BASKETS').getByTestId('adminSettings.toggles.status')).toBeVisible();
  await expect(page.getByTestId('nav.baskets')).toHaveCount(0);

  // the member: no nav entry, the route is a missing page, the API refuses
  await gotoWithToggles(member, '/dashboard');
  await expect(member.getByTestId('dashboard.page.welcome')).toBeVisible();
  await expect(member.getByTestId('nav.baskets')).toHaveCount(0);
  await member.goto('/baskets');
  await expect(member.getByTestId('errorPages.notFound')).toBeVisible();
  const refused = await apiOf(member).get('/api/baskets/mine');
  expect(refused.status()).toBe(404);
  expect((await refused.json()).code).toBe('FEATURE_DISABLED');
  const toggles = await apiOf(member).getJson<Record<string, boolean>>('/api/public/feature-toggles');
  expect(toggles['BASKETS']).toBe(false);

  // switching on is immediate (no confirm) and brings everything back
  await toggleRow(page, 'BASKETS').getByTestId('adminSettings.toggles.switch').click();
  await expect(toggleRow(page, 'BASKETS')).toHaveAttribute('data-enabled', 'true');
  await expect(page.locator('.p-confirmdialog-accept-button')).toHaveCount(0);
  await gotoWithToggles(member, '/baskets');
  await expectDataState(member, 'baskets.nearby.state', 'success');
  await expect(member.getByTestId('nav.baskets').first()).toBeAttached();
  expect((await apiOf(member).get('/api/baskets/mine')).status()).toBe(200);
});

test('C11 feature toggle list error state and retry @C11', async ({ page }) => {
  // GETs are retried twice by the retry interceptor
  await injectApiError(page, { url: '/api/admin/feature-toggles', method: 'GET', status: 500, times: 3 });
  await page.goto('/admin/feature-toggles');
  const { retry } = await expectErrorState(page, 'adminSettings.toggles.state');
  await retry.click();
  await expectDataState(page, 'adminSettings.toggles.state', 'success');
});

test('C11 settings pages are ORGA only @C11', async ({ as, apiOf }) => {
  const ambassador = await as('bot');
  await acceptPendingConsents(apiOf(ambassador));
  for (const path of ['/admin/feature-toggles', '/admin/configuration', '/admin/email-blocklist', '/admin/uploads', '/admin/admins']) {
    await ambassador.goto(path);
    await expect(ambassador, path).toHaveURL(/\/forbidden$/);
  }
});
