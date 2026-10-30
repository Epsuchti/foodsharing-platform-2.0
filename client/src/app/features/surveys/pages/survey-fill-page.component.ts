import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { SurveyForm, SurveyQuestion, SurveysService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { AnswerDraft, buildAnswers, missingRequired } from '../surveys.resource';

/**
 * /survey/:surveyId?storeId= (SV1), minimal chrome (no app menu): title, description, questions; the STORE question
 * shows the link's store read-only (without a store in the link the survey cannot be submitted, 409
 * STORE_ANSWER_LINK_ONLY explained). Submit once → thank-you state; already submitted → info state. Logged out,
 * authGuard sends the user to the login page and back here (returnUrl incl. the query).
 */
@Component({
  selector: 'fs-survey-fill-page',
  imports: [...DATA_STATE_IMPORTS, Button, MarkdownComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="form" skeleton="form" data-testid="surveys.fill.state">
      <ng-template [fsData]="form" let-f>
        <article class="fs-card sheet">
          <h1 data-testid="surveys.fill.title">{{ f.title }}</h1>
          @if (f.description) {<fs-markdown [markdown]="f.description" />}
          @if (done()) {
            <p class="notice notice--ok" role="status" data-testid="surveys.fill.thanks"><i class="pi pi-check-circle" aria-hidden="true"></i> {{ 'surveys.fill.thanks' | t }}</p>
          } @else if (f.alreadySubmitted) {
            <p class="notice" role="status" data-testid="surveys.fill.already"><i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'surveys.fill.already' | t }}</p>
          } @else {
            <form class="questions" (submit)="$event.preventDefault(); submit(f)" novalidate data-testid="surveys.fill.form">
              @for (q of f.questions; track q.id; let i = $index) {
                <fieldset class="question" data-testid="surveys.fill.question" [attr.data-type]="q.type" [attr.aria-invalid]="submitted() && missing() === q.id">
                  <legend>{{ q.label }} @if (q.required) {<span class="req" aria-hidden="true">*</span><span class="fs-visually-hidden">{{ 'surveys.fill.required' | t }}</span>}</legend>
                  @switch (q.type) {
                    @case ('TEXT') {
                      <label class="fs-visually-hidden" [for]="'a-' + i">{{ q.label }}</label>
                      <textarea [id]="'a-' + i" rows="3" maxlength="32600" [value]="first(q)" (input)="setText(q, $any($event.target).value)" data-testid="surveys.fill.text"></textarea>
                    }
                    @case ('SINGLE_CHOICE') {
                      @for (o of q.options; track o) {
                        <label class="choice"><input type="radio" [name]="'q-' + i" [checked]="has(q, o)" (change)="setOnly(q, o)" data-testid="surveys.fill.option" /> {{ o }}</label>
                      }
                    }
                    @case ('MULTIPLE_CHOICE') {
                      @for (o of q.options; track o) {
                        <label class="choice"><input type="checkbox" [checked]="has(q, o)" (change)="toggle(q, o)" data-testid="surveys.fill.option" /> {{ o }}</label>
                      }
                    }
                    @case ('BOOLEAN') {
                      <label class="choice"><input type="radio" [name]="'q-' + i" [checked]="has(q, 'true')" (change)="setOnly(q, 'true')" data-testid="surveys.fill.yes" /> {{ 'surveys.yes' | t }}</label>
                      <label class="choice"><input type="radio" [name]="'q-' + i" [checked]="has(q, 'false')" (change)="setOnly(q, 'false')" data-testid="surveys.fill.no" /> {{ 'surveys.no' | t }}</label>
                    }
                    @case ('STORE') {
                      @if (f.store) {
                        <p class="store" data-testid="surveys.fill.store"><i class="pi pi-shop" aria-hidden="true"></i> {{ f.store.name }}</p>
                      } @else {
                        <p class="notice notice--warn" data-testid="surveys.fill.storeMissing">{{ 'surveys.fill.storeMissing' | t }}</p>
                      }
                    }
                  }
                  @if (submitted() && missing() === q.id) {
                    <small class="error" data-testid="surveys.fill.requiredError">{{ 'surveys.fill.requiredError' | t }}</small>
                  }
                </fieldset>
              }
              @if (serverError(); as code) {
                <small class="error" data-testid="surveys.fill.serverError" [attr.data-code]="code">{{ 'errors.' + code | t }}</small>
              }
              <div class="actions">
                <p-button type="submit" icon="pi pi-send" [label]="'surveys.fill.submit' | t" [disabled]="storeMissing(f)" [loading]="send.busy()" data-testid="surveys.fill.submit" />
              </div>
            </form>
          }
        </article>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; width: min(100%, 44rem); margin: 0 auto; }
    .sheet { display: grid; gap: 0.75rem; padding: 1.25rem; }
    h1 { margin: 0; font-size: 1.5rem; }
    .questions { display: grid; gap: 1rem; }
    .question { display: grid; gap: 0.35rem; margin: 0; padding: 0; border: 0; min-width: 0; }
    legend { font-weight: 600; margin-bottom: 0.35rem; padding: 0; }
    .req { color: var(--fs-danger); margin-left: 0.2rem; }
    textarea { width: 100%; box-sizing: border-box; font: inherit; padding: 0.5rem; border: 1px solid var(--fs-border-strong, var(--fs-border)); border-radius: 0.4rem; background: var(--fs-surface); color: var(--fs-text); }
    .choice { display: flex; align-items: center; gap: 0.5rem; min-height: var(--fs-target-sm); }
    .store { margin: 0; font-weight: 600; }
    .notice { margin: 0; padding: 0.75rem 1rem; border-radius: 0.5rem; background: var(--fs-surface-muted); }
    .notice--ok { background: var(--fs-primary-soft); color: var(--fs-primary-strong); font-weight: 600; }
    .notice--warn { background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .error { color: var(--fs-danger); }
    .actions { display: flex; justify-content: flex-end; }
  `,
})
export class SurveyFillPageComponent {
  private readonly api = inject(SurveysService);

  readonly surveyId = input.required<string>();
  readonly storeId = input<string | undefined>(undefined);

  protected readonly draft = signal<AnswerDraft>({});
  protected readonly submitted = signal(false);
  protected readonly done = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly form = loadResource<SurveyForm, { surveyId: string; storeId?: string }>({
    params: () => ({ surveyId: this.surveyId(), storeId: this.storeId() || undefined }),
    loader: (params) => this.api.getSurveyForm(params),
    isEmpty: () => false,
  });

  protected readonly missing = computed(() => {
    const f = this.form.data();
    return f ? missingRequired(f.questions, this.draft()) : null;
  });

  protected first(q: SurveyQuestion): string {
    return this.draft()[q.id!]?.[0] ?? '';
  }

  protected has(q: SurveyQuestion, value: string): boolean {
    return !!this.draft()[q.id!]?.includes(value);
  }

  protected setText(q: SurveyQuestion, value: string): void {
    this.draft.update((d) => ({ ...d, [q.id!]: [value] }));
  }

  protected setOnly(q: SurveyQuestion, value: string): void {
    this.draft.update((d) => ({ ...d, [q.id!]: [value] }));
  }

  protected toggle(q: SurveyQuestion, value: string): void {
    this.draft.update((d) => {
      const current = d[q.id!] ?? [];
      return { ...d, [q.id!]: current.includes(value) ? current.filter((v) => v !== value) : [...current, value] };
    });
  }

  protected storeMissing(f: SurveyForm): boolean {
    return !f.store && f.questions.some((q) => q.type === 'STORE');
  }

  protected submit(f: SurveyForm): void {
    this.submitted.set(true);
    if (!this.missing() && !this.storeMissing(f)) void this.send.run(f);
  }

  protected readonly send = runMutation(
    (f: SurveyForm) => {
      this.serverError.set(null);
      return this.api.submitSurveyResponse({
        surveyId: f.id,
        surveyResponseCreate: { storeId: f.store?.id, answers: buildAnswers(f.questions, this.draft()) },
      });
    },
    {
      successToastKey: 'surveys.fill.saved',
      errorToast: false,
      onError: (error) => {
        this.serverError.set(error.code ?? 'UNKNOWN');
        if (error.code === 'SURVEY_ALREADY_SUBMITTED') this.form.reload();
      },
      onSuccess: () => this.done.set(true),
    },
  );
}
