import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';

import {
  AuthIdentityView,
  AuthMethods,
  AuthProvider,
  AuthService,
  PasskeyView,
} from '../../../api';
import {
  isPasskeyCancelled,
  WebAuthnClient,
  suggestPasskeyLabel,
} from '../../../core/auth/webauthn.client';
import { ApiErrorView, errorParams } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

type PasskeyDialog = { mode: 'add' } | { mode: 'rename'; passkey: PasskeyView };

const PROVIDER_ICONS: Record<AuthProvider, string> = {
  GOOGLE: 'pi pi-google',
  EMAIL: 'pi pi-envelope',
};

/**
 * Login methods of the account (D8.1–D8.4 UI): linked identities (Google, email) with unlink, passkeys with
 * add (WebAuthn registration), rename and delete. The last remaining method can never be removed.
 */
@Component({
  selector: 'fs-login-methods-card',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Dialog,
    InputText,
    ReactiveFormsModule,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="methods-title" id="login-methods">
      <header class="card__head">
        <span class="card__icon" aria-hidden="true"><i class="pi pi-lock"></i></span>
        <div class="card__head-text">
          <h2 id="methods-title">{{ 'account.methods.title' | t }}</h2>
          <p>{{ 'account.methods.text' | t }}</p>
        </div>
      </header>

      <fs-data-state
        [state]="methods"
        skeleton="list"
        [skeletonRows]="3"
        emptyIcon="pi pi-lock"
        [emptyTitle]="'account.methods.empty' | t"
        data-testid="account.settings.methods"
      >
        <ng-template [fsData]="methods" let-data>
          <ul class="rows">
            @for (identity of data.identities; track identity.id) {
              <li class="row" [attr.data-testid]="'account.settings.identity.' + identity.provider">
                <span class="row__icon" aria-hidden="true">
                  <i [class]="providerIcon(identity)"></i>
                </span>
                <span class="row__main">
                  <span class="row__title">
                    {{ 'account.methods.provider.' + identity.provider | t }}
                  </span>
                  <span class="row__meta">{{ identity.label }}</span>
                  <span class="row__meta">
                    {{
                      'account.methods.linkedSince'
                        | t: { date: (identity.createdAt | fsDate: 'date') }
                    }}
                    @if (identity.lastUsedAt) {
                      ·
                      {{
                        'account.methods.lastUsed'
                          | t: { date: (identity.lastUsedAt | fsDate: 'relative') }
                      }}
                    }
                  </span>
                </span>
                <span class="row__actions">
                  <p-button
                    [label]="'account.methods.remove' | t"
                    icon="pi pi-times"
                    severity="danger"
                    [text]="true"
                    size="small"
                    [disabled]="isLast() || busy()"
                    [attr.title]="isLast() ? ('account.methods.lastMethod' | t) : null"
                    (onClick)="confirmUnlink(identity)"
                    [attr.data-testid]="
                      'account.settings.identity.' + identity.provider + '.remove'
                    "
                  />
                </span>
              </li>
            }
          </ul>

          <div class="subhead">
            <h3>{{ 'account.passkeys.title' | t }}</h3>
            @if (webauthnSupported) {
              <p-button
                [label]="'account.passkeys.add' | t"
                icon="pi pi-plus"
                size="small"
                [loading]="register.busy()"
                (onClick)="openAdd()"
                data-testid="account.settings.passkeys.add"
              />
            }
          </div>

          @if (data.passkeys.length) {
            <ul class="rows" data-testid="account.settings.passkeys">
              @for (passkey of data.passkeys; track passkey.id) {
                <li class="row" [attr.data-testid]="'account.settings.passkey.' + passkey.id">
                  <span class="row__icon row__icon--key" aria-hidden="true"
                    ><i class="pi pi-key"></i
                  ></span>
                  <span class="row__main">
                    <span class="row__title" data-testid="account.settings.passkey.label">{{
                      passkey.label
                    }}</span>
                    <span class="row__meta">
                      {{
                        'account.passkeys.created'
                          | t: { date: (passkey.createdAt | fsDate: 'date') }
                      }}
                      @if (passkey.lastUsedAt) {
                        ·
                        {{
                          'account.methods.lastUsed'
                            | t: { date: (passkey.lastUsedAt | fsDate: 'relative') }
                        }}
                      }
                    </span>
                  </span>
                  <span class="row__actions">
                    <p-button
                      icon="pi pi-pencil"
                      severity="secondary"
                      [text]="true"
                      size="small"
                      [ariaLabel]="'account.passkeys.rename' | t"
                      [disabled]="busy()"
                      (onClick)="openRename(passkey)"
                      [attr.data-testid]="'account.settings.passkey.' + passkey.id + '.rename'"
                    />
                    <p-button
                      icon="pi pi-trash"
                      severity="danger"
                      [text]="true"
                      size="small"
                      [ariaLabel]="'account.passkeys.delete' | t"
                      [disabled]="isLast() || busy()"
                      (onClick)="confirmDelete(passkey)"
                      [attr.data-testid]="'account.settings.passkey.' + passkey.id + '.delete'"
                    />
                  </span>
                </li>
              }
            </ul>
          } @else {
            <p class="hint" data-testid="account.settings.passkeys.empty">
              <i class="pi pi-sparkles" aria-hidden="true"></i>
              <span>{{ 'account.passkeys.empty' | t }}</span>
            </p>
          }
          @if (!webauthnSupported) {
            <p class="hint">
              <i class="pi pi-info-circle" aria-hidden="true"></i>
              <span>{{ 'account.passkeys.unsupported' | t }}</span>
            </p>
          }
        </ng-template>
      </fs-data-state>
    </section>

    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="!!dialog()"
      (visibleChange)="!$event && closeDialog()"
      [modal]="true"
      [draggable]="false"
      [resizable]="false"
      [header]="
        (dialog()?.mode === 'rename' ? 'account.passkeys.renameTitle' : 'account.passkeys.addTitle')
          | t
      "
      [style]="{ width: 'min(28rem, calc(100vw - 2rem))' }"
      data-testid="account.settings.passkeyDialog"
    >
      <form class="dialog" (submit)="$event.preventDefault(); submitDialog()" novalidate>
        @if (dialog()?.mode === 'add') {
          <p class="dialog__text">{{ 'account.passkeys.addText' | t }}</p>
        }
        <label class="dialog__label" for="passkey-label">{{ 'account.passkeys.label' | t }}</label>
        <input
          pInputText
          id="passkey-label"
          [formControl]="labelControl"
          maxlength="100"
          autocomplete="off"
          [attr.aria-invalid]="labelControl.invalid && labelControl.touched"
          data-testid="account.settings.passkeyDialog.label"
        />
        <p class="dialog__hint">{{ 'account.passkeys.labelHint' | t }}</p>
        <div class="dialog__actions">
          <p-button
            [label]="'common.cancel' | t"
            severity="secondary"
            [text]="true"
            (onClick)="closeDialog()"
            data-testid="account.settings.passkeyDialog.cancel"
          />
          <p-button
            type="submit"
            [label]="(dialog()?.mode === 'rename' ? 'common.save' : 'account.passkeys.create') | t"
            [icon]="dialog()?.mode === 'rename' ? 'pi pi-check' : 'pi pi-key'"
            [loading]="register.busy() || rename.busy()"
            data-testid="account.settings.passkeyDialog.save"
          />
        </div>
      </form>
    </p-dialog>
  `,
  styleUrl: './account-card.scss',
  styles: `
    .row__icon--key {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .dialog {
      display: grid;
      gap: 0.5rem;
    }
    .dialog__text,
    .dialog__hint {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .dialog__text {
      margin-bottom: 0.5rem;
      font-size: 0.95rem;
    }
    .dialog__label {
      font-weight: 650;
    }
    .dialog input {
      width: 100%;
    }
    .dialog__actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 0.75rem;
    }
  `,
})
export class LoginMethodsCardComponent {
  private readonly authApi = inject(AuthService);
  private readonly webauthn = inject(WebAuthnClient);
  private readonly confirm = inject(ConfirmationService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);

  /** Opens the "add passkey" dialog once the methods are loaded (from the passkey nudge). */
  readonly autoAddPasskey = input(false);

  protected readonly webauthnSupported = this.webauthn.isSupported();

  protected readonly methods = loadResource({
    loader: () => this.authApi.listAuthMethods(),
    isEmpty: (value: AuthMethods) => !value.identities.length && !value.passkeys.length,
  });

  protected readonly isLast = computed(() => {
    const data = this.methods.data();
    return !data || data.identities.length + data.passkeys.length <= 1;
  });

  protected readonly dialog = signal<PasskeyDialog | null>(null);
  protected readonly labelControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(100)],
  });

  protected readonly unlink = runMutation(
    (identity: AuthIdentityView) => this.authApi.unlinkAuthMethod({ identityId: identity.id }),
    { successToastKey: 'account.methods.removed', onSuccess: () => this.methods.reload() },
  );

  protected readonly deletePasskey = runMutation(
    (passkey: PasskeyView) => this.authApi.deletePasskey({ passkeyId: passkey.id }),
    { successToastKey: 'account.passkeys.deleted', onSuccess: () => this.methods.reload() },
  );

  protected readonly rename = runMutation(
    (passkey: PasskeyView, label: string) =>
      this.authApi.renamePasskey({ passkeyId: passkey.id, passkeyRenameRequest: { label } }),
    {
      successToastKey: 'account.passkeys.renamed',
      onSuccess: (updated) => {
        this.methods.update((value) =>
          value
            ? {
                ...value,
                passkeys: value.passkeys.map((p) => (p.id === updated.id ? updated : p)),
              }
            : value!,
        );
        this.dialog.set(null);
      },
    },
  );

  protected readonly register = runMutation((label: string) => this.webauthn.register(label), {
    errorToast: false,
    successToastKey: 'account.passkeys.added',
    onSuccess: () => {
      this.dialog.set(null);
      this.methods.reload();
    },
    onError: (view) => this.registrationFailed(view),
  });

  protected readonly busy = computed(
    () =>
      this.unlink.busy() || this.deletePasskey.busy() || this.rename.busy() || this.register.busy(),
  );

  private autoAddDone = false;

  constructor() {
    effect(() => {
      const ready = this.methods.status() === 'success' || this.methods.status() === 'empty';
      if (ready && this.autoAddPasskey() && !this.autoAddDone && this.webauthnSupported) {
        this.autoAddDone = true;
        untracked(() => this.openAdd());
      }
    });
  }

  protected providerIcon(identity: AuthIdentityView): string {
    return PROVIDER_ICONS[identity.provider] ?? 'pi pi-user';
  }

  protected openAdd(): void {
    this.labelControl.reset(suggestPasskeyLabel());
    this.dialog.set({ mode: 'add' });
  }

  protected openRename(passkey: PasskeyView): void {
    this.labelControl.reset(passkey.label);
    this.dialog.set({ mode: 'rename', passkey });
  }

  protected closeDialog(): void {
    if (!this.register.busy()) {
      this.dialog.set(null);
    }
  }

  protected submitDialog(): void {
    const dialog = this.dialog();
    const label = this.labelControl.value.trim();
    this.labelControl.setValue(label);
    if (!dialog || this.labelControl.invalid) {
      this.labelControl.markAsTouched();
      return;
    }
    if (dialog.mode === 'add') {
      void this.register.run(label);
    } else {
      void this.rename.run(dialog.passkey, label);
    }
  }

  protected confirmUnlink(identity: AuthIdentityView): void {
    this.confirm.confirm({
      header: this.i18n.t('account.methods.removeTitle'),
      message: this.i18n.t('account.methods.removeText', {
        method: this.i18n.t(`account.methods.provider.${identity.provider}`),
        label: identity.label,
      }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('account.methods.remove'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.unlink.run(identity),
    });
  }

  protected confirmDelete(passkey: PasskeyView): void {
    this.confirm.confirm({
      header: this.i18n.t('account.passkeys.deleteTitle'),
      message: this.i18n.t('account.passkeys.deleteText', { label: passkey.label }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('account.passkeys.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.deletePasskey.run(passkey),
    });
  }

  private registrationFailed(view: ApiErrorView): void {
    if (isPasskeyCancelled(view)) {
      this.messages.add({
        severity: 'info',
        summary: this.i18n.t('account.passkeys.cancelled'),
        life: 5000,
      });
      return;
    }
    this.messages.add({
      severity: 'error',
      summary: this.i18n.t('account.passkeys.addFailed'),
      detail: this.i18n.error(view.code, errorParams(view)),
      life: 8000,
    });
  }
}
