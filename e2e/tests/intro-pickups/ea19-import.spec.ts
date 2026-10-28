import { expect, test } from '../../fixtures';
import { ZH } from './_intro';

/** EA19: CSV import of past intro pickups – preview (dedupe, mappings, reasons), confirm, re-import creates nothing. */
test.use({ seedReset: 'test', role: 'bot' });

const CSV =
  '﻿ID;Datum;No.;Betrieb;Kategorie;EinAb-Geber;FT-Besuch;Feedback\n' +
  '100004;01.09.2026;1;babus;Bäckerei;Anna;nein;"Gut\ngemacht"\n' +
  '100004;01.09.2026;1;Babus Bakery;Bäckerei;Anna;nein;doppelt\n' +
  '100099;02.09.2026;1;Coop;Supermarkt;Anna;ja;x\n' +
  '100005;03.12.2026;x;Coop;Supermarkt;Anna;ja;Zukunft\n' +
  '100005;05.09.2026;x;Coop;FT-Reinigung;Anna;ja;Nicht erschienen\n';

test('EA19 CSV import preview dedupes and confirms @EA19 @page', async ({ page }) => {
  await page.goto(`/regions/${ZH}/intro-pickups/admin`);
  await page.getByTestId('introPickups.admin.import').click();
  await expect(page).toHaveURL(/\/intro-pickups\/import$/);

  await page.getByTestId('introPickups.import.csv.input').setInputFiles({ name: 'einab.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) });
  await expect(page.getByTestId('introPickups.import.csv.file')).toHaveText('einab.csv');
  await page.getByTestId('introPickups.import.previewButton').click();

  const preview = page.getByTestId('introPickups.import.preview');
  await expect(preview.getByTestId('introPickups.import.total')).toHaveText('5');
  await expect(preview.getByTestId('introPickups.import.willCreate')).toHaveText('2');
  await expect(preview.getByTestId('introPickups.import.willSkip')).toHaveText('1');
  await expect(preview.getByTestId('introPickups.import.attention')).toHaveText('2');
  await expect(preview.getByTestId('introPickups.import.storeMapping').first()).toContainText("Babu's Bakery");
  await expect(preview.locator('[data-reason="DUPLICATE_IN_FILE"]')).toHaveCount(1);
  await expect(preview.locator('[data-reason="UNKNOWN_USER"]')).toHaveCount(1);
  await expect(preview.locator('[data-reason="FUTURE_DATE"]')).toHaveCount(1);

  await preview.getByTestId('introPickups.import.filter.NEEDS_ATTENTION').click();
  await expect(preview.getByTestId('introPickups.import.row')).toHaveCount(2);
  await preview.getByTestId('introPickups.import.filter.all').click();
  await expect(preview.getByTestId('introPickups.import.row')).toHaveCount(5);

  await preview.getByTestId('introPickups.import.confirm').click();
  await expect(page.getByTestId('introPickups.import.resultText')).toContainText('2');

  // the same file again: nothing new
  await page.getByTestId('introPickups.import.csv.input').setInputFiles({ name: 'einab-2.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) });
  await page.getByTestId('introPickups.import.previewButton').click();
  await expect(page.getByTestId('introPickups.import.willCreate')).toHaveText('0');
  await expect(page.getByTestId('introPickups.import.confirm').locator('button')).toBeDisabled();
});
