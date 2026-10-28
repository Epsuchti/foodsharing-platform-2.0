import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { Textarea } from '@openng/optimus-ui/textarea';

import { StoreTeamService } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';

/** What the apply dialog needs to know about a store (StoreDetail or a list row). */
export interface ApplyTarget {
  id: string;
  name: string;
  requiresVerified?: boolean;
  requiresHygieneCertificate?: boolean;
  requiresPhone?: boolean;
  requiresApplicationText?: boolean;
}

/**
 * Apply to a store team (S6): requirements checklist, application text (required when the store asks for one) and
 * every refusal (409/403/400) rendered as a sentence inside the dialog. Emits `applied` after success.
 */
@Component({
  selector: 'fs-store-apply-dialog',
  imports: [Button, Dialog, FormsModule, TPipe, Textarea],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="'stores.apply.title' | t: { name: store()?.name ?? '' }"
      [style]="{ width: 'min(36rem, 94vw)' }"
      data-testid="stores.apply.dialog"
    >
      <p class="muted">{{ 'stores.apply.intro' | t }}</p>
      @if (requirements().length) {
        <h3 class="sub">{{ 'stores.overview.requirements' | t }}</h3>
        <ul class="checklist" data-testid="stores.apply.requirements">
          @for (req of requirements(); track req) {
            <li><i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'stores.requirement.' + req | t }}</li>
          }
        </ul>
      }
      <label class="label" for="store-apply-text">
        {{ (store()?.requiresApplicationText ? 'stores.apply.textRequired' : 'stores.apply.text') | t }}
      </label>
      <textarea
        pTextarea
        id="store-apply-text"
        rows="5"
        maxlength="4000"
        [ngModel]="text()"
        (ngModelChange)="text.set($event)"
        [attr.aria-invalid]="missingText()"
        data-testid="stores.apply.text"
      ></textarea>
      @if (missingText()) {
        <p class="error" role="alert" data-testid="stores.apply.textMissing">{{ 'errors.APPLICATION_TEXT_REQUIRED' | t }}</p>
      }
      @if (attempted() && apply.error(); as error) {
        <p class="error" role="alert" data-testid="stores.apply.error" [attr.data-code]="error.code">
          {{ 'errors.' + error.code | t: error.args }}
        </p>
      }
      <ng-template #footer>
        <p-button
          [label]="'common.cancel' | t"
          severity="secondary"
          [text]="true"
          (onClick)="visible.set(false)"
          data-testid="stores.apply.cancel"
        />
        <p-button
          [label]="'stores.apply.submit' | t"
          icon="pi pi-send"
          [loading]="apply.busy()"
          (onClick)="submit()"
          data-testid="stores.apply.submit"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .muted { margin: 0 0 0.75rem; color: var(--fs-text-muted); }
    .sub { margin: 0 0 0.35rem; font-size: 0.95rem; }
    .checklist { display: grid; gap: 0.3rem; margin: 0 0 1rem; padding: 0; list-style: none; }
    .checklist li { display: flex; gap: 0.5rem; align-items: center; }
    .checklist .pi { color: var(--fs-primary); }
    .label { display: block; margin-bottom: 0.35rem; font-weight: 650; }
    textarea { width: 100%; resize: vertical; }
    .error { margin: 0.6rem 0 0; color: var(--fs-danger); font-weight: 600; }
  `,
})
export class StoreApplyDialogComponent {
  private readonly api = inject(StoreTeamService);

  readonly visible = model(false);
  readonly store = input<ApplyTarget | null>(null);
  readonly applied = output<void>();

  protected readonly text = signal('');
  protected readonly attempted = signal(false);

  protected readonly requirements = computed(() => {
    const store = this.store();
    if (!store) return [];
    return (
      [
        ['verified', store.requiresVerified],
        ['hygiene', store.requiresHygieneCertificate],
        ['phone', store.requiresPhone],
        ['applicationText', store.requiresApplicationText],
      ] as const
    )
      .filter(([, active]) => !!active)
      .map(([id]) => id);
  });

  protected readonly missingText = computed(
    () => this.attempted() && !!this.store()?.requiresApplicationText && !this.text().trim(),
  );

  protected readonly apply = runMutation(
    () => {
      const text = this.text().trim();
      return this.api.applyToStore({ storeId: this.store()!.id, storeApplicationCreate: text ? { text } : {} });
    },
    {
      errorToast: false,
      successToastKey: 'stores.apply.done',
      onSuccess: () => {
        this.visible.set(false);
        this.applied.emit();
      },
    },
  );

  constructor() {
    effect(() => {
      if (this.visible()) {
        this.text.set('');
        this.attempted.set(false);
      }
    });
  }

  protected submit(): void {
    this.attempted.set(true);
    if (this.missingText()) return;
    void this.apply.run();
  }
}
