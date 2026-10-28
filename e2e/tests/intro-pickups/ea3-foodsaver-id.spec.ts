import { expect, test } from '../../fixtures';

/** EA3: the own foodsaver ID is shown on the profile and on "my intro pickups", each with an explanation. */
test.use({ role: 'einab1' });

test('EA3 foodsaver ID shown on profile with help @EA3', async ({ page }) => {
  await page.goto('/account/profile');
  await expect(page.getByTestId('account.profile.foodsaverNumber')).toContainText('100004');
  await page.getByTestId('shared.foodsaverId.help').click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByTestId('shared.foodsaverId.number')).toHaveText('100004');
  await page.keyboard.press('Escape');

  await page.goto('/me/intro-pickups');
  await expect(page.getByTestId('introPickups.my.foodsaverNumber')).toHaveText('100004');
  await page.getByTestId('introPickups.my.foodsaverId').getByTestId('shared.foodsaverId.help').click();
  await expect(page.getByRole('dialog')).toBeVisible();
});
