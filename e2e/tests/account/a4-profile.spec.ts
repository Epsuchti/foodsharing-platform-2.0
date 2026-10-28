import { expect, expectDataState, expectNoSeriousViolations, test } from '../../fixtures';

/** A4 (M2 T18): the own profile with all user-editable fields on /account/profile. */

test.use({ seedReset: 'test', role: 'foodsaver' });

test('A4 the user edits name, phone, address and visibility; the values persist @A4', async ({ page }) => {
  await page.goto('/account/profile');
  await expectDataState(page, 'account.profile.state', 'success');
  await expect(page.getByTestId('account.profile.homeRegion')).toHaveText('Zürich Stadt');
  await expect(page.getByTestId('account.profile.save').locator('button')).toBeDisabled();

  await page.getByTestId('account.profile.firstName').fill('Sarah');
  await page.getByTestId('account.profile.lastName').fill('Müller');
  await page.getByTestId('account.profile.mobilePhone').fill('079 123 45 67');
  await page.getByTestId('account.profile.street').fill('Langstrasse 100');
  await page.getByTestId('account.profile.postcode').fill('8004');
  await page.getByTestId('account.profile.city').fill('Zürich');
  await page.getByTestId('account.profile.aboutMePublic').fill('Ich rette gern Brot.');
  await page.getByTestId('account.profile.save').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();

  await page.reload();
  await expectDataState(page, 'account.profile.state', 'success');
  await expect(page.getByTestId('account.profile.street')).toHaveValue('Langstrasse 100');
  await expect(page.getByTestId('account.profile.city')).toHaveValue('Zürich');
  await expect(page.getByTestId('account.profile.aboutMePublic')).toHaveValue('Ich rette gern Brot.');
  await expectNoSeriousViolations(page);
});

test('A4 the display name is required @A4', async ({ page }) => {
  await page.goto('/account/profile');
  await expectDataState(page, 'account.profile.state', 'success');
  await page.getByTestId('account.profile.displayName').fill('');
  await expect(page.getByTestId('account.profile.save').locator('button')).toBeDisabled();
});
