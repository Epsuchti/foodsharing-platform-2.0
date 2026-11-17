import type { Page } from '@playwright/test';
import { acceptPendingConsents, expectDataState, pickOption, seedRegion } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * AD1 (plan §14.4, decision 94): ORGA (orga@local) looks through every ambassador and working-group admin on
 * /admin/admins (search by email, unit kind, active filter), takes Bot (bot@local, ambassador of Zürich Stadt) out of
 * office with a reason and back in again. Bot then gives a member a scoped permission in the region's members tab,
 * sees where it comes from and takes it back (R8). Rules behind it: AdminListServiceTest (sorted, filters, paging, ORGA
 * only), UserAdminControllerTest (disabling needs a reason), RegionPermissionResolverTest, PermissionServiceMatrixTest;
 * client admin-settings.spec (filters go to the request, disabling needs a reason).
 */
test.use({ role: 'orga' });

const ZH = seedRegion('zuerich-stadt').id;
const adminRow = (page: Page, userId: string) => page.locator(`[data-testid="admin.admins.row"][data-user-id="${userId}"]`);

test('orga reviews the admins and takes an ambassador out of office and back; the ambassador grants a permission @AD1 @R8', async ({
  page,
  as,
  apiOf,
  dev,
}, testInfo) => {
  test.setTimeout(150_000);
  await acceptPendingConsents(apiOf(page));
  const ambassador = await as('bot'); // a login sets lastLoginAt
  const bot = await dev.user('bot');
  const member = await dev.user('foodsaver');
  const walk = new Walk(page, testInfo, 'orga');
  const row = adminRow(page, bot.id);
  /** Login times come from the real clock. */
  const lastLogins = page.getByTestId('admin.admins.lastLogin');

  await walk.step('finds the ambassador of Zürich Stadt among the admins', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminAdmins');
    await expect(page).toHaveURL(/\/admin\/admins$/);
    await expectDataState(page, 'admin.admins.state', 'success');
    await expect(row).toBeVisible();
    await expect(row.getByTestId('admin.admins.email')).toHaveText(bot.email);
    await expect(row.locator(`[data-testid="admin.admins.unit"][data-unit-id="${ZH}"]`)).toHaveAttribute('href', `/regions/${ZH}`);
    await expect(row.getByTestId('admin.admins.lastLogin')).not.toBeEmpty();
    await expect(row).toHaveAttribute('data-active', 'true');
    await checkpoint(page, 'admins', { mask: [lastLogins] });

    // working-group admins administer working groups only
    await pickOption(page, 'admin.admins.kind', 'WORKING_GROUP');
    walk.count(2);
    await expectDataState(page, 'admin.admins.state', 'success');
    await expect(page.locator('[data-testid="admin.admins.unit"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="admin.admins.unit"]:not([data-unit-type="WORKING_GROUP"])')).toHaveCount(0);
    await pickOption(page, 'admin.admins.kind', 'ALL');
    walk.count(2);

    // the search by email ignores case
    await walk.fill(page.getByTestId('admin.admins.search'), bot.email.toUpperCase());
    await expect(page.getByTestId('admin.admins.row')).toHaveCount(1);
    await expect(row).toBeVisible();
  });

  await walk.step('takes him out of office with a reason', async () => {
    await walk.click(row.getByTestId('admin.admins.toggleActive'));
    await walk.fill(page.getByTestId('admin.admins.activeDialog.message'), 'Hat das Amt abgegeben');
    await checkpoint(page, 'disable-dialog');
    await walk.click(page.getByTestId('admin.admins.activeDialog.confirm'));
    await expect(page.getByTestId('admin.admins.activeDialog')).toBeHidden();
    await expect(row).toHaveAttribute('data-active', 'false');
    await expect(row.getByTestId('admin.admins.inactive')).toBeVisible();
    // he is listed under "disabled"
    await walk.fill(page.getByTestId('admin.admins.search'), '');
    await pickOption(page, 'admin.admins.activeFilter', 'INACTIVE');
    walk.count(2);
    await expect(row).toBeVisible();
    await expect(page.locator('[data-testid="admin.admins.row"][data-active="true"]')).toHaveCount(0);
    await checkpoint(page, 'admins-disabled', { mask: [lastLogins] });
  });

  await walk.step('brings him back into office', async () => {
    await walk.click(row.getByTestId('admin.admins.toggleActive'));
    await walk.fill(page.getByTestId('admin.admins.activeDialog.message'), 'Zurück im Amt');
    await walk.click(page.getByTestId('admin.admins.activeDialog.confirm'));
    await expect(page.getByTestId('admin.admins.activeDialog')).toBeHidden();
    await expect(row).toHaveCount(0);
    await pickOption(page, 'admin.admins.activeFilter', 'ACTIVE');
    walk.count(2);
    await expect(row).toHaveAttribute('data-active', 'true');
  });

  // the ambassador, back in office, lets a member manage surveys
  const ambassadorApi = apiOf(ambassador);
  await acceptPendingConsents(ambassadorApi);
  const office = new Walk(ambassador, testInfo, 'ambassador');
  const grants = async () =>
    (await ambassadorApi.getJson<{ user: { id: string }; permission: string; source: string }[]>(`/api/regions/${ZH}/permissions`)).filter(
      (grant) => grant.user.id === member.id && grant.permission === 'CAN_MANAGE_SURVEYS',
    );

  await office.step('lets a member manage surveys and takes it back', async () => {
    await ambassador.goto(`/regions/${ZH}/members`);
    await expectDataState(ambassador, 'regions.members.list', 'success');
    await office.fill(ambassador.getByTestId('regions.members.search'), member.displayName);
    const memberRow = ambassador.locator(`[data-testid="regions.members.member"][data-user="${member.id}"]`);
    await expect(memberRow).toBeVisible();
    await office.click(memberRow.getByTestId('regions.members.permissionsButton'));
    const dialog = ambassador.getByTestId('regions.members.permissionsDialog');
    const permission = dialog.locator('[data-permission="CAN_MANAGE_SURVEYS"]');
    await expect(permission.getByTestId('regions.members.permissionSource')).toHaveAttribute('data-source', 'NONE');
    // grant: the source says "directly"
    await office.click(permission.getByTestId('regions.members.permission.CAN_MANAGE_SURVEYS'));
    await expect(permission.getByTestId('regions.members.permissionSource')).toHaveAttribute('data-source', 'DIRECT');
    expect((await grants()).map((grant) => grant.source)).toEqual(['DIRECT']);
    await checkpoint(ambassador, 'permissions-dialog');
    // revoke
    await office.click(permission.getByTestId('regions.members.permission.CAN_MANAGE_SURVEYS'));
    await expect(permission.getByTestId('regions.members.permissionSource')).toHaveAttribute('data-source', 'NONE');
    expect(await grants()).toEqual([]);
  });

  walk.finish({ steps: 3, clicks: 14, fields: 4 });
  office.finish({ steps: 1, clicks: 3, fields: 1 });
});
