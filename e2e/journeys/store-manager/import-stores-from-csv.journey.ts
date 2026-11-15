import { expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { ZURICH } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';

/**
 * Store coordinator Nina (bieb@local administrates the STORES_COORDINATION group of Zürich Stadt, docs/seed-ids.md)
 * imports stores from a CSV file (S2 / D16.2, decision 79): the file triggers a dry run with one valid row and one
 * with an unknown category, editing the text marks the preview stale until it is shown again, the import creates the
 * valid store and it appears in the region's store list. The parser, dedupe, geocoding and the coordination right are
 * unit tests (StoresCsvImportServiceTest); the API contract is StoresCsvImportZuerichIT.
 */
test.use({ role: 'bieb' });

const HEADER =
  'name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,team_status,public_info,manager_foodsaver_numbers';
const CSV = [
  '# e2e store import',
  HEADER,
  'UI Import Bäckerei Seefeld,Bäckerei,,Seefeldstrasse 10,8008,Zürich,47.3594,8.5546,COOPERATION_ESTABLISHED,OPEN,Hintereingang,',
  'UI Import Metzgerei,Metzgerei,,Langstrasse 1,8004,Zürich,,,,,,',
].join('\n');

test('store coordinator imports stores from a CSV file @S2 @D16.2', async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'store coordinator');

  await walk.step('opens the import from the region store list', async () => {
    await page.goto(`/regions/${ZURICH}/stores`);
    await expectDataState(page, 'stores.region.state', 'success');
    await walk.click(page.getByTestId('stores.region.import'));
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/stores/import$`));
    await expectDataState(page, 'stores.import.state', 'success');
    await expect(page.getByTestId('stores.import.columns')).toContainText('manager_foodsaver_numbers');
    await checkpoint(page, 'import-form');
  });

  await walk.step('chooses the file and reads the preview', async () => {
    // a file triggers the dry run right away
    await page.getByTestId('stores.import.csv.input').setInputFiles({ name: 'stores.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) });
    walk.count(1);
    const rows = page.getByTestId('stores.import.preview').getByTestId('stores.import.row');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toHaveAttribute('data-status', 'CREATE');
    await expect(rows.nth(0)).toHaveAttribute('data-line', '3');
    await expect(rows.nth(1)).toHaveAttribute('data-status', 'ERROR');
    await expect(rows.nth(1).getByTestId('stores.import.rowError')).toHaveText('Unbekannte Kategorie');
    await expect(page.getByTestId('stores.import.summary')).toHaveText('1 neu, 0 aktualisiert, 0 übersprungen, 1 mit Fehlern');
    await checkpoint(page, 'import-preview');

    // editing the text invalidates the preview until it is shown again
    const text = page.getByTestId('stores.import.text');
    await expect(text).toHaveValue(CSV);
    await walk.fill(text, `${CSV}\n`);
    await expect(page.getByTestId('stores.import.stale')).toBeVisible();
    await expect(page.getByTestId('stores.import.confirm').getByRole('button')).toBeDisabled();
    await checkpoint(page, 'import-preview-stale');
    await walk.click(page.getByTestId('stores.import.previewButton'));
    await expect(page.getByTestId('stores.import.stale')).toHaveCount(0);
  });

  await walk.step('imports the valid store', async () => {
    const confirm = page.getByTestId('stores.import.confirm').getByRole('button');
    await expect(confirm).toBeEnabled();
    await expect(confirm).toHaveText(/1 Betrieb importieren/);
    await walk.click(confirm);
    const result = page.getByTestId('stores.import.result');
    await expect(result).toContainText('Import abgeschlossen');
    await expect(result).toContainText('1 neu, 0 aktualisiert, 0 übersprungen, 1 mit Fehlern');
    await checkpoint(page, 'import-result');
  });

  await walk.step('finds the new store in the list', async () => {
    await walk.click(page.getByTestId('stores.import.backToList'));
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/stores$`));
    await expectDataState(page, 'stores.region.state', 'success');
    await walk.fill(page.getByTestId('stores.region.search'), 'UI Import');
    await expect(page.getByTestId('stores.region.row')).toHaveCount(1);
    await expect(page.getByTestId('stores.region.row')).toContainText('UI Import Bäckerei Seefeld');
  });

  walk.finish({ steps: 4, clicks: 5, fields: 2 });
});
