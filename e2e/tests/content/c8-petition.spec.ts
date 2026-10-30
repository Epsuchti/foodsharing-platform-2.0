import { expect, expectDataState, expectNoMissingKeys, setLanguage, test } from '../../fixtures';
import type { Page } from '@playwright/test';

/**
 * C8 (plan-m7 Q10): the petition banner of the shell (above the footer) and `/petition` follow the PETITION_BANNER
 * feature toggle (C11, ORGA switches it); a dismissed petition stays hidden in this browser.
 */

test.use({ seedReset: 'test' });

const PETITION_URL = 'https://petition.local.test/foodsharing';

/** Opens a page and waits until the feature toggles (loaded after the first page) have arrived. */
async function openWithToggles(page: Page, path: string): Promise<void> {
  const toggles = page.waitForResponse((response) => response.url().includes('/api/public/feature-toggles'));
  await page.goto(path);
  await toggles;
}

test('C8 petition banner follows the feature toggle @C8 @C11 @page', async ({ api, page }) => {
  await setLanguage(page, 'de');
  const banner = page.getByTestId('content.petition.banner');

  // seed: PETITION_BANNER on, 4213 signatures, 21 days left
  await openWithToggles(page, '/about');
  await expect(banner).toBeVisible();
  await expect(page.getByTestId('content.petition.bannerCount')).toContainText(/4.213/);
  await expect(page.getByTestId('content.petition.bannerCount')).toContainText('21');
  await expect(page.getByTestId('content.petition.bannerSign')).toHaveAttribute('href', PETITION_URL);
  await expect(page.getByTestId('content.petition.bannerSign')).toHaveAttribute('target', '_blank');
  await page.getByTestId('content.petition.bannerMore').click();
  await expect(page).toHaveURL(/\/petition$/);
  await expectDataState(page, 'content.petition.state', 'success');
  await expect(page.getByTestId('content.petition.signatures')).toHaveAttribute('data-value', '4213');
  await expect(page.getByTestId('content.petition.daysLeft')).toHaveText('21');
  await expect(page.getByTestId('content.petition.sign')).toHaveAttribute('href', PETITION_URL);
  await expectNoMissingKeys(page);

  // ORGA switches the toggle off: no banner, the petition page is gone
  await api.loginAs('orga');
  const off = await api.fetch('PATCH', '/api/admin/feature-toggles/PETITION_BANNER', { enabled: false });
  expect(off.status(), await off.text()).toBe(200);
  await openWithToggles(page, '/about');
  await expect(page.getByTestId('content.about.link').first()).toBeVisible();
  await expect(banner).toHaveCount(0);
  await page.goto('/petition');
  await expectDataState(page, 'content.petition.state', 'notFound');

  // on again: the banner is back; dismissing hides this petition for good (in this browser)
  const on = await api.fetch('PATCH', '/api/admin/feature-toggles/PETITION_BANNER', { enabled: true });
  expect(on.status(), await on.text()).toBe(200);
  await openWithToggles(page, '/about');
  await expect(banner).toBeVisible();
  await page.getByTestId('content.petition.bannerDismiss').click();
  await expect(banner).toHaveCount(0);
  const petition = page.waitForResponse((response) => response.url().includes('/api/public/petition'));
  await openWithToggles(page, '/about');
  await petition;
  await expect(page.getByTestId('content.about.link').first()).toBeVisible();
  await expect(banner).toHaveCount(0);
});
