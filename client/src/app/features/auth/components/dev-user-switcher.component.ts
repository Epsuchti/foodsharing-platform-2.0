import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';

import { ApiErrorCode, DevService, PersonasService, Role } from '../../../api';
import { errorParams } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { LoginFlowService } from '../login-flow.service';

/** `dev`: local/e2e dev API. `persona`: the test environment's persona panel behind a password (decision 173). */
export type SwitcherMode = 'dev' | 'persona';

/** A seed user as both APIs list it (DevUser, Persona). */
interface SwitcherUser {
  id: string;
  email: string;
  displayName: string;
  role: Role;
  descriptionKey: string;
}

type ListParams = { mode: 'dev' } | { mode: 'persona'; password: string };

/** Errors of the persona list that belong to the password field, not to the list. */
const PASSWORD_ERRORS = new Set<string>([
  ApiErrorCode.PersonaPanelPasswordInvalid,
  ApiErrorCode.TooManyRequests,
]);

/**
 * One click logs in as a seed user of any journey stage (D7.3).
 * - `dev` (local/e2e profile): test ids `auth.login.devSwitcher` (DataState) and `auth.login.devSwitcher.<email>`.
 * - `persona` (test environment, decision 173): the password field `auth.login.personaPanel.password` and the
 *   button `auth.login.personaPanel.unlock` come first; the password lives only in this component (never stored)
 *   and goes with every call. Then `auth.login.personaPanel` (DataState) and `auth.login.personaPanel.<email>`.
 */
@Component({
  selector: 'fs-dev-user-switcher',
  imports: [...DATA_STATE_IMPORTS, Button, InputText, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dev" [attr.aria-labelledby]="'dev-switcher-title'">
      <header class="dev__head">
        @if (persona()) {
          <span class="dev__badge"
            ><i class="pi pi-users" aria-hidden="true"></i> {{ 'auth.personaPanel.badge' | t }}</span
          >
        } @else {
          <span class="dev__badge"><i class="pi pi-wrench" aria-hidden="true"></i> Dev</span>
        }
        <div>
          <h2 id="dev-switcher-title" class="dev__title">
            {{ (persona() ? 'auth.personaPanel.title' : 'auth.devSwitcher.title') | t }}
          </h2>
          <p class="dev__text">
            {{ (persona() ? 'auth.personaPanel.text' : 'auth.devSwitcher.text') | t }}
          </p>
        </div>
      </header>
      @if (persona()) {
        <p class="dev__notice" data-testid="auth.login.personaPanel.notice">
          <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          <span>{{ 'auth.personaPanel.notice' | t }}</span>
        </p>
      }
      @if (showPasswordForm()) {
        <form class="dev__unlock" (submit)="unlock($event)" novalidate>
          <label class="dev__label" for="persona-panel-password">{{
            'auth.personaPanel.passwordLabel' | t
          }}</label>
          <div class="dev__row">
            <input
              pInputText
              id="persona-panel-password"
              type="password"
              autocomplete="off"
              [value]="draft()"
              (input)="typed($event)"
              [attr.aria-invalid]="passwordError() ? true : null"
              [attr.aria-describedby]="passwordError() ? 'persona-panel-error' : null"
              data-testid="auth.login.personaPanel.password"
            />
            <p-button
              type="submit"
              [label]="'auth.personaPanel.unlock' | t"
              icon="pi pi-lock-open"
              [loading]="users.isLoading()"
              [disabled]="!draft()"
              data-testid="auth.login.personaPanel.unlock"
            />
          </div>
          @if (passwordError(); as message) {
            <p
              id="persona-panel-error"
              class="dev__error"
              role="alert"
              data-testid="auth.login.personaPanel.error"
            >
              <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
              {{ message }}
            </p>
          }
        </form>
      }
      @if (showList()) {
        <fs-data-state
          [state]="users"
          skeleton="list"
          [skeletonRows]="6"
          [emptyTitle]="'auth.devSwitcher.empty' | t"
          emptyIcon="pi pi-users"
          [compact]="true"
          [attr.data-testid]="testIdPrefix()"
          [testId]="testIdPrefix()"
        >
          <ng-template [fsData]="users" let-list>
            <ul class="dev__list">
              @for (user of list; track user.id) {
                <li>
                  <button
                    type="button"
                    class="dev__user"
                    [disabled]="login.busy()"
                    [attr.aria-busy]="pendingId() === user.id"
                    (click)="loginAs(user)"
                    [attr.data-testid]="testIdPrefix() + '.' + user.email"
                  >
                    <fs-user-avatar
                      [name]="user.displayName"
                      [role]="user.role"
                      [colorSeed]="user.id"
                      size="md"
                    />
                    <span class="dev__who">
                      <span class="dev__name">
                        {{ user.displayName }}
                        <span class="dev__role">{{ 'common.role.' + user.role | t }}</span>
                      </span>
                      <span class="dev__desc">{{ user.descriptionKey | t }}</span>
                      <span class="dev__email">{{ user.email }}</span>
                    </span>
                    @if (pendingId() === user.id) {
                      <i class="pi pi-spin pi-spinner dev__go" aria-hidden="true"></i>
                    } @else {
                      <i class="pi pi-sign-in dev__go" aria-hidden="true"></i>
                    }
                  </button>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      }
    </section>
  `,
  styles: `
    .dev {
      display: grid;
      gap: 1rem;
      padding: clamp(1rem, 0.8rem + 1vw, 1.5rem);
      border: 1px dashed var(--fs-border-strong);
      border-radius: var(--fs-radius-lg);
      background: color-mix(in srgb, var(--fs-surface) 70%, transparent);
    }
    .dev__head {
      display: flex;
      align-items: flex-start;
      gap: 0.85rem;
    }
    .dev__badge {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      flex: 0 0 auto;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      background: var(--fs-warning-soft);
      border: 1px solid var(--fs-warning-border);
      color: var(--fs-warning-text);
      font-size: 0.75rem;
      font-weight: 750;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }
    .dev__title {
      margin: 0;
      font-size: 1.05rem;
    }
    .dev__text {
      margin: 0.15rem 0 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .dev__notice {
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      margin: 0;
      padding: 0.6rem 0.75rem;
      border: 1px solid var(--fs-danger-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-danger-soft);
      color: var(--fs-danger);
      font-size: 0.88rem;
      font-weight: 600;
    }
    .dev__notice .pi {
      margin-top: 0.15rem;
    }
    .dev__unlock {
      display: grid;
      gap: 0.4rem;
    }
    .dev__label {
      font-size: 0.88rem;
      font-weight: 650;
    }
    .dev__row {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .dev__row input {
      flex: 1 1 12rem;
      min-width: 0;
    }
    .dev__error {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      margin: 0;
      color: var(--fs-danger);
      font-size: 0.85rem;
    }
    .dev__list {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 17rem), 1fr));
      gap: 0.5rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .dev__user {
      display: flex;
      align-items: center;
      gap: 0.7rem;
      width: 100%;
      height: 100%;
      padding: 0.6rem 0.75rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      text-align: left;
      cursor: pointer;
      transition:
        border-color 150ms ease,
        box-shadow 150ms ease;
    }
    .dev__user:hover:not(:disabled) {
      border-color: var(--fs-primary);
      box-shadow: var(--fs-shadow-sm);
    }
    .dev__user:disabled {
      cursor: progress;
      opacity: 0.7;
    }
    .dev__who {
      display: grid;
      flex: 1 1 auto;
      min-width: 0;
      line-height: 1.3;
    }
    .dev__name {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.2rem 0.5rem;
      font-weight: 700;
    }
    .dev__role {
      color: var(--fs-primary-strong);
      font-size: 0.75rem;
      font-weight: 700;
    }
    .dev__desc {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .dev__email {
      overflow: hidden;
      color: var(--fs-text-muted);
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 0.72rem;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .dev__go {
      flex: 0 0 auto;
      color: var(--fs-text-muted);
    }
  `,
})
export class DevUserSwitcherComponent {
  private readonly devApi = inject(DevService);
  private readonly personasApi = inject(PersonasService);
  private readonly flow = inject(LoginFlowService);
  private readonly i18n = inject(I18nService);

  /** Where to go after the login (validated by LoginFlowService). */
  readonly returnUrl = input<string | null | undefined>(undefined);
  readonly mode = input<SwitcherMode>('dev');

  protected readonly persona = computed(() => this.mode() === 'persona');
  protected readonly testIdPrefix = computed(() =>
    this.persona() ? 'auth.login.personaPanel' : 'auth.login.devSwitcher',
  );

  /** What the user typed; the unlocked password is the one sent with every persona call. */
  protected readonly draft = signal('');
  private readonly password = signal<string | null>(null);

  protected readonly users = loadResource<SwitcherUser[], ListParams>({
    params: () => {
      if (!this.persona()) {
        return { mode: 'dev' };
      }
      const password = this.password();
      return password === null ? undefined : { mode: 'persona', password };
    },
    loader: (params) =>
      params.mode === 'dev'
        ? this.devApi.listDevUsers()
        : this.personasApi.listPersonas({ personaPanelRequest: { password: params.password } }),
  });

  /** A wrong password or too many attempts: shown at the field, the list stays hidden. */
  protected readonly passwordError = computed(() => {
    const error = this.users.error();
    return this.persona() && error && PASSWORD_ERRORS.has(error.code)
      ? this.i18n.error(error.code, errorParams(error))
      : null;
  });
  protected readonly showPasswordForm = computed(
    () => this.persona() && (this.password() === null || this.passwordError() !== null),
  );
  protected readonly showList = computed(
    () => !this.persona() || (this.password() !== null && this.passwordError() === null),
  );

  protected readonly pendingId = signal<string | null>(null);

  protected readonly login = runMutation(
    (user: SwitcherUser) =>
      this.persona()
        ? this.personasApi.loginAsPersona({
            personaLoginRequest: { password: this.password() ?? '', userId: user.id },
          })
        : this.devApi.loginAsDevUser({ devLoginRequest: { userId: user.id } }),
    {
      onSuccess: (info) => this.flow.finish(info, this.returnUrl(), 'DEV'),
      onError: () => this.pendingId.set(null),
    },
  );

  protected typed(event: Event): void {
    this.draft.set((event.target as HTMLInputElement).value);
  }

  protected unlock(event: Event): void {
    event.preventDefault();
    const password = this.draft();
    if (!password) {
      return;
    }
    if (this.password() === password) {
      this.users.reload();
    } else {
      this.password.set(password);
    }
  }

  protected loginAs(user: SwitcherUser): void {
    this.pendingId.set(user.id);
    void this.login.run(user);
  }
}
