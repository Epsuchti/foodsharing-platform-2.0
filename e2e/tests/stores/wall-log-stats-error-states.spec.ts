import { expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';

/** Error and retry states of the F5 pages: wall, log, statistics, store pickup history, my past pickups. */

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  for (const { tab, url, state, tag } of [
    { tab: 'wall', url: '/wall', state: 'stores.wall.state', tag: '@S11' },
    { tab: 'statistics', url: '/statistics', state: 'stores.statistics.state', tag: '@S14' },
    { tab: 'log', url: '/log', state: 'stores.log.list', tag: '@S12' },
    { tab: 'pickups/history', url: '/pickup-history', state: 'stores.pickupHistory.state', tag: '@PU6' },
  ]) {
    test(`${tab}: 500 shows retry and recovers ${tag}`, async ({ page, store }) => {
      const s01 = store.id('S-01');
      const injection = await injectApiError(page, { url: new RegExp(`/api/stores/${s01}${url}(\\?|$)`), status: 500 });
      await page.goto(`/stores/${s01}/${tab}`);
      const { retry } = await expectErrorState(page, state);
      await injection.remove();
      await retry.click();
      await expect(page.getByTestId(state)).toHaveAttribute('data-state', /success|empty/);
    });
  }

  test('wall post 500 keeps the text and shows a toast @S11', async ({ page, store }) => {
    const s01 = store.id('S-01');
    await page.goto(`/stores/${s01}/wall`);
    await expectDataState(page, 'stores.wall.state', 'success');
    const injection = await injectApiError(page, { url: `/api/stores/${s01}/wall`, method: 'POST', status: 500 });
    await page.getByTestId('stores.wall.text').fill('Wird nicht gespeichert');
    await page.getByTestId('stores.wall.submit').click();
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message-error')).toBeVisible();
    await expect(page.getByTestId('stores.wall.text')).toHaveValue('Wird nicht gespeichert');
    await injection.remove();
  });
});

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('past pickups: 500 shows retry and recovers @PU6', async ({ page }) => {
    const injection = await injectApiError(page, { url: /\/api\/users\/[^/]+\/pickups\/past/, status: 500 });
    await page.goto('/me/pickups/history');
    const { retry } = await expectErrorState(page, 'pickups.past.state');
    await injection.remove();
    await retry.click();
    await expectDataState(page, 'pickups.past.state', 'success');
  });
});
