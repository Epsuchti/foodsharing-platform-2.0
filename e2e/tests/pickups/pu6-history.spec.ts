import { expect, expectDataState, test } from '../../fixtures';

/**
 * PU6 pickup history. Store side: bieb manages S-01 and is KAM of the Coop chain (S-23 Coop Winterthur Bahnhof, not
 * in its team). User side: foodsaver has 24 past pickups (2 in the last 7 days) and 2 future sign-ups (S-01 Thursday,
 * S-04 Wednesday).
 */
interface UserPickupPage {
  items: { store: { id: string }; pickupAt: string }[];
  page: { totalItems: number };
}

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('PU6 store pickup history by range for manager and KAM @PU6 @D10.3 @page', async ({ page, store, as }) => {
    await page.goto(`/stores/${store.id('S-01')}/pickups`);
    await expectDataState(page, 'stores.pickups.state', 'success');
    await page.getByTestId('stores.pickups.history').click();
    await expect(page).toHaveURL(/\/pickups\/history$/);
    await expect(page.getByTestId('stores.page.tab.pickups')).toHaveAttribute('aria-current', 'page');
    await expectDataState(page, 'stores.pickupHistory.state', 'success');
    // default: last 30 days, foodsaver picked up on 2026-10-29
    await expect(page.locator('[data-testid="stores.pickupHistory.person"][data-user="Sarah Müller"]').first()).toBeVisible();

    // range: 1–15 October 2026
    await page.getByTestId('stores.pickupHistory.from').fill('2026-10-01');
    await page.getByTestId('stores.pickupHistory.to').fill('2026-10-15');
    const request = page.waitForRequest((r) => r.url().includes('/pickup-history?') && r.url().includes('from='));
    await page.getByTestId('stores.pickupHistory.apply').click();
    await request;
    await expect(page.getByTestId('stores.pickupHistory.state')).toHaveAttribute('data-state', /success|empty/);
    const times = await page.getByTestId('stores.pickupHistory.row').evaluateAll((rows) => rows.map((row) => row.getAttribute('data-pickup-at')));
    for (const at of times) {
      expect(Date.parse(at!)).toBeGreaterThanOrEqual(Date.parse('2026-09-30T22:00:00Z'));
      expect(Date.parse(at!)).toBeLessThanOrEqual(Date.parse('2026-10-15T22:00:00Z'));
    }
    await page.getByTestId('stores.pickupHistory.upcoming').click();
    await expectDataState(page, 'stores.pickups.state', 'success');

    // KAM of the chain, not in the team of S-23: the history is readable
    await page.goto(`/stores/${store.id('S-23')}/pickups/history`);
    await expect(page.getByTestId('stores.pickupHistory.state')).toHaveAttribute('data-state', /success|empty/);

    // team member foodsaver: no history
    const foodsaver = await as('foodsaver');
    await foodsaver.goto(`/stores/${store.id('S-01')}/pickups`);
    await expectDataState(foodsaver, 'stores.pickups.state', 'success');
    await expect(foodsaver.getByTestId('stores.pickups.history')).toHaveCount(0);
    await foodsaver.goto(`/stores/${store.id('S-01')}/pickups/history`);
    await expectDataState(foodsaver, 'stores.pickupHistory.state', 'forbidden');
  });
});

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver', seedReset: 'test' });

  test('PU6 user past pickups and delete all future pickups with reason @PU6', async ({ page, api, dev, as }) => {
    const me = await dev.user('foodsaver');
    const past = await api.getJson<UserPickupPage>(`/api/users/${me.id}/pickups/past`, { params: { size: 100 } });
    expect(past.page.totalItems).toBe(24);
    expect((await api.getJson<unknown[]>('/api/users/me/pickups')).length).toBe(2);

    await page.goto('/pickups/mine');
    await page.getByTestId('pickups.mine.history').click();
    await expect(page).toHaveURL(/\/me\/pickups\/history$/);
    await expectDataState(page, 'pickups.past.state', 'success');
    await expect(page.getByTestId('pickups.past.count')).toContainText('24');
    await expect(page.getByTestId('pickups.past.pickup')).toHaveCount(24);
    const shown = await page.getByTestId('pickups.past.pickup').evaluateAll((rows) => rows.map((row) => row.getAttribute('data-pickup-at')!));
    expect(shown).toEqual([...shown].sort().reverse());

    await page.getByTestId('pickups.past.deleteFuture').click();
    await page.getByTestId('pickups.past.deleteDialog.message').fill('Ich ziehe weg.');
    await page.getByTestId('pickups.past.deleteDialog.confirm').click();
    await expect(page.getByTestId('pickups.past.deleteDialog.message')).toBeHidden();
    await expect.poll(async () => (await api.getJson<unknown[]>('/api/users/me/pickups')).length).toBe(0);

    // admins: the user page links to the user's pickups
    const orga = await as('orga');
    await orga.goto(`/users/${me.id}`);
    await orga.getByTestId('usersAdmin.user.pickups').click();
    await expect(orga).toHaveURL(new RegExp(`/users/${me.id}/pickups$`));
    await expectDataState(orga, 'pickups.past.state', 'success');
    await expect(orga.getByTestId('pickups.past.count')).toContainText('24');
  });
});
