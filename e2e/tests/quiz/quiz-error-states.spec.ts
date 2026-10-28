import { expect, expectDataState, expectErrorState, expectSkeleton, injectApiError, test } from '../../fixtures';

/** Loading, error + retry, empty and not-found states of the quiz pages (M2 T19). */

test.use({ role: 'foodsaver' });

test('quizzes: loading skeleton, then error with retry recovers @Q1', async ({ page }) => {
  const slow = await injectApiError(page, { url: /\/api\/quizzes$/, delayMs: 30_000 });
  await page.goto('/quizzes');
  await expectSkeleton(page, 'quiz.list.state');
  await slow.remove();

  const injection = await injectApiError(page, { url: /\/api\/quizzes$/, status: 500 });
  await page.goto('/quizzes');
  const { retry } = await expectErrorState(page, 'quiz.list.state');
  await injection.remove();
  await retry.click();
  await expectDataState(page, 'quiz.list.state', 'success');
});

test('quiz session without a running session shows the empty state with a way back @Q2', async ({ page }) => {
  await page.goto('/quizzes/hygiene/session');
  await expectDataState(page, 'quiz.session.state', 'empty');
  await page.getByTestId('quiz.session.state.action').click();
  await expect(page).toHaveURL(/\/quizzes\/hygiene$/);
});

test('quiz results without a finished session show not found; unknown quiz kinds are not found @Q2', async ({ page }) => {
  await page.goto('/quizzes/reports/results');
  await expectDataState(page, 'quiz.results.state', 'notFound');
  await page.goto('/quizzes/no-such-quiz');
  await expect(page.getByTestId('errorPages.notFound')).toBeVisible();
});

test('quiz history: server error with retry @A8', async ({ page }) => {
  const injection = await injectApiError(page, { url: '/api/users/me/quiz-sessions', status: 503 });
  await page.goto('/quizzes/history');
  const { retry } = await expectErrorState(page, 'quiz.history.state');
  await injection.remove();
  await retry.click();
  await expectDataState(page, 'quiz.history.state', 'success');
});
