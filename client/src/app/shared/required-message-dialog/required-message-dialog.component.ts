import { ChangeDetectionStrategy, Component, computed, effect, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { Textarea } from '@openng/optimus-ui/textarea';

import { TPipe } from '../../core/i18n/t.pipe';

let nextId = 0;

/**
 * Dialog that asks for a required message before an action with consequences (verify or unverify a user, remove a
 * member, delete as admin). The text must have at least `minLength` characters (default 10, the server's rule).
 *
 * ```html
 * <fs-required-message-dialog [(visible)]="open" [title]="…" [text]="…" [confirmLabel]="…" severity="danger"
 *   [busy]="remove.busy()" (confirm)="remove.run($event)" testId="regions.members.removeDialog" />
 * ```
 */
@Component({
  selector: 'fs-required-message-dialog',
  imports: [Button, Dialog, FormsModule, TPipe, Textarea],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="title()"
      [style]="{ width: 'min(36rem, 94vw)' }"
      [attr.data-testid]="testId()"
    >
      @if (text()) {
        <p class="text">{{ text() }}</p>
      }
      <label class="label" [for]="fieldId">{{ label() ?? ('shared.requiredMessage.label' | t) }}</label>
      <textarea
        pTextarea
        [id]="fieldId"
        rows="4"
        [maxlength]="maxLength()"
        [ngModel]="message()"
        (ngModelChange)="message.set($event)"
        [attr.aria-invalid]="showError()"
        [attr.aria-describedby]="fieldId + '-hint'"
        [attr.data-testid]="testId() + '.message'"
      ></textarea>
      <small class="hint" [id]="fieldId + '-hint'" [class.hint--error]="showError()">
        @if (showError()) {
          {{ 'shared.requiredMessage.tooShort' | t: { min: minLength() } }}
        } @else {
          {{ 'shared.requiredMessage.count' | t: { count: length(), min: minLength() } }}
        }
      </small>
      <ng-template #footer>
        <p-button
          [label]="'common.cancel' | t"
          severity="secondary"
          [text]="true"
          (onClick)="visible.set(false)"
          [attr.data-testid]="testId() + '.cancel'"
        />
        <p-button
          [label]="confirmLabel() ?? ('shared.requiredMessage.confirm' | t)"
          [severity]="severity()"
          [loading]="busy()"
          (onClick)="submit()"
          [attr.data-testid]="testId() + '.confirm'"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .text {
      margin: 0 0 1rem;
      color: var(--fs-text-muted);
    }
    .label {
      display: block;
      margin-bottom: 0.35rem;
      font-weight: 650;
    }
    textarea {
      width: 100%;
      resize: vertical;
    }
    .hint {
      display: block;
      margin-top: 0.35rem;
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .hint--error {
      color: var(--fs-danger);
      font-weight: 600;
    }
  `,
})
export class RequiredMessageDialogComponent {
  readonly visible = model(false);
  readonly title = input.required<string>();
  readonly text = input<string | undefined>(undefined);
  readonly label = input<string | undefined>(undefined);
  readonly confirmLabel = input<string | undefined>(undefined);
  readonly severity = input<'primary' | 'danger' | 'warn'>('primary');
  readonly minLength = input(10);
  readonly maxLength = input(2000);
  readonly busy = input(false);
  readonly testId = input('shared.requiredMessage');
  /** The trimmed message; the dialog stays open (close it on success with `[(visible)]`). */
  readonly confirm = output<string>();

  protected readonly fieldId = `fs-required-message-${nextId++}`;
  protected readonly message = signal('');
  private readonly attempted = signal(false);
  protected readonly length = computed(() => this.message().trim().length);
  protected readonly showError = computed(() => this.attempted() && this.length() < this.minLength());

  constructor() {
    // a fresh text every time the dialog opens
    effect(() => {
      if (this.visible()) {
        this.message.set('');
        this.attempted.set(false);
      }
    });
  }

  protected submit(): void {
    this.attempted.set(true);
    if (this.length() >= this.minLength()) {
      this.confirm.emit(this.message().trim());
    }
  }
}
