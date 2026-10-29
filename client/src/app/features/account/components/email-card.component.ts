import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';

import { EmailChangeStatus, UserProfile, UsersService } from '../../../api';
import { errorParams } from '../../../core/data/api-error';
import { DataState, loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { looksLikeEmail } from '../account.util';

/**
 * Email address (A3, A49): the current address, a pending change (new address, requested at) with "Cancel change",
 * and the request form. The confirmation link goes to the new address, the old one gets a notice with an abort
 * link; the change happens on `/account/email/confirm`.
 */
@Component({
  selector: 'fs-email-card',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, InputText, ReactiveFormsModule, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="email-title" data-testid="account.settings.email">
      <header class="card__head">
        <span class="card__icon" aria-hidden="true"><i class="pi pi-envelope"></i></span>
        <div class="card__head-text">
          <h2 id="email-title">{{ 'account.email.title' | t }}</h2>
          <p>{{ 'account.email.text' | t }}</p>
        </div>
      </header>

      <fs-data-state [state]="profile()" skeleton="form" [skeletonRows]="1" data-testid="account.settings.email.state">
        <ng-template [fsData]="profile()" let-user>
          <ul class="rows">
            <li class="row">
              <span class="row__icon" aria-hidden="true"><i class="pi pi-at"></i></span>
              <span class="row__main">
                <span class="row__meta">{{ 'account.email.current' | t }}</span>
                <span class="row__title" data-testid="account.settings.email.current">{{ user.email }}</span>
              </span>
              @if (user.emailVerifiedAt) {
                <span class="badge" data-testid="account.settings.email.verified">
                  <i class="pi pi-verified" aria-hidden="true"></i>{{ 'account.email.verified' | t }}
                </span>
              }
            </li>
          </ul>
        </ng-template>
      </fs-data-state>

      <fs-data-state
        [state]="pending"
        skeleton="list"
        [skeletonRows]="1"
        [compact]="true"
        data-testid="account.settings.email.pendingState"
      >
        <ng-template fsEmpty></ng-template>
        <ng-template [fsData]="pending" let-change>
          <div class="pending" role="status" data-testid="account.settings.email.pending" [attr.data-email]="change.pendingEmail">
            <i class="pi pi-hourglass" aria-hidden="true"></i>
            <span class="pending__text">
              <strong>{{ 'account.email.pendingTitle' | t }}</strong>
              <span data-testid="account.settings.email.pendingAddress">{{
                'account.email.pendingText'
                  | t: { email: change.pendingEmail, time: (change.requestedAt | fsDate: 'dateTime') }
              }}</span>
            </span>
            <p-button
              [label]="'account.email.abortButton' | t"
              icon="pi pi-times"
              size="small"
              severity="danger"
              [text]="true"
              [loading]="abort.busy()"
              (onClick)="confirmAbort(change)"
              data-testid="account.settings.email.abort"
            />
          </div>
        </ng-template>
      </fs-data-state>

      <form class="request" (submit)="$event.preventDefault(); submit()" novalidate>
        <label for="email-new" class="request__label">{{ 'account.email.newLabel' | t }}</label>
        <div class="request__row">
          <input
            pInputText
            id="email-new"
            type="email"
            autocomplete="email"
            maxlength="320"
            [formControl]="newEmail"
            [attr.aria-invalid]="!!request.error() || (newEmail.touched && newEmail.invalid)"
            aria-describedby="email-new-hint"
            data-testid="account.settings.email.new"
          />
          <p-button
            type="submit"
            [label]="'account.email.request' | t"
            icon="pi pi-send"
            size="small"
            [disabled]="!valid()"
            [loading]="request.busy()"
            data-testid="account.settings.email.submit"
          />
        </div>
        <small id="email-new-hint" class="muted">{{ 'account.email.newHint' | t }}</small>
        @if (request.error(); as error) {
          <p class="problem" role="alert" data-testid="account.settings.email.error" [attr.data-code]="error.code">
            <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
            {{ i18n.error(error.code, errorParams(error)) }}
          </p>
        }
      </form>
    </section>
  `,
  styleUrl: './account-card.scss',
  styles: `
    .pending {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.6rem 0.8rem;
      padding: 0.8rem 0.95rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
      color: var(--fs-text);
    }
    .pending > .pi {
      color: var(--fs-warning-text);
    }
    .pending__text {
      display: grid;
      flex: 1 1 14rem;
      gap: 0.15rem;
      overflow-wrap: anywhere;
    }
    .request {
      display: grid;
      gap: 0.4rem;
    }
    .request__label {
      font-weight: 600;
    }
    .request__row {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .request__row input {
      flex: 1 1 16rem;
      min-width: 0;
    }
    .muted {
      color: var(--fs-text-muted);
    }
    .problem {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      margin: 0;
      color: var(--fs-danger);
      font-weight: 600;
    }
  `,
})
export class EmailCardComponent {
  private readonly usersApi = inject(UsersService);
  private readonly confirm = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  protected readonly i18n = inject(I18nService);
  protected readonly errorParams = errorParams;

  readonly profile = input.required<DataState<UserProfile>>();

  protected readonly pending = loadResource<EmailChangeStatus>({
    loader: () => this.usersApi.getEmailChange(),
    isEmpty: (status) => !status.pendingEmail,
  });

  protected readonly newEmail = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(320)],
  });

  protected valid(): boolean {
    return looksLikeEmail(this.newEmail.value) && this.newEmail.valid;
  }

  /** Field errors and codes (EMAIL_INVALID, EMAIL_TAKEN, EMAIL_BLOCKED) are shown below the input, not as toast. */
  protected readonly request = runMutation(
    (email: string) => this.usersApi.requestEmailChange({ emailChangeRequest: { newEmail: email } }),
    { errorToast: false },
  );

  protected async submit(): Promise<void> {
    if (!this.valid()) {
      return;
    }
    const email = this.newEmail.value.trim();
    if (await this.request.run(email)) {
      this.newEmail.reset('');
      this.pending.reload();
      this.messages.add({
        severity: 'success',
        summary: this.i18n.t('account.email.requested', { email }),
        life: 6000,
      });
    }
  }

  protected readonly abort = runMutation(() => this.usersApi.abortEmailChange(), {
    successToastKey: 'account.email.aborted',
    onSuccess: () => this.pending.reload(),
  });

  protected confirmAbort(change: EmailChangeStatus): void {
    this.confirm.confirm({
      header: this.i18n.t('account.email.abortTitle'),
      message: this.i18n.t('account.email.abortText', { email: change.pendingEmail ?? '' }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('account.email.abortButton'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.abort.run(),
    });
  }
}
