import { expect, expectNoSeriousViolations, test } from '../../fixtures';

/** R4 (M2 T18): the public region page and the region finder for visitors. */

test('R4 visitors find Zürich by postcode and see its public page @R4', async ({ page }) => {
  await page.goto('/find-region');
  await page.getByTestId('regions.find.picker.postcode').fill('8004');
  await page.getByTestId('regions.find.picker.suggestion.zuerich-stadt').click();
  await expect(page).toHaveURL(/\/r\/zuerich-stadt$/);

  await expect(page.getByTestId('regions.public.name')).toHaveText('Zürich Stadt');
  await expect(page.getByTestId('regions.public.contact')).toContainText('Reto Baumann');
  await expect(page.getByTestId('regions.public.parent')).toContainText('Zürich');
  await expect(page.getByTestId('regions.public.signup')).toHaveAttribute('href', '/login');
  // the active seed pin is on the map
  await expect(page.getByTestId('regions.public.map')).toBeVisible();
  await expectNoSeriousViolations(page);
});

test('R4 an unknown region shows not found @R4', async ({ page }) => {
  await page.goto('/r/gibt-es-nicht');
  await expect(page.getByTestId('regions.public.state')).toHaveAttribute('data-state', 'notFound');
});
