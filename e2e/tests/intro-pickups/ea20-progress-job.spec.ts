import { expect, test } from '../../fixtures';
import { ZH } from './_intro';

/** EA20: imports above 500 rows run as a job; the page shows its progress until done. */
test.use({ seedReset: 'test', role: 'bot' });

function bigCsv(rows: number): string {
  const lines = ['ID;Datum;No.;Betrieb;Kategorie;EinAb-Geber;FT-Besuch;Feedback'];
  const start = Date.UTC(2024, 0, 1);
  for (let i = 0; i < rows; i++) {
    const day = new Date(start + i * 86_400_000);
    const date = `${String(day.getUTCDate()).padStart(2, '0')}.${String(day.getUTCMonth() + 1).padStart(2, '0')}.${day.getUTCFullYear()}`;
    lines.push(`100004;${date};1;Laden ${i};Supermarkt;Anna;nein;ok`);
  }
  return lines.join('\n') + '\n';
}

test('EA20 progress bar for large import @EA20', async ({ page }) => {
  await page.goto(`/regions/${ZH}/intro-pickups/import`);
  await page.getByTestId('introPickups.import.csv.input').setInputFiles({ name: 'gross.csv', mimeType: 'text/csv', buffer: Buffer.from(bigCsv(520)) });
  await page.getByTestId('introPickups.import.previewButton').click();
  await expect(page.getByTestId('introPickups.import.willCreate')).toHaveText('520');
  await page.getByTestId('introPickups.import.confirm').click();
  const job = page.getByTestId('introPickups.import.job');
  await expect(job).toBeVisible();
  await expect(job).toHaveAttribute('data-status', 'DONE', { timeout: 30_000 });
});
