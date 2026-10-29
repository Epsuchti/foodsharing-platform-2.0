import { regionId } from '../fixtures';
import { expect, forEachVariant, screenshotTest } from './_setup';

/** Poll screens (M5 V1): list, ballot, results, form. Baselines later (decision 69). */
const zs = regionId('zuerich-stadt');
const poll = (n: number) => `/polls/00000045-0001-4000-8000-${String(n).padStart(12, '0')}`;

screenshotTest.use({ seedReset: 'file' });

forEachVariant((variant) => {
  screenshotTest.describe(`polls ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'foodsaver' });

    screenshotTest('list, ballot and results @V1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/regions/${zs}/polls`);
      await expect(page.getByTestId('polls.list.state')).toHaveAttribute('data-state', 'success');
      await visual.expectScreen(page, `polls-list-${variant.name}`);
      await page.goto(poll(1));
      await expect(page.getByTestId('polls.poll.ballot')).toBeVisible();
      await visual.expectScreen(page, `polls-ballot-${variant.name}`);
      await page.goto(poll(2));
      await expect(page.getByTestId('polls.poll.results')).toBeVisible();
      await visual.expectScreen(page, `polls-results-${variant.name}`);
    });

    screenshotTest('poll form @V1 @screenshot', async ({ page, visual }) => {
      await page.goto(`/polls/new?regionId=${zs}`);
      await page.getByTestId('polls.form.submit').click();
      await expect(page.getByTestId('polls.form.optionsError')).toBeVisible();
      await visual.expectScreen(page, `polls-form-${variant.name}`);
    });
  });
});
