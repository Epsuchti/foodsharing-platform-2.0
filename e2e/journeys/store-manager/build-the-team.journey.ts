import { expectDataState, seedStore } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { NAMES, ZURICH, memberRow } from '../../fixtures/flows/stores';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * Store manager Nina (bieb@local, manager of S-01 Coop Bahnhofbrücke and S-08 Wochenmarkt Helvetiaplatz) builds her
 * team: Julien (guide@local, in no team of S-01) applies from the region list and first forgets the text the store asks
 * for; Nina reads the application on the team tab and accepts it, looks through the one paged team list (status chips,
 * search, empty filter), moves Sarah to the standby list with the required message and back, and on S-08 invites
 * Julien through the search dialog and withdraws the invitation again, while Jonas (jumper@local, invited in the seed)
 * accepts his invitation on "my stores". The rules behind it (requirements, manager limits, who may be invited, list
 * order, distance and phone visibility) are unit tests (StoreTeamServiceTest, StorePermissionMatrixTest).
 */
test.use({ role: 'bieb' });

const S01 = seedStore('S-01');
const S08 = seedStore('S-08');

test('store manager accepts an application, sorts the team and invites people @S5 @S6 @S7 @S8 @S9', async ({
  page,
  as,
}, testInfo) => {
  test.setTimeout(150_000);
  const walk = new Walk(page, testInfo, 'store manager');

  await test.step('Julien applies to Coop Bahnhofbrücke from the region list', async () => {
    const guide = await as('guide');
    await guide.goto(`/regions/${ZURICH}/stores`);
    await expectDataState(guide, 'stores.region.state', 'success');
    await guide.getByTestId('stores.region.search').fill(S01.name);
    const row = guide.locator(`[data-testid="stores.region.row"][data-store="${S01.name}"]`);
    await row.getByTestId('stores.region.apply').click();
    await expect(guide.getByTestId('stores.apply.text')).toBeVisible();
    // S-01 asks for an application text
    await guide.getByTestId('stores.apply.submit').click();
    await expect(guide.getByTestId('stores.apply.error')).toHaveAttribute('data-code', 'APPLICATION_TEXT_REQUIRED');
    await checkpoint(guide, 'apply-dialog-text-required');
    await guide.getByTestId('stores.apply.text').fill('Ich wohne gleich um die Ecke und habe abends Zeit.');
    await guide.getByTestId('stores.apply.submit').click();
    await expect(guide.getByTestId('stores.apply.text')).toBeHidden();
    await expect(row.locator('fs-membership-badge')).toBeVisible();
  });

  await walk.step('reads and accepts the application on the team tab', async () => {
    await page.goto(`/stores/${S01.id}/overview`);
    await expect(page.getByTestId('stores.page.name')).toHaveText(S01.name);
    await walk.click(page.getByTestId('stores.page.tab.team'));
    await expectDataState(page, 'stores.team.state', 'success');
    await expectDataState(page, 'stores.team.list', 'success');
    // one list: members and jumpers together, the manager first, the jumper with a badge in words
    const members = page.getByTestId('stores.team.members');
    await expect(members.locator('[data-testid="stores.team.member"][data-status="MEMBER"]')).toHaveCount(3);
    await expect(page.getByTestId('stores.team.member').first()).toHaveAttribute('data-manager', 'true');
    await expect(memberRow(page, NAMES.bieb).getByTestId('stores.team.manager')).toBeVisible();
    await expect(memberRow(page, NAMES.bieb).getByTestId('stores.team.pickupCount')).toHaveText(/\d+/);
    await expect(memberRow(page, NAMES.jumper)).toHaveAttribute('data-status', 'JUMPER');
    await expect(memberRow(page, NAMES.jumper).getByTestId('stores.team.jumperBadge')).toBeVisible();
    // managers see the distance of every member
    await expect(page.getByTestId('stores.team.distance').first()).toContainText('km');
    const application = page.locator(`[data-testid="stores.team.application"][data-user="${NAMES.guide}"]`);
    await expect(application.getByTestId('stores.team.applicationText')).toContainText('um die Ecke');
    await expect(page.getByTestId('stores.team.invitations')).toBeVisible();
    if (isPhone(page)) {
      // owner report 2026-11-12: next to the call button the name and the stats stay on one line
      const card = page.getByTestId('stores.team.member').filter({ has: page.getByTestId('stores.team.call') }).first();
      const lines = (testId: string) =>
        card.getByTestId(testId).evaluate((el) => ({ height: el.getBoundingClientRect().height, line: parseFloat(getComputedStyle(el).lineHeight) }));
      for (const testId of ['stores.team.name', 'stores.team.pickupCount']) {
        const box = await lines(testId);
        expect(box.height, `${testId} on one line`).toBeLessThan(2 * box.line);
      }
    }
    await checkpoint(page, 'team-with-application');
    await walk.click(application.getByTestId('stores.team.accept'));
    await expect(application).toHaveCount(0);
    await expect(memberRow(page, NAMES.guide)).toHaveAttribute('data-status', 'MEMBER');
  });

  await walk.step('looks through the team list', async () => {
    await walk.click(page.getByTestId('stores.team.filter.jumper'));
    await expect(page.getByTestId('stores.team.filter.jumper')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('stores.team.member')).toHaveCount(1);
    await expect(memberRow(page, NAMES.jumper)).toBeVisible();
    await checkpoint(page, 'team-jumpers');
    await walk.click(page.getByTestId('stores.team.filter.all'));
    // accent- and case-insensitive name search
    await walk.fill(page.getByTestId('stores.team.search'), 'mull');
    await expect(page.getByTestId('stores.team.member')).toHaveCount(1);
    await expect(memberRow(page, NAMES.foodsaver)).toBeVisible();
    await walk.fill(page.getByTestId('stores.team.search'), 'niemand-hier');
    await expect(page.getByTestId('stores.team.emptyFilter')).toBeVisible();
    await walk.click(page.getByTestId('stores.team.resetFilter'));
    await expect(page.getByTestId('stores.team.search')).toHaveValue('');
    await expect(page.getByTestId('stores.team.member')).toHaveCount(5);
  });

  await walk.step('moves Sarah to the standby list and back', async () => {
    const sarah = memberRow(page, NAMES.foodsaver);
    await sarah.scrollIntoViewIfNeeded();
    await walk.click(sarah.getByTestId('stores.team.menu'));
    await expect(page.getByTestId('stores.team.toStandby')).toBeVisible();
    // a foodsaver (role below store manager) is never offered "appoint"
    await expect(page.getByTestId('stores.team.appointManager')).toHaveCount(0);
    await checkpoint(page, 'member-menu', { fullPage: false });
    await walk.click(page.getByTestId('stores.team.toStandby'));
    await walk.fill(
      page.getByTestId('stores.team.messageDialog.message'),
      'Du warst lange nicht mehr abholen, wir setzen dich vorerst auf die Springerliste.',
    );
    await checkpoint(page, 'standby-message');
    await walk.click(page.getByTestId('stores.team.messageDialog.confirm'));
    await expect(page.getByTestId('stores.team.messageDialog.message')).toBeHidden();
    await expect(sarah).toHaveAttribute('data-status', 'JUMPER');
    await expect(sarah.getByTestId('stores.team.jumperBadge')).toBeVisible();
    await walk.click(sarah.getByTestId('stores.team.menu'));
    await walk.click(page.getByTestId('stores.team.toTeam'));
    await expect(sarah).toHaveAttribute('data-status', 'MEMBER');
  });

  await test.step('Jonas accepts his invitation to Wochenmarkt Helvetiaplatz on "my stores"', async () => {
    const jumper = await as('jumper');
    await jumper.goto('/stores/mine');
    await expectDataState(jumper, 'stores.mine.state', 'success');
    const invitation = jumper.locator(`[data-testid="stores.mine.invitation"][data-store="${S08.name}"]`);
    await invitation.getByTestId('stores.mine.acceptInvitation').click();
    await expect(jumper.getByTestId('stores.mine.group.member')).toContainText(S08.name);
    await expect(jumper.getByTestId('stores.mine.group.invited')).toHaveCount(0);
  });

  await walk.step('invites Julien to Wochenmarkt Helvetiaplatz and withdraws it', async () => {
    await page.goto(`/stores/${S08.id}/team`);
    await expectDataState(page, 'stores.team.list', 'success');
    await expect(memberRow(page, NAMES.jumper)).toBeVisible();
    await walk.click(page.getByTestId('stores.team.invite'));
    await walk.fill(page.getByTestId('stores.invite.search'), 'Julien');
    const result = page.locator(`[data-testid="stores.invite.result"][data-user="${NAMES.guide}"]`);
    await expect(result).toBeVisible();
    await checkpoint(page, 'invite-dialog');
    await walk.click(result.getByTestId('stores.invite.send'));
    await expect(page.getByTestId('stores.invite.search')).toBeHidden();
    const invited = page.locator(`[data-testid="stores.team.invitation"][data-user="${NAMES.guide}"]`);
    await expect(invited).toBeVisible();
    await walk.click(invited.getByTestId('stores.team.withdraw'));
    await expect(invited).toHaveCount(0);
    // a name opens the profile
    await walk.click(memberRow(page, NAMES.jumper).getByTestId('stores.team.name'));
    await expect(page).toHaveURL(/\/profile\/[0-9a-f-]{36}/);
  });

  walk.finish({ steps: 4, clicks: 14, fields: 4 });
});
