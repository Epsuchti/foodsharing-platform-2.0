import { expect, expectDataState, test } from '../../fixtures';

/**
 * AD1 user management and A4 admin edits (M2 T18): /regions/:id/users and /users/:id. Seed: bot@local is
 * ambassador of Zürich Stadt; sleepy@local (Marco Bernasconi) is an inactive foodsaver there.
 */
const SLEEPY = '00000001-0001-4000-8000-000000000008';

test.use({ seedReset: 'test', role: 'bot' });

test('AD1 the ambassador searches and filters the users of the home region @AD1', async ({ page }) => {
  await page.goto('/regions/home/users');
  await expect(page).toHaveURL(/\/regions\/00000010-0001-4000-8000-000000000040\/users$/);
  await expectDataState(page, 'usersAdmin.list.state', 'success');
  await page.getByTestId('usersAdmin.list.search').fill('Bernasconi');
  await expect(page.getByTestId('usersAdmin.list.row')).toHaveCount(1);
  await page.getByTestId('usersAdmin.list.open').click();
  await expect(page).toHaveURL(new RegExp(`/users/${SLEEPY}$`));
  await expect(page.getByTestId('usersAdmin.user.name')).toHaveText('Marco Bernasconi');
});

test('A4 AD1 the ambassador corrects the address with a reason and disables the account @A4 @AD1', async ({ page }) => {
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

test('AD1 plain foodsavers get the forbidden state @AD1', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/regions/home/users');
  await expectDataState(page, 'usersAdmin.list.state', 'forbidden');
  await page.goto(`/users/${SLEEPY}`);
  await expectDataState(page, 'usersAdmin.user.state', 'forbidden');
});
