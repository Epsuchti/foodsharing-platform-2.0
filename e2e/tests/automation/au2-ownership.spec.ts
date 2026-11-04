import { expect, expectDataState, expectErrorState, injectApiError, seedStore, test } from '../../fixtures';
import { S06, openAutomation, typeNumber } from './_automation';

/** AU2: who may open a store's automation; error states of the automation tab. */

test('AU2 foodsaver and the former CAN_USE_AUTOMATIONS holder are forbidden, the store manager configures @AU2', async ({ as }) => {
  // foodsaver: team member of S-01 without edit rights → no tab, direct URL is forbidden
  const foodsaver = await as('foodsaver');
  await foodsaver.goto(`/stores/${seedStore('S-01').id}/overview`);
  await expect(foodsaver.getByTestId('stores.page.tabs')).toBeVisible();
  await expect(foodsaver.getByTestId('stores.page.tab.automation')).toHaveCount(0);
  await foodsaver.goto(`/stores/${seedStore('S-01').id}/automation/policy`);
  await expectDataState(foodsaver, 'automation.store.state', 'forbidden');

  // bot held CAN_USE_AUTOMATIONS in zuerich-stadt (not in S-06's team, no mayEditStore): decision 165 removed it
  const bot = await as('bot');
  await bot.goto(`/stores/${S06.id}/overview`);
  await expect(bot.getByTestId('stores.page.tabs')).toBeVisible();
  await expect(bot.getByTestId('stores.page.tab.automation')).toHaveCount(0);
  await bot.goto(`/stores/${S06.id}/automation/policy`);
  await expectDataState(bot, 'automation.store.state', 'forbidden');

  // bieb manages S-06 and has no region permission: tab and policy are available
  const bieb = await as('bieb');
  await bieb.goto(`/stores/${S06.id}/overview`);
  await bieb.getByTestId('stores.page.tab.automation').click();
  await expectDataState(bieb, 'automation.store.state', 'success');
  await expect(bieb.getByTestId('automation.policy.root')).toHaveAttribute('data-enabled', 'true');
  await expect(bieb.getByTestId('automation.policy.save')).toBeVisible();
});

test.describe('error states', () => {
  test.use({ role: 'bieb', seedReset: 'test' });

  test('AU2 automation tab shows error with retry, policy validation errors are rendered @AU2', async ({ page }) => {
    const injection = await injectApiError(page, { url: /\/api\/stores\/[^/]+\/automation$/, status: 500 });
    await page.goto(`/stores/${S06.id}/automation/policy`);
    const { retry } = await expectErrorState(page, 'automation.store.state');
    await injection.remove();
    await retry.click();
    await expectDataState(page, 'automation.store.state', 'success');

    await injectApiError(page, { url: /\/automation\/pickup-policy$/, method: 'PUT', status: 400, code: 'GRACE_MINUTES_TOO_LOW', times: 1 });
    await typeNumber(page.getByTestId('automation.policy.gapDays'), 4);
    await page.getByTestId('automation.policy.save').click();
    await expect(page.getByTestId('automation.policy.error')).toHaveAttribute('data-code', 'GRACE_MINUTES_TOO_LOW');
  });

  test('AU2 audits list error and retry @AU2', async ({ page }) => {
    await openAutomation(page, 'S-06', 'policy');
    const injection = await injectApiError(page, { url: /\/automation\/audits/, status: 500 });
    await page.getByTestId('automation.store.nav.audits').click();
    const { retry } = await expectErrorState(page, 'automation.audits.state');
    await injection.remove();
    await retry.click();
    await expectDataState(page, 'automation.audits.state', 'success');
  });
});
