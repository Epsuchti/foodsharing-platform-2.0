import { expect, expectNoSeriousViolations, test } from '../../fixtures';
import { openStore } from './_stores';

/** S15 map position and navigation. Coop Bahnhofbrücke (S-01) lies at 47.3779, 8.5427; foodsaver@local is a member. */

test.use({ role: 'foodsaver' });

test('S15 map position and navigation menu @S15', async ({ page }) => {
  await openStore(page, 'S-01');
  const trigger = page.getByTestId('stores.page.navigate');
  await expect(trigger).toHaveAttribute('aria-expanded', 'false');
  await trigger.click();
  await expect(trigger).toHaveAttribute('aria-expanded', 'true');
  const google = page.getByTestId('stores.page.navigate.google');
  const apple = page.getByTestId('stores.page.navigate.apple');
  await expect(google).toHaveAttribute('href', /google\.com\/maps\/dir\/\?api=1&destination=47\.3779,8\.5427/);
  await expect(google).toHaveAttribute('target', '_blank');
  await expect(apple).toHaveAttribute('href', /maps\.apple\.com\/\?daddr=47\.3779,8\.5427/);
  await expect(apple).toHaveAttribute('target', '_blank');
  // the open menu passes axe (scoped: it overlays the tab row, whose covered tab target-size would count as obscured)
  await expectNoSeriousViolations(page, { include: ['.p-popover'] });
  await expect(google).toBeVisible();
  // Escape closes the menu and gives the focus back to the trigger
  await page.keyboard.press('Escape');
  await expect(google).toBeHidden();
  await expect(trigger).toBeFocused();
  await expectNoSeriousViolations(page);
  await expect(page.getByTestId('stores.overview.mapView')).toBeVisible();
  await expect(page.getByTestId('stores.overview.mapView').locator('.leaflet-marker-icon')).toHaveCount(1);
  // members with wall access may call the store contact
  await expect(page.getByTestId('stores.page.call')).toHaveAttribute('href', 'tel:+41440000001');
  await expect(page.getByTestId('stores.overview.contact')).toContainText('Filialleitung');
});
