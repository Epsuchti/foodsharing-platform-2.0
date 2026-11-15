import { acceptPendingConsents, expectDataState, regionId } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { chooseOption } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';

/**
 * Ambassador Reto (bot@local) manages the members of Zürich Stadt (AD1, D1: the region's Members tab replaced the
 * users page, whose old URL redirects there). He filters the ambassadors, finds Marco (sleepy@local, inactive) by
 * name, opens his admin page, corrects the address with a reason and disables the account; both changes land in the
 * change history. Rules behind it: region-members.spec (plain members see no admin extras), RegionMembersServiceTest
 * (filters for managers only), PermissionServiceMatrixTest (who administrates a user), UserProfileServiceTest (admin
 * edits need a reason).
 */
test.use({ role: 'bot' });

const ZURICH = regionId('zuerich-stadt');
const MARCO = '00000001-0001-4000-8000-000000000008';

test('ambassador finds a member, corrects his address and disables the account @AD1 @R8 @A4 @D10.3', async ({ page, apiOf }, testInfo) => {
  test.setTimeout(120_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'ambassador');
  const members = page.getByTestId('regions.members.member');

  // the former users page redirects to the Members tab; there is no separate menu item any more
  await page.goto('/regions/home/users');
  await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/members$`));
  await expect(page.getByTestId('nav.users')).toHaveCount(0);

  await walk.step('opens the members of Zürich Stadt', async () => {
    await page.goto(`/regions/${ZURICH}`);
    await walk.click(page.getByTestId('regions.page.tab.members'));
    await expectDataState(page, 'regions.members.list', 'success');
    await expect(page.getByTestId('regions.members.admin')).toBeVisible();
    await checkpoint(page, 'members');
  });

  await walk.step('shows only the ambassadors, then everybody again', async () => {
    await chooseOption(page, 'regions.members.admin', /^Nur Botschafter/);
    walk.count(2);
    await expectDataState(page, 'regions.members.list', 'success');
    await expect(members.first()).toBeVisible();
    expect(await members.count()).toBe(await page.getByTestId('regions.members.adminTag').count());
    await checkpoint(page, 'members-ambassadors');
    await chooseOption(page, 'regions.members.admin', /^Alle$/);
    walk.count(2);
  });

  await walk.step('finds Marco and opens his admin page', async () => {
    await walk.fill(page.getByTestId('regions.members.search'), 'Bernasconi');
    await expect(members).toHaveCount(1);
    await expect(page.getByTestId('regions.members.lastPickup')).toBeVisible();
    await walk.click(page.getByTestId('regions.members.open'));
    await expect(page).toHaveURL(new RegExp(`/users/${MARCO}$`));
    await expectDataState(page, 'usersAdmin.user.state', 'success');
    await expect(page.getByTestId('usersAdmin.user.name')).toHaveText('Marco Bernasconi');
    await checkpoint(page, 'user-admin');
  });

  await walk.step('corrects the address with a reason', async () => {
    await walk.click(page.getByTestId('usersAdmin.user.edit'));
    await walk.fill(page.getByTestId('usersAdmin.user.street'), 'Seestrasse 12');
    // admin edits need a reason
    await expect(page.getByTestId('usersAdmin.user.save').locator('button')).toBeDisabled();
    await walk.fill(page.getByTestId('usersAdmin.user.reason'), 'Umzug gemeldet');
    await checkpoint(page, 'edit-dialog');
    await walk.click(page.getByTestId('usersAdmin.user.save'));
    await expect(page.getByTestId('usersAdmin.user.editDialog')).toBeHidden();
    await expect(page.getByText('Seestrasse 12')).toBeVisible();
  });

  await walk.step('disables the inactive account', async () => {
    await walk.click(page.getByTestId('usersAdmin.user.toggleActive'));
    await walk.fill(page.getByTestId('usersAdmin.user.activeDialog.message'), 'Seit Monaten inaktiv');
    await checkpoint(page, 'disable-dialog');
    await walk.click(page.getByTestId('usersAdmin.user.activeDialog.confirm'));
    await expect(page.getByTestId('usersAdmin.user.inactive')).toBeVisible();
    // both changes are in the change history
    await expectDataState(page, 'usersAdmin.user.history.list', 'success');
    await expect(page.getByTestId('usersAdmin.user.history.entry')).not.toHaveCount(0);
    await checkpoint(page, 'user-disabled');
  });

  walk.finish({ steps: 5, clicks: 10, fields: 4 });
});
