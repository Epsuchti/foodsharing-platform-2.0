import { expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';

/** Error, retry, forbidden and not-found states of the M2 T18 region pages. */

test.use({ role: 'foodsaver' });

test('my regions: error with retry recovers @R2', async ({ page }) => {
  const injection = await injectApiError(page, { url: '/api/regions/mine', status: 500 });
  await page.goto('/regions/mine');
  const { retry } = await expectErrorState(page, 'regions.mine.state');
  await injection.remove();
  await retry.click();
  await expectDataState(page, 'regions.mine.state', 'success');
});

test('region page: unknown region shows not found @R3', async ({ page }) => {
  await page.goto('/regions/00000010-0001-4000-8000-0000000009ff/overview');
  await expectDataState(page, 'regions.page.state', 'notFound');
});

test('group page: unknown group shows not found, server errors show retry @R9', async ({ page }) => {
  await page.goto('/groups/00000011-0001-4000-8000-0000000009ff');
  await expectDataState(page, 'regions.group.state', 'notFound');

  await injectApiError(page, { url: /\/api\/groups\/[0-9a-f-]+$/, status: 503 });
  await page.goto('/groups/00000011-0001-4000-8000-000000000033');
  await expectErrorState(page, 'regions.group.state');
});

test('settings tab: members get the forbidden state from the server @R3', async ({ page }) => {
  await page.goto('/regions/00000010-0001-4000-8000-000000000040/settings');
  await expectDataState(page, 'regions.settings.state', 'forbidden');
  await expect(page.getByTestId('regions.page.name')).toHaveText('Zürich Stadt');
});
