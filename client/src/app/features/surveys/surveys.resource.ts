import { Observable } from 'rxjs';

import { SurveyAnswer, SurveyQuestion, SurveyQuestionType, SurveyResults, SurveysService, UserSummary } from '../../api';

/** Co-author search for `fs-user-picker [search]` (users with CAN_MANAGE_SURVEYS, A36). */
export function coauthorSearch(api: SurveysService): (q: string) => Observable<UserSummary[]> {
  return (q) => api.listSurveyCoauthorCandidates({ q });
}

/** Question types with an options list (SurveyService: at least two options). */
export function hasOptions(type: SurveyQuestionType): boolean {
  return type === SurveyQuestionType.SingleChoice || type === SurveyQuestionType.MultipleChoice;
}

/**
 * Editor validation (mirrors SurveyService.validate): title 1–200, 1–100 questions with labels, at most one STORE
 * question, choice questions 2–20 options, none blank, unique. Returns the first error code or null.
 */
export function surveyErrors(
  title: string,
  questions: readonly SurveyQuestion[],
): 'title' | 'noQuestions' | 'label' | 'options' | 'store' | null {
  if (!title.trim() || title.trim().length > 200) return 'title';
  if (questions.length < 1 || questions.length > 100) return 'noQuestions';
  if (questions.some((q) => !q.label.trim())) return 'label';
  if (questions.filter((q) => q.type === SurveyQuestionType.Store).length > 1) return 'store';
  for (const q of questions) {
    if (!hasOptions(q.type)) continue;
    const opts = q.options.map((o) => o.trim());
    if (opts.length < 2 || opts.length > 20 || opts.some((o) => !o) || new Set(opts).size !== opts.length) return 'options';
  }
  return null;
}

/** Normalises a question before saving: options only for choice types, STORE always required. */
export function cleanQuestion(q: SurveyQuestion): SurveyQuestion {
  const type = q.type;
  return {
    ...(q.id ? { id: q.id } : {}),
    type,
    label: q.label.trim(),
    required: type === SurveyQuestionType.Store || q.required,
    options: hasOptions(type) ? q.options.map((o) => o.trim()) : [],
  };
}

/** Fill answers keyed by question id. STORE questions are left out (the server takes the link's store). */
export type AnswerDraft = Readonly<Record<string, readonly string[] | undefined>>;

/** First required question (not STORE) without an answer, or null when the form may be submitted. */
export function missingRequired(questions: readonly SurveyQuestion[], draft: AnswerDraft): string | null {
  for (const q of questions) {
    if (!q.required || q.type === SurveyQuestionType.Store || !q.id) continue;
    const values = (draft[q.id] ?? []).filter((v) => v.trim());
    if (!values.length) return q.id;
  }
  return null;
}

export function buildAnswers(questions: readonly SurveyQuestion[], draft: AnswerDraft): SurveyAnswer[] {
  return questions
    .filter((q) => q.id && q.type !== SurveyQuestionType.Store)
    .map((q) => ({ questionId: q.id!, values: (draft[q.id!] ?? []).filter((v) => v.trim()).map((v) => (q.type === SurveyQuestionType.Text ? v.trim() : v)) }))
    .filter((a) => a.values.length);
}

/** The share link of a survey, optionally bound to a store (STORE questions are answered only through it). */
export function surveyLink(origin: string, surveyId: string, storeId?: string | null): string {
  return `${origin}/survey/${surveyId}${storeId ? `?storeId=${encodeURIComponent(storeId)}` : ''}`;
}

/** Cell text of a result row for one question (STORE answers show the store name). */
export function cellText(results: SurveyResults, rowIndex: number, question: SurveyQuestion, labels: { yes: string; no: string }): string {
  const row = results.rows[rowIndex];
  if (question.type === SurveyQuestionType.Store) return row.store?.name ?? '';
  const values = row.answers[question.id ?? ''] ?? [];
  if (question.type === SurveyQuestionType.Boolean) return values.map((v) => (v === 'true' ? labels.yes : labels.no)).join(', ');
  return values.join(', ');
}

function csvCell(value: string): string {
  return /[",;\n\r]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

/** CSV (comma separated, RFC 4180 quoting): name, submitted at, one column per question. */
export function resultsCsv(results: SurveyResults, header: { user: string; submittedAt: string }, labels: { yes: string; no: string }): string {
  const lines = [[header.user, header.submittedAt, ...results.questions.map((q) => q.label)]];
  results.rows.forEach((row, i) => {
    lines.push([row.user.displayName, row.submittedAt, ...results.questions.map((q) => cellText(results, i, q, labels))]);
  });
  return lines.map((cells) => cells.map(csvCell).join(',')).join('\r\n') + '\r\n';
}
