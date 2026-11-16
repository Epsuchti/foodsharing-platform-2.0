import type { Page } from '@playwright/test';
import { acceptPendingConsents, expectDataState, pickOption } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm, newEmail, useFreshClientIp } from '../../fixtures/flows/auth';
import { Walk } from '../../fixtures/persona';

/**
 * A wave of spam sign-ups: ORGA (orga@local) blocks the throw-away mail domain (a duplicate is refused at the field)
 * and the sender's address for an hour, sees the automatic blocks of the API rate limit below them (D17.2: the limit
 * is off in the e2e profile, so the dev endpoint creates them) and lifts one, corrects the reason of the domain and
 * removes both entries once the wave is over. A visitor's sign-up is refused while they are in place and works
 * again afterwards. Rules behind it: EmailBlocklistServiceTest (pattern normalised, duplicates refused, matching),
 * IpBlockServiceTest (addresses and ranges, contexts, invalid address), MagicLinkServiceTest (EMAIL_DOMAIN_BLOCKED,
 * IP_BLOCKED), RateLimitBlockRegistryTest (levels, lift ends the block), ApiRateLimitFilterTest; client
 * admin-settings.spec (duplicate at the field, automatic blocks linked and lifted after confirm).
 */
test.use({ role: 'orga' });

const DOMAIN = 'wegwerfpost.example';
const SPAM_IP = '198.51.100.23';
const AUTO_IP = '203.0.113.7';

const blocklistRow = (page: Page, pattern: string) =>
  page.locator(`[data-testid="adminSettings.blocklist.row"][data-pattern="${pattern}"]`);

test('orga blocks a spam domain and address, lifts an automatic block and removes the blocks again @C12 @A1 @D17.2', async ({
  page,
  as,
  api,
  apiOf,
  dev,
}, testInfo) => {
  test.setTimeout(150_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'orga');
  const visitor = await as(null);
  await useFreshClientIp(visitor);

  /** The visitor asks for a sign-up link in the login form. */
  const requestLink = async (email: string) => {
    await visitor.goto('/login');
    await visitor.getByTestId('auth.login.email').fill(email);
    await visitor.getByTestId('auth.login.emailSubmit').click();
  };

  await walk.step('blocks the throw-away domain', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminSettings');
    await walk.click(page.getByTestId('adminSettings.tabs.blocklist'));
    await expect(page).toHaveURL(/\/admin\/email-blocklist$/);
    await expectDataState(page, 'adminSettings.blocklist.state', 'success');
    await walk.fill(page.getByTestId('adminSettings.blocklist.pattern'), `@${DOMAIN.toUpperCase()}`);
    await walk.fill(page.getByTestId('adminSettings.blocklist.reason'), 'Wegwerf-Adressen');
    await walk.click(page.getByTestId('adminSettings.blocklist.addButton'));
    // the server normalises the pattern (lower case, no leading @)
    await expect(blocklistRow(page, DOMAIN)).toBeVisible();
    await expect(blocklistRow(page, DOMAIN).getByTestId('adminSettings.blocklist.rowReason')).toHaveText('Wegwerf-Adressen');
    // a duplicate is refused at the field
    await walk.fill(page.getByTestId('adminSettings.blocklist.pattern'), DOMAIN);
    await walk.click(page.getByTestId('adminSettings.blocklist.addButton'));
    await expect(page.getByTestId('adminSettings.blocklist.error')).toBeVisible();
    await expect(blocklistRow(page, DOMAIN)).toHaveCount(1);
    await checkpoint(page, 'blocklist-duplicate');

    // a sign-up with that domain is refused in the login form
    await requestLink(`neu@${DOMAIN}`);
    await expect(visitor.getByTestId('auth.login.requestError')).toBeVisible();
    await checkpoint(visitor, 'sign-up-refused');
  });

  await walk.step('corrects the reason', async () => {
    await walk.click(blocklistRow(page, DOMAIN).getByTestId('adminSettings.blocklist.edit'));
    await walk.fill(page.getByTestId('adminSettings.blocklist.editReason'), 'Spam-Welle Oktober');
    await checkpoint(page, 'blocklist-edit');
    await walk.click(page.getByTestId('adminSettings.blocklist.editSave'));
    await expect(page.getByTestId('adminSettings.blocklist.editDialog')).toBeHidden();
    await expect(blocklistRow(page, DOMAIN).getByTestId('adminSettings.blocklist.rowReason')).toHaveText('Spam-Welle Oktober');
  });

  // meanwhile the API rate limit blocked an account and an address on its own (dev endpoint, see above)
  const foodsaver = await dev.user('foodsaver');
  await api.loginAs('orga');
  const accountBlock = await api.post('/api/dev/rate-limit-blocks', { scope: 'ACCOUNT', userId: foodsaver.id });
  expect(accountBlock.status()).toBe(201);
  const account = (await accountBlock.json()) as { id: string; level: number };
  expect(account.level).toBe(1);
  const ipBlock = await api.post('/api/dev/rate-limit-blocks', { scope: 'IP', ip: AUTO_IP });
  expect(ipBlock.status()).toBe(201);
  const automatic = (await ipBlock.json()) as { id: string };
  const autoRow = (id: string) => page.locator(`[data-testid="adminSettings.ipBlocks.autoRow"][data-id="${id}"]`);
  const ipRow = page.locator(`[data-testid="adminSettings.ipBlocks.row"][data-ip="${SPAM_IP}"]`);

  await walk.step('blocks the sender\'s address for an hour', async () => {
    await walk.click(page.getByTestId('adminSettings.tabs.ipBlocks'));
    await expect(page).toHaveURL(/\/admin\/ip-blocks$/);
    await walk.fill(page.getByTestId('adminSettings.ipBlocks.ip'), SPAM_IP);
    await pickOption(page, 'adminSettings.ipBlocks.durationSelect', '60');
    walk.count(2);
    await walk.click(page.getByTestId('adminSettings.ipBlocks.addButton'));
    // login and registration
    await expect(ipRow).toHaveAttribute('data-context', 'ALL');
    // an invalid address is shown at the field
    await walk.fill(page.getByTestId('adminSettings.ipBlocks.ip'), 'not-an-ip');
    await walk.click(page.getByTestId('adminSettings.ipBlocks.addButton'));
    await expect(page.getByTestId('adminSettings.ipBlocks.error')).toBeVisible();

    // the automatic blocks below: the account links to the profile, the address is code
    await expectDataState(page, 'adminSettings.ipBlocks.autoState', 'success');
    await expect(page.getByTestId('adminSettings.ipBlocks.autoRow')).toHaveCount(2);
    await expect(autoRow(account.id)).toHaveAttribute('data-scope', 'ACCOUNT');
    await expect(autoRow(account.id).getByRole('link', { name: foodsaver.displayName })).toHaveAttribute('href', `/profile/${foodsaver.id}`);
    await expect(autoRow(automatic.id)).toHaveAttribute('data-scope', 'IP');
    await expect(autoRow(automatic.id).locator('code')).toHaveText(AUTO_IP);
    await checkpoint(page, 'ip-blocks');

    // a sign-up from the blocked address is refused
    await visitor.context().setExtraHTTPHeaders({ 'X-Forwarded-For': SPAM_IP });
    const refused = await apiOf(visitor).post('/api/auth/magic-link', { email: newEmail('ipblock') });
    expect(refused.status()).toBe(403);
    expect((await refused.json()).code).toBe('IP_BLOCKED');
  });

  await walk.step('lifts the automatic block of the account', async () => {
    await walk.click(autoRow(account.id).getByTestId('adminSettings.ipBlocks.autoLift'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(autoRow(account.id)).toHaveCount(0);
    await expect(autoRow(automatic.id)).toHaveCount(1);
    // lifted means gone on the server too (a second lift is 404)
    expect((await api.delete(`/api/admin/rate-limit-blocks/${account.id}`)).status()).toBe(404);
  });

  await walk.step('removes both blocks once the wave is over', async () => {
    await walk.click(ipRow.getByTestId('adminSettings.ipBlocks.remove'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(ipRow).toHaveCount(0);
    expect((await apiOf(visitor).post('/api/auth/magic-link', { email: newEmail('ipfree') })).status()).toBe(202);

    await walk.click(page.getByTestId('adminSettings.tabs.blocklist'));
    await expectDataState(page, 'adminSettings.blocklist.state', 'success');
    await walk.click(blocklistRow(page, DOMAIN).getByTestId('adminSettings.blocklist.remove'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(blocklistRow(page, DOMAIN)).toHaveCount(0);
    await useFreshClientIp(visitor);
    await requestLink(`neu@${DOMAIN}`);
    await expect(visitor).toHaveURL(/\/login\/sent/);
  });

  walk.finish({ steps: 5, clicks: 19, fields: 6 });
});
