import { expect, expectDataState, expectStatus, test } from '../../fixtures';
import { NAMES, openStore, toast } from './_stores';

/** S7 invitations. Seed: jumper@local is invited to S-08 Wochenmarkt Helvetiaplatz (manager bieb). */

test.use({ seedReset: 'test' });

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('S7 invite, accept, withdraw and decline flows @S7 @page', async ({ page, as, apiOf, dev, store }) => {
    // the invited jumper accepts on /stores/mine
    const jumper = await as('jumper');
    await jumper.goto('/stores/mine');
    await expectDataState(jumper, 'stores.mine.state', 'success');
    const invitation = jumper.locator('[data-testid="stores.mine.invitation"][data-store="Wochenmarkt Helvetiaplatz"]');
    await invitation.getByTestId('stores.mine.acceptInvitation').click();
    await expect(toast(jumper)).toBeVisible();
    await expect(jumper.getByTestId('stores.mine.group.member')).toContainText('Wochenmarkt Helvetiaplatz');
    await expect(jumper.getByTestId('stores.mine.group.invited')).toHaveCount(0);

    // the manager invites guide through the search dialog and withdraws the invitation again
    await openStore(page, 'S-08', 'team');
    await expect(page.locator(`[data-testid="stores.team.member"][data-user="${NAMES.jumper}"]`)).toBeVisible();
    await page.getByTestId('stores.team.invite').click();
    await page.getByTestId('stores.invite.search').fill('Julien');
    const result = page.locator(`[data-testid="stores.invite.result"][data-user="${NAMES.guide}"]`);
    await result.getByTestId('stores.invite.send').click();
    await expect(page.getByTestId('stores.invite.search')).toBeHidden();
    const row = page.locator(`[data-testid="stores.team.invitation"][data-user="${NAMES.guide}"]`);
    await expect(row).toBeVisible();
    await row.getByTestId('stores.team.withdraw').click();
    await expect(row).toHaveCount(0);

    // invited again (API), guide declines on /stores/mine
    const guide = await dev.user('guide');
    await expectStatus(await apiOf(page).post(`/api/stores/${store.id('S-08')}/invitations/${guide.id}`), [200, 201, 204], 'invite guide');
    const guidePage = await as('guide');
    await guidePage.goto('/stores/mine');
    const guideInvitation = guidePage.locator('[data-testid="stores.mine.invitation"][data-store="Wochenmarkt Helvetiaplatz"]');
    await guideInvitation.getByTestId('stores.mine.declineInvitation').click();
    await expect(guidePage.getByTestId('stores.mine.group.invited')).toHaveCount(0);
    await openStore(page, 'S-08', 'team');
    await expect(page.getByTestId('stores.team.invitation')).toHaveCount(0);
  });
});
