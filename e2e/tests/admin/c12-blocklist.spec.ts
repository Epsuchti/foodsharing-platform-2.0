import type { Page } from '@playwright/test';
import { expect, expectDataState, expectErrorState, injectApiError, pickOption, test } from '../../fixtures';
import { acceptConfirm, newEmail, randomClientIp, useFreshClientIp } from '../auth/_auth';

/**
 * C12 email blocklist and IP blocks (plan-m7 Q13, A53, A1): ORGA blocks a domain and an address range on
 * `/admin/email-blocklist` and `/admin/ip-blocks`; sign-ups from them are refused (400 EMAIL_DOMAIN_BLOCKED,
 * 403 IP_BLOCKED); entries are edited and removed, after which sign-ups work again.
 */
test.use({ seedReset: 'test', role: 'orga' });

const blocklistRow = (page: Page, pattern: string) =>
  page.locator(`[data-testid="adminSettings.blocklist.row"][data-pattern="${pattern}"]`);

test('C12 orga adds blocklist domain and IP block; blocked sign-up refused; entries edited and removed @C12 @A1 @page', async ({ page, as, apiOf }) => {
  const domain = `blocked-${Date.now().toString(36)}.example`;

  // --- email blocklist ---
  await page.goto('/admin/email-blocklist');
  await expectDataState(page, 'adminSettings.blocklist.state', 'success'); // seed entries (mailinator.com, …)
  await page.getByTestId('adminSettings.blocklist.pattern').fill(`@${domain.toUpperCase()}`);
  await page.getByTestId('adminSettings.blocklist.reason').fill('Wegwerf-Adressen');
  await page.getByTestId('adminSettings.blocklist.addButton').click();
  // the server normalises the pattern (lower case, no leading @)
  await expect(blocklistRow(page, domain)).toBeVisible();
  await expect(blocklistRow(page, domain).getByTestId('adminSettings.blocklist.rowReason')).toHaveText('Wegwerf-Adressen');

  // a duplicate is refused at the field
  await page.getByTestId('adminSettings.blocklist.pattern').fill(domain);
  await page.getByTestId('adminSettings.blocklist.addButton').click();
  await expect(page.getByTestId('adminSettings.blocklist.error')).toBeVisible();
  await expect(page.locator(`[data-testid="adminSettings.blocklist.row"][data-pattern="${domain}"]`)).toHaveCount(1);

  // a sign-up with that domain is refused in the login form
  const visitor = await as(null);
  await useFreshClientIp(visitor);
  await visitor.goto('/login');
  await visitor.getByTestId('auth.login.email').fill(`neu@${domain}`);
  await visitor.getByTestId('auth.login.emailSubmit').click();
  await expect(visitor.getByTestId('auth.login.requestError')).toBeVisible();

  // edit the reason
  await blocklistRow(page, domain).getByTestId('adminSettings.blocklist.edit').click();
  await page.getByTestId('adminSettings.blocklist.editReason').fill('Spam-Welle Oktober');
  await page.getByTestId('adminSettings.blocklist.editSave').click();
  await expect(page.getByTestId('adminSettings.blocklist.editDialog')).toBeHidden();
  await expect(blocklistRow(page, domain).getByTestId('adminSettings.blocklist.rowReason')).toHaveText('Spam-Welle Oktober');

  // --- IP block (one hour, login and registration) ---
  const ip = randomClientIp();
  await page.getByTestId('adminSettings.tabs.ipBlocks').click();
  await expect(page).toHaveURL(/\/admin\/ip-blocks$/);
  await page.getByTestId('adminSettings.ipBlocks.ip').fill(ip);
  await pickOption(page, 'adminSettings.ipBlocks.durationSelect', '60');
  await page.getByTestId('adminSettings.ipBlocks.addButton').click();
  const ipRow = page.locator(`[data-testid="adminSettings.ipBlocks.row"][data-ip="${ip}"]`);
  await expect(ipRow).toHaveAttribute('data-context', 'ALL');
  // an invalid address is shown at the field
  await page.getByTestId('adminSettings.ipBlocks.ip').fill('not-an-ip');
  await page.getByTestId('adminSettings.ipBlocks.addButton').click();
  await expect(page.getByTestId('adminSettings.ipBlocks.error')).toBeVisible();

  const signUpFrom = (address: string, email: string) =>
    apiOf(visitor).post('/api/auth/magic-link', { email }, { headers: { 'X-Forwarded-For': address } });
  const blockedIp = await signUpFrom(ip, newEmail('ipblock'));
  expect(blockedIp.status()).toBe(403);
  expect((await blockedIp.json()).code).toBe('IP_BLOCKED');

  // --- remove both; sign-ups work again ---
  await ipRow.getByTestId('adminSettings.ipBlocks.remove').click();
  await acceptConfirm(page);
  await expect(ipRow).toHaveCount(0);
  expect((await signUpFrom(ip, newEmail('ipfree'))).status()).toBe(202);

  await page.getByTestId('adminSettings.tabs.blocklist').click();
  await expectDataState(page, 'adminSettings.blocklist.state', 'success');
  await blocklistRow(page, domain).getByTestId('adminSettings.blocklist.remove').click();
  await acceptConfirm(page);
  await expect(blocklistRow(page, domain)).toHaveCount(0);
  expect((await signUpFrom(randomClientIp(), `neu@${domain}`)).status()).toBe(202);
});

test('C12 blocklist and IP block lists show error states with retry @C12', async ({ page }) => {
  await injectApiError(page, { url: '/api/admin/email-blocklist', method: 'GET', status: 500, times: 3 });
  await page.goto('/admin/email-blocklist');
  const { retry } = await expectErrorState(page, 'adminSettings.blocklist.state');
  await retry.click();
  await expectDataState(page, 'adminSettings.blocklist.state', 'success');

  await injectApiError(page, { url: '/api/admin/ip-blocks', method: 'GET', status: 500, times: 3 });
  await page.goto('/admin/ip-blocks');
  const ipBlocks = await expectErrorState(page, 'adminSettings.ipBlocks.state');
  await ipBlocks.retry.click();
  await expect(page.getByTestId('adminSettings.ipBlocks.state')).toHaveAttribute('data-state', /success|empty/);
});
