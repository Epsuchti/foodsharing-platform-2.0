import type { Page } from '@playwright/test';
import {
  acceptPendingConsents,
  expect,
  expectDataState,
  expectErrorState,
  injectApiError,
  pickOption,
  seedRegion,
  test,
} from '../../fixtures';

/**
 * AD1 admins UI (plan-m7 Q13, plan §14.4, decision 94): ORGA lists every ambassador and working-group admin on
 * `/admin/admins` (search, unit kind, active filter), disables and re-enables an admin with a reason; an
 * ambassador manages scoped permissions of a member in the region members tab (matrix with source).
 */
test.use({ seedReset: 'test' });

const ZH = seedRegion('zuerich-stadt').id;
const adminRow = (page: Page, userId: string) => page.locator(`[data-testid="admin.admins.row"][data-user-id="${userId}"]`);

test.describe('orga', () => {
  test.use({ role: 'orga' });

  test('AD1 orga lists and filters admins, disables and enables an admin with a reason @AD1 @page', async ({ page, as, dev }) => {
    await as('bot'); // a login sets lastLoginAt
    const bot = await dev.user('bot');

    await page.goto('/admin/admins');
    await expectDataState(page, 'admin.admins.state', 'success');
    const row = adminRow(page, bot.id);
    await expect(row).toBeVisible();
    await expect(row.getByTestId('admin.admins.email')).toHaveText(bot.email);
    await expect(row.locator(`[data-testid="admin.admins.unit"][data-unit-id="${ZH}"]`)).toHaveAttribute('href', `/regions/${ZH}`);
    await expect(row.getByTestId('admin.admins.lastLogin')).not.toBeEmpty();
    await expect(row).toHaveAttribute('data-active', 'true');

    // search by email (case-insensitive) narrows the list to the ambassador
    await page.getByTestId('admin.admins.search').fill(bot.email.toUpperCase());
    await expect(page.getByTestId('admin.admins.row')).toHaveCount(1);
    await expect(row).toBeVisible();

    // working-group admins administer working groups only
    await page.getByTestId('admin.admins.search').fill('');
    await pickOption(page, 'admin.admins.kind', 'WORKING_GROUP');
    await expectDataState(page, 'admin.admins.state', 'success');
    await expect(page.locator('[data-testid="admin.admins.unit"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="admin.admins.unit"]:not([data-unit-type="WORKING_GROUP"])')).toHaveCount(0);
    await pickOption(page, 'admin.admins.kind', 'ALL');

    // disable with a reason: the row is marked and appears under "disabled"
    await page.getByTestId('admin.admins.search').fill(bot.email);
    await expect(page.getByTestId('admin.admins.row')).toHaveCount(1);
    await row.getByTestId('admin.admins.toggleActive').click();
    await page.getByTestId('admin.admins.activeDialog.message').fill('E2E AD1: hat das Amt abgegeben');
    await page.getByTestId('admin.admins.activeDialog.confirm').click();
    await expect(page.getByTestId('admin.admins.activeDialog')).toBeHidden();
    await expect(row).toHaveAttribute('data-active', 'false');
    await expect(row.getByTestId('admin.admins.inactive')).toBeVisible();
    await page.getByTestId('admin.admins.search').fill('');
    await pickOption(page, 'admin.admins.activeFilter', 'INACTIVE');
    await expect(row).toBeVisible();
    await expect(page.locator('[data-testid="admin.admins.row"][data-active="true"]')).toHaveCount(0);

    // enable again
    await row.getByTestId('admin.admins.toggleActive').click();
    await page.getByTestId('admin.admins.activeDialog.message').fill('E2E AD1: zurück im Amt');
    await page.getByTestId('admin.admins.activeDialog.confirm').click();
    await expect(page.getByTestId('admin.admins.activeDialog')).toBeHidden();
    await expect(row).toHaveCount(0);
    await pickOption(page, 'admin.admins.activeFilter', 'ACTIVE');
    await expect(row).toHaveAttribute('data-active', 'true');
  });

  test('AD1 admins list error state and retry @AD1', async ({ page }) => {
    await injectApiError(page, { url: '/api/admin/admins', method: 'GET', status: 500, times: 3 });
    await page.goto('/admin/admins');
    const { retry } = await expectErrorState(page, 'admin.admins.state');
    await retry.click();
    await expectDataState(page, 'admin.admins.state', 'success');
  });
});

test('AD1 ambassador grants a permission in the members tab, sees its source and revokes it @AD1 @R8', async ({ as, apiOf, dev }) => {
  const ambassador = await as('bot');
  await acceptPendingConsents(apiOf(ambassador));
  const member = await dev.user('foodsaver');

  await ambassador.goto(`/regions/${ZH}/members`);
  await expectDataState(ambassador, 'regions.members.list', 'success');
  await ambassador.getByTestId('regions.members.search').fill(member.displayName);
  const memberRow = ambassador.locator(`[data-testid="regions.members.member"][data-user="${member.id}"]`);
  await expect(memberRow).toBeVisible();
  await memberRow.getByTestId('regions.members.permissionsButton').click();

  const dialog = ambassador.getByTestId('regions.members.permissionsDialog');
  const permission = dialog.locator('[data-permission="CAN_MANAGE_SURVEYS"]');
  await expect(permission.getByTestId('regions.members.permissionSource')).toHaveAttribute('data-source', 'NONE');

  // grant: the source says "directly", the member list shows the permission tag
  await permission.getByTestId('regions.members.permission.CAN_MANAGE_SURVEYS').click();
  await expect(permission.getByTestId('regions.members.permissionSource')).toHaveAttribute('data-source', 'DIRECT');
  const grants = await apiOf(ambassador).getJson<{ user: { id: string }; permission: string; source: string }[]>(`/api/regions/${ZH}/permissions`);
  expect(grants.find((grant) => grant.user.id === member.id && grant.permission === 'CAN_MANAGE_SURVEYS')?.source).toBe('DIRECT');

  // revoke
  await permission.getByTestId('regions.members.permission.CAN_MANAGE_SURVEYS').click();
  await expect(permission.getByTestId('regions.members.permissionSource')).toHaveAttribute('data-source', 'NONE');
  const after = await apiOf(ambassador).getJson<{ user: { id: string }; permission: string }[]>(`/api/regions/${ZH}/permissions`);
  expect(after.some((grant) => grant.user.id === member.id && grant.permission === 'CAN_MANAGE_SURVEYS')).toBe(false);
});
