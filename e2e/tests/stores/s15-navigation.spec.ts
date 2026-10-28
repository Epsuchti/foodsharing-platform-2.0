import { expect, test } from '../../fixtures';
import { openStore } from './_stores';

/** S15 map position and navigation. Coop Bahnhofbrücke (S-01) lies at 47.3779, 8.5427; foodsaver@local is a member. */

test.use({ role: 'foodsaver' });

test('S15 map position and navigation buttons @S15', async ({ page }) => {
  await openStore(page, 'S-01');
  await expect(page.getByTestId('stores.page.navigate')).toHaveAttribute('href', /query=47\.3779,8\.5427/);
  await expect(page.getByTestId('stores.page.navigate')).toHaveAttribute('target', '_blank');
  await expect(page.getByTestId('stores.page.navigateApple')).toHaveAttribute('href', /maps\.apple\.com\/\?ll=47\.3779,8\.5427/);
  await expect(page.getByTestId('stores.overview.mapView')).toBeVisible();
  await expect(page.getByTestId('stores.overview.mapView').locator('.leaflet-marker-icon')).toHaveCount(1);
  // members with wall access may call the store contact
  await expect(page.getByTestId('stores.page.call')).toHaveAttribute('href', 'tel:+41440000001');
  await expect(page.getByTestId('stores.overview.contact')).toContainText('Filialleitung');
});
