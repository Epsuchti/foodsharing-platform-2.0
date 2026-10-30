import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { catchError, of, throwError } from 'rxjs';

import { ActiveQuestion, AnswerResult, QuizKind, QuizzesService } from '../../../api';
import { toApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { QuizSolutionListComponent } from '../components/quiz-solution-list.component';
import { quizKindOf } from '../quiz-kind';

/**
 * /quizzes/:kind/session (Q2): one question at a time with multi-select answers, a countdown for timed sessions,
 * then the solution with explanations and the wiki link. The countdown starts from the server's
 * `questionAgeSeconds` and only counts elapsed seconds locally — the browser clock is never compared with the
 * server's; the server decides whether an answer was too late.
 */
@Component({
  selector: 'fs-quiz-session-page',
  imports: [...DATA_STATE_IMPORTS, Button, QuizSolutionListComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="narrow">
      <fs-data-state
        [state]="question"
        skeleton="card"
        emptyIcon="pi pi-check-circle"
        [emptyTitle]="'quiz.session.noSession' | t"
        [emptyText]="'quiz.session.noSessionText' | t"
        [emptyActionLabel]="'quiz.session.toQuiz' | t"
        (emptyAction)="toIntro()"
        backLink="/quizzes"
        data-testid="quiz.session.state"
      >
        <ng-template [fsData]="question">
<div class="stack">
          @if (question.data(); as q) {
          <div class="top">
            <span class="progress-label" data-testid="quiz.session.progress">
              {{ 'quiz.session.progress' | t: { index: q.index + 1, total: q.total } }}
            </span>
            @if (isPractice()) {
              <span class="tag">{{ 'quiz.session.practice' | t }}</span>
            }
            @if (remaining() !== null && !result()) {
              <span
                class="countdown"
                [class.countdown--low]="(remaining() ?? 0) <= 10"
                role="timer"
                [attr.aria-label]="'quiz.session.remaining' | t: { seconds: remaining() }"
                data-testid="quiz.session.countdown"
              >
                <i class="pi pi-stopwatch" aria-hidden="true"></i>{{ clock() }}
              </span>
            }
          </div>
          <div
            class="bar"
            role="progressbar"
            [attr.aria-label]="'quiz.session.progress' | t: { index: q.index + 1, total: q.total }"
            [attr.aria-valuenow]="q.index + (result() ? 1 : 0)"
            aria-valuemin="0"
            [attr.aria-valuemax]="q.total"
          >
            <span [style.width.%]="((q.index + (result() ? 1 : 0)) / q.total) * 100"></span>
          </div>

          <section class="fs-card question" data-testid="quiz.session.question" [attr.data-question]="q.question.id">
            <h1 class="question__text">{{ q.question.text }}</h1>
            @if (!result()) {
              @if (timedOut()) {
                <p class="banner banner--danger" data-testid="quiz.session.timedOut">
                  <i class="pi pi-clock" aria-hidden="true"></i>{{ 'quiz.session.timedOut' | t }}
                </p>
              } @else {
                <p class="muted">{{ 'quiz.session.chooseAll' | t }}</p>
              }
              <ul class="options">
                @for (answer of q.question.answers; track answer.id) {
                  <li>
                    <label class="option" [class.option--checked]="selected().has(answer.id)" [class.option--disabled]="timedOut()">
                      <input
                        type="checkbox"
                        [checked]="selected().has(answer.id)"
                        [disabled]="timedOut()"
                        (change)="toggle(answer.id)"
                        [attr.data-answer]="answer.id"
                        data-testid="quiz.session.answer"
                      />
                      <span>{{ answer.text }}</span>
                    </label>
                  </li>
                }
              </ul>
              <div class="actions">
                <p-button
                  [label]="'quiz.session.submit' | t"
                  icon="pi pi-check"
                  [loading]="answer.busy()"
                  (onClick)="answer.run()"
                  data-testid="quiz.session.submit"
                />
                @if (!timedOut() && selected().size === 0) {
                  <span class="muted">{{ 'quiz.session.noneHint' | t }}</span>
                }
              </div>
            } @else {
              @let r = result()!;
              @if (r.timedOut) {
                <p class="banner banner--danger" data-testid="quiz.session.answeredLate">
                  <i class="pi pi-clock" aria-hidden="true"></i>{{ 'quiz.session.answeredLate' | t }}
                </p>
              }
              <p
                [class]="'banner ' + ((r.failurePoints ?? 0) > 0 ? 'banner--danger' : 'banner--success')"
                data-testid="quiz.session.verdict"
              >
                @if ((r.failurePoints ?? 0) > 0) {
                  <i class="pi pi-times-circle" aria-hidden="true"></i>
                  {{ 'quiz.session.failurePoints' | t: { points: r.failurePoints } }}
                } @else {
                  <i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'quiz.session.correct' | t }}
                }
              </p>
              <fs-quiz-solution-list [answers]="r.solution" />
              @if (q.question.wikiLink) {
                <a class="wiki" [href]="q.question.wikiLink" target="_blank" rel="noopener" data-testid="quiz.session.wiki">
                  <i class="pi pi-external-link" aria-hidden="true"></i>{{ 'quiz.session.wiki' | t }}
                </a>
              }
              <div class="actions">
                @if (r.finished) {
                  <p-button
                    [label]="'quiz.session.toResults' | t"
                    icon="pi pi-chart-bar"
                    [routerLink]="['/quizzes', kind(), 'results']"
                    [queryParams]="isPractice() ? { practice: true } : {}"
                    data-testid="quiz.session.results"
                  />
                } @else {
                  <p-button
                    [label]="'quiz.session.next' | t"
                    icon="pi pi-arrow-right"
                    iconPos="right"
                    (onClick)="next()"
                    data-testid="quiz.session.next"
                  />
                }
              </div>
            }
          </section>
          }
        </div>
</ng-template>
      </fs-data-state>
    </div>
  `,
  styleUrl: '../quiz.scss',
  styles: `
    .top {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.65rem;
    }
    .progress-label {
      font-weight: 700;
      color: var(--fs-text-muted);
    }
    .tag {
      padding: 0.1rem 0.55rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.75rem;
      font-weight: 650;
    }
    .countdown {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      margin-left: auto;
      padding: 0.25rem 0.7rem;
      border-radius: 999px;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-variant-numeric: tabular-nums;
      font-weight: 700;
    }
    .countdown--low {
      background: var(--fs-danger-soft);
      color: var(--fs-danger);
    }
    .bar {
      height: 0.4rem;
      overflow: hidden;
      border-radius: 999px;
      background: var(--fs-surface-muted);
    }
    .bar span {
      display: block;
      height: 100%;
      border-radius: inherit;
      background: var(--fs-primary);
      transition: width 0.3s ease;
    }
    .question {
      display: grid;
      gap: 1rem;
    }
    .question__text {
      margin: 0;
      font-size: clamp(1.15rem, 1rem + 0.6vw, 1.4rem);
      line-height: 1.4;
    }
    .options {
      display: grid;
      gap: 0.5rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .option {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.8rem 0.9rem;
      border: 2px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      line-height: 1.45;
      cursor: pointer;
    }
    .option input {
      flex: none;
      width: 1.15rem;
      height: 1.15rem;
      margin: 0.1rem 0 0;
      accent-color: var(--fs-primary);
    }
    .option--checked {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
    }
    .option--disabled {
      opacity: 0.6;
      cursor: default;
    }
    .wiki {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      width: fit-content;
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
  `,
})
export class QuizSessionPageComponent {
  private readonly api = inject(QuizzesService);
  private readonly router = inject(Router);

  readonly kind = input.required<string>();
  readonly practice = input<string | undefined>(undefined);

  protected readonly isPractice = computed(() => this.practice() === 'true');
  private readonly quizKind = computed(() => quizKindOf(this.kind()) as QuizKind);
  private readonly round = signal(0);

  /** `null` when no session is running (409 NO_RUNNING_SESSION) → empty state with a way back. */
  protected readonly question = loadResource<ActiveQuestion | null, { kind: QuizKind; test: boolean; round: number }>({
    params: () => ({ kind: this.quizKind(), test: this.isPractice(), round: this.round() }),
    loader: ({ kind, test }) =>
      this.api.getNextQuestion({ kind, test }).pipe(
        catchError((error: unknown) =>
          toApiErrorView(error).code === 'NO_RUNNING_SESSION' ? of(null) : throwError(() => error),
        ),
      ),
    isEmpty: (value) => value === null,
    keepDataOnError: false,
  });

  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly result = signal<AnswerResult | undefined>(undefined);

  /** Seconds counted locally since the question arrived. */
  private readonly elapsed = signal(0);

  protected readonly remaining = computed(() => {
    const q = this.question.data();
    const duration = q?.question.durationSeconds;
    if (!q || !duration) {
      return null;
    }
    return Math.max(0, duration - q.questionAgeSeconds - this.elapsed());
  });

  protected readonly timedOut = computed(() => !!this.question.data()?.timedOut || this.remaining() === 0);

  protected readonly clock = computed(() => {
    const seconds = this.remaining() ?? 0;
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  });

  protected readonly answer = runMutation(
    () =>
      this.api.answerQuestion({
        kind: this.quizKind(),
        test: this.isPractice(),
        selectedAnswers: { answerIds: this.timedOut() ? [] : [...this.selected()] },
      }),
    { onSuccess: (result) => this.result.set(result) },
  );

  constructor() {
    const timer = setInterval(() => this.elapsed.update((value) => value + 1), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
    // a new question resets the local state
    effect(() => {
      this.question.data();
      this.elapsed.set(0);
      this.selected.set(new Set());
      this.result.set(undefined);
    });
  }

  protected toggle(id: string): void {
    const next = new Set(this.selected());
    if (!next.delete(id)) {
      next.add(id);
    }
    this.selected.set(next);
  }

  protected next(): void {
    this.round.update((value) => value + 1);
  }

  protected toIntro(): void {
    void this.router.navigate(['/quizzes', this.kind()], {
      queryParams: this.isPractice() ? { practice: true } : {},
    });
  }
}
