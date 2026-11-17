import type { Page } from '@playwright/test';
import { acceptPendingConsents, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * D11.5.5 persona walkthrough "orga" (decision 3, design review M7 round 3): the orga team pauses the food baskets for
 * everybody from the feature toggles, checks the effect on a member's app (no menu entry, the page is gone, the API
 * refuses) and switches them back on. The member (foodsaver@local) is a second browser session. Rules behind it:
 * FeatureToggleServiceTest (defaults, cache, history, 404 FEATURE_DISABLED), client feature-toggles.spec (featureGuard
 * → not found) and admin-settings.spec (switching off asks first); settings pages are ORGA only: client
 * orga-routes.spec.
 */
test.use({ role: 'orga' });

const toggleRow = (page: Page, key: string) => page.locator(`[data-testid="adminSettings.toggles.row"][data-key="${key}"]`);

/** Opens a page and waits until the client has loaded the feature toggles (they count as off before). */
async function gotoWithToggles(page: Page, path: string): Promise<void> {
  const loaded = page.waitForResponse((response) => response.url().endsWith('/api/public/feature-toggles') && response.ok());
  await page.goto(path);
  await loaded;
}

/** The member's way to the baskets: side menu on desktop, menu drawer or tab on phones. */
function basketsEntry(page: Page) {
  return page.locator('[data-testid="nav.baskets"], [data-testid="nav.drawer.baskets"], [data-testid="nav.tab.baskets"]');
}

test('orga pauses the food baskets for everybody, checks a member\'s app and switches them back on @D11.5.5 @C11', async ({
  page,
  as,
  apiOf,
}, testInfo) => {
  test.setTimeout(120_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'orga');
  const member = await as('foodsaver');
  const memberApi = apiOf(member);
  await acceptPendingConsents(memberApi);
  await gotoWithToggles(member, '/dashboard');
  await expect(basketsEntry(member).first()).toBeAttached();

  await walk.step('opens the feature toggles', async () => {
    await gotoWithToggles(page, '/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminSettings');
    await expect(page).toHaveURL(/\/admin\/feature-toggles$/);
    await expectDataState(page, 'adminSettings.toggles.state', 'success');
    await expect(toggleRow(page, 'BASKETS')).toHaveAttribute('data-enabled', 'true');
    await checkpoint(page, 'feature-toggles');
  });

  await walk.step('pauses the food baskets', async () => {
    await walk.click(toggleRow(page, 'BASKETS').getByTestId('adminSettings.toggles.switch'));
    await expect(page.locator('.p-confirmdialog-accept-button')).toBeVisible();
    await checkpoint(page, 'pause-confirm');
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(toggleRow(page, 'BASKETS')).toHaveAttribute('data-enabled', 'false');
    await expect(toggleRow(page, 'BASKETS').getByTestId('adminSettings.toggles.status')).toBeVisible();
    // the orga's own navigation follows at once
    await expect(page.getByTestId('nav.baskets')).toHaveCount(0);
    await checkpoint(page, 'baskets-paused');
  });

  await walk.step("checks a member's app", async () => {
    await gotoWithToggles(member, '/dashboard');
    await expect(member.getByTestId('dashboard.page.welcome')).toBeVisible();
    await expect(basketsEntry(member)).toHaveCount(0);
    await member.goto('/baskets');
    await expect(member.getByTestId('errorPages.notFound')).toBeVisible();
    await checkpoint(member, 'member-baskets-gone');
    // the API refuses too
    const refused = await memberApi.get('/api/baskets/mine');
    expect(refused.status()).toBe(404);
    expect((await refused.json()).code).toBe('FEATURE_DISABLED');
  });

  await walk.step('switches the baskets back on', async () => {
    // switching on needs no confirmation
    await walk.click(toggleRow(page, 'BASKETS').getByTestId('adminSettings.toggles.switch'));
    await expect(toggleRow(page, 'BASKETS')).toHaveAttribute('data-enabled', 'true');
    await expect(page.locator('.p-confirmdialog-accept-button')).toHaveCount(0);
    await gotoWithToggles(member, '/baskets');
    await expectDataState(member, 'baskets.nearby.state', 'success');
    await expect(basketsEntry(member).first()).toBeAttached();
    expect((await memberApi.get('/api/baskets/mine')).status()).toBe(200);
  });

  // round 3: 5 clicks on desktop and phone
  walk.finish({ steps: 4, clicks: 5, fields: 0 });
});
