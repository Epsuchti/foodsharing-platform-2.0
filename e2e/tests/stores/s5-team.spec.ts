import { acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';
import { NAMES, memberRow, openStore } from './_stores';

/**
 * S5 team list. Seed: S-01 Coop Bahnhofbrücke = bieb (manager), foodsaver, sleepy, jumper (jumper); S-07 Hiltl =
 * welcome (manager), foodsaver, guide applied; S-08 Wochenmarkt = bieb (manager), jumper invited. Team status:
 * S-04 CLOSED, S-01 OPEN, S-07 OPEN_SEARCHING.
 */

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('S5 team list shows members, jumpers, applied, invited and stats @S5 @page', async ({ page, as, apiOf }) => {
    await openStore(page, 'S-01', 'team');
    await expectDataState(page, 'stores.team.state', 'success');
    const members = page.getByTestId('stores.team.members');
    await expect(members.getByTestId('stores.team.member')).toHaveCount(3);
    await expect(memberRow(page, NAMES.bieb).getByTestId('stores.team.manager')).toBeVisible();
    await expect(memberRow(page, NAMES.foodsaver).getByTestId('stores.team.manager')).toHaveCount(0);
    // pickup statistics per member (bieb has 30 past pickups in the seed, A20)
    await expect(memberRow(page, NAMES.bieb).getByTestId('stores.team.pickupCount')).toHaveText(/\d+/);
    const jumpers = page.getByTestId('stores.team.jumpers');
    await expect(jumpers.getByTestId('stores.team.member')).toHaveCount(1);
    await expect(jumpers).toContainText(NAMES.jumper);
    // managers see applications and invitations
    await expect(page.getByTestId('stores.team.applications')).toBeVisible();
    await expect(page.getByTestId('stores.team.invitations')).toBeVisible();

    await openStore(page, 'S-08', 'team');
    await expect(page.locator(`[data-testid="stores.team.invitation"][data-user="${NAMES.jumper}"]`)).toBeVisible();

    const welcome = await as('welcome');
    await acceptPendingConsents(apiOf(welcome));
    await openStore(welcome, 'S-07', 'team');
    const application = welcome.locator(`[data-testid="stores.team.application"][data-user="${NAMES.guide}"]`);
    await expect(application).toBeVisible();
    await expect(application.getByTestId('stores.team.accept')).toBeVisible();
  });
});

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('S5 team status badge closed/open/searching @S5', async ({ page }) => {
    const expected: [string, string][] = [
      ['S-04', 'CLOSED'],
      ['S-01', 'OPEN'],
      ['S-07', 'OPEN_SEARCHING'],
    ];
    for (const [store, status] of expected) {
      await openStore(page, store);
      await expect(page.getByTestId('stores.page.header').locator('fs-team-status-badge')).toHaveAttribute('data-status', status);
      await expect(page.getByTestId('stores.overview.requirements')).toBeVisible();
    }
    // members see the team but neither applications nor invitations nor row actions
    await openStore(page, 'S-01', 'team');
    await expectDataState(page, 'stores.team.state', 'success');
    await expect(page.getByTestId('stores.team.members').getByTestId('stores.team.member')).toHaveCount(3);
    await expect(page.getByTestId('stores.team.applications')).toHaveCount(0);
    await expect(page.getByTestId('stores.team.invitations')).toHaveCount(0);
    await expect(page.getByTestId('stores.team.menu')).toHaveCount(0);
    await expect(page.getByTestId('stores.team.invite')).toHaveCount(0);
  });
});
