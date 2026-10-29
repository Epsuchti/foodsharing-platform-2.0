import { seedStore } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/** Survey screens (M5 SV1): list, editor, results, bare fill page. Baselines later (decision 69). */
const survey = '00000046-0001-4000-8000-000000000001';

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`surveys ${variant.name}`, () => {
    screenshotTest.describe('manager', () => {
      screenshotTest.use({ variant, role: 'bot' });

      screenshotTest('list, editor and results @SV1 @screenshot', async ({ page, visual }) => {
        await page.goto('/surveys');
        await expect(page.getByTestId('surveys.list.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `surveys-list-${variant.name}`);
        await page.goto(`/surveys/${survey}`);
        await expect(page.getByTestId('surveys.editor.question').first()).toBeVisible();
        await visual.expectScreen(page, `surveys-editor-${variant.name}`);
        await page.goto(`/surveys/${survey}/results`);
        await expect(page.getByTestId('surveys.results.table')).toBeVisible();
        await visual.expectScreen(page, `surveys-results-${variant.name}`);
      });
    });

    screenshotTest.describe('respondent', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('bare fill page @SV1 @screenshot', async ({ page, visual }) => {
        await page.goto(`/survey/${survey}?storeId=${seedStore('S-01').id}`);
        await expect(page.getByTestId('surveys.fill.form')).toBeVisible();
        await visual.expectScreen(page, `surveys-fill-${variant.name}`);
      });
    });
  });
});
