import { injectApiError } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Stores CSV import page (M7 F1, S2 / D16.2): empty form, dry-run preview with an error row, import result,
 * loading and forbidden × light/dark × de/fr. Baselines are generated in Docker in the end batch
 * (decision 69: `e2e/scripts/screenshots.sh --update-snapshots`).
 */
const ZURICH = '00000010-0001-4000-8000-000000000040';
const CSV = [
  'name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,team_status,public_info,manager_foodsaver_numbers',
  'Screen Bäckerei Seefeld,Bäckerei,,Seefeldstrasse 10,8008,Zürich,47.3594,8.5546,COOPERATION_ESTABLISHED,OPEN,,',
  'Screen Coop Enge,Supermarkt,,Bederstrasse 1,8002,Zürich,,,IN_NEGOTIATION,OPEN_SEARCHING,,999999',
  'Screen Metzgerei,Metzgerei,,Langstrasse 1,8004,Zürich,,,,,,',
].join('\n');

screenshotTest.use({ seedReset: 'test' });

forEachVariant((variant) => {
  screenshotTest.describe(`stores import ${variant.name}`, () => {
    screenshotTest.describe('store coordination', () => {
      screenshotTest.use({ variant, role: 'bieb' });

      screenshotTest('stores import form @S2 @D16.2 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/stores/import`);
        await expect(page.getByTestId('stores.import.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `stores-import-form-${variant.name}`);
      });

      screenshotTest('stores import preview @S2 @D16.2 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/stores/import`);
        await page
          .getByTestId('stores.import.csv.input')
          .setInputFiles({ name: 'stores.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) });
        await expect(page.getByTestId('stores.import.row')).toHaveCount(3);
        await visual.expectScreen(page, `stores-import-preview-${variant.name}`);
      });

      screenshotTest('stores import result @S2 @D16.2 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/stores/import`);
        await page
          .getByTestId('stores.import.csv.input')
          .setInputFiles({ name: 'stores.csv', mimeType: 'text/csv', buffer: Buffer.from(CSV) });
        await expect(page.getByTestId('stores.import.confirm').getByRole('button')).toBeEnabled();
        await page.getByTestId('stores.import.confirm').click();
        await expect(page.getByTestId('stores.import.result')).toBeVisible();
        await visual.expectScreen(page, `stores-import-result-${variant.name}`);
      });

      screenshotTest('stores import loading @S2 @D16.2 @screenshot', async ({ page, visual }) => {
        await injectApiError(page, { url: `/api/regions/${ZURICH}`, delayMs: 30_000 });
        await page.goto(`/regions/${ZURICH}/stores/import`);
        await expect(page.getByTestId('stores.import.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `stores-import-loading-${variant.name}`, { waitForData: false });
      });
    });

    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('stores import forbidden @S2 @D16.2 @screenshot', async ({ page, visual }) => {
        await page.goto(`/regions/${ZURICH}/stores/import`);
        await expect(page.getByTestId('stores.import.state')).toHaveAttribute('data-state', 'forbidden');
        await visual.expectScreen(page, `stores-import-forbidden-${variant.name}`);
      });
    });
  });
});
