import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { QuizKind, QuizOverview, QuizSessionStatus, QuizStatus } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { QuizHistoryPageComponent } from './pages/quiz-history-page.component';
import { QuizSessionPageComponent } from './pages/quiz-session-page.component';
import { QuizzesPageComponent } from './pages/quizzes-page.component';
import { localized, quizKindOf, quizPhase, quizSlug } from './quiz-kind';

const STATUS: QuizStatus = { triesSinceLastPass: 0, waitDaysAfterNextFailure: 0, mayStartNow: true, requiresConfirmation: false };

function overview(kind: QuizOverview['kind'], mayTry = true, status: Partial<QuizStatus> = {}): QuizOverview {
  return {
    kind,
    name: kind,
    questionCountTimed: 8,
    questionCountUntimed: 10,
    maxFailurePoints: 3,
    mayTry,
    mayRead: false,
    mayEdit: false,
    status: { ...STATUS, ...status },
  };
}

const problem = (code: string, status: number) => ({ status, code, message: code, retryable: false, traceId: 't' });

describe('quiz helpers', () => {
  it('maps kinds to slugs and back', () => {
    expect(quizSlug(QuizKind.StoreManager)).toBe('store-manager');
    expect(quizKindOf('saving-food')).toBe('SAVING_FOOD');
    expect(quizKindOf('nope')).toBeUndefined();
  });

  it('derives the status phase in priority order', () => {
    expect(quizPhase(STATUS)).toBe('notStarted');
    expect(quizPhase({ ...STATUS, running: { questionCount: 10, questionsAnswered: 2, timed: false } })).toBe('running');
    expect(quizPhase({ ...STATUS, currentWaitDays: -1, lastSessionStatus: QuizSessionStatus.Failed })).toBe('disqualified');
    expect(quizPhase({ ...STATUS, currentWaitDays: 30, lastSessionStatus: QuizSessionStatus.Failed })).toBe('wait');
    expect(quizPhase({ ...STATUS, requiresConfirmation: true, passedAt: '2026-11-01T10:00:00Z', lastSessionStatus: QuizSessionStatus.Passed })).toBe('confirmPending');
    expect(quizPhase({ ...STATUS, passedAt: '2026-01-06T19:20:00Z', expiresInDays: 66, lastSessionStatus: QuizSessionStatus.Passed })).toBe('expiring');
    expect(quizPhase({ ...STATUS, passedAt: '2026-01-06T19:20:00Z', lastSessionStatus: QuizSessionStatus.Passed })).toBe('passed');
    expect(quizPhase({ ...STATUS, lastSessionStatus: QuizSessionStatus.Failed, triesSinceLastPass: 1 })).toBe('failed');
  });

  it('localizes with German and then any language as fallback', () => {
    expect(localized({ de: 'Hallo', fr: 'Salut' }, 'fr')).toBe('Salut');
    expect(localized({ de: 'Hallo' }, 'it')).toBe('Hallo');
    expect(localized({ en: 'Hi' }, 'it')).toBe('Hi');
    expect(localized(undefined, 'de')).toBe('');
  });
});

describe('quiz pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const qa = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'quizzes', component: QuizzesPageComponent },
            { path: 'quizzes/history', component: QuizHistoryPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('quizzes: loading, then cards with status, locked quizzes without a start button', async () => {
    await harness.navigateByUrl('/quizzes');
    expect(q('[data-testid="quiz.list.state"]')?.getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/quizzes').flush([
      overview(QuizKind.Foodsaver, true, { lastSessionStatus: QuizSessionStatus.Failed, triesSinceLastPass: 1 }),
      overview(QuizKind.StoreManager, false),
    ]);
    await settle();
    expect(qa('[data-testid="quiz.list.card"]').length).toBe(2);
    expect(q('[data-kind="FOODSAVER"] [data-testid="quiz.list.status"]')?.getAttribute('data-phase')).toBe('failed');
    expect(q('[data-kind="STORE_MANAGER"] [data-testid="quiz.list.locked"]')).not.toBeNull();
    expect(q('[data-kind="STORE_MANAGER"] [data-testid="quiz.list.open"]')).toBeNull();
  });

  it('quizzes: a server error shows the error state, retry reloads', async () => {
    await harness.navigateByUrl('/quizzes');
    http.expectOne('/api/quizzes').flush(problem('VALIDATION_FAILED', 400), { status: 400, statusText: 'Bad Request' });
    await settle();
    expect(q('[data-testid="quiz.list.state"]')?.getAttribute('data-state')).toBe('error');
    (q('[data-testid="quiz.list.state.retry"] button') as HTMLButtonElement).click();
    await settle();
    http.expectOne('/api/quizzes').flush([overview(QuizKind.Hygiene)]);
    await settle();
    expect(q('[data-testid="quiz.list.state"]')?.getAttribute('data-state')).toBe('success');
  });

  it('history: empty state for a user without sessions', async () => {
    await harness.navigateByUrl('/quizzes/history');
    http.expectOne('/api/users/me/quiz-sessions').flush([]);
    await settle();
    expect(q('[data-testid="quiz.history.state"]')?.getAttribute('data-state')).toBe('empty');
  });
});

describe('quiz session page', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  async function create() {
    const fixture = TestBed.createComponent(QuizSessionPageComponent);
    fixture.componentRef.setInput('kind', 'hygiene');
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, q: (s: string) => el.querySelector(s) };
  }

  it('no running session → empty state', async () => {
    const { fixture, q } = await create();
    http
      .expectOne((req) => req.url === '/api/quizzes/HYGIENE/sessions/current/question')
      .flush(problem('NO_RUNNING_SESSION', 409), { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(q('[data-testid="quiz.session.state"]')?.getAttribute('data-state')).toBe('empty');
  });

  it('answers the question with the selected ids and shows the solution', async () => {
    const { fixture, el, q } = await create();
    http.expectOne((req) => req.url === '/api/quizzes/HYGIENE/sessions/current/question').flush({
      index: 2,
      total: 5,
      questionAgeSeconds: 5,
      timedOut: false,
      question: { id: 'q1', text: 'Frage?', durationSeconds: 60, answers: [{ id: 'a1', text: 'Ja' }, { id: 'a2', text: 'Nein' }] },
    });
    await fixture.whenStable();
    expect(q('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('2');
    expect(q('[data-testid="quiz.session.countdown"]')?.textContent).toContain('0:55');
    (el.querySelector('[data-answer="a1"]') as HTMLInputElement).click();
    (q('[data-testid="quiz.session.submit"] button') as HTMLButtonElement).click();
    const answer = http.expectOne((req) => req.url === '/api/quizzes/HYGIENE/sessions/current/answer');
    expect(answer.request.body).toEqual({ answerIds: ['a1'] });
    answer.flush({
      timedOut: false,
      finished: false,
      failurePoints: 0,
      solution: [
        { id: 'a1', text: 'Ja', rating: 'CORRECT', selected: true, explanation: 'Weil.' },
        { id: 'a2', text: 'Nein', rating: 'WRONG', selected: false },
      ],
    });
    await fixture.whenStable();
    expect(el.querySelectorAll('[data-testid="quiz.solution.answer"]').length).toBe(2);
    expect(q('[data-testid="quiz.session.next"]')).not.toBeNull();
  });

  it('timed out questions disable the answers and submit an empty selection', async () => {
    const { fixture, el, q } = await create();
    http.expectOne((req) => req.url === '/api/quizzes/HYGIENE/sessions/current/question').flush({
      index: 0,
      total: 5,
      questionAgeSeconds: 90,
      timedOut: true,
      question: { id: 'q1', text: 'Frage?', durationSeconds: 60, answers: [{ id: 'a1', text: 'Ja' }] },
    });
    await fixture.whenStable();
    expect(q('[data-testid="quiz.session.timedOut"]')).not.toBeNull();
    expect((el.querySelector('[data-answer="a1"]') as HTMLInputElement).disabled).toBe(true);
    (q('[data-testid="quiz.session.submit"] button') as HTMLButtonElement).click();
    const answer = http.expectOne((req) => req.url === '/api/quizzes/HYGIENE/sessions/current/answer');
    expect(answer.request.body).toEqual({ answerIds: [] });
    answer.flush({ timedOut: true, finished: true, failurePoints: 2, solution: [] });
  });
});
