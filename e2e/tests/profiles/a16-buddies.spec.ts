import { acceptConfirm } from '../stores/_stores';
import { expect, expectDataState, test } from '../../fixtures';

/**
 * A16 buddies ("Ich kenne"). Seed: foodsaver↔bieb and foodsaver↔guide CONFIRMED, jumper→foodsaver REQUESTED.
 * guide and bieb are not buddies yet; the request rings a BUDDY_REQUEST bell linking to the requester's profile.
 */
test.use({ seedReset: 'test', role: 'guide' });

test('A16 buddy request, accept via bell, list and remove @A16 @N3', async ({ page, as, profiles, notifications }) => {
  const [biebId, guideId, jumperId] = await Promise.all([profiles.userId('bieb'), profiles.userId('guide'), profiles.userId('jumper')]);

  // the seed lists: foodsaver has two buddies and jumper's open request
  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/profile/me/buddies');
  await expectDataState(foodsaver, 'profiles.buddies.state', 'success');
  await expect(foodsaver.getByTestId('profiles.buddies.buddy')).toHaveCount(2);
  await expectDataState(foodsaver, 'profiles.buddies.requests.state', 'success');
  await expect(foodsaver.locator(`[data-testid="profiles.buddies.request"][data-user-id="${jumperId}"]`)).toBeVisible();

  // guide asks bieb
  await page.goto(`/profile/${biebId}`);
  await expectDataState(page, 'profiles.page.state', 'success');
  await page.getByTestId('profiles.page.buddyRequest').click();
  await expect(page.getByTestId('profiles.page.buddyWithdraw')).toBeVisible();
  await expect.poll(async () => (await notifications.bells('bieb', 'BUDDY_REQUEST')).length).toBe(1);

  // bieb opens the bell and accepts on guide's profile
  const bieb = await as('bieb');
  await bieb.goto('/bells');
  await expectDataState(bieb, 'bells.page.state', 'success');
  await bieb.locator('[data-testid="bells.page.item"][data-type="BUDDY_REQUEST"]').first().getByTestId('bells.page.item.open').click();
  await expect(bieb).toHaveURL(new RegExp(`/profile/${guideId}$`));
  await expectDataState(bieb, 'profiles.page.state', 'success');
  await bieb.getByTestId('profiles.page.buddyAccept').click();
  await expect(bieb.getByTestId('profiles.page.buddyConfirmed')).toBeVisible();

  // both see each other in their list; bieb removes guide again
  await page.reload();
  await expect(page.getByTestId('profiles.page.buddyConfirmed')).toBeVisible();
  await bieb.goto('/profile/me/buddies');
  await expectDataState(bieb, 'profiles.buddies.state', 'success');
  const row = bieb.locator(`[data-testid="profiles.buddies.buddy"][data-user-id="${guideId}"]`);
  await expect(row).toBeVisible();
  await row.getByTestId('profiles.buddies.remove').click();
  await acceptConfirm(bieb);
  await expect(row).toHaveCount(0);
  await page.reload();
  await expect(page.getByTestId('profiles.page.buddyRequest')).toBeVisible();
});
