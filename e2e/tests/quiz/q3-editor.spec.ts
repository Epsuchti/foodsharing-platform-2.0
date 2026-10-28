import { expect, test } from '../../fixtures';

/** Q3: the quiz editor (settings, questions, answers, comments) and its access rule. */

test.use({ seedReset: 'test' });

test('Q3 quiz AG admin edits settings, questions, answers and comments @Q3 @page', async ({ as, apiOf }) => {
  const page = await as('orga');
  const api = apiOf(page);
  await page.goto('/quizzes/edit');
  await page.locator('[data-testid="quiz.editor.listItem"][data-kind="HYGIENE"]').click();
  await expect(page).toHaveURL(/\/quizzes\/edit\/hygiene$/);
  await expect(page.getByTestId('quiz.editor.state')).toHaveAttribute('data-state', 'success');
  await expect(page.getByTestId('quiz.editor.questions')).toHaveAttribute('data-state', 'success');
  const before = await page.getByTestId('quiz.editor.question').count();
  expect(before).toBeGreaterThan(0);

  // settings
  await page.getByTestId('quiz.editor.maxFailurePoints').locator('input').fill('3');
  await page.getByTestId('quiz.editor.reason').fill('e2e: stricter');
  await page.getByTestId('quiz.editor.saveSettings').click();
  await expect.poll(async () => (await api.getJson<{ maxFailurePoints: number }>('/api/quizzes/HYGIENE')).maxFailurePoints).toBe(3);

  // new question in two languages
  await page.getByTestId('quiz.editor.addQuestion').click();
  const dialog = page.getByTestId('quiz.editor.questionDialog');
  await dialog.getByTestId('quiz.editor.questionText.field.de').fill('Wie lange darf Hackfleisch ungekühlt bleiben?');
  await dialog.getByTestId('quiz.editor.questionText.tab.fr').click();
  await dialog.getByTestId('quiz.editor.questionText.field.fr').fill('Combien de temps la viande hachée peut-elle rester hors du frigo ?');
  await dialog.getByTestId('quiz.editor.mandatory').click();
  await dialog.getByTestId('quiz.editor.saveQuestion').click();
  await expect(page.getByTestId('quiz.editor.question')).toHaveCount(before + 1);

  // the dialog stays open on the new question: add a correct answer with explanation
  await expect(dialog.getByTestId('quiz.editor.answers')).toBeVisible();
  await dialog.getByTestId('quiz.editor.addAnswer').click();
  await dialog.getByTestId('quiz.editor.answerText.field.de').fill('Gar nicht');
  await dialog.getByTestId('quiz.editor.explanation.field.de').fill('Hackfleisch muss durchgehend gekühlt sein.');
  await dialog.getByTestId('quiz.editor.rating').click();
  await page.getByRole('option', { name: 'Richtig' }).click();
  await expect(page.getByRole('listbox')).toBeHidden();
  await dialog.getByTestId('quiz.editor.saveAnswer').click();
  await expect(dialog.getByTestId('quiz.editor.answer')).toHaveCount(1);
  await expect(dialog.getByTestId('quiz.editor.answer')).toHaveAttribute('data-rating', 'CORRECT');
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();

  const created = page.getByTestId('quiz.editor.question').last();
  await expect(created).toContainText('Hackfleisch');
  await expect(created.getByTestId('quiz.editor.mandatoryBadge')).toBeVisible();

  // comment
  await created.getByTestId('quiz.editor.openComments').click();
  await expect(page.getByTestId('quiz.editor.comments')).toHaveAttribute('data-state', 'empty');
  await page.getByTestId('quiz.editor.commentText').fill('Bitte Quelle im Wiki ergänzen.');
  await page.getByTestId('quiz.editor.addComment').click();
  await expect(page.getByTestId('quiz.editor.comment')).toHaveCount(1);
  await page.keyboard.press('Escape');
  await expect(created.getByTestId('quiz.editor.openComments')).toContainText('1');

  // delete with confirmation
  await created.getByTestId('quiz.editor.deleteQuestion').click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Löschen' }).click();
  await expect(page.getByTestId('quiz.editor.question')).toHaveCount(before);
});

test('Q3 non-editor gets forbidden state @Q3', async ({ as }) => {
  const page = await as('foodsaver');
  await page.goto('/quizzes/edit/foodsaver');
  await expect(page.getByTestId('quiz.editor.state')).toHaveAttribute('data-state', 'forbidden');
  await page.goto('/quizzes/edit');
  await expect(page.getByTestId('quiz.editor.list')).toHaveAttribute('data-state', 'empty');
});
