import { Role, SurveyQuestion, SurveyQuestionType as T, SurveyResults } from '../../api';
import { buildAnswers, cleanQuestion, missingRequired, resultsCsv, surveyErrors, surveyLink } from './surveys.resource';

const q = (id: string, type: T, required = false, options: string[] = []): SurveyQuestion => ({ id, type, label: `L${id}`, required, options });

describe('survey helpers', () => {
  it('validates the editor like SurveyService', () => {
    expect(surveyErrors('', [q('1', T.Text)])).toBe('title');
    expect(surveyErrors('T', [])).toBe('noQuestions');
    expect(surveyErrors('T', [{ ...q('1', T.Text), label: ' ' }])).toBe('label');
    expect(surveyErrors('T', [q('1', T.Store), q('2', T.Store)])).toBe('store');
    expect(surveyErrors('T', [q('1', T.SingleChoice, false, ['a'])])).toBe('options');
    expect(surveyErrors('T', [q('1', T.MultipleChoice, false, ['a', 'a '])])).toBe('options');
    expect(surveyErrors('T', [q('1', T.SingleChoice, false, ['a', 'b']), q('2', T.Boolean), q('3', T.Store)])).toBeNull();
  });

  it('cleans questions: options only for choices, STORE required', () => {
    expect(cleanQuestion({ type: T.Text, label: ' x ', required: false, options: ['a'] })).toEqual({ type: T.Text, label: 'x', required: false, options: [] });
    expect(cleanQuestion(q('9', T.Store)).required).toBe(true);
  });

  it('builds answers without STORE and checks required questions', () => {
    const qs = [q('1', T.Store, true), q('2', T.Text, true), q('3', T.MultipleChoice, false, ['a', 'b'])];
    expect(missingRequired(qs, { '2': [' '] })).toBe('2');
    expect(missingRequired(qs, { '2': ['hi'] })).toBeNull();
    expect(buildAnswers(qs, { '1': ['x'], '2': [' hi '], '3': [] })).toEqual([{ questionId: '2', values: ['hi'] }]);
  });

  it('builds links and CSV', () => {
    expect(surveyLink('https://x', 's1')).toBe('https://x/survey/s1');
    expect(surveyLink('https://x', 's1', 'st')).toBe('https://x/survey/s1?storeId=st');
    const results: SurveyResults = {
      responseCount: 1,
      questions: [q('1', T.Store), q('2', T.Boolean), q('3', T.Text)],
      rows: [
        {
          user: { id: 'u', displayName: 'Ann', role: Role.Foodsaver, verified: true },
          submittedAt: '2026-11-02T09:00:00Z',
          store: { id: 'st', name: 'Coop' },
          answers: { '2': ['true'], '3': ['a, "b"'] },
        },
      ],
    };
    expect(resultsCsv(results, { user: 'Name', submittedAt: 'At' }, { yes: 'Yes', no: 'No' })).toBe(
      'Name,At,L1,L2,L3\r\nAnn,2026-11-02T09:00:00Z,Coop,Yes,"a, ""b"""\r\n',
    );
  });
});
