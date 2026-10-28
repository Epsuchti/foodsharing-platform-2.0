import { expect, type Page } from '@playwright/test';

import type { ApiClient } from '../../fixtures';

/** Helpers of the quiz specs (M2 T19). */

export type QuizKind = 'FOODSAVER' | 'STORE_MANAGER' | 'AMBASSADOR' | 'HYGIENE' | 'FOODSHARER' | 'SAVING_FOOD' | 'REPORTS';

interface AnswerDetail {
  id: string;
  rating: 'CORRECT' | 'WRONG' | 'NEUTRAL';
}
interface QuestionDetail {
  id: string;
  answers: AnswerDetail[];
}
interface ActiveQuestion {
  index: number;
  total: number;
  question: { id: string; durationSeconds?: number };
}
interface AnswerResult {
  finished: boolean;
}

/** Correct answer ids per question id, read through the editor API (needs an editor, e.g. orga). */
export async function solutionKey(editor: ApiClient, kind: QuizKind): Promise<Map<string, string[]>> {
  const questions = await editor.getJson<QuestionDetail[]>(`/api/quizzes/${kind}/questions`);
  return new Map(
    questions.map((question) => [
      question.id,
      question.answers.filter((answer) => answer.rating === 'CORRECT').map((answer) => answer.id),
    ]),
  );
}

/**
 * Screenshots need a stable quiz: keep one question (a mandatory one when there is one) ask exactly one and allow no mistake.
 * Answer order is still shuffled by the server, so screenshots mask the answer lists.
 */
export async function singleQuestionQuiz(editor: ApiClient, kind: QuizKind): Promise<void> {
  const questions = await editor.getJson<(QuestionDetail & { mandatory: boolean })[]>(`/api/quizzes/${kind}/questions`);
  const keep = questions.find((question) => question.mandatory) ?? questions[0];
  for (const question of questions.filter((q) => q.id !== keep.id)) {
    const response = await editor.fetch('DELETE', `/api/quizzes/${kind}/questions/${question.id}`);
    expect(response.ok(), await response.text()).toBe(true);
  }
  await editor.patchJson(`/api/quizzes/${kind}`, { questionCountTimed: 1, questionCountUntimed: 1, maxFailurePoints: 0 });
}

/** Accepts only the privacy policy (the legal agreement stays for the quiz confirmation step). */
export async function acceptPrivacy(api: ApiClient): Promise<void> {
  const consents = await api.getJson<{ kind: string; currentVersion: string; pending: boolean }[]>('/api/legal/consents');
  for (const consent of consents.filter((c) => c.pending && c.kind === 'PRIVACY_POLICY')) {
    await api.postJson('/api/legal/consents', { kind: consent.kind, version: consent.currentVersion });
  }
}

/** Runs a whole session through the API: `correct` answers everything right, otherwise nothing is chosen. */
export async function playSession(
  api: ApiClient,
  kind: QuizKind,
  options: { correct: boolean; key?: Map<string, string[]>; timed?: boolean; test?: boolean },
): Promise<void> {
  const test = options.test ?? false;
  const start = await api.fetch('POST', `/api/quizzes/${kind}/sessions`, { timed: options.timed ?? false, test });
  expect(start.status(), await start.text()).toBe(201);
  for (;;) {
    const current = await api.getJson<ActiveQuestion>(`/api/quizzes/${kind}/sessions/current/question?test=${test}`);
    const answerIds = options.correct ? (options.key?.get(current.question.id) ?? []) : [];
    const result = await api.postJson<AnswerResult>(`/api/quizzes/${kind}/sessions/current/answer?test=${test}`, { answerIds });
    if (result.finished) return;
  }
}

/** Answers the session shown in the browser with the solution key until the results link appears. */
export async function playInBrowser(page: Page, key: Map<string, string[]>): Promise<number> {
  let answered = 0;
  for (;;) {
    const question = page.getByTestId('quiz.session.question');
    await expect(question).toBeVisible();
    const id = await question.getAttribute('data-question');
    for (const answerId of key.get(id!) ?? []) {
      await question.locator(`[data-answer="${answerId}"]`).check();
    }
    await page.getByTestId('quiz.session.submit').click();
    await expect(page.getByTestId('quiz.session.verdict')).toBeVisible();
    answered++;
    const results = page.getByTestId('quiz.session.results');
    if (await results.isVisible()) {
      await results.click();
      return answered;
    }
    await page.getByTestId('quiz.session.next').click();
    await expect(page.getByTestId('quiz.session.question')).not.toHaveAttribute('data-question', id!);
  }
}
