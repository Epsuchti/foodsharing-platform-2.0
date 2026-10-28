import { expect, expectDataState, test } from '../../fixtures';

/** R7/R10 (M2 T18): ORGA region administration on /admin/regions. */

test.use({ seedReset: 'test' });

test.describe('orga', () => {
  test.use({ role: 'orga' });

  test('R7 creates a region below Kanton Zürich, edits it and deletes it with a reason @R7', async ({ page }) => {
    await page.goto('/admin/regions');
    await expectDataState(page, 'regionAdmin.list', 'success');
    await page.getByTestId('regionAdmin.open.europa').click();
    await page.getByTestId('regionAdmin.open.schweiz').click();
    await page.getByTestId('regionAdmin.open.kanton-zuerich').click();
    await expect(page.locator('[data-region="zuerich-stadt"]')).toBeVisible();

    await page.getByTestId('regionAdmin.create').click();
    await page.getByTestId('regionAdmin.form.name').fill('Testbezirk Uster');
    await page.getByTestId('regionAdmin.form.slug').fill('testbezirk-uster');
    await page.getByTestId('regionAdmin.form.save').click();
    const row = page.locator('[data-region="testbezirk-uster"]');
    await expect(row).toBeVisible();

    await row.getByTestId('regionAdmin.edit').click();
    await page.getByTestId('regionAdmin.form.name').fill('Testbezirk Uster-Greifensee');
    // editing needs a reason
    await expect(page.getByTestId('regionAdmin.form.save').locator('button')).toBeDisabled();
    await page.getByTestId('regionAdmin.form.reason').fill('Name präzisiert');
    await page.getByTestId('regionAdmin.form.save').click();
    await expect(row).toContainText('Testbezirk Uster-Greifensee');

    await row.getByTestId('regionAdmin.delete').click();
    await page.getByTestId('regionAdmin.deleteDialog.message').fill('Testbezirk wieder entfernt');
    await page.getByTestId('regionAdmin.deleteDialog.confirm').click();
    await expect(row).toHaveCount(0);
  });

  test('R10 assigns and removes a working-group function @R10', async ({ page }) => {
    await page.goto('/admin/regions');
    await page.getByTestId('regionAdmin.search').fill('Zürich Stadt');
    const row = page.locator('[data-region="zuerich-stadt"]');
    await row.getByTestId('regionAdmin.functions').click();
    const dialog = page.getByTestId('regionAdmin.functionsDialog');
    await expect(dialog.locator('[data-function="PR"]')).toBeVisible();
    await dialog.locator('[data-function="PR"]').getByTestId('regionAdmin.removeFunction').click();
    await expect(dialog.locator('[data-function="PR"]')).toHaveCount(0);

    await page.getByTestId('regionAdmin.newFunction').click();
    await page.getByRole('option', { name: 'Öffentlichkeitsarbeit' }).click();
    await page.getByTestId('regionAdmin.newGroup').click();
    await page.getByRole('option', { name: 'Öffentlichkeitsarbeit Zürich' }).click();
    // wait for the select overlay to close: on touch devices the next tap would only dismiss it
    await expect(page.getByRole('listbox')).toBeHidden();
    await page.getByTestId('regionAdmin.assign').click();
    await expect(dialog.locator('[data-function="PR"]')).toBeVisible();
  });

  test('R7 CSV import shows a preview before importing @R7', async ({ page }) => {
    await page.goto('/admin/regions');
    await page.getByTestId('regionAdmin.import').click();
    const csv = 'parent_slug;slug;name;type;time_zone;country;postcodes\nkanton-zuerich;csv-test;CSV Test;CITY;Europe/Zurich;CH;8610\n';
    await page.getByTestId('regionAdmin.csv.input').setInputFiles({ name: 'regions.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
    await expect(page.getByTestId('regionAdmin.importSummary')).toBeVisible();
    await expect(page.getByTestId('regionAdmin.importRow')).toHaveCount(1);
    await page.getByTestId('regionAdmin.importConfirm').click();
    await expect(page.getByTestId('regionAdmin.importDialog')).toBeHidden();
  });
});

test('R7 region administration is ORGA only @R7', async ({ as }) => {
  const page = await as('bot');
  await page.goto('/admin/regions');
  await expect(page).toHaveURL(/\/forbidden$/);
});
