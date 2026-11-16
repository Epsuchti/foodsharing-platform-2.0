import { acceptPendingConsents, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Q3 quiz editor: ORGA (orga@local, edits every quiz) makes the hygiene quiz stricter, adds a mandatory question in
 * German and French with a correct answer and its explanation, leaves a comment for the quiz team and removes the
 * question again after second thoughts. Rules behind it: QuizEditorCapabilityTest (who edits which quiz; plain
 * foodsavers get the forbidden state), QuizRuleServiceTest (failure points); client quiz-pages.spec.
 */
test.use({ role: 'orga' });

test('orga makes the hygiene quiz stricter, adds a question with an answer and a comment and removes it again @Q3', async ({
  page,
  apiOf,
}, testInfo) => {
  test.setTimeout(150_000);
  const api = apiOf(page);
  await acceptPendingConsents(api);
  const walk = new Walk(page, testInfo, 'orga');
  const questions = page.getByTestId('quiz.editor.question');
  const dialog = page.getByTestId('quiz.editor.questionDialog');
  let before = 0;

  await walk.step('opens the hygiene quiz in the quiz editor', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminQuizzes');
    await expect(page).toHaveURL(/\/quizzes\/edit$/);
    await expectDataState(page, 'quiz.editor.list', 'success');
    await checkpoint(page, 'quiz-list');
    await walk.click(page.locator('[data-testid="quiz.editor.listItem"][data-kind="HYGIENE"]'));
    await expect(page).toHaveURL(/\/quizzes\/edit\/hygiene$/);
    await expectDataState(page, 'quiz.editor.state', 'success');
    await expectDataState(page, 'quiz.editor.questions', 'success');
    before = await questions.count();
    expect(before).toBeGreaterThan(0);
    await checkpoint(page, 'quiz-editor');
  });

  await walk.step('allows fewer failure points', async () => {
    await walk.fill(page.getByTestId('quiz.editor.maxFailurePoints').locator('input'), '3');
    await walk.fill(page.getByTestId('quiz.editor.reason'), 'Strenger nach dem Audit');
    await walk.click(page.getByTestId('quiz.editor.saveSettings'));
    await expect.poll(async () => (await api.getJson<{ maxFailurePoints: number }>('/api/quizzes/HYGIENE')).maxFailurePoints).toBe(3);
  });

  await walk.step('adds a mandatory question in German and French with its answer', async () => {
    await walk.click(page.getByTestId('quiz.editor.addQuestion'));
    await walk.fill(dialog.getByTestId('quiz.editor.questionText.field.de'), 'Wie lange darf Hackfleisch ungekühlt bleiben?');
    await walk.click(dialog.getByTestId('quiz.editor.questionText.tab.fr'));
    await walk.fill(dialog.getByTestId('quiz.editor.questionText.field.fr'), 'Combien de temps la viande hachée peut-elle rester hors du frigo ?');
    await walk.click(dialog.getByTestId('quiz.editor.mandatory'));
    await walk.click(dialog.getByTestId('quiz.editor.saveQuestion'));
    await expect(questions).toHaveCount(before + 1);
    // the dialog stays open on the new question: a correct answer with its explanation
    await expect(dialog.getByTestId('quiz.editor.answers')).toBeVisible();
    await walk.click(dialog.getByTestId('quiz.editor.addAnswer'));
    await walk.fill(dialog.getByTestId('quiz.editor.answerText.field.de'), 'Gar nicht');
    await walk.fill(dialog.getByTestId('quiz.editor.explanation.field.de'), 'Hackfleisch muss durchgehend gekühlt sein.');
    await walk.click(dialog.getByTestId('quiz.editor.rating'));
    await walk.click(page.getByRole('option', { name: 'Richtig' }));
    await expect(page.getByRole('listbox')).toBeHidden();
    await checkpoint(page, 'question-dialog', { fullPage: false });
    await walk.click(dialog.getByTestId('quiz.editor.saveAnswer'));
    await expect(dialog.getByTestId('quiz.editor.answer')).toHaveCount(1);
    await expect(dialog.getByTestId('quiz.editor.answer')).toHaveAttribute('data-rating', 'CORRECT');
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    const created = questions.last();
    await expect(created).toContainText('Hackfleisch');
    await expect(created.getByTestId('quiz.editor.mandatoryBadge')).toBeVisible();
  });

  await walk.step('leaves a comment for the quiz team', async () => {
    const created = questions.last();
    await walk.click(created.getByTestId('quiz.editor.openComments'));
    await expect(page.getByTestId('quiz.editor.comments')).toHaveAttribute('data-state', 'empty');
    await walk.fill(page.getByTestId('quiz.editor.commentText'), 'Bitte Quelle im Wiki ergänzen.');
    await walk.click(page.getByTestId('quiz.editor.addComment'));
    await expect(page.getByTestId('quiz.editor.comment')).toHaveCount(1);
    // the comment's time comes from the real clock
    await checkpoint(page, 'comments', {
      fullPage: false,
      mask: [page.getByTestId('quiz.editor.comment').locator('fs-user-chip')],
    });
    await page.keyboard.press('Escape');
    await expect(created.getByTestId('quiz.editor.openComments')).toContainText('1');
  });

  await walk.step('removes the question again', async () => {
    await walk.click(questions.last().getByTestId('quiz.editor.deleteQuestion'));
    const confirm = page.getByRole('alertdialog');
    await expect(confirm).toBeVisible();
    await checkpoint(page, 'delete-question');
    await walk.click(confirm.getByRole('button', { name: 'Löschen' }));
    await expect(questions).toHaveCount(before);
  });

  walk.finish({ steps: 5, clicks: 16, fields: 7 });
});
