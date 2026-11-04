import { expect, expectDataState, test } from '../../fixtures';

/**
 * One /pickups page (owner feedback 2026-11-04): "My pickups" (first 5) above "Where can I help?" (first 10), each with
 * "Show N more"; /pickups/mine redirects to /pickups#mine. Seed: foodsaver has 18 options, bieb 8 own pickups.
 */

test('PU7 pickups page shows both sections and reveals more options @PU7 @PU3 @page', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/pickups');
  await expectDataState(page, 'pickups.mine.state', 'success');
  await expectDataState(page, 'pickups.options.state', 'success');
  await expect(page.getByTestId('pickups.mine.pickup')).toHaveCount(2);
  await expect(page.getByTestId('pickups.mine.showMore')).toHaveCount(0);
  await expect(page.getByTestId('pickups.options.option')).toHaveCount(10);
  const total = Number(await page.getByTestId('pickups.options.count').textContent());
  expect(total).toBeGreaterThan(10);
  await page.getByTestId('pickups.options.showMore').click();
  await expect(page.getByTestId('pickups.options.option')).toHaveCount(Math.min(total, 20));
  // the focus moved into the revealed cards
  await expect(page.getByTestId('pickups.options.option').nth(10).getByRole('link')).toBeFocused();
  await expect(page.getByTestId('pickups.options.mine')).toHaveCount(0);
});

test('PU7 my pickups show 5 first, then the rest; /pickups/mine redirects @PU7 @PU3', async ({ as }) => {
  const page = await as('bieb');
  await page.goto('/pickups/mine');
  await expect(page).toHaveURL(/\/pickups#mine$/);
  await expectDataState(page, 'pickups.mine.state', 'success');
  await expect(page.getByTestId('pickups.mine.pickup')).toHaveCount(5);
  await expect(page.getByTestId('pickups.mine.count')).toHaveText('8');
  await page.getByTestId('pickups.mine.showMore').click();
  await expect(page.getByTestId('pickups.mine.pickup')).toHaveCount(8);
  await expect(page.getByTestId('pickups.mine.showMore')).toHaveCount(0);
});
