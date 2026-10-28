import { expect, test, type SessionInfo } from '../../fixtures';
import {
  acceptConsentPage,
  myActions,
  newEmail,
  randomClientIp,
  requestLinkWithForm,
  skipPasskeyNudge,
  useFreshClientIp,
  type HistoryEntryPage,
} from './_auth';

/** A1: sign-up with Google or a magic link, registration attempt tracking, email blocklist (plan §8). */

test.beforeEach(async ({ page }) => {
  await skipPasskeyNudge(page);
});

test('A1 sign-up with Google creates an account @A1 @D8.1 @page', async ({ page, apiOf }) => {
  const email = newEmail('google');
  await page.goto('/login');
  await page.getByTestId('auth.login.google').click();

  // the embedded fake Google (local/e2e profile)
  await expect(page.getByTestId('dev.oidc.page')).toBeVisible();
  await page.getByTestId('dev.oidc.new-email').fill(email);
  await page.getByTestId('dev.oidc.new-name').fill('Gina Neu');
  await page.getByTestId('dev.oidc.new-submit').click();

  // a new account accepts the privacy policy first (EA4), then continues to the dashboard
  await acceptConsentPage(page, /\/dashboard$/);
  await expect(page.getByTestId('dashboard.page.name')).toHaveText('Gina Neu');

  const api = apiOf(page);
  const session = await api.session();
  expect(session.user?.email).toBe(email);
  const actions = await myActions(api);
  expect(actions).toContain('ACCOUNT_CREATED');
  expect(actions).toContain('LOGIN');
});

test('A1 Google login links to existing email account @A1', async ({ page, apiOf, dev }) => {
  const newbie = await dev.user('newbie');
  await page.goto('/login?returnUrl=%2Faccount%2Fsettings');
  await page.getByTestId('auth.login.google').click();
  await page.getByTestId('dev.oidc.new-email').fill('newbie@local');
  await page.getByTestId('dev.oidc.new-name').fill('Lea B.');
  await page.getByTestId('dev.oidc.new-submit').click();

  // back on the page the login started from (after the pending privacy consent of the seed newbie)
  await acceptConsentPage(page, /\/account\/settings$/);
  const api = apiOf(page);
  const session = await api.session();
  expect(session.user?.id).toBe(newbie.id);
  expect(await myActions(api)).toContain('ACCOUNT_LINKED');
  await expect(page.getByTestId('account.settings.identity.GOOGLE')).toBeVisible();
});

test('A1 unverified Google email is rejected @A1', async ({ page, apiOf }) => {
  await page.goto('/login');
  await page.getByTestId('auth.login.google').click();
  await page.getByTestId('dev.oidc.new-email').fill(newEmail('unverified'));
  await page.getByTestId('dev.oidc.new-unverified').check();
  await page.getByTestId('dev.oidc.new-submit').click();

  await expect(page).toHaveURL(/\/login\?error=GOOGLE_EMAIL_NOT_VERIFIED/);
  await expect(page.getByTestId('auth.login.error')).toBeVisible();
  expect((await apiOf(page).session()).authenticated).toBe(false);
  // the email link is still offered as the way out
  await expect(page.getByTestId('auth.login.email')).toBeVisible();
});

test('A1 magic link sign-up for a new email @A1 @D8.2', async ({ page, mails, apiOf }) => {
  await useFreshClientIp(page);
  const email = newEmail('link');
  await page.goto('/login?method=email');
  await expect(page.getByTestId('auth.login.email')).toBeFocused();
  await requestLinkWithForm(page, email);

  const mail = await mails.waitForMail({ to: email });
  await page.goto(mails.extractPath(mail, '/login/verify'));

  await acceptConsentPage(page, /\/dashboard$/);
  const api = apiOf(page);
  const session: SessionInfo = await api.session();
  expect(session.user?.email).toBe(email);
  expect(session.user?.role).toBe('FOODSHARER');
  expect(await myActions(api)).toEqual(expect.arrayContaining(['ACCOUNT_CREATED', 'LOGIN']));
});

test('A1 blocked email domain shows EMAIL_DOMAIN_BLOCKED @A1', async ({ page, mails }) => {
  await useFreshClientIp(page);
  await page.goto('/login');
  await page.getByTestId('auth.login.email').fill('spam@mailinator.com');
  await page.getByTestId('auth.login.emailSubmit').click();

  await expect(page.getByTestId('auth.login.requestError')).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
  expect(await mails.list('spam@mailinator.com')).toHaveLength(0);
});

test('A1 login requests are rate limited @A1', async ({ page, api }) => {
  const ip = await useFreshClientIp(page);
  const email = newEmail('limit');
  for (let i = 0; i < 3; i++) {
    const response = await api.post('/api/auth/magic-link', { email }, { headers: { 'X-Forwarded-For': ip } });
    expect(response.status()).toBe(202);
  }

  await page.goto('/login');
  await page.getByTestId('auth.login.email').fill(email);
  await page.getByTestId('auth.login.emailSubmit').click();

  const toast = page.getByTestId('shell.toast');
  await expect(toast).toContainText('900');
  await expect(page).toHaveURL(/\/login$/);
});

test('A1 registration attempts are recorded @A1', async ({ api, as }) => {
  const blocked = await api.post(
    '/api/auth/magic-link',
    { email: 'someone@example.invalid' },
    { headers: { 'X-Forwarded-For': randomClientIp() } },
  );
  expect(blocked.status()).toBe(400);
  expect((await blocked.json()).code).toBe('EMAIL_DOMAIN_BLOCKED');

  const orgaPage = await as('orga');
  const orga = orgaPage.context().request;
  const response = await orga.get('/api/history?actions=REGISTRATION_BLOCKED&size=50');
  expect(response.status()).toBe(200);
  const history = (await response.json()) as HistoryEntryPage;
  const entry = history.items.find((item) => item.metadata?.['emailDomain'] === 'example.invalid');
  expect(entry, 'REGISTRATION_BLOCKED entry for example.invalid').toBeTruthy();
  expect(entry?.metadata?.['reason']).toBe('EMAIL_DOMAIN_BLOCKED');
});
