import type { Page } from '@playwright/test';
import { expect, expectDataState, test, type ApiClient } from '../../fixtures';
import { NAMES, memberRow, openStore, pageSize } from './_stores';

/**
 * S9 team list details: member distance (maySeeMemberDistance), row actions only for managers, and the one paged
 * list (resume item 14): status chips, name search, sort and page 2. Seed S-01: bieb (manager), foodsaver, sleepy,
 * jumper (jumper).
 */

type Member = { user: { id: string; displayName: string }; phone?: string; status: string; isManager: boolean };
type MemberPage = { items: Member[]; page: { totalItems: number } };

/** All team members of a store in the server's order for the given query. */
async function teamList(client: ApiClient, storeId: string, query = ''): Promise<MemberPage> {
  return client.getJson<MemberPage>(`/api/stores/${storeId}/team/members?size=100${query}`);
}

/** Display names of the rendered rows, top to bottom. */
async function rowNames(page: Page): Promise<string[]> {
  return page.getByTestId('stores.team.member').evaluateAll((rows) => rows.map((row) => row.getAttribute('data-user') ?? ''));
}

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('S9 distance and row actions only for managers @S9', async ({ page, as }) => {
    await openStore(page, 'S-01', 'team');
    await expectDataState(page, 'stores.team.list', 'success');
    await expect(page.getByTestId('stores.team.distance').first()).toBeVisible();
    await expect(page.getByTestId('stores.team.distance').first()).toContainText('km');
    await expect(page.getByTestId('stores.team.menu').first()).toBeVisible();

    const foodsaver = await as('foodsaver');
    await openStore(foodsaver, 'S-01', 'team');
    await expectDataState(foodsaver, 'stores.team.list', 'success');
    await expect(foodsaver.getByTestId('stores.team.member').first()).toBeVisible();
    await expect(foodsaver.getByTestId('stores.team.distance')).toHaveCount(0);
    await expect(foodsaver.getByTestId('stores.team.menu')).toHaveCount(0);
  });

  test('S9 appoint is only offered where the server allows it; names open the profile @S9', async ({ page, api, store }) => {
    const all = await teamList(api, store.id('S-01'));
    await openStore(page, 'S-01', 'team');
    await expectDataState(page, 'stores.team.list', 'success');
    // Sarah is a foodsaver (role below store manager): no "appoint" at all
    await memberRow(page, NAMES.foodsaver).getByTestId('stores.team.menu').click();
    await expect(page.getByTestId('stores.team.remove')).toBeVisible();
    await expect(page.getByTestId('stores.team.appointManager')).toHaveCount(0);
    await page.keyboard.press('Escape');
    // the menu follows the server's appointBlocker for every row
    const appointable = all.items.filter((m) => (m as Member & { mayBeAppointed?: boolean }).mayBeAppointed);
    expect(appointable.every((m) => !m.isManager)).toBe(true);

    await memberRow(page, NAMES.foodsaver).getByTestId('stores.team.name').click();
    await expect(page).toHaveURL(/\/profile\/[0-9a-f-]{36}/);
  });

  test('S9 one list: status chips, search and empty filter @S9', async ({ page, api, store }) => {
    const all = await teamList(api, store.id('S-01'));
    await openStore(page, 'S-01', 'team');
    await expectDataState(page, 'stores.team.list', 'success');
    // Alle: members and jumpers in one list, managers first; the jumper carries a badge in words
    await expect(page.getByTestId('stores.team.member')).toHaveCount(all.page.totalItems);
    await expect(page.getByTestId('stores.team.count')).toContainText(String(all.page.totalItems));
    expect(await rowNames(page)).toEqual(all.items.map((member) => member.user.displayName));
    await expect(page.getByTestId('stores.team.member').first()).toHaveAttribute('data-manager', 'true');
    await expect(memberRow(page, NAMES.jumper)).toHaveAttribute('data-status', 'JUMPER');
    await expect(memberRow(page, NAMES.jumper).getByTestId('stores.team.jumperBadge')).toBeVisible();
    await expect(page.getByTestId('stores.team.filter.all')).toContainText(String(all.page.totalItems));

    await page.getByTestId('stores.team.filter.jumper').click();
    await expect(page.getByTestId('stores.team.filter.jumper')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('stores.team.member')).toHaveCount(1);
    await expect(memberRow(page, NAMES.jumper)).toBeVisible();

    await page.getByTestId('stores.team.filter.manager').click();
    await expect(page.getByTestId('stores.team.member')).toHaveCount(1);
    await expect(memberRow(page, NAMES.bieb)).toBeVisible();

    await page.getByTestId('stores.team.filter.member').click();
    await expect(page.locator('[data-testid="stores.team.member"][data-status="JUMPER"]')).toHaveCount(0);
    await expect(memberRow(page, NAMES.bieb)).toBeVisible();

    // accent- and case-insensitive name search
    await page.getByTestId('stores.team.filter.all').click();
    await page.getByTestId('stores.team.search').fill('mull');
    await expect(page.getByTestId('stores.team.member')).toHaveCount(1);
    await expect(memberRow(page, NAMES.foodsaver)).toBeVisible();

    await page.getByTestId('stores.team.search').fill('niemand-hier');
    await expect(page.getByTestId('stores.team.emptyFilter')).toBeVisible();
    await page.getByTestId('stores.team.resetFilter').click();
    await expect(page.getByTestId('stores.team.member')).toHaveCount(all.page.totalItems);
    await expect(page.getByTestId('stores.team.search')).toHaveValue('');
    await expect(page.getByTestId('stores.team.search')).toBeFocused();
  });

  test('S9 sort by pickups and last pickup, page 2 @S9', async ({ page, api, store }) => {
    const s01 = store.id('S-01');
    const byCount = await teamList(api, s01, '&sort=PICKUP_COUNT');
    const byLast = await teamList(api, s01, '&sort=LAST_PICKUP');
    await openStore(page, 'S-01', 'team');
    await expectDataState(page, 'stores.team.list', 'success');

    await page.getByTestId('stores.team.sort').click();
    await page.getByRole('option', { name: 'Abholungen', exact: true }).click();
    await expect.poll(() => rowNames(page)).toEqual(byCount.items.map((member) => member.user.displayName));

    await page.getByTestId('stores.team.sort').click();
    await page.getByRole('option', { name: 'Letzte Abholung', exact: true }).click();
    await expect.poll(() => rowNames(page)).toEqual(byLast.items.map((member) => member.user.displayName));

    // pages of two (the seed team is shorter than the UI's 20): page 2 continues the same order
    await pageSize(page, s01, 2);
    await page.reload();
    await expectDataState(page, 'stores.team.list', 'success');
    const paginator = page.getByTestId('stores.team.paginator');
    await expect(paginator).toBeVisible();
    const first = await teamList(api, s01);
    await expect.poll(() => rowNames(page)).toEqual(first.items.slice(0, 2).map((member) => member.user.displayName));
    await paginator.locator('.p-paginator-next').click();
    await expect.poll(() => rowNames(page)).toEqual(first.items.slice(2, 4).map((member) => member.user.displayName));
    await expect(paginator.locator('[aria-current="page"]')).toHaveText('2');
  });
});

/** Phone numbers of team members the viewer may call (TeamMember.phone is only sent when the contact is visible). */
async function callable(client: ApiClient, storeId: string, myId: string): Promise<number> {
  const team = await teamList(client, storeId);
  return team.items.filter((member) => member.phone && member.user.id !== myId).length;
}

test.describe('phone buttons', () => {
  test.use({ role: 'foodsaver' });

  test('S9 phone buttons only where the contact is visible @S9', async ({ page, api, dev, store, as, apiOf }) => {
    const s01 = store.id('S-01');
    const me = await dev.user('foodsaver');
    const expected = await callable(api, s01, me.id);
    expect(expected).toBeGreaterThan(0);
    await openStore(page, 'S-01', 'team');
    await expectDataState(page, 'stores.team.list', 'success');
    await expect(page.getByTestId('stores.team.call')).toHaveCount(expected);
    await expect(page.getByTestId('stores.team.call').first()).toHaveAttribute('href', /^tel:/);

    const jumperPage = await as('jumper');
    const jumper = await dev.user('jumper');
    const jumperExpected = await callable(apiOf(jumperPage), s01, jumper.id);
    await openStore(jumperPage, 'S-01', 'team');
    await expectDataState(jumperPage, 'stores.team.list', 'success');
    await expect(jumperPage.getByTestId('stores.team.call')).toHaveCount(jumperExpected);
  });
});
