import { acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';

/**
 * R2 (M2 T18): my regions, finding a region by postcode, joining it, making it the home region and leaving.
 * Seed: newbie@local has no region; foodsaver@local is in Zürich Stadt (home) and the OPEN kochgruppe.
 */

// the newcomer test changes newbie's memberships
test.use({ seedReset: 'test' });

test('R2 my regions lists the home region first and the working groups @R2', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/regions/mine');
  await expectDataState(page, 'regions.mine.state', 'success');
  await expect(page.getByTestId('regions.mine.region').first()).toContainText('Zürich Stadt');
  await expect(page.getByTestId('regions.mine.region').first()).toContainText(/Stammbezirk/);
  await expect(page.getByTestId('regions.mine.groups')).toContainText('Kochgruppe');
});

test('R2 a newcomer finds a region by postcode and joins it; the first region becomes the home region @R2', async ({ as, apiOf }) => {
  const page = await as('newbie');
  await acceptPendingConsents(apiOf(page));

  await page.goto('/regions/mine');
  await expectDataState(page, 'regions.mine.state', 'empty');
  await page.getByTestId('regions.mine.state').getByRole('button').click();
  await expect(page).toHaveURL(/\/find-region$/);

  await page.getByTestId('regions.find.picker.postcode').fill('8004');
  await page.getByTestId('regions.find.picker.suggestion.zuerich-stadt').click();
  await expect(page).toHaveURL(/\/regions\/[0-9a-f-]+\/overview$/);

  await page.getByTestId('regions.page.join').click();
  await expect(page.getByTestId('regions.page.memberBadge')).toContainText('Stammbezirk');

  await page.goto('/regions/mine');
  await expect(page.getByTestId('regions.mine.region').first()).toContainText('Zürich Stadt');
});

test('R2 a foodsaver joins Winterthur, makes it the home region and leaves Zürich @R2', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/regions/00000010-0001-4000-8000-000000000041/overview');
  await page.getByTestId('regions.page.join').click();
  await expect(page.getByTestId('regions.page.memberBadge')).toBeVisible();
  await page.getByTestId('regions.page.makeHome').click();
  await expect(page.getByTestId('regions.page.memberBadge')).toContainText('Stammbezirk');

  await page.goto('/regions/00000010-0001-4000-8000-000000000040/overview');
  await page.getByTestId('regions.page.leave').click();
  await page.locator('.p-confirmdialog').getByRole('button', { name: 'Austreten' }).click();
  await expect(page.getByTestId('regions.page.join')).toBeVisible();
});

test('R2 /regions/home goes to the home region, or to the finder without one @R2', async ({ as, apiOf }) => {
  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/regions/home');
  await expect(foodsaver).toHaveURL(/\/regions\/00000010-0001-4000-8000-000000000040\/overview$/);

  const newbie = await as('newbie');
  await acceptPendingConsents(apiOf(newbie));
  await newbie.goto('/regions/home');
  await expect(newbie).toHaveURL(/\/find-region$/);
});
