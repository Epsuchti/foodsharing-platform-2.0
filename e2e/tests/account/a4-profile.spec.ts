import { acceptPendingConsents, expect, expectDataState, expectNoSeriousViolations, test } from '../../fixtures';

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

test('A4 the mobile number is required: clearing it blocks saving, the server refuses it too @A4', async ({ page, api }) => {
  await page.goto('/account/profile');
  await expectDataState(page, 'account.profile.state', 'success');
  const save = page.getByTestId('account.profile.save').locator('button');
  await expect(page.getByTestId('account.profile.mobileRequired')).toHaveCount(0);
  await page.getByTestId('account.profile.mobilePhone').fill('');
  await page.getByTestId('account.profile.city').fill('Zürich');
  await expect(page.getByTestId('account.profile.mobileRequired')).toBeVisible();
  await expect(save).toBeDisabled();
  await page.getByTestId('account.profile.mobilePhone').fill('079 123 45 67');
  await expect(page.getByTestId('account.profile.mobileRequired')).toHaveCount(0);
  await expect(save).toBeEnabled();

  const refused = await api.patch('/api/users/me/profile', { mobilePhone: '' });
  expect(refused.status()).toBe(400);
  expect(await refused.json()).toMatchObject({ code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'mobilePhone', code: 'NotBlank' }] });
  // a save that leaves the field out still works
  expect((await api.patch('/api/users/me/profile', { city: 'Zürich' })).status()).toBe(200);
});

test('A4 an account without a mobile number is asked for it on the profile @A4', async ({ as, apiOf }) => {
  const page = await as('newbie');
  await acceptPendingConsents(apiOf(page));
  await page.goto('/account/profile');
  await expectDataState(page, 'account.profile.state', 'success');
  await expect(page.getByTestId('account.profile.mobileRequired')).toBeVisible();
  await page.getByTestId('account.profile.city').fill('Zürich');
  await expect(page.getByTestId('account.profile.save').locator('button')).toBeDisabled();
  await page.getByTestId('account.profile.mobilePhone').fill('079 555 12 34');
  await page.getByTestId('account.profile.save').click();
  await expect(page.getByTestId('shell.toast').locator('.p-toast-message').first()).toBeVisible();
  expect((await apiOf(page).getJson<{ mobilePhone?: string }>('/api/users/me')).mobilePhone).toBe('+41795551234');
});
