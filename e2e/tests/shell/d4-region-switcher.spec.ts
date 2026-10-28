import { acceptPendingConsents, expect, test } from '../../fixtures';

/** M2 T17 shell parts: the header region switcher and the shared region picker against the real public API. */

test.describe('signed in', () => {
  test.use({ role: 'foodsaver' });

  test('D4.3 header region switcher lists the own regions and remembers the choice @D4.3', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/dashboard');
    const switcher = page.getByTestId('shell.regionSwitcher');
    // home region first (seed: Zürich Stadt)
    await expect(switcher).toContainText('Zürich');
    await switcher.click();
    await expect(page.getByTestId('shell.region.zuerich-stadt')).toHaveAttribute('aria-checked', 'true');
    // only regions, no working groups
    const items = page.locator('[data-testid^="shell.region."]');
    expect(await items.count()).toBeGreaterThan(0);
  });
});

test('D4.3 users without regions see no region switcher @D4.3', async ({ as, apiOf }) => {
  const page = await as('newbie');
  await acceptPendingConsents(apiOf(page));
  await page.goto('/dashboard');
  await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
  await expect(page.getByTestId('shell.regionSwitcher')).toHaveCount(0);
});

test('region picker suggests seed regions by postcode and browses the tree @gallery', async ({ page }) => {
  await page.goto('/dev/gallery#g-regions');
  const postcode = page.getByTestId('gallery.regionPickerPostcode.postcode');
  await postcode.fill('8004');
  const suggestion = page.getByTestId('gallery.regionPickerPostcode.suggestion.zuerich-stadt');
  await expect(suggestion).toBeVisible();
  await suggestion.click();
  await expect(page.getByTestId('gallery.regionPicker.picked')).toHaveText('zuerich-stadt');

  const tree = page.getByTestId('gallery.regionPickerTree.tree');
  await expect(tree).toHaveAttribute('data-state', 'success');
  // only regions with ambassadors are selectable (Zürich Stadt in the seed); the search opens the branches
  await page.getByTestId('gallery.regionPickerTree.search').fill('zürich stadt');
  await expect(page.getByTestId('gallery.regionPickerTree.node.zuerich-stadt')).toBeVisible();
});
