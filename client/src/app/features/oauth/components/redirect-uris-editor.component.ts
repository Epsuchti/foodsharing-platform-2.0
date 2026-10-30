import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';

import { TPipe } from '../../../core/i18n/t.pipe';
import { MAX_REDIRECT_URIS, redirectUriProblem } from '../oauth.util';

/**
 * List editor of an OAuth client's redirect URIs (1–10): one input per URI, add/remove rows, the server's https rule
 * checked per row once something is typed (`[(uris)]`).
 */
@Component({
  selector: 'fs-oauth-redirect-uris-editor',
  imports: [Button, InputText, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="uris" [attr.data-testid]="testId()">
      <legend class="label">{{ 'oauth.clients.form.redirectUris' | t }}</legend>
      <p class="hint" [id]="testId() + '-hint'">{{ 'oauth.clients.form.redirectUrisHint' | t }}</p>
      @for (row of rows(); track $index; let i = $index) {
        <div class="uri" [attr.data-testid]="testId() + '.row'">
          <label class="fs-visually-hidden" [for]="inputId(i)">
            {{ 'oauth.clients.form.redirectUriN' | t: { n: i + 1 } }}
          </label>
          <input
            pInputText
            type="url"
            inputmode="url"
            [id]="inputId(i)"
            [value]="row.value"
            placeholder="https://app.example.org/callback"
            [attr.aria-invalid]="!!row.problem"
            [attr.aria-describedby]="row.problem ? inputId(i) + '-error' : testId() + '-hint'"
            (input)="set(i, $any($event.target).value)"
            [attr.data-testid]="testId() + '.input'"
          />
          <p-button
            icon="pi pi-times"
            severity="secondary"
            [text]="true"
            [rounded]="true"
            [disabled]="rows().length === 1"
            [ariaLabel]="'oauth.clients.form.removeRedirectUri' | t: { n: i + 1 }"
            (onClick)="remove(i)"
            [attr.data-testid]="testId() + '.remove'"
          />
          @if (row.problem) {
            <p class="error" [id]="inputId(i) + '-error'" [attr.data-testid]="testId() + '.error'">
              {{ 'oauth.clients.form.redirect.' + row.problem | t }}
            </p>
          }
        </div>
      }
      <div>
        <p-button
          [label]="'oauth.clients.form.addRedirectUri' | t"
          icon="pi pi-plus"
          severity="secondary"
          [text]="true"
          size="small"
          [disabled]="rows().length >= max"
          (onClick)="add()"
          [attr.data-testid]="testId() + '.add'"
        />
      </div>
    </fieldset>
  `,
  styles: `
    .uris {
      display: grid;
      gap: 0.5rem;
      min-width: 0;
      margin: 0;
      padding: 0;
      border: 0;
    }
    .label {
      padding: 0;
      font-size: 0.875rem;
      font-weight: 600;
    }
    .hint {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.8125rem;
    }
    .uri {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 0.25rem 0.5rem;
      align-items: center;
    }
    .uri input {
      width: 100%;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 0.875rem;
    }
    .error {
      grid-column: 1 / -1;
      margin: 0;
      color: var(--fs-danger);
      font-size: 0.8125rem;
    }
  `,
})
export class RedirectUrisEditorComponent {
  readonly uris = model.required<string[]>();
  readonly testId = input('oauth.clients.form.redirectUris');

  protected readonly max = MAX_REDIRECT_URIS;
  /** Empty rows are not flagged (only the whole list being empty is, by the dialog). */
  protected readonly rows = computed(() =>
    this.uris().map((value) => ({ value, problem: value.trim() ? redirectUriProblem(value) : null })),
  );

  protected inputId(index: number): string {
    return `${this.testId().replace(/\./g, '-')}-${index}`;
  }

  protected set(index: number, value: string): void {
    this.uris.update((list) => list.map((item, i) => (i === index ? value : item)));
  }

  protected add(): void {
    this.uris.update((list) => (list.length >= MAX_REDIRECT_URIS ? list : [...list, '']));
  }

  protected remove(index: number): void {
    this.uris.update((list) => (list.length > 1 ? list.filter((_, i) => i !== index) : list));
  }
}
