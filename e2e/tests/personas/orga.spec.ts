import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';
import { Walk } from './_persona';

/**
 * D11.5.5 persona walkthrough "orga" (decision 3, design review M7 round 3): the orga team pauses the food baskets
 * for everybody from the feature toggles, checks the effect on a member's app (no nav entry, the page is gone) and
 * switches them back on. The member (foodsaver@local) is a second browser session. Runs on desktop and on the phone
 * project.
 */
test.use({ seedReset: 'test', role: 'orga' });

const toggleRow = (page: Page, key: string) => page.locator(`[data-testid="adminSettings.toggles.row"][data-key="${key}"]`);

/** Opens a page and waits until the client has loaded the feature toggles (they count as off before). */
async function gotoWithToggles(page: Page, path: string): Promise<void> {
  const loaded = page.waitForResponse((response) => response.url().endsWith('/api/public/feature-toggles') && response.ok());
  await page.goto(path);
  await loaded;
}

/** The member's way to the baskets: header link or "More" menu on desktop, menu drawer on phones. */
function basketsEntry(page: Page) {
  return page.locator('[data-testid="nav.baskets"], [data-testid="nav.drawer.baskets"]');
}

test('D11.5.5 orga: switches a feature off and on and sees the effect @D11.5.5 @C11 @page', async ({ page, as, apiOf }, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'orga');
  const member = await as('foodsaver');
  await acceptPendingConsents(apiOf(member));
  await gotoWithToggles(member, '/dashboard');
  await expect(basketsEntry(member).first()).toBeAttached();

  await walk.step('opens the feature toggles', async () => {
    await gotoWithToggles(page, '/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminSettings');
    await expect(page).toHaveURL(/\/admin\/feature-toggles$/);
    await expectDataState(page, 'adminSettings.toggles.state', 'success');
    await expect(toggleRow(page, 'BASKETS')).toHaveAttribute('data-enabled', 'true');
  });

  await walk.step('pauses the food baskets', async () => {
    await walk.click(toggleRow(page, 'BASKETS').getByTestId('adminSettings.toggles.switch'));
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(toggleRow(page, 'BASKETS')).toHaveAttribute('data-enabled', 'false');
    await expect(toggleRow(page, 'BASKETS').getByTestId('adminSettings.toggles.status')).toBeVisible();
    // the orga's own navigation follows at once
    await expect(page.getByTestId('nav.baskets')).toHaveCount(0);
  });

  await walk.step("checks a member's app", async () => {
    await gotoWithToggles(member, '/dashboard');
    await expect(member.getByTestId('dashboard.page.welcome')).toBeVisible();
    await expect(basketsEntry(member)).toHaveCount(0);
    await member.goto('/baskets');
    await expect(member.getByTestId('errorPages.notFound')).toBeVisible();
  });

  await walk.step('switches the baskets back on', async () => {
    await walk.click(toggleRow(page, 'BASKETS').getByTestId('adminSettings.toggles.switch'));
    await expect(toggleRow(page, 'BASKETS')).toHaveAttribute('data-enabled', 'true');
    await gotoWithToggles(member, '/baskets');
    await expectDataState(member, 'baskets.nearby.state', 'success');
    await expect(basketsEntry(member).first()).toBeAttached();
  });

  // round 3: 5 clicks on desktop and phone
  walk.finish({ steps: 4, clicks: 5, fields: 0 });
});
