import type { Page } from '@playwright/test';
import { acceptPendingConsents, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm } from '../../fixtures/flows/auth';
import { Walk } from '../../fixtures/persona';

/**
 * ORGA (orga@local) prepares the autumn campaigns in the settings: a new support address (an invalid one is refused at
 * its row) and the petition link and count, all listed in the history drawer; the donation page shows two donation
 * forms instead of the campaign card and the donation modal is switched on. A visitor sees the forms (their host is
 * allowed into frames by the CSP, decision 97), the modal once, and the petition banner with the new count; after
 * the petition the orga switches the banner off and the petition page is gone. Rules behind it:
 * ConfigurationServiceTest (typed values, only changes recorded), DonationSettingsServiceTest (the donation.* keys),
 * DonationFrameSourcesTest, CampaignServiceTest;
 * client content-q11.spec (modal once per hours setting logged in/out, https forms only), content-q10.spec (banner
 * dismissed, toggle off), admin-settings.spec (invalid value at the row, history drawer).
 */
test.use({ role: 'orga' });

const ONE_TIME = 'https://spenden.local.test/einmalig';
const CIRCLE = 'https://spenden.local.test/freundeskreis';
const PETITION = 'https://petition.local.test/foodsharing-herbst';

const configRow = (page: Page, key: string) => page.locator(`[data-testid="adminSettings.configuration.row"][data-key="${key}"]`);
const toggleRow = (page: Page, key: string) => page.locator(`[data-testid="adminSettings.toggles.row"][data-key="${key}"]`);

/** Opens a page and waits until the feature toggles (loaded after the first page) have arrived. */
async function openWithToggles(page: Page, path: string): Promise<void> {
  const toggles = page.waitForResponse((response) => response.url().includes('/api/public/feature-toggles'));
  await page.goto(path);
  await toggles;
}

/** Opens a page and waits until the donation modal had its chance (toggles loaded, donation settings answered). */
async function openWithDonation(page: Page, path: string): Promise<void> {
  const donation = page.waitForResponse((response) => response.url().includes('/api/public/donations'));
  await openWithToggles(page, path);
  await donation;
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

test('orga sets up the donation forms, the donation modal and the petition; a visitor sees them @C14 @C7 @C8 @C11', async ({
  page,
  as,
  apiOf,
}, testInfo) => {
  test.setTimeout(150_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'orga');

  await walk.step('changes the support address and the petition in the configuration', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminSettings');
    await expect(page).toHaveURL(/\/admin\/feature-toggles$/);
    await walk.click(page.getByTestId('adminSettings.tabs.configuration'));
    await expect(page).toHaveURL(/\/admin\/configuration$/);
    await expectDataState(page, 'adminSettings.configuration.state', 'success');
    const save = async (key: string, value: string) => {
      await walk.fill(configRow(page, key).getByTestId('adminSettings.configuration.input'), value);
      await walk.click(configRow(page, key).getByTestId('adminSettings.configuration.save'));
    };
    // an invalid address is refused at its row
    await save('support.email', 'keine-adresse');
    await expect(configRow(page, 'support.email').getByTestId('adminSettings.configuration.error')).toBeVisible();
    await checkpoint(page, 'configuration-invalid');
    await save('support.email', 'hilfe@foodsharing.example');
    await expect(configRow(page, 'support.email').getByTestId('adminSettings.configuration.error')).toHaveCount(0);
    await expect(configRow(page, 'support.email').getByTestId('adminSettings.configuration.save').locator('button')).toBeDisabled();
    await save('petition.url', PETITION);
    await expect(configRow(page, 'petition.url').getByTestId('adminSettings.configuration.save').locator('button')).toBeDisabled();
    await save('petition.signatures', '12345');
    await expect(configRow(page, 'petition.signatures').getByTestId('adminSettings.configuration.save').locator('button')).toBeDisabled();

    // the history drawer lists the three changes with their new values
    await walk.click(page.getByTestId('adminSettings.configuration.historyButton'));
    const history = page.getByTestId('adminSettings.configuration.history.list');
    await expect(history).toHaveAttribute('data-state', 'success');
    await expect(page.getByTestId('adminSettings.configuration.history.entry')).toHaveCount(3);
    await expect(history).toContainText('hilfe@foodsharing.example');
    await expect(history).toContainText('12345');
    await checkpoint(page, 'configuration-history');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('adminSettings.configuration.historyDrawer')).toHaveCount(0);
  });

  await walk.step('puts two donation forms on the donation page and switches the modal on', async () => {
    await walk.click(page.getByTestId('adminSettings.tabs.donations'));
    await expect(page).toHaveURL(/\/admin\/donations$/);
    await expectDataState(page, 'adminSettings.donations.state', 'success');
    await walk.click(page.getByTestId('adminSettings.donations.showCampaignCard'));
    await walk.click(page.getByTestId('adminSettings.donations.showModal'));
    await walk.fill(page.getByTestId('adminSettings.donations.modalHoursLoggedOut'), '24');
    await walk.fill(page.getByTestId('adminSettings.donations.iframeOneTimeUrl'), ONE_TIME);
    await walk.fill(page.getByTestId('adminSettings.donations.iframeFriendshipCircleUrl'), CIRCLE);
    await checkpoint(page, 'donation-settings');
    await walk.click(page.getByTestId('adminSettings.donations.save'));
    await expect
      .poll(async () => (await apiOf(page).getJson<{ iframeOneTimeUrl?: string }>('/api/admin/donations')).iframeOneTimeUrl)
      .toBe(ONE_TIME);
    // the modal also needs its feature toggle
    await walk.click(page.getByTestId('adminSettings.tabs.toggles'));
    await expectDataState(page, 'adminSettings.toggles.state', 'success');
    await expect(toggleRow(page, 'DONATION_MODAL')).toHaveAttribute('data-enabled', 'false');
    await walk.click(toggleRow(page, 'DONATION_MODAL').getByTestId('adminSettings.toggles.switch'));
    await expect(toggleRow(page, 'DONATION_MODAL')).toHaveAttribute('data-enabled', 'true');
    // the modal shows at once, to the orga too
    await expect(page.getByTestId('content.donationModal.body')).toBeVisible();
    await walk.click(page.getByTestId('content.donationModal.later'));
    await expect(page.getByTestId('content.donationModal.body')).toHaveCount(0);
  });

  const guest = await as(null);
  const visitor = new Walk(guest, testInfo, 'visitor');
  const modal = guest.getByTestId('content.donationModal.body');

  await visitor.step('opens the donation page with its two forms', async () => {
    const response = await guest.goto('/donate');
    // decision 97: the CSP lets the forms' host into frames
    expect(response?.headers()['content-security-policy']).toMatch(/frame-src[^;]*https:\/\/spenden\.local\.test/);
    await expectDataState(guest, 'content.donate.state', 'success');
    await expect(guest.getByTestId('content.donate.campaign')).toHaveCount(0);
    const frames = guest.getByTestId('content.donate.frame');
    await expect(frames).toHaveCount(2);
    await expect(frames.nth(0)).toHaveAttribute('data-kind', 'oneTime');
    await expect(frames.nth(1)).toHaveAttribute('data-kind', 'friendshipCircle');
    const iframes = guest.getByTestId('content.donate.iframe');
    await expect(iframes.nth(0)).toHaveAttribute('src', ONE_TIME);
    await expect(iframes.nth(1)).toHaveAttribute('src', CIRCLE);
    await expect(iframes.nth(0)).toHaveAttribute('title', /.+/);
    // no modal on the donation page itself
    await expect(modal).toHaveCount(0);
    // the provider's forms are not reachable from the test machine: their frames are masked
    await checkpoint(guest, 'donate-forms', { mask: [iframes] });
  });

  await visitor.step('meets the donation modal once and the petition banner', async () => {
    await openWithDonation(guest, '/release-notes');
    await expect(modal).toBeVisible();
    await checkpoint(guest, 'donation-modal');
    await visitor.click(guest.getByTestId('content.donationModal.later'));
    await expect(modal).toHaveCount(0);
    await openWithDonation(guest, '/faq');
    await expectDataState(guest, 'content.faq.state', 'success');
    await expect(modal).toHaveCount(0);

    const banner = guest.getByTestId('content.petition.banner');
    await expect(banner).toBeVisible();
    await expect(guest.getByTestId('content.petition.bannerCount')).toContainText(/12.345/);
    await expect(guest.getByTestId('content.petition.bannerSign')).toHaveAttribute('href', PETITION);
    await expect(guest.getByTestId('content.petition.bannerSign')).toHaveAttribute('target', '_blank');
    await visitor.click(guest.getByTestId('content.petition.bannerMore'));
    await expect(guest).toHaveURL(/\/petition$/);
    await expectDataState(guest, 'content.petition.state', 'success');
    await expect(guest.getByTestId('content.petition.signatures')).toHaveAttribute('data-value', '12345');
    await expect(guest.getByTestId('content.petition.daysLeft')).toHaveText('21');
    await expect(guest.getByTestId('content.petition.sign')).toHaveAttribute('href', PETITION);
    // the banner stays off the petition page
    await expect(banner).toHaveCount(0);
    await checkpoint(guest, 'petition');

    // dismissed, it stays hidden in this browser
    await openWithToggles(guest, '/release-notes');
    await expect(banner).toBeVisible();
    await visitor.click(guest.getByTestId('content.petition.bannerDismiss'));
    await expect(banner).toHaveCount(0);
    await openWithToggles(guest, '/release-notes');
    await expectDataState(guest, 'content.releaseNotes.state', 'success');
    await expect(banner).toHaveCount(0);
  });

  await walk.step('switches the petition banner off after the petition', async () => {
    await expect(toggleRow(page, 'PETITION_BANNER')).toHaveAttribute('data-enabled', 'true');
    await walk.click(toggleRow(page, 'PETITION_BANNER').getByTestId('adminSettings.toggles.switch'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(toggleRow(page, 'PETITION_BANNER')).toHaveAttribute('data-enabled', 'false');
    await guest.goto('/petition');
    await expectDataState(guest, 'content.petition.state', 'notFound');
  });

  walk.finish({ steps: 3, clicks: 17, fields: 7 });
  visitor.finish({ steps: 2, clicks: 3, fields: 0 });
});
