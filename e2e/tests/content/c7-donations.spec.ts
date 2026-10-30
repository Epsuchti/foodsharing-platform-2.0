import type { Page } from '@playwright/test';
import {
  expect,
  expectDataState,
  expectErrorState,
  expectNoMissingKeys,
  injectApiError,
  setLanguage,
  test,
} from '../../fixtures';

/**
 * C7 donations (plan-m7 Q11, A54, decision 97): `/donate` shows the campaign pages and the provider's donation forms
 * as configured by ORGA (`updateDonationSettings`, C14); the shell's donation modal follows the DONATION_MODAL toggle
 * and `donation.show-modal`, at most once per `modalHoursLoggedOut` / `modalHoursLoggedIn` hours in a browser.
 * Seed: campaign card on with `donation-campaign-1/2`, no forms, modal off, 24 h logged out / 168 h logged in.
 */
test.use({ seedReset: 'test' });

const SHOWN_KEY = 'fs.donationModalShownAt';
const ONE_TIME = 'https://spenden.local.test/einmalig';
const CIRCLE = 'https://spenden.local.test/freundeskreis';

/** Opens a page and waits until the feature toggles (loaded after the first page) have arrived. */
async function openWithToggles(page: Page, path: string): Promise<void> {
  const toggles = page.waitForResponse((response) => response.url().includes('/api/public/feature-toggles'));
  await page.goto(path);
  await toggles;
}

/**
 * Opens a page and waits until the modal had its chance: toggles loaded and the donation settings answered (only
 * requested while DONATION_MODAL is on), so "no modal" is not asserted too early.
 */
async function openWithDonation(page: Page, path: string): Promise<void> {
  const donation = page.waitForResponse((response) => response.url().includes('/api/public/donations'));
  await openWithToggles(page, path);
  await donation;
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

/** Moves the last showing of the modal in this browser back by `hours`. */
async function shownHoursAgo(page: Page, hours: number): Promise<void> {
  await page.evaluate(([key, ms]) => localStorage.setItem(key, String(Date.now() - ms)), [SHOWN_KEY, hours * 3_600_000] as const);
}

test('C7 donation page follows admin configuration; modal appears per hours setting @C7 @C14 @page', async ({ api, page, as }) => {
  await setLanguage(page, 'de');
  const modal = page.getByTestId('content.donationModal.body');

  // seed: the two campaign pages, no donation forms, the info link; no modal
  await openWithToggles(page, '/donate');
  await expectDataState(page, 'content.donate.state', 'success');
  await expect(page.getByTestId('content.donate.campaign')).toHaveCount(2);
  await expect(page.getByTestId('content.donate.frame')).toHaveCount(0);
  await expect(page.getByTestId('content.donate.info')).toHaveAttribute('href', 'https://foodsharing.local.test/spenden');
  await expectNoMissingKeys(page);
  await openWithToggles(page, '/about');
  await expect(page.getByTestId('content.about.link').first()).toBeVisible();
  await expect(modal).toHaveCount(0);

  // ORGA: forms instead of the campaign card, the modal every 24 h for visitors (DONATION_MODAL toggle on)
  await api.loginAs('orga');
  const settings = await api.getJson<Record<string, unknown>>('/api/admin/donations');
  await api.putJson('/api/admin/donations', {
    ...settings,
    showCampaignCard: false,
    showModal: true,
    modalHoursLoggedOut: 24,
    modalHoursLoggedIn: 168,
    iframeOneTimeUrl: ONE_TIME,
    iframeFriendshipCircleUrl: CIRCLE,
  });
  const on = await api.fetch('PATCH', '/api/admin/feature-toggles/DONATION_MODAL', { enabled: true });
  expect(on.status(), await on.text()).toBe(200);

  // /donate: two forms in their order, the CSP lets their host into frames (decision 97); no modal on /donate itself
  const response = await page.goto('/donate');
  expect(response?.headers()['content-security-policy']).toMatch(/frame-src[^;]*https:\/\/spenden\.local\.test/);
  await expectDataState(page, 'content.donate.state', 'success');
  await expect(page.getByTestId('content.donate.campaign')).toHaveCount(0);
  const frames = page.getByTestId('content.donate.frame');
  await expect(frames).toHaveCount(2);
  await expect(frames.nth(0)).toHaveAttribute('data-kind', 'oneTime');
  await expect(frames.nth(1)).toHaveAttribute('data-kind', 'friendshipCircle');
  await expect(page.getByTestId('content.donate.iframe').nth(0)).toHaveAttribute('src', ONE_TIME);
  await expect(page.getByTestId('content.donate.iframe').nth(1)).toHaveAttribute('src', CIRCLE);
  await expect(page.getByTestId('content.donate.iframe').nth(0)).toHaveAttribute('title', /.+/);
  await expect(modal).toHaveCount(0);

  // another page: the modal opens once (text from the CMS page `donation-modal`)
  await openWithDonation(page, '/about');
  await expect(modal).toBeVisible();
  await expectNoMissingKeys(page);
  await page.getByTestId('content.donationModal.later').click();
  await expect(modal).toHaveCount(0);
  await openWithDonation(page, '/faq');
  await expectDataState(page, 'content.faq.state', 'success');
  await expect(modal).toHaveCount(0);

  // 23 hours later it stays closed, after 25 hours it is back; "donate" leads to /donate
  await shownHoursAgo(page, 23);
  await openWithDonation(page, '/about');
  await expect(page.getByTestId('content.about.link').first()).toBeVisible();
  await expect(modal).toHaveCount(0);
  await shownHoursAgo(page, 25);
  await openWithDonation(page, '/about');
  await expect(modal).toBeVisible();
  await page.getByTestId('content.donationModal.donate').click();
  await expect(page).toHaveURL(/\/donate$/);
  await expect(modal).toHaveCount(0);

  // signed in, the logged-in interval counts: 25 hours are not enough, 169 are
  const member = await as('foodsaver');
  const memberModal = member.getByTestId('content.donationModal.body');
  await openWithDonation(member, '/dashboard');
  await expect(memberModal).toBeVisible();
  await shownHoursAgo(member, 25);
  await openWithDonation(member, '/dashboard');
  await expect(member.getByTestId('dashboard.page.welcome')).toBeVisible();
  await expect(memberModal).toHaveCount(0);
  await shownHoursAgo(member, 169);
  await openWithDonation(member, '/dashboard');
  await expect(memberModal).toBeVisible();

  // the toggle off: no modal even when due
  const off = await api.fetch('PATCH', '/api/admin/feature-toggles/DONATION_MODAL', { enabled: false });
  expect(off.status(), await off.text()).toBe(200);
  await page.evaluate((key) => localStorage.removeItem(key), SHOWN_KEY);
  await openWithToggles(page, '/about');
  await expect(page.getByTestId('content.about.link').first()).toBeVisible();
  await expect(modal).toHaveCount(0);
});

test('donate page: server error with retry @C7', async ({ page }) => {
  // the retry interceptor repeats GETs twice: three failures reach the page
  const failure = await injectApiError(page, { url: /\/api\/public\/donations(\?|$)/, status: 500, times: 3 });
  await page.goto('/donate');
  const { retry } = await expectErrorState(page, 'content.donate.state');
  await failure.remove();
  await retry.click();
  await expectDataState(page, 'content.donate.state', 'success');
});
