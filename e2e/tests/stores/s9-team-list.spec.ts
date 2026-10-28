import { expect, expectDataState, test } from '../../fixtures';
import { openStore } from './_stores';

/** S9 team list details: member distance (maySeeMemberDistance) and row actions only for managers. */

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('S9 distance and row actions only for managers @S9', async ({ page, as }) => {
    await openStore(page, 'S-01', 'team');
    await expectDataState(page, 'stores.team.state', 'success');
    await expect(page.getByTestId('stores.team.distance').first()).toBeVisible();
    await expect(page.getByTestId('stores.team.distance').first()).toContainText('km');
    await expect(page.getByTestId('stores.team.menu').first()).toBeVisible();

    const foodsaver = await as('foodsaver');
    await openStore(foodsaver, 'S-01', 'team');
    await expectDataState(foodsaver, 'stores.team.state', 'success');
    await expect(foodsaver.getByTestId('stores.team.member').first()).toBeVisible();
    await expect(foodsaver.getByTestId('stores.team.distance')).toHaveCount(0);
    await expect(foodsaver.getByTestId('stores.team.menu')).toHaveCount(0);
  });
});
