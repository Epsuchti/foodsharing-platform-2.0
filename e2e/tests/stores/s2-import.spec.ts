import { expect, expectDataState, test } from '../../fixtures';
import { ZURICH } from './_stores';

/**
 * S2 / D16.2 stores CSV import page (/regions/:regionId/stores/import). Zürich Stadt has a STORES_COORDINATION group
 * whose admin is bieb@local (docs/seed-ids.md), so bieb gets the entry on the region store list; the dry run shows a
 * preview with an error row (unknown category), confirm imports the valid rows and the store appears in the list.
 * Plain foodsavers of the region see no entry and get the forbidden state on the route.
 */
const HEADER =
  'name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,team_status,public_info,manager_foodsaver_numbers';
const CSV = [
  '# e2e store import',
  HEADER,
  'UI Import Bäckerei Seefeld,Bäckerei,,Seefeldstrasse 10,8008,Zürich,47.3594,8.5546,COOPERATION_ESTABLISHED,OPEN,Hintereingang,',
  'UI Import Metzgerei,Metzgerei,,Langstrasse 1,8004,Zürich,,,,,,',
].join('\n');

test.describe('store coordination', () => {
  test.use({ role: 'bieb', seedReset: 'test' });

  test('S2 stores CSV import: dry run preview with error row, confirm, store in list @S2 @D16.2 @page', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/stores`);
    await expectDataState(page, 'stores.region.state', 'success');
    await page.getByTestId('stores.region.import').click();
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/stores/import$`));
    await expectDataState(page, 'stores.import.state', 'success');
    await expect(page.getByTestId('stores.import.columns')).toContainText('manager_foodsaver_numbers');

    // a file triggers the dry run right away
    await page
      .getByTestId('stores.import.csv.input')
      .setInputFiles({ name: 'stores.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) });
    const preview = page.getByTestId('stores.import.preview');
    await expect(preview).toBeVisible();
    const rows = preview.getByTestId('stores.import.row');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toHaveAttribute('data-status', 'CREATE');
    await expect(rows.nth(0)).toHaveAttribute('data-line', '3');
    await expect(rows.nth(1)).toHaveAttribute('data-status', 'ERROR');
    await expect(rows.nth(1).getByTestId('stores.import.rowError')).toHaveText('Unbekannte Kategorie');
    await expect(page.getByTestId('stores.import.summary')).toHaveText('1 neu, 0 aktualisiert, 0 übersprungen, 1 mit Fehlern');

    // editing the text invalidates the preview until it is shown again
    const text = page.getByTestId('stores.import.text');
    await expect(text).toHaveValue(CSV);
    await text.fill(`${CSV}\n`);
    await expect(page.getByTestId('stores.import.stale')).toBeVisible();
    const confirm = page.getByTestId('stores.import.confirm').getByRole('button');
    await expect(confirm).toBeDisabled();
    await page.getByTestId('stores.import.previewButton').click();
    await expect(page.getByTestId('stores.import.stale')).toHaveCount(0);

    await expect(confirm).toBeEnabled();
    await expect(confirm).toHaveText(/1 Betrieb importieren/);
    await confirm.click();
    const result = page.getByTestId('stores.import.result');
    await expect(result).toContainText('Import abgeschlossen');
    await expect(result).toContainText('1 neu, 0 aktualisiert, 0 übersprungen, 1 mit Fehlern');

    await page.getByTestId('stores.import.backToList').click();
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/stores$`));
    await expectDataState(page, 'stores.region.state', 'success');
    await page.getByTestId('stores.region.search').fill('UI Import');
    await expect(page.getByTestId('stores.region.row')).toHaveCount(1);
    await expect(page.getByTestId('stores.region.row')).toContainText('UI Import Bäckerei Seefeld');
  });
});

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('S2 stores CSV import: no entry and forbidden route without store coordination @S2 @D16.2', async ({ page }) => {
    await page.goto(`/regions/${ZURICH}/stores`);
    await expectDataState(page, 'stores.region.state', 'success');
    await expect(page.getByTestId('stores.region.import')).toHaveCount(0);

    await page.goto(`/regions/${ZURICH}/stores/import`);
    await expectDataState(page, 'stores.import.state', 'forbidden');
    await expect(page.getByTestId('stores.import.csv')).toHaveCount(0);
  });
});
