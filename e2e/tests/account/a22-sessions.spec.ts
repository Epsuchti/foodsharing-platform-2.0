import { expect, test } from '../../fixtures';
import { acceptConfirm, sessionIdOf, skipPasskeyNudge, type HistoryEntryPage, type SessionView } from '../auth/_auth';

/** A22: active sessions, revoke one, log out everywhere, security log. */

// every test starts from the seed state so the session lists only contain this test's logins
test.use({ role: 'foodsaver', seedReset: 'test' });

test.beforeEach(async ({ page }) => {
  await skipPasskeyNudge(page);
});

test('A22 active sessions list marks current @A22 @page', async ({ page, as, apiOf }) => {
  const phone = await as('foodsaver');
  const phoneSession = await sessionIdOf(apiOf(phone));
  const ownSession = await sessionIdOf(apiOf(page));

  await page.goto('/account/sessions');
  const list = page.getByTestId('account.sessions.list');
  await expect(list).toHaveAttribute('data-state', 'success');
  await expect(list.locator('[data-testid^="account.sessions.item."][data-current]')).toHaveCount(2);

  const current = page.getByTestId(`account.sessions.item.${ownSession}`);
  await expect(current).toHaveAttribute('data-current', 'true');
  await expect(current.getByTestId('account.sessions.current')).toBeVisible();
  await expect(page.getByTestId(`account.sessions.item.${phoneSession}`)).toHaveAttribute('data-current', 'false');
  await expect(page.getByTestId(`account.sessions.item.${phoneSession}.revoke`)).toBeVisible();
  await expect(page.getByTestId('account.sessions.logoutEverywhere')).toBeVisible();
});

test('A22 revoke another session logs that context out @A22', async ({ page, as, apiOf }) => {
  const other = await as('foodsaver');
  const otherSession = await sessionIdOf(apiOf(other));
  await other.goto('/account/settings');
  await expect(other.getByTestId('account.settings.methods')).toHaveAttribute('data-state', 'success');

  await page.goto('/account/sessions');
  await page.getByTestId(`account.sessions.item.${otherSession}.revoke`).click();
  await acceptConfirm(page);
  await expect(page.getByTestId(`account.sessions.item.${otherSession}`)).toHaveCount(0);
  await expect(page.getByTestId('account.sessions.onlyThis')).toBeVisible();

  // the other browser is logged out: its next request ends in the "session expired" dialog
  await other.getByTestId('account.tabs.sessions').click();
  await expect(other.getByTestId('shell.sessionExpired.login')).toBeVisible();
  expect((await apiOf(other).session()).authenticated).toBe(false);
  // and this one is still logged in
  expect((await apiOf(page).session()).authenticated).toBe(true);
});

test('A22 log out everywhere @A22', async ({ page, as, apiOf }) => {
  const laptop = await as('foodsaver');
  const phone = await as('foodsaver');

  await page.goto('/account/sessions');
  await expect(page.locator('[data-testid^="account.sessions.item."][data-current]')).toHaveCount(3);
  await page.getByTestId('account.sessions.logoutEverywhere').click();
  await acceptConfirm(page);

  await expect(page.locator('[data-testid^="account.sessions.item."][data-current]')).toHaveCount(1);
  await expect(page.getByTestId('account.sessions.logoutEverywhere')).toHaveCount(0);
  expect((await apiOf(laptop).session()).authenticated).toBe(false);
  expect((await apiOf(phone).session()).authenticated).toBe(false);
  const sessions = await apiOf(page).getJson<SessionView[]>('/api/auth/sessions');
  expect(sessions.map((session) => session.current)).toEqual([true]);
});

test('A22 security log lists logins and revocations @A22 @page', async ({ page, as, apiOf }) => {
  const other = await as('foodsaver');
  const otherApi = apiOf(other);
  // a second device logs in and is then signed out from here
  const revoked = await apiOf(page).delete(`/api/auth/sessions/${await sessionIdOf(otherApi)}`);
  expect(revoked.status()).toBe(204);

  await page.goto('/account/security-log');
  const list = page.getByTestId('account.securityLog.list');
  await expect(list).toHaveAttribute('data-state', 'success');
  await expect(list.locator('[data-action="LOGIN"]').first()).toBeVisible();
  await expect(list.locator('[data-action="SESSION_REVOKED"]').first()).toBeVisible();

  const log = await apiOf(page).getJson<HistoryEntryPage>('/api/auth/security-log');
  const actions = log.items.map((entry) => entry.action);
  expect(actions).toContain('LOGIN');
  expect(actions).toContain('SESSION_REVOKED');
});
