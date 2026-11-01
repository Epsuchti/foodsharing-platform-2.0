import type { Page } from '@playwright/test';
import { expect, expectDataState, test } from '../../fixtures';
import { acceptConfirm } from '../auth/_auth';

/**
 * D17.2 automatic blocks of the API rate limit: the limit itself is off in the e2e profile (one client IP, many test
 * users; ApiRateLimitFilterTest covers it), so the dev endpoint creates the blocks. ORGA sees them on
 * `/admin/ip-blocks` below the manual IP blocks (account linked to the profile, IP as code) and lifts one.
 */
test.use({ seedReset: 'test', role: 'orga' });

const autoRow = (page: Page, id: string) => page.locator(`[data-testid="adminSettings.ipBlocks.autoRow"][data-id="${id}"]`);

test('D17.2 orga sees automatic rate-limit blocks and lifts one @D17.2 @page', async ({ page, api, dev }) => {
  await page.goto('/admin/ip-blocks');
  await expectDataState(page, 'adminSettings.ipBlocks.autoState', 'empty');

  const foodsaver = await dev.user('foodsaver');
  const accountResponse = await api.post('/api/dev/rate-limit-blocks', { scope: 'ACCOUNT', userId: foodsaver.id });
  expect(accountResponse.status()).toBe(201);
  const account = (await accountResponse.json()) as { id: string; level: number };
  expect(account.level).toBe(1);
  const ipResponse = await api.post('/api/dev/rate-limit-blocks', { scope: 'IP', ip: '203.0.113.7' });
  expect(ipResponse.status()).toBe(201);
  const ip = (await ipResponse.json()) as { id: string };

  await page.reload();
  await expectDataState(page, 'adminSettings.ipBlocks.autoState', 'success');
  await expect(page.getByTestId('adminSettings.ipBlocks.autoRow')).toHaveCount(2);
  await expect(autoRow(page, account.id)).toHaveAttribute('data-scope', 'ACCOUNT');
  await expect(autoRow(page, account.id).getByRole('link', { name: foodsaver.displayName })).toHaveAttribute('href', `/profile/${foodsaver.id}`);
  await expect(autoRow(page, ip.id)).toHaveAttribute('data-scope', 'IP');
  await expect(autoRow(page, ip.id).locator('code')).toHaveText('203.0.113.7');

  // lifting asks first, then the row is gone; the other block stays
  await autoRow(page, account.id).getByTestId('adminSettings.ipBlocks.autoLift').click();
  await acceptConfirm(page);
  await expect(autoRow(page, account.id)).toHaveCount(0);
  await expect(autoRow(page, ip.id)).toHaveCount(1);

  // lifted means gone on the server too (a second lift is 404)
  expect((await api.delete(`/api/admin/rate-limit-blocks/${account.id}`)).status()).toBe(404);
});
