import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Drawer } from '@openng/optimus-ui/drawer';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';

import { QuestionComment, QuestionDetail, QuizDetail, QuizKind, QuizzesService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { LanguageTabsEditorComponent, TranslatedText } from '../../../shared/language-tabs-editor/language-tabs-editor.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { QuestionDialogComponent } from '../components/question-dialog.component';
import { QUIZ_ICONS, localized, quizKindOf } from '../quiz-kind';

/**
 * /quizzes/edit/:kind (Q3): quiz settings (names, descriptions, max failure points, question counts), the question
 * list with mandatory / duration / failure points, the question dialog with answers, comments per question in a
 * drawer, delete with confirmation. Readers (`mayRead` without `mayEdit`) see everything read-only.
 */
@Component({
  selector: 'fs-quiz-editor-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Drawer,
    FormsModule,
    InputNumber,
    InputText,
    LanguageTabsEditorComponent,
    PageHeaderComponent,
    QuestionDialogComponent,
    Textarea,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state
      [state]="quiz"
      skeleton="form"
      backLink="/quizzes/edit"
      [askWho]="'quiz.editor.askWho' | t"
      data-testid="quiz.editor.state"
    >
      <ng-template [fsData]="quiz" let-q>
<div class="stack">
        <fs-page-header
          [title]="name(q)"
          [eyebrow]="'quiz.editor.eyebrow' | t"
          [icon]="icons[q.kind]"
          backLink="/quizzes/edit"
          [backLabel]="'quiz.editor.back' | t"
        />

        <section class="fs-card card" data-testid="quiz.editor.settings">
          <h2 class="card__title"><i class="pi pi-cog" aria-hidden="true"></i>{{ 'quiz.editor.settings' | t }}</h2>
          <fieldset class="settings" [disabled]="!q.mayEdit">
            <fs-language-tabs-editor [(ngModel)]="names" [label]="'quiz.editor.name' | t" [disabled]="!q.mayEdit" testId="quiz.editor.names" />
            <fs-language-tabs-editor
              [(ngModel)]="descriptions"
              [label]="'quiz.editor.description' | t"
              [multiline]="true"
              [rows]="3"
              [requiredLanguages]="[]"
              [disabled]="!q.mayEdit"
              testId="quiz.editor.descriptions"
            />
            <div class="numbers">
              <div class="field">
                <label for="qe-max">{{ 'quiz.editor.maxFailurePoints' | t }}</label>
                <p-inputnumber inputId="qe-max" [(ngModel)]="maxFailurePoints" [min]="0" [max]="100" [showButtons]="true" [disabled]="!q.mayEdit" data-testid="quiz.editor.maxFailurePoints" />
              </div>
              <div class="field">
                <label for="qe-untimed">{{ 'quiz.editor.countUntimed' | t }}</label>
                <p-inputnumber inputId="qe-untimed" [(ngModel)]="countUntimed" [min]="0" [max]="100" [showButtons]="true" [disabled]="!q.mayEdit" data-testid="quiz.editor.countUntimed" />
              </div>
              <div class="field">
                <label for="qe-timed">{{ 'quiz.editor.countTimed' | t }}</label>
                <p-inputnumber inputId="qe-timed" [(ngModel)]="countTimed" [min]="0" [max]="100" [showButtons]="true" [disabled]="!q.mayEdit" data-testid="quiz.editor.countTimed" />
              </div>
            </div>
            <p class="muted">{{ 'quiz.editor.pool' | t: { count: q.questionCount } }}</p>
            @if (q.mayEdit) {
              <div class="field">
                <label for="qe-reason">{{ 'quiz.editor.reason' | t }}</label>
                <input pInputText id="qe-reason" [(ngModel)]="reason" maxlength="500" data-testid="quiz.editor.reason" />
              </div>
              <div class="actions">
                <p-button
                  [label]="'common.save' | t"
                  icon="pi pi-check"
                  [disabled]="!names().de?.trim()"
                  [loading]="saveSettings.busy()"
                  (onClick)="saveSettings.run()"
                  data-testid="quiz.editor.saveSettings"
                />
              </div>
            }
          </fieldset>
        </section>

        <section class="fs-card card" data-testid="quiz.editor.questionsCard">
          <div class="card__head">
            <h2 class="card__title"><i class="pi pi-list" aria-hidden="true"></i>{{ 'quiz.editor.questions' | t }}</h2>
            @if (q.mayEdit) {
              <p-button [label]="'quiz.editor.newQuestion' | t" icon="pi pi-plus" (onClick)="openQuestion(undefined)" data-testid="quiz.editor.addQuestion" />
            }
          </div>
          <fs-data-state
            [state]="questions"
            skeleton="table"
            [compact]="true"
            [emptyTitle]="'quiz.editor.noQuestions' | t"
            data-testid="quiz.editor.questions"
          >
            <ng-template [fsData]="questions">
              <ol class="questions">
                @for (question of questions.data(); track question.id; let i = $index) {
                  <li class="question" [attr.data-question]="question.id" data-testid="quiz.editor.question">
                    <span class="question__no">{{ i + 1 }}</span>
                    <div class="question__main">
                      <span class="question__text">{{ text(question.texts) }}</span>
                      <span class="badges">
                        @if (question.mandatory) {
                          <span class="badge badge--strong" data-testid="quiz.editor.mandatoryBadge">{{ 'quiz.editor.mandatory' | t }}</span>
                        }
                        <span class="badge">{{ 'quiz.editor.fpShort' | t: { count: question.failurePoints } }}</span>
                        @if (question.durationSeconds) {
                          <span class="badge"><i class="pi pi-stopwatch" aria-hidden="true"></i> {{ question.durationSeconds }} s</span>
                        }
                        <span class="badge">{{ 'quiz.editor.answerCount' | t: { count: question.answers.length } }}</span>
                      </span>
                    </div>
                    <div class="question__actions">
                      <p-button
                        icon="pi pi-comments"
                        [label]="'' + question.commentCount"
                        [text]="true"
                        severity="secondary"
                        [ariaLabel]="'quiz.editor.comments' | t"
                        (onClick)="openComments(question)"
                        data-testid="quiz.editor.openComments"
                      />
                      @if (q.mayEdit) {
                        <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" severity="secondary" [ariaLabel]="'quiz.editor.editQuestion' | t" (onClick)="openQuestion(question)" data-testid="quiz.editor.editQuestion" />
                        <p-button icon="pi pi-trash" [text]="true" [rounded]="true" severity="danger" [ariaLabel]="'quiz.editor.deleteQuestion' | t" (onClick)="askDelete(question)" data-testid="quiz.editor.deleteQuestion" />
                      }
                    </div>
                  </li>
                }
              </ol>
            </ng-template>
          </fs-data-state>
        </section>

        <fs-quiz-question-dialog
          [kind]="q.kind"
          [question]="editing()"
          [(visible)]="dialogOpen"
          (changed)="onQuestionChanged($event)"
        />

        <p-drawer
          [(visible)]="commentsOpen"
          position="right"
          [header]="'quiz.editor.comments' | t"
          [style]="{ width: 'min(28rem, 100vw)' }"
          data-testid="quiz.editor.commentsDrawer"
        >
          @if (commentQuestion(); as cq) {
            <p class="muted drawer-question">{{ text(cq.texts) }}</p>
            <fs-data-state [state]="comments" skeleton="list" [compact]="true" [emptyTitle]="'quiz.editor.noComments' | t" data-testid="quiz.editor.comments">
              <ng-template [fsData]="comments">
                <ul class="comments">
                  @for (comment of comments.data(); track comment.id) {
                    <li class="comment" data-testid="quiz.editor.comment">
                      <fs-user-chip [name]="comment.author.displayName" [userId]="comment.author.id" [avatarUrl]="comment.author.avatarUrl" [role]="comment.author.role" [subtitle]="comment.createdAt | fsDate: 'dateTime'" />
                      <p>{{ comment.text }}</p>
                    </li>
                  }
                </ul>
              </ng-template>
            </fs-data-state>
            <div class="comment-form">
              <label for="qe-comment" class="fs-visually-hidden">{{ 'quiz.editor.newComment' | t }}</label>
              <textarea pTextarea id="qe-comment" rows="3" [(ngModel)]="commentText" maxlength="4000" [placeholder]="'quiz.editor.newComment' | t" data-testid="quiz.editor.commentText"></textarea>
              <p-button
                [label]="'quiz.editor.addComment' | t"
                icon="pi pi-send"
                [disabled]="!commentText().trim()"
                [loading]="addComment.busy()"
                (onClick)="addComment.run()"
                data-testid="quiz.editor.addComment"
              />
            </div>
          }
        </p-drawer>
      </div>
</ng-template>
    </fs-data-state>
  `,
  styleUrl: '../quiz.scss',
  styles: `
    .card__head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
    }
    .settings {
      display: grid;
      gap: 1rem;
      min-width: 0;
      margin: 0;
      padding: 0;
      border: 0;
    }
    .numbers {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 12rem), 1fr));
      gap: 1rem;
    }
    .field {
      display: grid;
      gap: 0.35rem;
    }
    .field label {
      font-size: 0.9rem;
      font-weight: 650;
    }
    .questions {
      display: grid;
      gap: 0.5rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .question {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr);
      gap: 0.5rem 0.85rem;
      padding: 0.75rem 0.85rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
    }
    @media (min-width: 48rem) {
      .question {
        grid-template-columns: auto minmax(0, 1fr) auto;
        align-items: center;
      }
    }
    .question__no {
      display: grid;
      place-items: center;
      width: 1.9rem;
      height: 1.9rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.85rem;
      font-weight: 700;
    }
    .question__main {
      display: grid;
      gap: 0.4rem;
      min-width: 0;
    }
    .question__text {
      font-weight: 600;
      line-height: 1.4;
    }
    .question__actions {
      display: flex;
      justify-content: flex-end;
      grid-column: 2;
      gap: 0.15rem;
    }
    @media (min-width: 48rem) {
      .question__actions {
        grid-column: 3;
      }
    }
    .badges {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .badge {
      padding: 0.08rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
      font-size: 0.75rem;
      font-weight: 650;
    }
    .badge--strong {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .drawer-question {
      margin-bottom: 1rem;
    }
    .comments {
      display: grid;
      gap: 0.85rem;
      margin: 0 0 1rem;
      padding: 0;
      list-style: none;
    }
    .comment p {
      margin: 0.4rem 0 0;
      line-height: 1.5;
      white-space: pre-line;
    }
    .comment-form {
      display: grid;
      gap: 0.6rem;
    }
  `,
})
export class QuizEditorPageComponent {
  private readonly api = inject(QuizzesService);
  private readonly i18n = inject(I18nService);
  private readonly confirm = inject(ConfirmationService);

  readonly kind = input.required<string>();
  protected readonly quizKind = computed(() => quizKindOf(this.kind()) as QuizKind);
  protected readonly icons = QUIZ_ICONS;

  protected readonly quiz = loadResource<QuizDetail, QuizKind>({
    params: () => this.quizKind(),
    loader: (kind) => this.api.getQuiz({ kind }),
  });
  protected readonly questions = loadResource<QuestionDetail[], QuizKind | undefined>({
    params: () => (this.quiz.data() ? this.quizKind() : undefined),
    loader: (kind) => this.api.listQuestions({ kind: kind! }),
  });

  protected readonly names = signal<TranslatedText>({});
  protected readonly descriptions = signal<TranslatedText>({});
  protected readonly maxFailurePoints = signal(0);
  protected readonly countUntimed = signal(0);
  protected readonly countTimed = signal(0);
  protected readonly reason = signal('');

  protected readonly dialogOpen = signal(false);
  protected readonly editingId = signal<string | undefined>(undefined);
  /** The edited question, always the freshest copy from the list (answers change while the dialog is open). */
  protected readonly editing = computed(() =>
    this.questions.data()?.find((question) => question.id === this.editingId()),
  );

  protected readonly commentsOpen = signal(false);
  protected readonly commentQuestion = signal<QuestionDetail | undefined>(undefined);
  protected readonly commentText = signal('');
  protected readonly comments = loadResource<QuestionComment[], { kind: QuizKind; questionId: string }>({
    params: () => {
      const question = this.commentQuestion();
      return question && this.commentsOpen() ? { kind: this.quizKind(), questionId: question.id } : undefined;
    },
    loader: ({ kind, questionId }) => this.api.listQuestionComments({ kind, questionId }),
  });

  constructor() {
    effect(() => {
      const quiz = this.quiz.data();
      if (quiz) {
        this.names.set({ ...quiz.names });
        this.descriptions.set({ ...quiz.descriptions });
        this.maxFailurePoints.set(quiz.maxFailurePoints);
        this.countUntimed.set(quiz.questionCountUntimed);
        this.countTimed.set(quiz.questionCountTimed);
      }
    });
  }

  protected name(quiz: QuizDetail): string {
    return localized(quiz.names, this.i18n.language()) || quiz.kind;
  }

  protected text(texts: Record<string, string>): string {
    return localized(texts, this.i18n.language());
  }

  protected readonly saveSettings = runMutation(
    () =>
      this.api.updateQuiz({
        kind: this.quizKind(),
        quizUpdate: {
          names: this.names() as Record<string, string>,
          descriptions: this.descriptions() as Record<string, string>,
          maxFailurePoints: this.maxFailurePoints(),
          questionCountUntimed: this.countUntimed(),
          questionCountTimed: this.countTimed(),
          reason: this.reason().trim() || undefined,
        },
      }),
    {
      successToastKey: 'quiz.editor.settingsSaved',
      onSuccess: () => {
        this.reason.set('');
        this.quiz.reload();
      },
    },
  );

  protected openQuestion(question: QuestionDetail | undefined): void {
    this.editingId.set(question?.id);
    this.dialogOpen.set(true);
  }

  /** A new question stays open in the dialog so answers can be added right away. */
  protected onQuestionChanged(question: QuestionDetail | undefined): void {
    if (question) {
      this.editingId.set(question.id);
    }
    this.questions.reload();
    this.quiz.reload();
  }

  private readonly deleteQuestion = runMutation(
    (questionId: string) => this.api.deleteQuestion({ kind: this.quizKind(), questionId }),
    {
      successToastKey: 'quiz.editor.questionDeleted',
      onSuccess: () => {
        this.questions.reload();
        this.quiz.reload();
      },
    },
  );

  protected askDelete(question: QuestionDetail): void {
    this.confirm.confirm({
      header: this.i18n.t('quiz.editor.deleteQuestion'),
      message: this.i18n.t('quiz.editor.deleteQuestionText', { text: this.text(question.texts) }),
      acceptLabel: this.i18n.t('quiz.editor.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.deleteQuestion.run(question.id),
    });
  }

  protected openComments(question: QuestionDetail): void {
    this.commentQuestion.set(question);
    this.commentText.set('');
    this.commentsOpen.set(true);
  }

  protected readonly addComment = runMutation(
    () =>
      this.api.addQuestionComment({
        kind: this.quizKind(),
        questionId: this.commentQuestion()!.id,
        questionCommentCreate: { text: this.commentText().trim() },
      }),
    {
      successToastKey: 'quiz.editor.commentAdded',
      onSuccess: () => {
        this.commentText.set('');
        this.comments.reload();
        this.questions.reload();
      },
    },
  );
}
