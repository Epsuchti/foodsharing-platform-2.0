import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { of } from 'rxjs';

import { Survey, SurveyQuestion, SurveyQuestionType, SurveysService, UserSummary } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';
import { cleanQuestion, coauthorSearch, hasOptions, surveyErrors } from '../surveys.resource';

const newQuestion = (): SurveyQuestion => ({ type: SurveyQuestionType.Text, label: '', required: false, options: [] });

/**
 * /surveys/new and /surveys/:surveyId (SV1): title, description, question builder (type, label, required, options
 * for choice types, STORE hint "answered via the link"), co-authors from the candidates (CAN_MANAGE_SURVEYS), save
 * and delete (owner only; responses are kept, decision 71).
 */
@Component({
  selector: 'fs-survey-editor-page',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, FormsModule, InputText, PageHeaderComponent, RouterLink, Select, Textarea, TPipe, UserChipComponent, UserPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="(surveyId() ? 'surveys.editor.editTitle' : 'surveys.editor.newTitle') | t" icon="pi pi-list-check" backLink="/surveys" [backLabel]="'surveys.title' | t">
      @if (existing.data(); as s) {
        <p-button icon="pi pi-table" [outlined]="true" [label]="'surveys.results.title' | t" [routerLink]="['/surveys', s.id, 'results']" data-testid="surveys.editor.results" />
      }
    </fs-page-header>
    <fs-data-state [state]="existing" skeleton="form" data-testid="surveys.editor.state">
      <ng-template [fsData]="existing" let-_>
        <form class="form fs-card" (ngSubmit)="submit()" novalidate>
          <label for="survey-title">{{ 'surveys.editor.titleLabel' | t }}</label>
          <input pInputText id="survey-title" name="title" maxlength="200" [ngModel]="title()" (ngModelChange)="title.set($event)" [attr.aria-invalid]="submitted() && error() === 'title'" data-testid="surveys.editor.title" />
          <label for="survey-description">{{ 'surveys.editor.description' | t }}</label>
          <textarea pTextarea id="survey-description" name="description" rows="3" [ngModel]="description()" (ngModelChange)="description.set($event)" data-testid="surveys.editor.description"></textarea>

          <h2>{{ 'surveys.editor.questions' | t }}</h2>
          @for (q of questions(); track $index; let i = $index, first = $first, last = $last) {
            <fieldset class="question" data-testid="surveys.editor.question" [attr.data-type]="q.type">
              <legend>{{ 'surveys.editor.questionN' | t: { n: i + 1 } }}</legend>
              <div class="row">
                <span class="field field--type">
                  <label [for]="'q-type-' + i">{{ 'surveys.editor.type' | t }}</label>
                  <p-select [inputId]="'q-type-' + i" [name]="'type' + i" [options]="typeOptions()" optionLabel="label" optionValue="value" [ngModel]="q.type" (ngModelChange)="patch(i, { type: $event })" appendTo="body" data-testid="surveys.editor.questionType" />
                </span>
                <span class="field">
                  <label [for]="'q-label-' + i">{{ 'surveys.editor.label' | t }}</label>
                  <input pInputText [id]="'q-label-' + i" [name]="'label' + i" maxlength="500" [ngModel]="q.label" (ngModelChange)="patch(i, { label: $event })" data-testid="surveys.editor.questionLabel" />
                </span>
              </div>
              @if (q.type === 'STORE') {
                <small class="hint" data-testid="surveys.editor.storeHint">{{ 'surveys.editor.storeHint' | t }}</small>
              } @else {
                <span class="check">
                  <p-checkbox [inputId]="'q-required-' + i" [name]="'required' + i" [binary]="true" [ngModel]="q.required" (ngModelChange)="patch(i, { required: $event })" data-testid="surveys.editor.questionRequired" />
                  <label [for]="'q-required-' + i">{{ 'surveys.editor.required' | t }}</label>
                </span>
              }
              @if (choice(q.type)) {
                <div class="options">
                  @for (o of q.options; track $index; let j = $index) {
                    <div class="option">
                      <label class="fs-visually-hidden" [for]="'q-' + i + '-o-' + j">{{ 'surveys.editor.option' | t: { n: j + 1 } }}</label>
                      <input pInputText [id]="'q-' + i + '-o-' + j" [name]="'q' + i + 'o' + j" maxlength="200" [ngModel]="o" (ngModelChange)="setOption(i, j, $event)" data-testid="surveys.editor.optionInput" />
                      <p-button icon="pi pi-times" severity="danger" [text]="true" [ariaLabel]="'surveys.editor.removeOption' | t" (onClick)="removeOption(i, j)" data-testid="surveys.editor.removeOption" />
                    </div>
                  }
                  <p-button icon="pi pi-plus" size="small" [text]="true" [label]="'surveys.editor.addOption' | t" (onClick)="addOption(i)" data-testid="surveys.editor.addOption" />
                </div>
              }
              <div class="question__actions">
                <p-button icon="pi pi-arrow-up" [text]="true" [disabled]="first" [ariaLabel]="'surveys.editor.up' | t" (onClick)="move(i, -1)" />
                <p-button icon="pi pi-arrow-down" [text]="true" [disabled]="last" [ariaLabel]="'surveys.editor.down' | t" (onClick)="move(i, 1)" />
                <p-button icon="pi pi-trash" severity="danger" [text]="true" [ariaLabel]="'surveys.editor.removeQuestion' | t" (onClick)="removeQuestion(i)" data-testid="surveys.editor.removeQuestion" />
              </div>
            </fieldset>
          }
          <p-button icon="pi pi-plus" [outlined]="true" [label]="'surveys.editor.addQuestion' | t" (onClick)="questions.set([...questions(), newQuestion()])" data-testid="surveys.editor.addQuestion" />

          <h2>{{ 'surveys.editor.coauthors' | t }}</h2>
          <ul class="coauthors" data-testid="surveys.editor.coauthors">
            @for (u of coauthors(); track u.id) {
              <li data-testid="surveys.editor.coauthor" [attr.data-user-id]="u.id">
                <fs-user-chip [name]="u.displayName" [userId]="u.id" [avatarUrl]="u.avatarUrl" />
                <p-button icon="pi pi-times" [text]="true" severity="danger" [ariaLabel]="('surveys.editor.removeCoauthor' | t) + ': ' + u.displayName" (onClick)="removeCoauthor(u.id)" data-testid="surveys.editor.removeCoauthor" />
              </li>
            } @empty {
              <li class="hint">{{ 'surveys.editor.noCoauthors' | t }}</li>
            }
          </ul>
          <fs-user-picker [search]="search" [excludeIds]="coauthorIds()" [label]="'surveys.editor.addCoauthor' | t" (picked)="addCoauthor($event)" testId="surveys.editor.coauthorPicker" />

          @if (submitted() && error(); as code) {
            <small class="error" data-testid="surveys.editor.error" [attr.data-code]="code">{{ 'surveys.editor.error.' + code | t }}</small>
          }
          @if (serverError(); as code) {
            <small class="error" data-testid="surveys.editor.serverError" [attr.data-code]="code">{{ 'errors.' + code | t }}</small>
          }
          <div class="actions">
            @if (existing.data()?.mayDelete) {
              <p-button icon="pi pi-trash" severity="danger" [outlined]="true" [label]="'surveys.editor.delete' | t" [loading]="remove.busy()" (onClick)="confirmDelete()" data-testid="surveys.editor.delete" />
            }
            <p-button type="submit" icon="pi pi-check" [label]="(surveyId() ? 'common.save' : 'surveys.editor.create') | t" [loading]="save.busy()" data-testid="surveys.editor.submit" />
          </div>
        </form>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; width: min(100%, 48rem); margin: 0 auto; }
    .form { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.5rem; padding: 1rem; min-width: 0; }
    .form input, .form textarea { width: 100%; min-width: 0; box-sizing: border-box; }
    label, legend { font-weight: 600; }
    h2 { margin: 0.75rem 0 0; font-size: 1.05rem; }
    .question { display: grid; gap: 0.5rem; margin: 0; padding: 0.75rem; border: 1px solid var(--fs-border); border-radius: 0.5rem; min-width: 0; }
    .row { display: flex; flex-wrap: wrap; gap: 0.75rem; }
    .field { display: grid; gap: 0.25rem; flex: 1 1 14rem; min-width: 0; }
    .field--type { flex: 0 1 13rem; }
    .check { display: inline-flex; align-items: center; gap: 0.5rem; min-height: 2.5rem; }
    .check label { font-weight: 400; }
    .options { display: grid; gap: 0.3rem; padding-left: 0.75rem; }
    .option { display: flex; align-items: center; gap: 0.25rem; }
    .option input { flex: 1; width: auto; }
    .question__actions { display: flex; justify-content: flex-end; gap: 0.25rem; }
    .coauthors { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.25rem; }
    .coauthors li { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; }
    .hint { color: var(--fs-text-muted); }
    .error { color: var(--fs-danger); }
    .actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 0.5rem; margin-top: 0.5rem; }
  `,
})
export class SurveyEditorPageComponent {
  private readonly api = inject(SurveysService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly confirm = inject(ConfirmationService);

  readonly surveyId = input<string | undefined>(undefined);

  protected readonly newQuestion = newQuestion;
  protected readonly search = coauthorSearch(this.api);
  protected readonly title = signal('');
  protected readonly description = signal('');
  protected readonly questions = signal<SurveyQuestion[]>([newQuestion()]);
  protected readonly coauthors = signal<UserSummary[]>([]);
  protected readonly coauthorIds = computed(() => this.coauthors().map((u) => u.id));
  protected readonly submitted = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly error = computed(() => surveyErrors(this.title(), this.questions()));

  protected readonly existing = loadResource<Survey | null, string>({
    params: () => this.surveyId() ?? '',
    loader: (surveyId) => (surveyId ? this.api.getSurvey({ surveyId }) : of(null)),
    isEmpty: () => false,
  });

  protected readonly typeOptions = computed(() => {
    this.i18n.language();
    return Object.values(SurveyQuestionType).map((value) => ({ value, label: this.i18n.t(`surveys.type.${value}`) }));
  });

  constructor() {
    effect(() => {
      const s = this.existing.data();
      if (!s) return;
      untracked(() => {
        this.title.set(s.title);
        this.description.set(s.description ?? '');
        this.questions.set(s.questions.map((q) => ({ ...q, options: [...q.options] })));
        this.coauthors.set(s.coauthors);
      });
    });
  }

  protected choice(type: SurveyQuestionType): boolean {
    return hasOptions(type);
  }

  protected patch(index: number, change: Partial<SurveyQuestion>): void {
    this.questions.update((list) =>
      list.map((q, i) => {
        if (i !== index) return q;
        const next = { ...q, ...change };
        if (change.type && hasOptions(change.type) && next.options.length < 2) next.options = [...next.options, '', ''].slice(0, Math.max(2, next.options.length));
        return next;
      }),
    );
  }

  protected setOption(qi: number, oi: number, value: string): void {
    this.patch(qi, { options: this.questions()[qi].options.map((o, i) => (i === oi ? value : o)) });
  }

  protected addOption(qi: number): void {
    this.patch(qi, { options: [...this.questions()[qi].options, ''] });
  }

  protected removeOption(qi: number, oi: number): void {
    this.patch(qi, { options: this.questions()[qi].options.filter((_, i) => i !== oi) });
  }

  protected move(index: number, delta: number): void {
    this.questions.update((list) => {
      const next = [...list];
      [next[index], next[index + delta]] = [next[index + delta], next[index]];
      return next;
    });
  }

  protected removeQuestion(index: number): void {
    this.questions.update((list) => list.filter((_, i) => i !== index));
  }

  protected addCoauthor(user: UserSummary): void {
    if (!this.coauthorIds().includes(user.id)) this.coauthors.update((list) => [...list, user]);
  }

  protected removeCoauthor(id: string): void {
    this.coauthors.update((list) => list.filter((u) => u.id !== id));
  }

  protected submit(): void {
    this.submitted.set(true);
    if (!this.error()) void this.save.run();
  }

  protected readonly save = runMutation(
    () => {
      this.serverError.set(null);
      const surveyWrite = {
        title: this.title().trim(),
        description: this.description(),
        questions: this.questions().map(cleanQuestion),
        coauthorIds: this.coauthorIds(),
      };
      const surveyId = this.surveyId();
      return surveyId ? this.api.updateSurvey({ surveyId, surveyWrite }) : this.api.createSurvey({ surveyWrite });
    },
    {
      successToastKey: 'surveys.editor.saved',
      errorToast: false,
      onError: (error) => this.serverError.set(error.code ?? 'UNKNOWN'),
      onSuccess: (survey: Survey) => {
        if (this.surveyId()) this.existing.reload();
        else void this.router.navigate(['/surveys', survey.id]);
      },
    },
  );

  protected readonly remove = runMutation(() => this.api.deleteSurvey({ surveyId: this.surveyId()! }), {
    successToastKey: 'surveys.editor.deleted',
    onSuccess: () => void this.router.navigate(['/surveys']),
  });

  protected confirmDelete(): void {
    this.confirm.confirm({
      header: this.i18n.t('surveys.editor.deleteTitle'),
      message: this.i18n.t('surveys.editor.deleteText', { title: this.title() }),
      acceptLabel: this.i18n.t('surveys.editor.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(),
    });
  }
}
