import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Textarea } from '@openng/optimus-ui/textarea';

import { TPipe } from '../../../core/i18n/t.pipe';
import { appendPlaceholder, renderTemplate, unknownPlaceholders } from '../automation.util';

/**
 * Message textarea with placeholder chips (click inserts `{{key}}`), a warning for unknown placeholders and a live
 * preview rendered with `values` (AU1 linked-store messages, AU4 advert messages).
 */
@Component({
  selector: 'fs-template-editor',
  imports: [FormsModule, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="editor">
      @if (label()) {
        <label [for]="inputId()">{{ label() }}</label>
      }
      <textarea
        pTextarea
        [id]="inputId()"
        rows="3"
        [maxlength]="maxLength()"
        [ngModel]="value()"
        (ngModelChange)="value.set($event)"
        [attr.data-testid]="testId() + '.text'"
      ></textarea>
      <ul class="chips" [attr.aria-label]="'automation.template.placeholders' | t">
        @for (key of placeholders(); track key) {
          <li>
            <button type="button" class="chip" (click)="insert(key)" [attr.data-testid]="testId() + '.placeholder.' + key">{{ chipLabel(key) }}</button>
          </li>
        }
      </ul>
      @if (unknown().length) {
        <p class="error-text" [attr.data-testid]="testId() + '.unknown'">{{ 'automation.template.unknown' | t: { names: unknown().join(', ') } }}</p>
      }
      @if (value().trim()) {
        <div class="preview" [attr.data-testid]="testId() + '.preview'">
          <span class="muted">{{ 'automation.template.preview' | t }}</span><br />{{ rendered() }}
        </div>
      }
    </div>
  `,
  styleUrl: '../automation.scss',
  styles: `
    :host { display: block; }
    .editor { display: grid; gap: 0.35rem; }
    textarea { width: 100%; }
  `,
})
export class TemplateEditorComponent {
  readonly value = model('');
  readonly placeholders = input.required<readonly string[]>();
  readonly values = input<Record<string, string | number>>({});
  readonly label = input<string | undefined>(undefined);
  readonly inputId = input('template');
  readonly maxLength = input(2000);
  readonly testId = input('automation.template');

  protected readonly unknown = computed(() => unknownPlaceholders(this.value(), this.placeholders()));
  protected readonly rendered = computed(() => renderTemplate(this.value(), this.values()));

  protected chipLabel(key: string): string {
    return '{{' + key + '}}';
  }

  protected insert(key: string): void {
    this.value.set(appendPlaceholder(this.value(), key));
  }
}
