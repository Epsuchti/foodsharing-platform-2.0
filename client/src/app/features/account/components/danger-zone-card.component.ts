import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';

import { UserProfile, UsersService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { DataState } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { DELETE_REASON_MAX, deletionConfirmed } from '../account.util';

/**
 * Danger zone (A13, A40): delete the own account. The dialog asks for an optional reason and the re-typed email
 * address; the account is anonymised on the server (authored posts stay as "deleted member"), every session ends,
 * and the app returns to the start page logged out.
 */
@Component({
  selector: 'fs-danger-zone-card',
  imports: [...DATA_STATE_IMPORTS, Button, Dialog, FormsModule, InputText, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card danger" aria-labelledby="danger-title" data-testid="account.settings.danger">
      <header class="card__head">
        <span class="card__icon card__icon--danger" aria-hidden="true"><i class="pi pi-exclamation-triangle"></i></span>
        <div class="card__head-text">
          <h2 id="danger-title">{{ 'account.delete.title' | t }}</h2>
          <p>{{ 'account.delete.text' | t }}</p>
        </div>
      </header>
      <fs-data-state [state]="profile()" skeleton="form" [skeletonRows]="1" data-testid="account.settings.danger.state">
        <ng-template [fsData]="profile()">
          <div>
            <p-button
              [label]="'account.delete.open' | t"
              icon="pi pi-trash"
              severity="danger"
              [outlined]="true"
              (onClick)="open()"
              data-testid="account.settings.danger.open"
            />
          </div>
        </ng-template>
      </fs-data-state>
    </section>

    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="dialogOpen()"
      (visibleChange)="dialogOpen.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="'account.delete.dialogTitle' | t"
      [style]="{ width: 'min(34rem, 94vw)' }"
      data-testid="account.settings.danger.dialog"
    >
      <form class="form" (submit)="$event.preventDefault(); remove.run()" novalidate>
        <p class="warning">
          <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          <span>{{ 'account.delete.warning' | t }}</span>
        </p>
        <div class="field">
          <label for="delete-reason">{{ 'account.delete.reason' | t }}</label>
          <textarea
            pTextarea
            id="delete-reason"
            name="reason"
            rows="3"
            [maxlength]="reasonMax"
            [ngModel]="reason()"
            (ngModelChange)="reason.set($event)"
            data-testid="account.settings.danger.reason"
          ></textarea>
          <small class="muted">{{ 'account.delete.reasonHint' | t }}</small>
        </div>
        <div class="field">
          <label for="delete-confirm">{{ 'account.delete.retype' | t: { email: email() } }}</label>
          <input
            pInputText
            id="delete-confirm"
            name="confirm"
            type="email"
            autocomplete="off"
            [ngModel]="typed()"
            (ngModelChange)="typed.set($event)"
            data-testid="account.settings.danger.retype"
          />
        </div>
        <div class="actions">
          <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="dialogOpen.set(false)" />
          <p-button
            type="submit"
            [label]="'account.delete.confirm' | t"
            icon="pi pi-trash"
            severity="danger"
            [disabled]="!confirmed()"
            [loading]="remove.busy()"
            data-testid="account.settings.danger.confirm"
          />
        </div>
      </form>
    </p-dialog>
  `,
  styleUrl: './account-card.scss',
  styles: `
    .danger {
      border: 1px solid var(--fs-danger);
    }
    .card__icon--danger {
      background: var(--fs-danger-soft, var(--fs-surface-muted));
      color: var(--fs-danger);
    }
    .form {
      display: grid;
      gap: 1rem;
    }
    .warning {
      display: flex;
      align-items: flex-start;
      gap: 0.6rem;
      margin: 0;
      padding: 0.8rem 0.95rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
      color: var(--fs-text);
    }
    .warning .pi {
      margin-top: 0.2rem;
      color: var(--fs-warning-text);
    }
    .field {
      display: grid;
      gap: 0.35rem;
    }
    .field label {
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .muted {
      color: var(--fs-text-muted);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 0.5rem;
    }
  `,
})
export class DangerZoneCardComponent {
  private readonly usersApi = inject(UsersService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);

  readonly profile = input.required<DataState<UserProfile>>();

  protected readonly reasonMax = DELETE_REASON_MAX;
  protected readonly dialogOpen = signal(false);
  protected readonly reason = signal('');
  protected readonly typed = signal('');
  protected readonly email = computed(() => this.profile().data()?.email ?? '');
  protected readonly confirmed = computed(() => deletionConfirmed(this.typed(), this.email()));

  protected open(): void {
    this.reason.set('');
    this.typed.set('');
    this.dialogOpen.set(true);
  }

  protected readonly remove = runMutation(
    () => {
      const reason = this.reason().trim();
      return this.usersApi.deleteMyAccount({ accountDeletion: reason ? { reason } : {} });
    },
    {
      onSuccess: () => {
        // the server ended every session: forget it here before anything else asks (no "session expired" dialog)
        this.dialogOpen.set(false);
        this.session.clear();
        this.messages.add({ severity: 'success', summary: this.i18n.t('account.delete.done'), life: 8000 });
        void this.router.navigateByUrl('/');
      },
    },
  );
}
