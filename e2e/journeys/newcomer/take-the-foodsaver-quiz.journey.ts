import type { Page } from '@playwright/test';
import { SEED_NOW, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptPrivacy, singleQuestionQuiz, solutionKey } from '../../fixtures/flows/quiz';
import { Walk } from '../../fixtures/persona';

/**
 * Q1/Q2: Lea (newbie@local, one failed try yesterday in the seed) looks at the quizzes, practises the foodsaver quiz
 * with the timer and runs out of time, then takes it for real without the timer, passes, accepts the legal agreement
 * in the confirmation (Ve1) and finds both sessions in her quiz history (A8, D10.3). The quiz team keeps one question
 * and allows no mistake (orga through the editor API), so the session and the solution look the same on every run;
 * the server shuffles the answers, so their lists are masked. The real ten-question pool is walked in first-days.
 *
 * Rules behind it: QuizRuleServiceTest (who may try which quiz, the wait/disqualification table, hygiene expiry,
 * timed answers, failure points), QuizSessionServiceTest (practice sessions never count, the store manager
 * confirmation needs the confidentiality notice, who reads someone's quiz history), QuizSeedTextsTest (all quizzes
 * named in five languages).
 */
test.use({ role: 'newbie' });

const plusHours = (hours: number) => new Date(Date.parse(SEED_NOW) + hours * 3_600_000).toISOString();

function card(page: Page, kind: string) {
  return page.locator(`[data-testid="quiz.list.card"][data-kind="${kind}"]`);
}

test('newcomer practises the foodsaver quiz against the clock, then passes it and reads her quiz history @Q1 @Q2 @Ve1 @A8 @D10.3', async ({
  page,
  api,
  apiOf,
  dev,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'newcomer');
  const me = apiOf(page);
  await acceptPrivacy(me);
  await api.loginAs('orga');
  await singleQuestionQuiz(api, 'FOODSAVER');
  const key = await solutionKey(api, 'FOODSAVER');
  const options = page.locator('.options');
  const solution = page.locator('fs-quiz-solution-list');

  await walk.step('looks at the quizzes', async () => {
    await page.goto('/quizzes');
    await expectDataState(page, 'quiz.list.state', 'success');
    await expect(card(page, 'FOODSAVER').getByTestId('quiz.list.name')).toHaveText('Foodsaver-Quiz');
    // seed: one failed try yesterday
    await expect(card(page, 'FOODSAVER').getByTestId('quiz.list.status')).toHaveAttribute('data-phase', 'failed');
    // the store manager quiz is for verified foodsavers: locked, with the reason instead of a way in
    await expect(card(page, 'STORE_MANAGER').getByTestId('quiz.list.locked')).toBeVisible();
    await expect(card(page, 'STORE_MANAGER').getByTestId('quiz.list.open')).toHaveCount(0);
    await checkpoint(page, 'quizzes');
  });

  await walk.step('practises with the timer and runs out of time', async () => {
    await walk.click(page.getByTestId('quiz.list.practice'));
    await expect(page).toHaveURL(/practice=true/);
    await expect(page.getByTestId('quiz.list.status')).toHaveCount(0);
    await walk.click(card(page, 'FOODSAVER').getByTestId('quiz.list.open'));
    await expect(page.getByTestId('quiz.intro.practiceBanner')).toBeVisible();
    await walk.click(page.getByTestId('quiz.intro.timed'));
    await walk.click(page.getByTestId('quiz.intro.startButton'));
    await expect(page.getByTestId('quiz.session.question')).toBeVisible();
    await expect(page.getByTestId('quiz.session.countdown')).toBeVisible();
    await expect(page.getByTestId('quiz.session.timedOut')).toHaveCount(0);
    await checkpoint(page, 'timed-question', { mask: [options, page.getByTestId('quiz.session.countdown')] });

    // she is called away; the server clock decides, the browser clock is irrelevant
    await dev.setClock(plusHours(1));
    await page.reload();
    await expect(page.getByTestId('quiz.session.timedOut')).toBeVisible();
    await expect(page.getByTestId('quiz.session.answer').first()).toBeDisabled();
    await walk.click(page.getByTestId('quiz.session.submit'));
    await expect(page.getByTestId('quiz.session.answeredLate')).toBeVisible();
    await expect(page.getByTestId('quiz.session.verdict')).toContainText(/\d/);
    await checkpoint(page, 'answered-late', { mask: [solution] });
    await walk.click(page.getByTestId('quiz.session.results'));
    await expect(page.getByTestId('quiz.results.practice')).toBeVisible();
    await expect(page.getByTestId('quiz.results.confirm')).toHaveCount(0);
    // practice has no effect: still a foodsharer, the seed's failed try is still the last one that counts
    expect((await me.session()).user?.role).toBe('FOODSHARER');
    const status = await me.getJson<{ lastSessionStatus?: string; passedAt?: string }>('/api/quizzes/FOODSAVER/status');
    expect(status.lastSessionStatus).toBe('FAILED');
    expect(status.passedAt ?? null).toBeNull();
  });

  await walk.step('takes the quiz for real without the timer and passes', async () => {
    await page.goto('/quizzes');
    await walk.click(card(page, 'FOODSAVER').getByTestId('quiz.list.open'));
    await expect(page).toHaveURL(/\/quizzes\/foodsaver$/);
    await expect(page.getByTestId('quiz.intro.failed')).toBeVisible();
    await expect(page.getByTestId('quiz.intro.practiceBanner')).toHaveCount(0);
    await checkpoint(page, 'quiz-intro');
    await walk.click(page.getByTestId('quiz.intro.untimed'));
    await walk.click(page.getByTestId('quiz.intro.startButton'));
    await expect(page).toHaveURL(/\/quizzes\/foodsaver\/session$/);
    await expect(page.getByTestId('quiz.session.countdown')).toHaveCount(0);
    const question = page.getByTestId('quiz.session.question');
    const id = (await question.getAttribute('data-question'))!;
    for (const answerId of key.get(id) ?? []) await walk.check(question.locator(`[data-answer="${answerId}"]`));
    await walk.click(page.getByTestId('quiz.session.submit'));
    await expect(page.getByTestId('quiz.session.verdict')).toBeVisible();
    await expect(page.getByTestId('quiz.session.answeredLate')).toHaveCount(0);
    await checkpoint(page, 'solution', { mask: [solution] });
    await walk.click(page.getByTestId('quiz.session.results'));
    await expect(page).toHaveURL(/\/quizzes\/foodsaver\/results$/);
    await expect(page.getByTestId('quiz.results.summary')).toHaveAttribute('data-status', 'PASSED');
    await expect(page.getByTestId('quiz.results.question')).toHaveCount(1);
  });

  await walk.step('accepts the legal agreement and becomes a foodsaver', async () => {
    const confirm = page.getByTestId('quiz.results.confirm');
    await expectDataState(page, 'quiz.results.document', 'success');
    await expect(confirm.getByTestId('quiz.results.confirmButton').locator('button')).toBeDisabled();
    await checkpoint(page, 'results-confirm', { mask: [solution] });
    await walk.click(confirm.getByTestId('quiz.results.agree'));
    await walk.click(confirm.getByTestId('quiz.results.confirmButton'));
    await expect(page.getByTestId('quiz.results.confirmed')).toBeVisible();
    expect((await me.session()).user?.role).toBe('FOODSAVER');
    const consents = await me.getJson<{ kind: string; currentVersion: string; acceptedVersion?: string; required: boolean; pending: boolean }[]>(
      '/api/legal/consents',
    );
    const agreement = consents.find((c) => c.kind === 'LEGAL_AGREEMENT')!;
    expect(agreement).toMatchObject({ required: true, pending: false, acceptedVersion: agreement.currentVersion });
    // no second prompt for the agreement on the consent wall
    await page.goto('/me/activity');
    await expect(page).toHaveURL(/\/me\/activity$/);
  });

  await walk.step('reads her quiz history', async () => {
    await page.goto('/quizzes');
    await expect(card(page, 'FOODSAVER').getByTestId('quiz.list.status')).toHaveAttribute('data-phase', 'passed');
    await walk.click(page.getByTestId('quiz.list.history'));
    await expect(page).toHaveURL(/\/quizzes\/history$/);
    await expectDataState(page, 'quiz.history.state', 'success');
    const rows = page.getByTestId('quiz.history.row');
    await expect(rows.first()).toHaveAttribute('data-status', 'PASSED');
    await expect(rows.first()).toHaveAttribute('data-kind', 'FOODSAVER');
    await expect(page.locator('[data-testid="quiz.history.row"][data-status="FAILED"]').first()).toBeVisible();
    await checkpoint(page, 'quiz-history');
  });

  walk.finish({ steps: 5, clicks: 15, fields: 0 });
});
