import { type ApiClient, injectApiError } from '../fixtures';
import { SEED_NOW } from '../support/env';
import { acceptPrivacy, playSession, singleQuestionQuiz, solutionKey } from '../tests/quiz/_quiz';
import { expect, forEachVariant, screenshotTest, type Variant } from './_setup';

/**
 * Quiz screens (M2 T19): quizzes, practice mode, intro (expiring / wait / disqualified), session question, solution,
 * timed out, results passed with confirmation and failed with wait, history, editor + question dialog, forbidden,
 * loading and error — × light/dark × de/fr. Baselines are generated in Docker (`e2e/scripts/screenshots.sh`).
 */
screenshotTest.use({ seedReset: 'test' });

/** Quiz names and questions come in the user's language: align the server setting with the variant. */
async function language(api: ApiClient, variant: Variant): Promise<void> {
  await api.patchJson('/api/users/me/settings', { language: variant.lang });
}

forEachVariant((variant) => {
  screenshotTest.describe(`quiz ${variant.name}`, () => {
    screenshotTest.describe('newbie', () => {
      screenshotTest.use({ variant, role: 'newbie' });

      screenshotTest('quizzes @Q1 @screenshot', async ({ page, apiOf, visual }) => {
              await acceptPrivacy(apiOf(page));
        await language(apiOf(page), variant);
        await page.goto('/quizzes');
        await expect(page.getByTestId('quiz.list.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `quizzes-${variant.name}`);
      });

      screenshotTest('quiz session question, solution and timed out @Q2 @screenshot', async ({ page, api, apiOf, dev, visual }) => {
        await api.loginAs('orga');
        await singleQuestionQuiz(api, 'FOODSAVER');
              const own = apiOf(page);
        await acceptPrivacy(own);
        await language(own, variant);
        await page.goto('/quizzes/foodsaver');
        await expect(page.getByTestId('quiz.intro.start')).toBeVisible();
        await visual.expectScreen(page, `quiz-intro-${variant.name}`);

        await page.getByTestId('quiz.intro.startButton').click();
        const question = page.getByTestId('quiz.session.question');
        await expect(question).toBeVisible();
        await visual.expectScreen(page, `quiz-session-${variant.name}`, { mask: [page.locator('.options')] });
        await page.getByTestId('quiz.session.submit').click();
        await expect(page.getByTestId('quiz.session.verdict')).toBeVisible();
        await visual.expectScreen(page, `quiz-solution-${variant.name}`, { mask: [page.locator('fs-quiz-solution-list')] });

        // a timed practice round, then the server clock moves past the question time
        await page.goto('/quizzes/foodsaver?practice=true');
        await page.getByTestId('quiz.intro.timed').click();
        await page.getByTestId('quiz.intro.startButton').click();
        await expect(page.getByTestId('quiz.session.countdown')).toBeVisible();
        await dev.setClock(new Date(Date.parse(SEED_NOW) + 3_600_000).toISOString());
        await page.reload();
        await expect(page.getByTestId('quiz.session.timedOut')).toBeVisible();
        await visual.expectScreen(page, `quiz-timed-out-${variant.name}`, { mask: [page.locator('.options')] });
      });

    });

    screenshotTest.describe('foodsaver', () => {
      screenshotTest.use({ variant, role: 'foodsaver' });

      screenshotTest('quiz results passed with confirmation @Q2 @screenshot', async ({ page, apiOf, api, visual }) => {
        await api.loginAs('orga');
        await singleQuestionQuiz(api, 'STORE_MANAGER');
        const key = await solutionKey(api, 'STORE_MANAGER');
              const own = apiOf(page);
          await language(own, variant);
        await playSession(own, 'STORE_MANAGER', { correct: true, key });
        await page.goto('/quizzes/store-manager/results');
        await expect(page.getByTestId('quiz.results.document')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `quiz-results-passed-${variant.name}`, { mask: [page.locator('fs-quiz-solution-list')] });
      });

      screenshotTest('quizzes practice mode @Q2 @screenshot', async ({ page, apiOf, visual }) => {
              await language(apiOf(page), variant);
        await page.goto('/quizzes?practice=true');
        await expect(page.getByTestId('quiz.list.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `quizzes-practice-${variant.name}`);
      });

      screenshotTest('quizzes loading @Q1 @screenshot', async ({ page, visual }) => {
              await injectApiError(page, { url: /\/api\/quizzes$/, delayMs: 30_000 });
        await page.goto('/quizzes');
        await expect(page.getByTestId('quiz.list.state')).toHaveAttribute('data-state', 'loading');
        await visual.expectScreen(page, `quizzes-loading-${variant.name}`, { waitForData: false });
      });

      screenshotTest('quizzes error @Q1 @screenshot', async ({ page, visual }) => {
              await injectApiError(page, { url: /\/api\/quizzes$/, status: 500 });
        await page.goto('/quizzes');
        await expect(page.getByTestId('quiz.list.state')).toHaveAttribute('data-state', 'error');
        await visual.expectScreen(page, `quizzes-error-${variant.name}`);
      });

      screenshotTest('quiz intro expiring @Q2 @screenshot', async ({ page, apiOf, visual }) => {
              await language(apiOf(page), variant);
        await page.goto('/quizzes/hygiene');
        await expect(page.getByTestId('quiz.intro.expiring')).toBeVisible();
        await visual.expectScreen(page, `quiz-intro-expiring-${variant.name}`);
      });

      screenshotTest('quiz editor forbidden @Q3 @screenshot', async ({ page, visual }) => {
              await page.goto('/quizzes/edit/foodsaver');
        await expect(page.getByTestId('quiz.editor.state')).toHaveAttribute('data-state', 'forbidden');
        await visual.expectScreen(page, `quiz-editor-forbidden-${variant.name}`);
      });
    });

    screenshotTest.describe('profile', () => {
      screenshotTest.use({ variant, role: 'profile' });

      screenshotTest('quiz intro wait and disqualified @Q2 @screenshot', async ({ page, api, apiOf, dev, visual }) => {
        await api.loginAs('orga');
        await singleQuestionQuiz(api, 'FOODSAVER');
              const own = apiOf(page);
        await acceptPrivacy(own);
        await language(own, variant);
        await playSession(own, 'FOODSAVER', { correct: false });
        await page.goto('/quizzes/foodsaver');
        await expect(page.getByTestId('quiz.intro.wait')).toBeVisible();
        await visual.expectScreen(page, `quiz-intro-wait-${variant.name}`);

        await dev.setClock(new Date(Date.parse(SEED_NOW) + 31 * 86_400_000).toISOString());
        await playSession(own, 'FOODSAVER', { correct: false });
        await page.goto('/quizzes/foodsaver/results');
        await expect(page.getByTestId('quiz.results.disqualified')).toBeVisible();
        await visual.expectScreen(page, `quiz-results-disqualified-${variant.name}`, { mask: [page.locator('fs-quiz-solution-list')] });
      });

      screenshotTest('quiz results failed with wait @Q2 @screenshot', async ({ page, api, apiOf, visual }) => {
        await api.loginAs('orga');
        await singleQuestionQuiz(api, 'FOODSAVER');
              const own = apiOf(page);
        await acceptPrivacy(own);
        await language(own, variant);
        await playSession(own, 'FOODSAVER', { correct: false });
        await page.goto('/quizzes/foodsaver/results');
        await expect(page.getByTestId('quiz.results.wait')).toBeVisible();
        await visual.expectScreen(page, `quiz-results-failed-${variant.name}`, { mask: [page.locator('fs-quiz-solution-list')] });
      });

      screenshotTest('quiz history @A8 @screenshot', async ({ page, apiOf, visual }) => {
              await acceptPrivacy(apiOf(page));
        await page.goto('/quizzes/history');
        await expect(page.getByTestId('quiz.history.state')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `quiz-history-${variant.name}`);
      });
    });

    screenshotTest.describe('orga', () => {
      screenshotTest.use({ variant, role: 'orga' });

      screenshotTest('quiz editor and question dialog @Q3 @screenshot', async ({ page, apiOf, visual }) => {
              await language(apiOf(page), variant);
        await page.goto('/quizzes/edit');
        await expect(page.getByTestId('quiz.editor.list')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `quiz-editor-list-${variant.name}`);

        await page.goto('/quizzes/edit/hygiene');
        await expect(page.getByTestId('quiz.editor.questions')).toHaveAttribute('data-state', 'success');
        await visual.expectScreen(page, `quiz-editor-${variant.name}`);

        await page.getByTestId('quiz.editor.editQuestion').first().click();
        const dialog = page.getByTestId('quiz.editor.questionDialog');
        await expect(dialog.getByTestId('quiz.editor.answers')).toBeVisible();
        await visual.expectScreen(page, `quiz-editor-dialog-${variant.name}`, { fullPage: false });
      });
    });
  });
});
