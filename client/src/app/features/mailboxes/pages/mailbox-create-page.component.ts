import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';

import { Mailbox, MailboxesService, Role, UserSummary } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';
import { MailboxStore } from '../mailboxes.resource';

/** Valid e-mail name (A35): lowercase letters, digits, dots and dashes, 2–63 characters. */
export const EMAIL_NAME = /^[a-z0-9][a-z0-9.-]{1,62}$/;

/**
 * /mailboxes/new (MB1, MB6): a region or working-group mailbox (admins of the unit) or, for ORGA, a personal
 * mailbox of a user. Invalid, reserved and taken names come back as field errors (MAILBOX_NAME_*).
 */
@Component({
  selector: 'fs-mailbox-create-page',
  imports: [Button, FormsModule, InputText, PageHeaderComponent, Select, TPipe, UserChipComponent, UserPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'mailboxes.create.title' | t" icon="pi pi-envelope" backLink="/mailboxes" [backLabel]="'mailboxes.title' | t" />
    <form class="form fs-card" (ngSubmit)="create.run()">
      @if (isOrga()) {
        <fieldset class="kind">
          <legend>{{ 'mailboxes.create.kind' | t }}</legend>
          <label><input type="radio" name="kind" value="region" [ngModel]="kind()" (ngModelChange)="kind.set($event)" data-testid="mailboxes.create.kindRegion" /> {{ 'mailboxes.create.region' | t }}</label>
          <label><input type="radio" name="kind" value="user" [ngModel]="kind()" (ngModelChange)="kind.set($event)" data-testid="mailboxes.create.kindUser" /> {{ 'mailboxes.create.personal' | t }}</label>
        </fieldset>
      }
      @if (kind() === 'region') {
        <label for="mailbox-unit">{{ 'mailboxes.create.unit' | t }}</label>
        <p-select inputId="mailbox-unit" name="unit" [options]="units()" optionLabel="name" optionValue="id" [ngModel]="unitId()" (ngModelChange)="unitId.set($event)" [placeholder]="'mailboxes.create.unitPlaceholder' | t" appendTo="body" data-testid="mailboxes.create.unit" />
      } @else {
        @if (user(); as u) {
          <div class="picked" data-testid="mailboxes.create.user">
            <fs-user-chip [name]="u.displayName" [userId]="u.id" [avatarUrl]="u.avatarUrl" />
            <p-button icon="pi pi-times" size="small" [text]="true" [rounded]="true" [ariaLabel]="'mailboxes.new.change' | t" (onClick)="user.set(null)" />
          </div>
        } @else {
          <fs-user-picker (picked)="user.set($event)" testId="mailboxes.create.picker" />
        }
      }
      <label for="mailbox-email-name">{{ 'mailboxes.create.emailName' | t }}</label>
      <input
        pInputText
        id="mailbox-email-name"
        name="emailName"
        maxlength="63"
        autocomplete="off"
        [ngModel]="emailName()"
        (ngModelChange)="emailName.set($event.toLowerCase())"
        [attr.aria-invalid]="!!error() || (emailName() !== '' && !valid())"
        aria-describedby="mailbox-email-name-hint"
        data-testid="mailboxes.create.emailName"
      />
      <small id="mailbox-email-name-hint" class="hint" [class.error]="!!error() || (emailName() !== '' && !valid())" data-testid="mailboxes.create.hint" [attr.data-code]="error()">
        @if (error(); as code) {
          {{ 'errors.' + code | t }}
        } @else if (emailName() !== '' && !valid()) {
          {{ 'mailboxes.create.invalidName' | t }}
        } @else {
          {{ 'mailboxes.create.nameHint' | t }}
        }
      </small>
      <label for="mailbox-display-name">{{ 'mailboxes.create.name' | t }}</label>
      <input pInputText id="mailbox-display-name" name="name" maxlength="100" [ngModel]="name()" (ngModelChange)="name.set($event)" data-testid="mailboxes.create.name" />
      <div class="actions">
        <p-button type="submit" [label]="'mailboxes.create.submit' | t" icon="pi pi-check" [disabled]="!ready()" [loading]="create.busy()" data-testid="mailboxes.create.submit" />
      </div>
    </form>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 40rem);
      margin: 0 auto;
    }
    .form {
      display: grid;
      gap: 0.4rem;
      padding: 1rem;
    }
    label,
    legend {
      margin-top: 0.4rem;
      font-weight: 600;
    }
    .kind {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
      margin: 0;
      padding: 0;
      border: 0;
    }
    .kind label {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      min-height: 2.5rem;
      font-weight: 400;
    }
    .picked {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .hint {
      color: var(--fs-text-muted);
    }
    .error {
      color: var(--fs-danger-text, #b42318);
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 0.5rem;
    }
  `,
})
export class MailboxCreatePageComponent {
  private readonly api = inject(MailboxesService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly store = inject(MailboxStore);

  protected readonly isOrga = computed(() => this.session.hasRole(Role.Orga));
  protected readonly kind = signal<'region' | 'user'>('region');
  protected readonly units = computed(() => (this.session.session().units ?? []).filter((u) => u.admin || this.isOrga()));
  protected readonly unitId = signal<string | null>(null);
  protected readonly user = signal<UserSummary | null>(null);
  protected readonly emailName = signal('');
  protected readonly name = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly valid = computed(() => EMAIL_NAME.test(this.emailName()));
  protected readonly ready = computed(
    () => this.valid() && (this.kind() === 'region' ? !!this.unitId() : !!this.user()),
  );

  protected readonly create = runMutation(
    () => {
      this.error.set(null);
      const mailboxCreate = { emailName: this.emailName(), ...(this.name().trim() ? { name: this.name().trim() } : {}) };
      return this.kind() === 'region'
        ? this.api.createRegionMailbox({ regionId: this.unitId()!, mailboxCreate })
        : this.api.createUserMailbox({ userId: this.user()!.id, mailboxCreate });
    },
    {
      successToastKey: 'mailboxes.create.created',
      errorToast: false,
      onError: (error) => this.error.set(error.code ?? 'UNKNOWN'),
      onSuccess: (mailbox: Mailbox) => {
        this.store.bump();
        void this.router.navigate(['/mailboxes', mailbox.id, 'inbox']);
      },
    },
  );
}
