import { addVirtualAuthenticator, expect, test } from '../../fixtures';
import {
  loginWithSwitcher,
  logoutWithMenu,
  myActions,
  requestLinkWithForm,
  sessionIdOf,
  skipPasskeyNudge,
  useFreshClientIp,
  type HistoryEntryPage,
} from './_auth';

/** A2: login / logout, passkeys, session refresh after a role change, open-redirect guard, expired sessions. */

test('A2 magic link login and logout @A2 @page', async ({ page, mails, apiOf }) => {
  await useFreshClientIp(page);
  await page.goto('/login');
  await requestLinkWithForm(page, 'foodsaver@local');

  const mail = await mails.waitForMail({ to: 'foodsaver@local' });
  await page.goto(mails.extractPath(mail, '/login/verify'));
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByTestId('dashboard.page.name')).toHaveText('Sarah Müller');

  // first login on this device with a link → "log in faster next time" (once per device)
  await expect(page.getByTestId('auth.passkeyNudge')).toBeVisible();
  await page.getByTestId('auth.passkeyNudge.dismiss').click();
  await expect(page.getByTestId('auth.passkeyNudge')).toBeHidden();

  await logoutWithMenu(page);
  await expect(page).toHaveURL(/\/$/);
  expect((await apiOf(page).session()).authenticated).toBe(false);
});

test('A2 used or expired token shows error state with new-link action @A2', async ({ page, mails, as }) => {
  await useFreshClientIp(page);
  await skipPasskeyNudge(page);
  await page.goto('/login?returnUrl=%2Faccount%2Fsessions');
  await requestLinkWithForm(page, 'bieb@local');
  const mail = await mails.waitForMail({ to: 'bieb@local' });
  const link = mails.extractPath(mail, '/login/verify');

  // first use logs in and honours the return URL carried by the link
  await page.goto(link);
  await expect(page).toHaveURL(/\/account\/sessions$/);

  // second use (another browser) fails
  const other = await as(null);
  await other.goto(link);
  await expect(other.getByTestId('auth.verify.tokenError')).toBeVisible();
  await expect(other.getByTestId('auth.verify.state')).toHaveAttribute('data-state', 'error');
  await other.getByTestId('auth.verify.newLink').click();
  await expect(other).toHaveURL(/\/login\?method=email/);
  await expect(other.getByTestId('auth.login.email')).toBeFocused();

  // a made-up token is rejected the same way
  await other.goto('/login/verify?token=not-a-real-token');
  await expect(other.getByTestId('auth.verify.tokenError')).toBeVisible();
});

test('A2 passkey registration and login @A2 @chromium-only', async ({ page, apiOf }) => {
  await skipPasskeyNudge(page);
  const authenticator = await addVirtualAuthenticator(page);
  await page.goto('/login');
  await loginWithSwitcher(page, 'foodsaver');

  await page.goto('/account/settings');
  await expect(page.getByTestId('account.settings.methods')).toHaveAttribute('data-state', 'success');
  await page.getByTestId('account.settings.passkeys.add').click();
  const label = page.getByTestId('account.settings.passkeyDialog.label');
  await expect(label).not.toHaveValue('');
  await label.fill('E2E security key');
  await page.getByTestId('account.settings.passkeyDialog.save').click();
  await expect(page.getByTestId('account.settings.passkeys')).toContainText('E2E security key');
  expect(await authenticator.credentials()).toHaveLength(1);

  // 1) passkey autofill (conditional mediation): the virtual authenticator answers right away
  await logoutWithMenu(page);
  await page.goto('/login?returnUrl=%2Faccount%2Fsecurity-log');
  await expect(page).toHaveURL(/\/account\/security-log$/);

  // 2) the "log in with a passkey" button, as in browsers without passkey autofill
  await logoutWithMenu(page);
  await page.addInitScript(() => {
    Object.defineProperty(PublicKeyCredential, 'isConditionalMediationAvailable', { value: undefined });
  });
  await page.goto('/login?returnUrl=%2Faccount%2Fsecurity-log');
  await page.getByTestId('auth.login.passkey').click();
  await expect(page).toHaveURL(/\/account\/security-log$/);

  const api = apiOf(page);
  expect((await api.session()).user?.email).toBe('foodsaver@local');
  const log = await api.getJson<HistoryEntryPage>('/api/auth/security-log');
  expect(log.items.map((entry) => entry.action)).toContain('PASSKEY_REGISTERED');
  expect(log.items.some((entry) => entry.action === 'LOGIN' && entry.metadata?.['method'] === 'PASSKEY')).toBe(true);
  await expect(page.getByTestId('account.securityLog.entry').first()).toBeVisible();
});

test.describe('logged in as newbie', () => {
  test.use({ role: 'newbie' });

  test('A2 session refresh after role change @A2', async ({ page, dev }) => {
    await page.clock.install();
    await page.goto('/dashboard');
    await page.getByTestId('shell.userMenu').click();
    await expect(page.getByTestId('shell.userMenu.role')).toHaveText('Foodsharer:in');
    await page.getByTestId('shell.userMenu').click();
    await expect(page.getByTestId('shell.userMenu.role')).toBeHidden();

    await dev.setRole('newbie', 'ORGA');
    // the tab comes back into view after more than the refresh throttle
    await page.clock.fastForward('02:00');
    const refreshed = page.waitForResponse((response) => response.url().endsWith('/api/auth/session'));
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    await refreshed;

    await page.getByTestId('shell.userMenu').click();
    await expect(page.getByTestId('shell.userMenu.role')).toHaveText('Orga');
  });
});

test('A2 open redirect is ignored @A2', async ({ page }) => {
  await skipPasskeyNudge(page);
  await page.goto('/login?returnUrl=https%3A%2F%2Fevil.example%2Fsteal');
  // the Google button never forwards a foreign return URL
  const google = page.waitForRequest((request) => request.url().includes('/oauth2/authorization/google'));
  await page.getByTestId('auth.login.google').click();
  expect(new URL((await google).url()).searchParams.get('returnUrl')).toBeNull();
  await expect(page.getByTestId('dev.oidc.page')).toBeVisible();

  await page.goto('/login?returnUrl=%2F%2Fevil.example');
  await loginWithSwitcher(page, 'foodsaver', /\/dashboard$/);
  await expect(page).toHaveURL(/^http:\/\/localhost:\d+\/dashboard$/);
});

test.describe('logged in as foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('A2 expired session shows dialog and returns after login @A2', async ({ page, apiOf }) => {
    await page.goto('/account/settings');
    await expect(page.getByTestId('account.settings.methods')).toHaveAttribute('data-state', 'success');

    // the session ends elsewhere (e.g. "log out everywhere" on another device)
    const api = apiOf(page);
    const response = await api.delete(`/api/auth/sessions/${await sessionIdOf(api)}`);
    expect(response.status()).toBe(204);

    await page.getByTestId('account.tabs.sessions').click();
    await expect(page.getByTestId('shell.sessionExpired.login')).toBeVisible();
    await page.getByTestId('shell.sessionExpired.login').click();
    await expect(page).toHaveURL(/\/login\?returnUrl=%2Faccount%2Fsessions$/);

    await loginWithSwitcher(page, 'foodsaver', /\/account\/sessions$/);
    await expect(page.getByTestId('account.sessions.list')).toHaveAttribute('data-state', 'success');
    expect(await myActions(api)).toContain('SESSION_REVOKED');
  });
});
