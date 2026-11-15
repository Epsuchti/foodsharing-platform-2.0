import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { logoutWithMenu, myActions, useFreshClientIp } from '../../fixtures/flows/auth';
import { Walk } from '../../fixtures/persona';

/**
 * Newcomer Gina signs up with an email link (A1, D8.2): the link opens her new account after the privacy consent
 * (EA4), a second use of the same link (her phone) is refused with a way to a new link. Later she opens her settings
 * logged out, is sent to the login and signs in with Google (the embedded fake OIDC of the e2e profile): the Google
 * identity is linked to the account of the same verified address and she lands back on the settings (D8.1).
 *
 * Rules behind it are unit tests: account creation from Google, blocked domains and the recorded attempt
 * (AccountServiceTest), unverified Google addresses (GoogleOidcUserServiceTest), rate limits (LoginRateLimiterTest),
 * used/expired tokens (MagicLinkServiceTest), open redirects (ReturnUrlValidatorTest, client return-url.spec).
 */
const EMAIL = 'gina.neu@example.org';

test('newcomer signs up with an email link and links Google to her account @A1 @A2 @D8.1 @D8.2', async ({
  page,
  as,
  apiOf,
  mails,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'newcomer');
  // magic-link requests are rate limited per client IP; every run uses an IP of its own
  await useFreshClientIp(page);
  let link = '';

  await walk.step('asks for a login link', async () => {
    await page.goto('/login');
    await expect(page.getByTestId('auth.login.email')).toBeVisible();
    await checkpoint(page, 'login');
    await walk.fill(page.getByTestId('auth.login.email'), EMAIL);
    await walk.click(page.getByTestId('auth.login.emailSubmit'));
    await expect(page).toHaveURL(/\/login\/sent\?/);
    await expect(page.getByTestId('auth.sent.email')).toHaveText(EMAIL);
    await checkpoint(page, 'magic-link-sent');
  });

  await walk.step('opens the link from the mail and accepts the privacy policy', async () => {
    const mail = await mails.waitForMail({ to: EMAIL });
    link = mails.extractPath(mail, '/login/verify');
    await page.goto(link);
    await expect(page).toHaveURL(/\/consent(\?|$)/);
    await expect(page.getByTestId('legal.consent.documents')).toHaveAttribute('data-state', 'success');
    await checkpoint(page, 'consent');
    const checks = page.locator('[data-testid^="legal.consent.check."] input');
    for (let i = 0; i < (await checks.count()); i++) await walk.check(checks.nth(i));
    await walk.click(page.getByTestId('legal.consent.submit'));
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByTestId('dashboard.page.name')).toHaveText('gina.neu');
    // first login on this device with a link → "log in faster next time"; never without WebAuthn (Linux WebKit)
    if (await page.evaluate(() => typeof window.PublicKeyCredential === 'function')) {
      await expect(page.getByTestId('auth.passkeyNudge')).toBeVisible();
      await checkpoint(page, 'dashboard-passkey-nudge');
      await walk.click(page.getByTestId('auth.passkeyNudge.dismiss'));
    }
    await expect(page.getByTestId('auth.passkeyNudge')).toBeHidden();

    const api = apiOf(page);
    const session = await api.session();
    expect(session.user?.email).toBe(EMAIL);
    expect(session.user?.role).toBe('FOODSHARER');
    expect(await myActions(api)).toEqual(expect.arrayContaining(['ACCOUNT_CREATED', 'LOGIN']));
  });

  await walk.step('opens the same link again on her phone', async () => {
    const phone = await as(null);
    await phone.goto(link);
    await expect(phone.getByTestId('auth.verify.tokenError')).toBeVisible();
    await expect(phone.getByTestId('auth.verify.state')).toHaveAttribute('data-state', 'error');
    await checkpoint(phone, 'link-used');
    await walk.click(phone.getByTestId('auth.verify.newLink'));
    await expect(phone).toHaveURL(/\/login\?method=email/);
    await expect(phone.getByTestId('auth.login.email')).toBeFocused();
  });

  await walk.step('logs out', async () => {
    walk.count(2);
    await logoutWithMenu(page);
    await expect(page).toHaveURL(/\/$/);
    expect((await apiOf(page).session()).authenticated).toBe(false);
  });

  await walk.step('opens her settings and signs in with Google', async () => {
    await page.goto('/account/settings');
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Faccount%2Fsettings$/);
    await walk.click(page.getByTestId('auth.login.google'));
    // the embedded fake Google (local/e2e profile)
    await expect(page.getByTestId('dev.oidc.page')).toBeVisible();
    await walk.fill(page.getByTestId('dev.oidc.new-email'), EMAIL);
    await walk.fill(page.getByTestId('dev.oidc.new-name'), 'Gina Neu');
    await walk.click(page.getByTestId('dev.oidc.new-submit'));
    await expect(page).toHaveURL(/\/account\/settings$/);
    await expect(page.getByTestId('account.settings.methods')).toHaveAttribute('data-state', 'success');
    await expect(page.getByTestId('account.settings.identity.GOOGLE')).toBeVisible();
    await checkpoint(page, 'login-methods');
    const api = apiOf(page);
    expect((await api.session()).user?.email).toBe(EMAIL);
    expect(await myActions(api)).toContain('ACCOUNT_LINKED');
  });

  // measured 2026-11-15 (rework step 3): 9 clicks on desktop and phone
  walk.finish({ steps: 5, clicks: 9, fields: 3 });
});
