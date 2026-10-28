import { expect, expectDataState, test, type ApiClient } from '../../fixtures';
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

/** Phone numbers of team members the viewer may call (TeamMember.phone is only sent when the contact is visible). */
async function callable(client: ApiClient, storeId: string, myId: string): Promise<number> {
  type Member = { user: { id: string }; phone?: string };
  const team = await client.getJson<{ members: Member[]; jumpers?: Member[] }>(`/api/stores/${storeId}/team`);
  return [...team.members, ...(team.jumpers ?? [])].filter((member) => member.phone && member.user.id !== myId).length;
}

test.describe('phone buttons', () => {
  test.use({ role: 'foodsaver' });

  test('S9 phone buttons only where the contact is visible @S9', async ({ page, api, dev, store, as, apiOf }) => {
    const s01 = store.id('S-01');
    const me = await dev.user('foodsaver');
    const expected = await callable(api, s01, me.id);
    expect(expected).toBeGreaterThan(0);
    await openStore(page, 'S-01', 'team');
    await expectDataState(page, 'stores.team.state', 'success');
    await expect(page.getByTestId('stores.team.call')).toHaveCount(expected);
    await expect(page.getByTestId('stores.team.call').first()).toHaveAttribute('href', /^tel:/);

    const jumperPage = await as('jumper');
    const jumper = await dev.user('jumper');
    const jumperExpected = await callable(apiOf(jumperPage), s01, jumper.id);
    await openStore(jumperPage, 'S-01', 'team');
    await expectDataState(jumperPage, 'stores.team.state', 'success');
    await expect(jumperPage.getByTestId('stores.team.call')).toHaveCount(jumperExpected);
  });
});
