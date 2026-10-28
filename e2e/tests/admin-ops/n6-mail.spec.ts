import { expect, test } from '../../fixtures';

/**
 * N6: mail outbox admin — status filter and search reach the API, empty state. The e2e seed has no outbox rows
 * (backend request: seed SENT/PENDING/FAILED rows so the retry flow can be tested end to end).
 */
test.use({ seedReset: 'test' });

test('N6 outbox admin filters by status and searches @N6 @page', async ({ as }) => {
  const page = await as('orga');
  await page.goto('/admin/mail-outbox');
  const state = page.getByTestId('adminOps.outbox.state');
  await expect(state).toHaveAttribute('data-state', /success|empty/);

  const failed = page.waitForRequest((request) => request.url().includes('/api/admin/mail-outbox') && request.url().includes('status=FAILED'));
  await page.getByTestId('adminOps.outbox.filter').locator('.p-togglebutton').nth(3).click();
  await failed;
  await expect(state).toHaveAttribute('data-state', /success|empty/);
  await expect(page.locator('[data-testid="adminOps.outbox.row"]:not([data-status="FAILED"])')).toHaveCount(0);

  const searched = page.waitForRequest((request) => request.url().includes('/api/admin/mail-outbox') && request.url().includes('search=nobody'));
  await page.getByTestId('adminOps.outbox.search').fill('nobody@nowhere.invalid');
  await searched;
  await expect(state).toHaveAttribute('data-state', 'empty');
});
