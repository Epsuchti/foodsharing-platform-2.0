import { S07_WEDNESDAY, expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';
import { openPickups, pickupRow } from './_pickups';

/** Error, retry and conflict states of the F4 pickup pages (listPickups, checkEligibility, signUp, options, regular). */

const errorToast = (page: import('@playwright/test').Page) => page.getByTestId('shell.toast').locator('.p-toast-message-error');

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('pickups tab: 500 shows retry and recovers @PU3', async ({ page, store }) => {
    const injection = await injectApiError(page, { url: `/api/stores/${store.id('S-07')}/pickups`, status: 500 });
    await page.goto(`/stores/${store.id('S-07')}/pickups`);
    const { retry } = await expectErrorState(page, 'stores.pickups.state');
    await injection.remove();
    await retry.click();
    await expectDataState(page, 'stores.pickups.state', 'success');
  });

  test('sign-up: eligibility 500 and sign-up 409 are shown @PU3 @PU4', async ({ page }) => {
    const eligibility = await injectApiError(page, { url: /\/eligibility$/, status: 500 });
    await openPickups(page, 'S-07');
    await pickupRow(page, S07_WEDNESDAY).getByTestId('stores.pickups.slot.free').first().click();
    const { retry } = await expectErrorState(page, 'pickups.signup.state');
    await eligibility.remove();
    await retry.click();
    await expectDataState(page, 'pickups.signup.state', 'success');

    const conflict = await injectApiError(page, { url: /\/signups$/, method: 'POST', status: 409, code: 'PICKUP_NOT_ELIGIBLE' });
    await page.getByTestId('pickups.signup.confirm').click();
    await expect(errorToast(page)).toBeVisible();
    await expect(page.getByTestId('pickups.signup.confirm')).toBeVisible();
    await conflict.remove();
  });

  test('options: 429 shows retry @PU7', async ({ page }) => {
    const injection = await injectApiError(page, { url: '/api/users/me/pickups/options', status: 429 });
    await page.goto('/pickups');
    await expectErrorState(page, 'pickups.options.state');
    await injection.remove();
    await page.reload();
    await expectDataState(page, 'pickups.options.state', 'success');
  });
});

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('regular pickups: 409 on save keeps the editor @PU1', async ({ page }) => {
    const injection = await injectApiError(page, {
      url: /\/regular-pickups$/,
      method: 'PUT',
      status: 409,
      code: 'DUPLICATE_PICKUP_DAY_TIME',
    });
    await openPickups(page, 'S-01');
    await page.getByTestId('stores.pickups.regular').click();
    await expect(page.getByTestId('stores.regular.row')).toHaveCount(2);
    await page.getByTestId('stores.regular.save').click();
    await expect(errorToast(page)).toBeVisible();
    await expect(page.getByTestId('stores.regular.row')).toHaveCount(2);
    await injection.remove();
  });
});
