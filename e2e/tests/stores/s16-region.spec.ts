import { expect, expectDataState, test } from '../../fixtures';
import { ZURICH, center, openStore } from './_stores';

/**
 * S16 store region. Coop Bahnhofbrücke (S-01) lies in Zürich Stadt; its team members are not members of Winterthur,
 * so orga@local (store coordinator everywhere) cannot move it there.
 */

test.use({ role: 'orga' });

test('S16 store region shown; move blocked when members are not in target region @S16', async ({ page }) => {
  await openStore(page, 'S-01');
  await expect(page.getByTestId('stores.page.region')).toHaveText(/Zürich Stadt/);
  await expect(page.getByTestId('stores.page.region')).toHaveAttribute('href', `/regions/${ZURICH}/stores`);

  await page.getByTestId('stores.page.edit').click();
  await expectDataState(page, 'stores.form.state', 'success');
  await center(page, 'stores.form.region');
  await page.getByTestId('stores.form.region').click();
  await page.getByRole('searchbox').last().fill('Winterthur');
  await page.getByRole('option', { name: /Winterthur/ }).first().click();
  await center(page, 'stores.form.submit');
  await page.getByTestId('stores.form.submit').click();

  await expect(page.getByTestId('stores.form.error')).toBeVisible();
  await expect(page.getByTestId('stores.form.error')).toContainText(/Zielbezirk/);
  await expect(page).toHaveURL(/\/edit$/);

  // nothing moved
  await openStore(page, 'S-01');
  await expect(page.getByTestId('stores.page.region')).toHaveText(/Zürich Stadt/);
});
