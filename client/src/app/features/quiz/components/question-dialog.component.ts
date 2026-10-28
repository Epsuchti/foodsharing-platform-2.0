import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { AnswerDetail, AnswerRating, QuestionDetail, QuizKind, QuizzesService } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { LanguageTabsEditorComponent, TranslatedText } from '../../../shared/language-tabs-editor/language-tabs-editor.component';
import { localized } from '../quiz-kind';

interface AnswerDraft {
  id?: string;
  texts: TranslatedText;
  explanations: TranslatedText;
  rating: AnswerRating;
}

/**
 * Create / edit a quiz question (Q3): texts in five languages, duration (timed sessions), wiki link, mandatory,
 * failure points; for an existing question its answers with rating and explanation (add, edit, delete).
 */
@Component({
  selector: 'fs-quiz-question-dialog',
  imports: [Button, Dialog, FormsModule, InputNumber, InputText, LanguageTabsEditorComponent, Select, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [header]="(question() ? 'quiz.editor.editQuestion' : 'quiz.editor.newQuestion') | t"
      [(visible)]="visible"
      [modal]="true"
      [draggable]="false"
      [dismissableMask]="true"
      [style]="{ width: 'min(46rem, 96vw)' }"
      data-testid="quiz.editor.questionDialog"
    >
      <div class="form">
        <fs-language-tabs-editor
          [(ngModel)]="texts"
          [label]="'quiz.editor.questionText' | t"
          [multiline]="true"
          [rows]="3"
          testId="quiz.editor.questionText"
        />
        <div class="grid">
          <div class="field">
            <label for="q-fp">{{ 'quiz.editor.failurePoints' | t }}</label>
            <p-inputnumber inputId="q-fp" [(ngModel)]="failurePoints" [min]="0" [max]="10" [showButtons]="true" data-testid="quiz.editor.failurePoints" />
          </div>
          <div class="field">
            <label for="q-duration">{{ 'quiz.editor.duration' | t }}</label>
            <p-inputnumber inputId="q-duration" [(ngModel)]="duration" [min]="0" [max]="600" [showButtons]="true" suffix=" s" data-testid="quiz.editor.duration" />
          </div>
          <label class="switch" for="q-mandatory">
            <p-toggleswitch inputId="q-mandatory" [(ngModel)]="mandatory" data-testid="quiz.editor.mandatory" />
            <span>{{ 'quiz.editor.mandatory' | t }}</span>
          </label>
        </div>
        <div class="field">
          <label for="q-wiki">{{ 'quiz.editor.wikiLink' | t }}</label>
          <input pInputText id="q-wiki" type="url" [(ngModel)]="wikiLink" maxlength="500" placeholder="https://wiki.foodsharing.de/…" data-testid="quiz.editor.wikiLink" />
        </div>
        <div class="actions">
          <p-button
            [label]="'common.save' | t"
            icon="pi pi-check"
            [disabled]="!texts().de?.trim()"
            [loading]="save.busy()"
            (onClick)="save.run()"
            data-testid="quiz.editor.saveQuestion"
          />
        </div>

        @if (question(); as q) {
          <section class="answers" data-testid="quiz.editor.answers">
            <h3>{{ 'quiz.editor.answers' | t }}</h3>
            <ul>
              @for (answer of q.answers; track answer.id) {
                <li class="answer" [attr.data-rating]="answer.rating" data-testid="quiz.editor.answer">
                  <span [class]="'rating rating--' + answer.rating">{{ 'quiz.rating.' + answer.rating | t }}</span>
                  <span class="answer__text">{{ text(answer.texts) }}</span>
                  <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" severity="secondary" [ariaLabel]="'quiz.editor.editAnswer' | t" (onClick)="editAnswer(answer)" data-testid="quiz.editor.editAnswer" />
                  <p-button icon="pi pi-trash" [text]="true" [rounded]="true" severity="danger" [ariaLabel]="'quiz.editor.deleteAnswer' | t" (onClick)="askDeleteAnswer(answer)" data-testid="quiz.editor.deleteAnswer" />
                </li>
              } @empty {
                <li class="muted">{{ 'quiz.editor.noAnswers' | t }}</li>
              }
            </ul>
            @if (answerDraft(); as draft) {
              <div class="answer-form" data-testid="quiz.editor.answerForm">
                <fs-language-tabs-editor [(ngModel)]="draft.texts" [label]="'quiz.editor.answerText' | t" testId="quiz.editor.answerText" />
                <fs-language-tabs-editor
                  [(ngModel)]="draft.explanations"
                  [label]="'quiz.editor.explanation' | t"
                  [multiline]="true"
                  [rows]="2"
                  [requiredLanguages]="[]"
                  testId="quiz.editor.explanation"
                />
                <div class="field">
                  <label for="a-rating">{{ 'quiz.editor.rating' | t }}</label>
                  <p-select inputId="a-rating" [options]="ratings()" optionLabel="label" optionValue="value" [(ngModel)]="draft.rating" appendTo="body" data-testid="quiz.editor.rating" />
                </div>
                <div class="actions">
                  <p-button [label]="'common.save' | t" icon="pi pi-check" [disabled]="!draft.texts.de?.trim()" [loading]="saveAnswer.busy()" (onClick)="saveAnswer.run()" data-testid="quiz.editor.saveAnswer" />
                  <p-button [label]="'common.cancel' | t" [text]="true" severity="secondary" (onClick)="answerDraft.set(undefined)" />
                </div>
              </div>
            } @else {
              <p-button [label]="'quiz.editor.addAnswer' | t" icon="pi pi-plus" severity="secondary" [outlined]="true" (onClick)="newAnswer()" data-testid="quiz.editor.addAnswer" />
            }
          </section>
        }
      </div>
    </p-dialog>
  `,
  styles: `
    .form {
      display: grid;
      gap: 1rem;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 11rem), 1fr));
      align-items: end;
      gap: 1rem;
    }
    .field {
      display: grid;
      gap: 0.35rem;
    }
    .field label {
      font-weight: 650;
      font-size: 0.9rem;
    }
    .switch {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      min-height: 2.6rem;
      font-weight: 650;
      cursor: pointer;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .answers {
      display: grid;
      gap: 0.75rem;
      padding-top: 1rem;
      border-top: 1px solid var(--fs-border);
    }
    .answers h3 {
      margin: 0;
      font-size: 1rem;
    }
    .answers ul {
      display: grid;
      gap: 0.35rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .answer {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto auto;
      align-items: center;
      gap: 0.6rem;
      padding: 0.3rem 0.3rem 0.3rem 0.6rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
    }
    .rating {
      padding: 0.1rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .rating--CORRECT {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .rating--WRONG {
      background: var(--fs-danger-soft);
      color: var(--fs-danger);
    }
    .answer-form {
      display: grid;
      gap: 0.85rem;
      padding: 1rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
    }
    .muted {
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
  `,
})
export class QuestionDialogComponent {
  private readonly api = inject(QuizzesService);
  private readonly i18n = inject(I18nService);
  private readonly confirm = inject(ConfirmationService);

  readonly kind = input.required<QuizKind>();
  /** The question to edit; undefined → create. */
  readonly question = input<QuestionDetail | undefined>(undefined);
  readonly visible = model(false);
  /** Emitted after every successful change (the page reloads the list). */
  readonly changed = output<QuestionDetail | undefined>();

  protected readonly texts = signal<TranslatedText>({});
  protected readonly failurePoints = signal(1);
  protected readonly duration = signal<number | null>(60);
  protected readonly mandatory = signal(false);
  protected readonly wikiLink = signal('');
  protected readonly answerDraft = signal<AnswerDraft | undefined>(undefined);

  protected readonly ratings = computed(() =>
    Object.values(AnswerRating).map((value) => ({
      value,
      label: this.i18n.t(`quiz.rating.${value}`),
    })),
  );

  constructor() {
    // (re)fill the form whenever the dialog opens for a question
    effect(() => {
      if (!this.visible()) {
        return;
      }
      const q = this.question();
      this.texts.set({ ...(q?.texts ?? {}) });
      this.failurePoints.set(q?.failurePoints ?? 1);
      this.duration.set(q?.durationSeconds ?? 60);
      this.mandatory.set(q?.mandatory ?? false);
      this.wikiLink.set(q?.wikiLink ?? '');
      this.answerDraft.set(undefined);
    });
  }

  protected text(texts: Record<string, string>): string {
    return localized(texts, this.i18n.language());
  }

  protected readonly save = runMutation(
    () => {
      const questionUpdate = {
        texts: this.texts() as Record<string, string>,
        failurePoints: this.failurePoints(),
        durationSeconds: this.duration() ?? 0,
        mandatory: this.mandatory(),
        wikiLink: this.wikiLink().trim() || undefined,
      };
      const q = this.question();
      return q
        ? this.api.updateQuestion({ kind: this.kind(), questionId: q.id, questionUpdate })
        : this.api.createQuestion({ kind: this.kind(), questionUpdate });
    },
    { successToastKey: 'quiz.editor.questionSaved', onSuccess: (saved) => this.changed.emit(saved) },
  );

  protected newAnswer(): void {
    this.answerDraft.set({ texts: {}, explanations: {}, rating: AnswerRating.Wrong });
  }

  protected editAnswer(answer: AnswerDetail): void {
    this.answerDraft.set({
      id: answer.id,
      texts: { ...answer.texts },
      explanations: { ...answer.explanations },
      rating: answer.rating,
    });
  }

  protected readonly saveAnswer = runMutation(
    () => {
      const draft = this.answerDraft()!;
      const questionId = this.question()!.id;
      const answerUpdate = {
        texts: draft.texts as Record<string, string>,
        explanations: draft.explanations as Record<string, string>,
        rating: draft.rating,
      };
      return draft.id
        ? this.api.updateAnswer({ kind: this.kind(), questionId, answerId: draft.id, answerUpdate })
        : this.api.createAnswer({ kind: this.kind(), questionId, answerUpdate });
    },
    {
      successToastKey: 'quiz.editor.answerSaved',
      onSuccess: () => {
        this.answerDraft.set(undefined);
        this.changed.emit(this.question());
      },
    },
  );

  private readonly deleteAnswer = runMutation(
    (answerId: string) => this.api.deleteAnswer({ kind: this.kind(), questionId: this.question()!.id, answerId }),
    { successToastKey: 'quiz.editor.answerDeleted', onSuccess: () => this.changed.emit(this.question()) },
  );

  protected askDeleteAnswer(answer: AnswerDetail): void {
    this.confirm.confirm({
      header: this.i18n.t('quiz.editor.deleteAnswer'),
      message: this.i18n.t('quiz.editor.deleteAnswerText', { text: this.text(answer.texts) }),
      acceptLabel: this.i18n.t('quiz.editor.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.deleteAnswer.run(answer.id),
    });
  }
}
