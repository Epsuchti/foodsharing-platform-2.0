import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { QuizOverview, QuizzesService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { QuizStatusChipComponent } from '../components/quiz-status-chip.component';
import { QUIZ_ICONS, quizPhase, quizSlug } from '../quiz-kind';

/**
 * /quizzes (Q1): every quiz with the user's status; locked quizzes explain why; practice mode (`?practice=true`) runs
 * sessions that never change roles or try counters.
 */
@Component({
  selector: 'fs-quizzes-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    FormsModule,
    PageHeaderComponent,
    QuizStatusChipComponent,
    RouterLink,
    ToggleSwitch,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'quiz.list.title' | t" [subtitle]="'quiz.list.subtitle' | t" icon="pi pi-graduation-cap">
      <p-button
        [label]="'quiz.list.history' | t"
        icon="pi pi-history"
        severity="secondary"
        [outlined]="true"
        routerLink="/quizzes/history"
        data-testid="quiz.list.history"
      />
    </fs-page-header>

    <label class="practice fs-card" for="quiz-practice">
      <p-toggleswitch
        inputId="quiz-practice"
        [ngModel]="isPractice()"
        (ngModelChange)="setPractice($event)"
        data-testid="quiz.list.practice"
      />
      <span class="practice__text">
        <strong>{{ 'quiz.list.practice' | t }}</strong>
        <span class="muted">{{ 'quiz.list.practiceHint' | t }}</span>
      </span>
    </label>

    <fs-data-state
      [state]="quizzes"
      skeleton="card"
      [skeletonRows]="4"
      [emptyTitle]="'quiz.list.empty' | t"
      data-testid="quiz.list.state"
    >
      <ng-template [fsData]="quizzes">
        <ul class="quizzes">
          @for (quiz of sorted(); track quiz.kind) {
            <li
              class="quiz fs-card"
              [class.quiz--locked]="!quiz.mayTry"
              [attr.data-kind]="quiz.kind"
              data-testid="quiz.list.card"
            >
              <div class="quiz__head">
                <span class="quiz__icon" aria-hidden="true"><i [class]="icons[quiz.kind]"></i></span>
                <div class="quiz__title">
                  <h2 data-testid="quiz.list.name">{{ quiz.name }}</h2>
                  @if (quiz.mayTry && !isPractice()) {
                    <fs-quiz-status-chip [status]="quiz.status" data-testid="quiz.list.status" />
                  }
                </div>
              </div>
              @if (quiz.description) {
                <p class="quiz__text">{{ quiz.description }}</p>
              }
              <ul class="quiz__facts">
                <li>
                  <i class="pi pi-list-check" aria-hidden="true"></i>
                  {{ 'quiz.list.questions' | t: { count: quiz.questionCountUntimed } }}
                </li>
                <li>
                  <i class="pi pi-stopwatch" aria-hidden="true"></i>
                  {{ 'quiz.list.questionsTimed' | t: { count: quiz.questionCountTimed } }}
                </li>
                <li>
                  <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
                  {{ 'quiz.list.maxFailurePoints' | t: { count: quiz.maxFailurePoints } }}
                </li>
              </ul>
              <div class="quiz__actions">
                @if (quiz.mayTry) {
                  <p-button
                    [label]="actionKey(quiz) | t"
                    [icon]="actionIcon(quiz)"
                    [routerLink]="link(quiz)"
                    [queryParams]="isPractice() ? { practice: true } : {}"
                    [severity]="isPractice() ? 'secondary' : undefined"
                    data-testid="quiz.list.open"
                  />
                } @else {
                  <p class="locked" data-testid="quiz.list.locked">
                    <i class="pi pi-lock" aria-hidden="true"></i>{{ 'quiz.list.locked.' + quiz.kind | t }}
                  </p>
                }
                @if (quiz.mayEdit || quiz.mayRead) {
                  <p-button
                    [label]="(quiz.mayEdit ? 'quiz.list.edit' : 'quiz.list.read') | t"
                    [icon]="quiz.mayEdit ? 'pi pi-pencil' : 'pi pi-eye'"
                    [text]="true"
                    severity="secondary"
                    [routerLink]="['/quizzes/edit', slug(quiz)]"
                    data-testid="quiz.list.edit"
                  />
                }
              </div>
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../quiz.scss',
  styles: `
    .practice {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      padding: 0.85rem 1.1rem;
      cursor: pointer;
    }
    .practice__text {
      display: grid;
      gap: 0.1rem;
    }
    .quizzes {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr));
      gap: 1rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .quiz {
      display: grid;
      grid-template-rows: auto auto 1fr auto;
      gap: 0.85rem;
      min-width: 0;
    }
    .quiz--locked {
      background: var(--fs-surface-muted);
      box-shadow: none;
    }
    .quiz__head {
      display: flex;
      align-items: flex-start;
      gap: 0.85rem;
    }
    .quiz__icon {
      display: grid;
      flex: none;
      place-items: center;
      width: 2.75rem;
      height: 2.75rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.2rem;
    }
    .quiz--locked .quiz__icon {
      background: var(--fs-surface);
      color: var(--fs-text-muted);
    }
    .quiz__title {
      display: grid;
      gap: 0.35rem;
      min-width: 0;
    }
    .quiz__title h2 {
      margin: 0;
      font-size: 1.1rem;
      line-height: 1.3;
    }
    .quiz__text {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.92rem;
      line-height: 1.5;
    }
    .quiz__facts {
      display: flex;
      flex-wrap: wrap;
      align-content: flex-start;
      gap: 0.35rem 1rem;
      margin: 0;
      padding: 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
      list-style: none;
    }
    .quiz__facts .pi {
      margin-right: 0.3rem;
      font-size: 0.8rem;
    }
    .quiz__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .locked {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
  `,
})
export class QuizzesPageComponent {
  private readonly api = inject(QuizzesService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  /** `?practice=true` (component input binding). */
  readonly practice = input<string | undefined>(undefined);
  protected readonly isPractice = computed(() => this.practice() === 'true');

  protected readonly icons = QUIZ_ICONS;

  /** Names and descriptions come in the user's language: reload when it changes. */
  protected readonly quizzes = loadResource<QuizOverview[], string>({
    params: () => this.i18n.language(),
    loader: () => this.api.listQuizzes(),
  });

  /** Quizzes the user may take first, the locked ones last (stable within each group). */
  protected readonly sorted = computed(() =>
    [...(this.quizzes.data() ?? [])].sort((a, b) => Number(b.mayTry) - Number(a.mayTry)),
  );

  protected setPractice(value: boolean): void {
    void this.router.navigate([], { queryParams: { practice: value ? true : null }, replaceUrl: true });
  }

  protected slug(quiz: QuizOverview): string {
    return quizSlug(quiz.kind);
  }

  protected link(quiz: QuizOverview): string[] {
    if (this.isPractice()) {
      return ['/quizzes', this.slug(quiz)];
    }
    const phase = quizPhase(quiz.status);
    if (phase === 'running') {
      return ['/quizzes', this.slug(quiz), 'session'];
    }
    if (phase === 'confirmPending') {
      return ['/quizzes', this.slug(quiz), 'results'];
    }
    return ['/quizzes', this.slug(quiz)];
  }

  protected actionKey(quiz: QuizOverview): string {
    if (this.isPractice()) {
      return 'quiz.list.practiceAction';
    }
    switch (quizPhase(quiz.status)) {
      case 'running':
        return 'quiz.list.continue';
      case 'confirmPending':
        return 'quiz.list.confirm';
      case 'notStarted':
        return 'quiz.list.start';
      case 'failed':
        return quiz.status.mayStartNow ? 'quiz.list.retry' : 'quiz.list.details';
      default:
        return 'quiz.list.details';
    }
  }

  protected actionIcon(quiz: QuizOverview): string {
    const phase = quizPhase(quiz.status);
    if (phase === 'failed' && quiz.status.mayStartNow) {
      return 'pi pi-replay';
    }
    return this.isPractice() || phase === 'notStarted' || phase === 'running' ? 'pi pi-play' : 'pi pi-arrow-right';
  }
}
