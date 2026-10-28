import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { forkJoin, map } from 'rxjs';

import { ApiErrorCode, QuizKind, QuizOverview, QuizStatus, QuizzesService } from '../../../api';
import { ApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { QuizStatusChipComponent } from '../components/quiz-status-chip.component';
import { QUIZ_ICONS, quizKindOf, quizPhase } from '../quiz-kind';

interface IntroData {
  quiz: QuizOverview;
  /** Status of the chosen mode (practice sessions have their own). */
  status: QuizStatus;
}

function notFound(): ApiErrorView {
  return { status: 404, code: ApiErrorCode.QuizNotFound, message: 'Quiz not found', retryable: false, fieldErrors: [] };
}

/** /quizzes/:kind (Q2): description, rules, timed vs untimed choice, practice mode, start / continue. */
@Component({
  selector: 'fs-quiz-intro-page',
  imports: [...DATA_STATE_IMPORTS, Button, PageHeaderComponent, QuizStatusChipComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="data" skeleton="detail" backLink="/quizzes" data-testid="quiz.intro.state">
      <ng-template [fsData]="data" let-d>
        <div class="narrow">
          <fs-page-header
            [title]="d.quiz.name"
            [eyebrow]="(isPractice() ? 'quiz.intro.practiceEyebrow' : 'quiz.intro.eyebrow') | t"
            [icon]="icons[d.quiz.kind]"
            backLink="/quizzes"
            [backLabel]="'quiz.intro.back' | t"
          />

          @if (d.quiz.description) {
            <p class="lead" data-testid="quiz.intro.description">{{ d.quiz.description }}</p>
          }

          @if (!d.quiz.mayTry) {
            <p class="banner" data-testid="quiz.intro.locked">
              <i class="pi pi-lock" aria-hidden="true"></i>{{ 'quiz.list.locked.' + d.quiz.kind | t }}
            </p>
          } @else {
            @if (isPractice()) {
              <p class="banner banner--info" data-testid="quiz.intro.practiceBanner">
                <i class="pi pi-info-circle" aria-hidden="true"></i>{{ 'quiz.intro.practiceBanner' | t }}
              </p>
            } @else {
              @switch (phase()) {
                @case ('disqualified') {
                  <p class="banner banner--danger" data-testid="quiz.intro.disqualified">
                    <i class="pi pi-ban" aria-hidden="true"></i>{{ 'quiz.intro.disqualified' | t }}
                  </p>
                }
                @case ('wait') {
                  <p class="banner" data-testid="quiz.intro.wait">
                    <i class="pi pi-clock" aria-hidden="true"></i>
                    {{ 'quiz.intro.wait' | t: { days: d.status.currentWaitDays } }}
                  </p>
                }
                @case ('confirmPending') {
                  <p class="banner banner--success" data-testid="quiz.intro.confirmPending">
                    <i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'quiz.intro.confirmPending' | t }}
                  </p>
                }
                @case ('expiring') {
                  <p class="banner" data-testid="quiz.intro.expiring">
                    <i class="pi pi-hourglass" aria-hidden="true"></i>
                    {{ 'quiz.intro.expiring' | t: { days: d.status.expiresInDays } }}
                  </p>
                }
                @case ('passed') {
                  <p class="banner banner--success" data-testid="quiz.intro.passed">
                    <i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'quiz.intro.passed' | t }}
                  </p>
                }
                @case ('failed') {
                  <p class="banner" data-testid="quiz.intro.failed">
                    <i class="pi pi-info-circle" aria-hidden="true"></i>
                    {{ 'quiz.intro.failed' | t: { tries: d.status.triesSinceLastPass } }}
                  </p>
                }
              }
            }

            <section class="fs-card card" data-testid="quiz.intro.rules">
              <h2 class="card__title"><i class="pi pi-list-check" aria-hidden="true"></i>{{ 'quiz.intro.rules' | t }}</h2>
              <ul class="rules">
                <li>{{ 'quiz.intro.ruleFailurePoints' | t: { count: d.quiz.maxFailurePoints } }}</li>
                <li>{{ 'quiz.intro.ruleMultiple' | t }}</li>
                <li>{{ 'quiz.intro.ruleSolution' | t }}</li>
                @if (!isPractice() && isRoleQuiz(d.quiz.kind)) {
                  <li>{{ 'quiz.intro.ruleTries' | t }}</li>
                }
                @if (!isPractice() && d.quiz.kind === 'HYGIENE') {
                  <li>{{ 'quiz.intro.ruleHygiene' | t }}</li>
                }
              </ul>
              @if (!isPractice() && d.status.waitDaysAfterNextFailure !== 0 && phase() !== 'disqualified') {
                <p class="banner" data-testid="quiz.intro.nextFailure">
                  <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
                  @if (d.status.waitDaysAfterNextFailure < 0) {
                    {{ 'quiz.intro.nextFailureDisqualifies' | t }}
                  } @else {
                    {{ 'quiz.intro.nextFailureWait' | t: { days: d.status.waitDaysAfterNextFailure } }}
                  }
                </p>
              }
            </section>

            @if (d.status.running) {
              <section class="fs-card card" data-testid="quiz.intro.running">
                <h2 class="card__title"><i class="pi pi-play" aria-hidden="true"></i>{{ 'quiz.intro.runningTitle' | t }}</h2>
                <p class="muted">
                  {{ 'quiz.intro.runningText' | t: { answered: d.status.running.questionsAnswered, total: d.status.running.questionCount } }}
                </p>
                <div class="actions">
                  <p-button
                    [label]="'quiz.list.continue' | t"
                    icon="pi pi-play"
                    (onClick)="goToSession()"
                    data-testid="quiz.intro.continue"
                  />
                </div>
              </section>
            } @else if (d.status.mayStartNow) {
              <section class="fs-card card" data-testid="quiz.intro.start">
                <h2 class="card__title"><i class="pi pi-sliders-h" aria-hidden="true"></i>{{ 'quiz.intro.mode' | t }}</h2>
                <div class="modes" role="radiogroup" [attr.aria-label]="'quiz.intro.mode' | t">
                  <button
                    type="button"
                    role="radio"
                    class="mode"
                    [class.mode--active]="!timed()"
                    [attr.aria-checked]="!timed()"
                    (click)="timed.set(false)"
                    data-testid="quiz.intro.untimed"
                  >
                    <i class="pi pi-book" aria-hidden="true"></i>
                    <strong>{{ 'quiz.intro.untimed' | t }}</strong>
                    <span>{{ 'quiz.intro.untimedText' | t: { count: d.quiz.questionCountUntimed } }}</span>
                  </button>
                  <button
                    type="button"
                    role="radio"
                    class="mode"
                    [class.mode--active]="timed()"
                    [attr.aria-checked]="timed()"
                    (click)="timed.set(true)"
                    data-testid="quiz.intro.timed"
                  >
                    <i class="pi pi-stopwatch" aria-hidden="true"></i>
                    <strong>{{ 'quiz.intro.timed' | t }}</strong>
                    <span>{{ 'quiz.intro.timedText' | t: { count: d.quiz.questionCountTimed } }}</span>
                  </button>
                </div>
                <div class="actions">
                  <p-button
                    [label]="(isPractice() ? 'quiz.intro.startPractice' : 'quiz.intro.start') | t"
                    icon="pi pi-play"
                    [loading]="start.busy()"
                    (onClick)="start.run()"
                    data-testid="quiz.intro.startButton"
                  />
                </div>
              </section>
            }

            @if (!isPractice() && d.status.lastSessionStatus) {
              <div class="actions">
                <p-button
                  [label]="'quiz.intro.lastResults' | t"
                  icon="pi pi-chart-bar"
                  severity="secondary"
                  [outlined]="true"
                  [routerLink]="['results']"
                  data-testid="quiz.intro.results"
                />
                <fs-quiz-status-chip [status]="d.status" />
              </div>
            }
          }
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../quiz.scss',
  styles: `
    .lead {
      margin: 0;
      font-size: 1.05rem;
      line-height: 1.6;
    }
    .rules {
      display: grid;
      gap: 0.4rem;
      margin: 0;
      padding-left: 1.2rem;
      line-height: 1.5;
    }
    .modes {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr));
      gap: 0.75rem;
    }
    .mode {
      display: grid;
      gap: 0.3rem;
      justify-items: start;
      padding: 0.9rem 1rem;
      border: 2px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .mode .pi {
      color: var(--fs-text-muted);
      font-size: 1.1rem;
    }
    .mode span {
      color: var(--fs-text-muted);
      font-size: 0.88rem;
      line-height: 1.45;
    }
    .mode--active {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
    }
    .mode--active .pi {
      color: var(--fs-primary-strong);
    }
    .mode:focus-visible {
      outline: 2px solid var(--fs-primary);
      outline-offset: 2px;
    }
  `,
})
export class QuizIntroPageComponent {
  private readonly api = inject(QuizzesService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  /** Route `:kind` slug and `?practice=true` (component input binding). */
  readonly kind = input.required<string>();
  readonly practice = input<string | undefined>(undefined);

  protected readonly isPractice = computed(() => this.practice() === 'true');
  protected readonly quizKind = computed(() => quizKindOf(this.kind()) as QuizKind);
  protected readonly timed = signal(false);
  protected readonly icons = QUIZ_ICONS;

  protected readonly data = loadResource<IntroData, { kind: QuizKind; test: boolean; lang: string }>({
    params: () => ({ kind: this.quizKind(), test: this.isPractice(), lang: this.i18n.language() }),
    loader: ({ kind, test }) =>
      forkJoin({
        quizzes: this.api.listQuizzes(),
        status: this.api.getQuizStatus({ kind, test }),
      }).pipe(
        map(({ quizzes, status }) => {
          const quiz = quizzes.find((candidate) => candidate.kind === kind);
          if (!quiz) {
            throw notFound();
          }
          return { quiz, status };
        }),
      ),
  });

  protected readonly phase = computed(() => {
    const status = this.data.data()?.status;
    return status ? quizPhase(status) : 'notStarted';
  });

  protected readonly start = runMutation(
    () =>
      this.api.startQuizSession({
        kind: this.quizKind(),
        startQuizSession: { timed: this.timed(), test: this.isPractice() },
      }),
    { onSuccess: () => this.goToSession() },
  );

  protected isRoleQuiz(kind: QuizKind): boolean {
    return kind === 'FOODSAVER' || kind === 'STORE_MANAGER' || kind === 'AMBASSADOR';
  }

  protected goToSession(): void {
    void this.router.navigate(['/quizzes', this.kind(), 'session'], {
      queryParams: this.isPractice() ? { practice: true } : {},
    });
  }
}
