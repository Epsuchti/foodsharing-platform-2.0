import { acceptConfirm } from '../stores/_stores';
import { BANANA_TEXT, acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';

/**
 * A15 trust bananas / G4 ratings (A46, upstream BananaPermissions): verified giver and recipient, not self, once per
 * pair, message ≥ 100 characters; recipient, sender or ORGA delete. bieb has not given welcome a banana in the seed.
 */
test.use({ seedReset: 'test', role: 'bieb' });

test('A15 verified user gives a banana with 100 characters, duplicate refused, recipient deletes it @A15 @G4 @N3', async ({ page, as, apiOf, profiles, notifications }) => {
  const welcomeId = await profiles.userId('welcome');
  const biebId = await profiles.userId('bieb');
  const before = await profiles.profile<{ stats: { bananaCount: number } }>('welcome', welcomeId);

  await page.goto(`/profile/${welcomeId}`);
  await expectDataState(page, 'profiles.page.state', 'success');
  await page.getByTestId('profiles.page.banana').click();
  const dialog = page.getByTestId('profiles.bananaDialog.message');
  await expect(dialog).toBeVisible();

  // too short: the dialog keeps the text and sends nothing
  await page.getByTestId('profiles.bananaDialog.message').fill('Danke für alles!');
  await page.getByTestId('profiles.bananaDialog.confirm').click();
  await expect(page.getByTestId('profiles.bananaDialog.message')).toHaveAttribute('aria-invalid', 'true');

  // exactly 100 characters is enough
  const message = BANANA_TEXT.slice(0, 100);
  expect(message.trim()).toHaveLength(100);
  await page.getByTestId('profiles.bananaDialog.message').fill(message);
  await page.getByTestId('profiles.bananaDialog.confirm').click();
  await expect(dialog).toBeHidden();
  await expect(page.getByTestId('profiles.page.bananaGiven')).toBeVisible();
  await expect(page.getByTestId('profiles.page.banana')).toHaveCount(0);

  // the recipient got a bell; a second banana of the same giver is refused
  await expect.poll(async () => (await notifications.bells('welcome', 'BANANA_GIVEN')).length).toBe(1);
  const duplicate = await profiles.giveBanana('bieb', welcomeId);
  expect(duplicate.status).toBe(409);
  expect(JSON.parse(duplicate.body).code).toBe('BANANA_ALREADY_GIVEN');

  // the recipient sees it under received bananas and deletes it
  const welcome = await as('welcome');
  await acceptPendingConsents(apiOf(welcome));
  await welcome.goto('/profile/me?tab=bananas');
  await expect(welcome).toHaveURL(new RegExp(`/profile/${welcomeId}\\?tab=bananas$`));
  await expectDataState(welcome, 'profiles.bananas.state', 'success');
  const item = welcome.locator(`[data-testid="profiles.bananas.item"][data-sender-id="${biebId}"]`);
  await expect(item.getByTestId('profiles.bananas.message')).toHaveText(message);
  await expect(welcome.getByTestId('profiles.page.stat.bananas').locator('strong')).toHaveText(String(before.stats.bananaCount + 1));
  await item.getByTestId('profiles.bananas.delete').click();
  await acceptConfirm(welcome);
  await expect(item).toHaveCount(0);
  await expect(welcome.getByTestId('profiles.page.stat.bananas').locator('strong')).toHaveText(String(before.stats.bananaCount));
});
