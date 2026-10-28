import {
  expect,
  expectDataState,
  expectErrorState,
  goOffline,
  goOnline,
  injectApiError,
  seedStore,
  test,
} from '../../fixtures';
import { ZURICH, center, openStore } from './_stores';

/** Error, retry, forbidden, not-found, conflict, rate-limit and offline states of the F2 store pages. */

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('my stores: 500 shows retry and recovers @S1', async ({ page }) => {
    const injection = await injectApiError(page, { url: '/api/users/me/stores', status: 500 });
    await page.goto('/stores/mine');
    const { retry } = await expectErrorState(page, 'stores.mine.state');
    await injection.remove();
    await retry.click();
    await expectDataState(page, 'stores.mine.state', 'success');
  });

  test('region stores: 429 and offline keep the page usable @S1', async ({ page, context }) => {
    const injection = await injectApiError(page, { url: /\/api\/regions\/[0-9a-f-]+\/stores/, status: 429 });
    await page.goto(`/regions/${ZURICH}/stores`);
    await expectErrorState(page, 'stores.region.state');
    await injection.remove();
    await page.reload();
    await expectDataState(page, 'stores.region.state', 'success');

    await goOffline(context);
    await page.getByTestId('stores.region.search').fill('Migros');
    await expectDataState(page, 'stores.region.state', 'offline');
    await goOnline(context);
    await expectDataState(page, 'stores.region.state', 'success');
    await expect(page.getByTestId('stores.region.row')).toHaveCount(2);
  });

  test('store page: public view for region members, 403 outside the region, 404 for unknown stores @S1 @S6', async ({ page }) => {
    // foodsaver is not in the team of Hotel Seefeld (S-11) but in its region: limited view with "apply"
    await page.goto(`/stores/${seedStore('S-11').id}`);
    await expectDataState(page, 'stores.page.state', 'success');
    await expect(page.getByTestId('stores.membership.apply')).toBeVisible();
    await expect(page.getByTestId('stores.page.tab.team')).toHaveCount(0);
    // Winterthur (S-23) is outside foodsaver's regions
    await page.goto(`/stores/${seedStore('S-23').id}`);
    await expectDataState(page, 'stores.page.state', 'forbidden');
    await page.goto('/stores/00000020-0001-4000-8000-0000000009ff');
    await expectDataState(page, 'stores.page.state', 'notFound');
  });
});

test.describe('store manager', () => {
  test.use({ role: 'bieb' });

  test('store form: 409 on save is shown and keeps the form, 500 on load retries @S2', async ({ page }) => {
    const conflict = await injectApiError(page, {
      url: /\/api\/stores\/[0-9a-f-]+$/,
      method: 'PATCH',
      status: 409,
      code: 'CONCURRENT_MODIFICATION',
    });
    await openStore(page, 'S-01');
    await page.getByTestId('stores.page.edit').click();
    await expectDataState(page, 'stores.form.state', 'success');
    await page.getByTestId('stores.form.description').fill('Konflikt');
    await center(page, 'stores.form.submit');
    await page.getByTestId('stores.form.submit').click();
    await expect(page.getByTestId('stores.form.error')).toBeVisible();
    await expect(page.getByTestId('stores.form.description')).toHaveValue('Konflikt');
    await conflict.remove();

    const failure = await injectApiError(page, { url: '/api/store-metadata', status: 500 });
    await page.reload();
    const { retry } = await expectErrorState(page, 'stores.form.state');
    await failure.remove();
    await retry.click();
    await expectDataState(page, 'stores.form.state', 'success');
  });
});
