import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Editor } from '@openng/optimus-ui/editor';
import type { EditorInitEvent } from '@openng/optimus-ui/types/editor';

import { TPipe } from '../../../core/i18n/t.pipe';

/**
 * Rich-text mail body (MB4): `p-editor` (Quill, itself loaded on demand) with a small toolbar — bold, italic,
 * underline, lists, link, clear formatting. The compose page renders it inside `@defer`, so neither this chunk nor
 * Quill is part of the compose page bundle. The value is HTML; the server sanitises it (A35).
 */
@Component({
  selector: 'fs-mail-body-editor',
  imports: [Editor, FormsModule, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-editor name="body" [ngModel]="value()" (ngModelChange)="value.set($event ?? '')" [style]="{ height: '16rem' }" (onInit)="label($event)" data-testid="mailboxes.compose.body">
      <ng-template #header>
        <span class="ql-formats">
          <button type="button" class="ql-bold" [attr.aria-label]="'mailboxes.compose.editor.bold' | t"></button>
          <button type="button" class="ql-italic" [attr.aria-label]="'mailboxes.compose.editor.italic' | t"></button>
          <button type="button" class="ql-underline" [attr.aria-label]="'mailboxes.compose.editor.underline' | t"></button>
        </span>
        <span class="ql-formats">
          <button type="button" class="ql-list" value="ordered" [attr.aria-label]="'mailboxes.compose.editor.ordered' | t"></button>
          <button type="button" class="ql-list" value="bullet" [attr.aria-label]="'mailboxes.compose.editor.bullet' | t"></button>
          <button type="button" class="ql-blockquote" [attr.aria-label]="'mailboxes.compose.editor.quote' | t"></button>
        </span>
        <span class="ql-formats">
          <button type="button" class="ql-link" [attr.aria-label]="'mailboxes.compose.editor.link' | t"></button>
          <button type="button" class="ql-clean" [attr.aria-label]="'mailboxes.compose.editor.clean' | t"></button>
        </span>
      </ng-template>
    </p-editor>
  `,
  styles: `
    :host { display: block; min-width: 0; }
  `,
})
export class MailBodyEditorComponent {
  readonly value = model('');
  /** Id of the visible label; the Quill content element gets it as `aria-labelledby`. */
  readonly labelledBy = input.required<string>();

  protected label(event: EditorInitEvent): void {
    const root = event.editor.root as HTMLElement;
    root.setAttribute('role', 'textbox');
    root.setAttribute('aria-multiline', 'true');
    root.setAttribute('aria-labelledby', this.labelledBy());
  }
}
