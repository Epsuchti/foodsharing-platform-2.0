import { expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';
import { NAMES, ZURICH, confirmWithMessage, memberAction, openStore } from './_stores';

/** Error, retry, forbidden and conflict states of the F3 team tab and dialogs. */

test.describe('bieb', () => {
  test.use({ role: 'bieb' });

  test('team: 500 shows retry and recovers, 403 shows forbidden @S5', async ({ page }) => {
    const injection = await injectApiError(page, { url: /\/api\/stores\/[0-9a-f-]+\/team$/, status: 500 });
    await openStore(page, 'S-01', 'team');
    const { retry } = await expectErrorState(page, 'stores.team.state');
    await injection.remove();
    await retry.click();
    await expectDataState(page, 'stores.team.state', 'success');

    const forbidden = await injectApiError(page, { url: /\/api\/stores\/[0-9a-f-]+\/team$/, status: 403 });
    await page.reload();
    await expectDataState(page, 'stores.team.state', 'forbidden');
    await forbidden.remove();
  });

  test('team: 409 on standby keeps the dialog data and shows the reason @S8', async ({ page }) => {
    const injection = await injectApiError(page, {
      url: /\/api\/stores\/[0-9a-f-]+\/team\/[0-9a-f-]+\/standby$/,
      status: 409,
      code: 'MANAGER_CANNOT_LEAVE',
    });
    await openStore(page, 'S-01', 'team');
    await memberAction(page, NAMES.foodsaver, 'toStandby');
    await page.getByTestId('stores.team.messageDialog.message').fill('Bitte eine Weile pausieren, danke dir.');
    await page.getByTestId('stores.team.messageDialog.confirm').click();
    await expect(page.getByTestId('shell.toast').locator('.p-toast-message-error')).toBeVisible();
    await expect(page.getByTestId('stores.team.messageDialog.message')).toHaveValue(/pausieren/);
    await injection.remove();
    await confirmWithMessage(page, 'Bitte eine Weile pausieren, danke dir.');
    await expect(page.getByTestId('stores.team.jumpers')).toContainText(NAMES.foodsaver);
  });
});

test.describe('guide', () => {
  test.use({ role: 'guide' });

  test('apply: 409 and 429 are explained inside the dialog @S6', async ({ page }) => {
    const injection = await injectApiError(page, {
      url: /\/api\/stores\/[0-9a-f-]+\/applications$/,
      status: 409,
      code: 'NOT_REGION_MEMBER',
    });
    await page.goto(`/regions/${ZURICH}/stores`);
    await page.getByTestId('stores.region.search').fill('Coop Stauffacher');
    await page.locator('[data-testid="stores.region.row"][data-store="Coop Stauffacher"]').getByTestId('stores.region.apply').click();
    await page.getByTestId('stores.apply.submit').click();
    await expect(page.getByTestId('stores.apply.error')).toHaveAttribute('data-code', 'NOT_REGION_MEMBER');
    await injection.remove();

    const rateLimit = await injectApiError(page, { url: /\/api\/stores\/[0-9a-f-]+\/applications$/, status: 429 });
    await page.getByTestId('stores.apply.submit').click();
    await expect(page.getByTestId('stores.apply.error')).toHaveAttribute('data-code', 'TOO_MANY_REQUESTS');
    await rateLimit.remove();
    await page.getByTestId('stores.apply.submit').click();
    await expect(page.getByTestId('stores.apply.text')).toBeHidden();
  });
});
