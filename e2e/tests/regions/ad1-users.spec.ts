import { expect, expectDataState, test } from '../../fixtures';
import { chooseOption } from '../stores/_stores';

/**
 * AD1 user management and A4 admin edits (M2 T18): the region's Members tab (D1, review 2026-11-10: the former
 * /regions/:id/users page redirects there) and /users/:id. Seed: bot@local is ambassador of Zürich Stadt;
 * sleepy@local (Marco Bernasconi) is an inactive foodsaver there.
 */
const SLEEPY = '00000001-0001-4000-8000-000000000008';
const ZURICH = '00000010-0001-4000-8000-000000000040';

test.use({ seedReset: 'test', role: 'bot' });

test('AD1 R8 the ambassador searches and filters the members of the home region, the old URL redirects @AD1 @R8', async ({ page }) => {
  await page.goto('/regions/home/users');
  await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/members$`));
  await expectDataState(page, 'regions.members.list', 'success');
  // the users page extras for people who manage members
  // bot@local uses Swiss German (Nur Botschafter*inne)
  await chooseOption(page, 'regions.members.admin', /^Nur Botschafter/);
  await expectDataState(page, 'regions.members.list', 'success');
  const ambassadors = page.getByTestId('regions.members.member');
  await expect(ambassadors.first()).toBeVisible();
  expect(await ambassadors.count()).toBe(await page.getByTestId('regions.members.adminTag').count());
  await chooseOption(page, 'regions.members.admin', /^All[ei]$/);

  await page.getByTestId('regions.members.search').fill('Bernasconi');
  await expect(page.getByTestId('regions.members.member')).toHaveCount(1);
  await expect(page.getByTestId('regions.members.lastPickup')).toBeVisible();
  await page.getByTestId('regions.members.open').click();
  await expect(page).toHaveURL(new RegExp(`/users/${SLEEPY}$`));
  await expect(page.getByTestId('usersAdmin.user.name')).toHaveText('Marco Bernasconi');
  // D1: no separate "Mitglieder" item in the menu any more
  await expect(page.getByTestId('nav.users')).toHaveCount(0);
});

test('A4 AD1 the ambassador corrects the address with a reason and disables the account @A4 @AD1 @D10.3', async ({ page }) => {
  await page.goto(`/users/${SLEEPY}`);
  await expectDataState(page, 'usersAdmin.user.state', 'success');

  await page.getByTestId('usersAdmin.user.edit').click();
  await page.getByTestId('usersAdmin.user.street').fill('Seestrasse 12');
  await expect(page.getByTestId('usersAdmin.user.save').locator('button')).toBeDisabled();
  await page.getByTestId('usersAdmin.user.reason').fill('Umzug gemeldet');
  await page.getByTestId('usersAdmin.user.save').click();
  await expect(page.getByTestId('usersAdmin.user.editDialog')).toBeHidden();
  await expect(page.getByText('Seestrasse 12')).toBeVisible();

  await page.getByTestId('usersAdmin.user.toggleActive').click();
  await page.getByTestId('usersAdmin.user.activeDialog.message').fill('Seit Monaten inaktiv');
  await page.getByTestId('usersAdmin.user.activeDialog.confirm').click();
  await expect(page.getByTestId('usersAdmin.user.inactive')).toBeVisible();

  // both changes are in the change history
  await expectDataState(page, 'usersAdmin.user.history.list', 'success');
  await expect(page.getByTestId('usersAdmin.user.history.entry').first()).toBeVisible();
});

test('AD1 R8 plain foodsavers see the members list without the admin extras @AD1 @R8', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/regions/home/users');
  await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/members$`));
  await expectDataState(page, 'regions.members.list', 'success');
  await expect(page.getByTestId('regions.members.member').first()).toBeVisible();
  await expect(page.getByTestId('regions.members.admin')).toHaveCount(0);
  await expect(page.getByTestId('regions.members.lastPickup')).toHaveCount(0);
  await expect(page.getByTestId('regions.members.open')).toHaveCount(0);
  await page.goto(`/users/${SLEEPY}`);
  await expectDataState(page, 'usersAdmin.user.state', 'forbidden');
});
