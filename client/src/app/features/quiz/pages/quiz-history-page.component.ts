import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { QuizSessionView, QuizzesService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { QUIZ_ICONS } from '../quiz-kind';

/**
 * Quiz session history (A8): `/quizzes/history` for the user, `/quizzes/history/:userId` for ambassadors, user
 * managers and quiz editors (the server decides; 403 → forbidden state). Practice sessions are marked.
 */
@Component({
  selector: 'fs-quiz-history-page',
  imports: [...DATA_STATE_IMPORTS, DateTimePipe, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="(userId() ? 'quiz.history.userTitle' : 'quiz.history.title') | t"
      [subtitle]="'quiz.history.subtitle' | t"
      icon="pi pi-history"
      [backLink]="userId() ? ['/users', userId()] : '/quizzes'"
      [backLabel]="(userId() ? 'quiz.history.backToUser' : 'quiz.history.backToQuizzes') | t"
    />
    <fs-data-state
      [state]="sessions"
      skeleton="table"
      emptyIcon="pi pi-history"
      [emptyTitle]="'quiz.history.empty' | t"
      [emptyText]="'quiz.history.emptyText' | t"
      [askWho]="'quiz.history.askWho' | t"
      data-testid="quiz.history.state"
    >
      <ng-template [fsData]="sessions">
        <ol class="sessions">
          @for (session of sorted(); track session.id) {
            <li class="fs-card row" [attr.data-status]="session.status" [attr.data-kind]="session.kind" data-testid="quiz.history.row">
              <span class="row__icon" [class]="'row__icon row__icon--' + session.status" aria-hidden="true">
                <i [class]="icons[session.kind]"></i>
              </span>
              <span class="row__main">
                <strong>{{ 'quiz.kind.' + session.kind | t }}</strong>
                <span class="muted">
                  {{ session.startedAt | fsDate: 'dateTime' }}
                  · {{ (session.timed ? 'quiz.history.timed' : 'quiz.history.untimed') | t }}
                  · {{ 'quiz.history.questions' | t: { count: session.questionCount } }}
                </span>
              </span>
              <span class="row__tags">
                @if (session.test) {
                  <span class="tag" data-testid="quiz.history.practice">{{ 'quiz.session.practice' | t }}</span>
                }
                <span [class]="'tag tag--' + session.status" data-testid="quiz.history.status">
                  {{ 'quiz.status.' + session.status | t }}
                </span>
                @if (session.status !== 'RUNNING') {
                  <span class="points">
                    {{ 'quiz.results.points' | t: { points: session.failurePoints, max: session.maxFailurePoints } }}
                  </span>
                }
              </span>
            </li>
          }
        </ol>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../quiz.scss',
  styles: `
    .sessions {
      display: grid;
      gap: 0.6rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .row {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      align-items: center;
      gap: 0.5rem 0.9rem;
      padding: 0.85rem 1rem;
    }
    @media (min-width: 48rem) {
      .row {
        grid-template-columns: auto minmax(0, 1fr) auto;
      }
    }
    .row__icon {
      display: grid;
      place-items: center;
      width: 2.4rem;
      height: 2.4rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
    }
    .row__icon--PASSED {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .row__icon--FAILED {
      background: var(--fs-danger-soft);
      color: var(--fs-danger);
    }
    .row__main {
      display: grid;
      gap: 0.15rem;
      min-width: 0;
    }
    .row__tags {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
      grid-column: 2;
    }
    @media (min-width: 48rem) {
      .row__tags {
        grid-column: 3;
        justify-content: flex-end;
      }
    }
    .tag,
    .points {
      padding: 0.12rem 0.55rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.78rem;
      font-weight: 650;
      white-space: nowrap;
    }
    .points {
      background: none;
      color: var(--fs-text-muted);
    }
    .tag--PASSED {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .tag--FAILED {
      background: var(--fs-danger-soft);
      color: var(--fs-danger);
    }
    .tag--RUNNING {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
  `,
})
export class QuizHistoryPageComponent {
  private readonly api = inject(QuizzesService);

  /** `:userId` (component input binding); absent for the own history. */
  readonly userId = input<string | undefined>(undefined);
  protected readonly icons = QUIZ_ICONS;

  protected readonly sessions = loadResource<QuizSessionView[], { userId: string | undefined }>({
    params: () => ({ userId: this.userId() }),
    loader: ({ userId }) => (userId ? this.api.listUserQuizSessions({ userId }) : this.api.listMyQuizSessions()),
  });

  protected readonly sorted = computed(() =>
    [...(this.sessions.data() ?? [])].sort((a, b) => b.startedAt.localeCompare(a.startedAt)),
  );
}
