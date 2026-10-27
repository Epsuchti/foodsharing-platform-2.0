import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';

import { DevService, DevUser } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { LoginFlowService } from '../login-flow.service';

/**
 * Dev user switcher (local/e2e profile only, D7.3): one click logs in as a seed user of any journey stage.
 * Test ids: `auth.login.devSwitcher` (DataState) and `auth.login.devSwitcher.<email>` per user.
 */
@Component({
  selector: 'fs-dev-user-switcher',
  imports: [...DATA_STATE_IMPORTS, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dev" [attr.aria-labelledby]="'dev-switcher-title'">
      <header class="dev__head">
        <span class="dev__badge"><i class="pi pi-wrench" aria-hidden="true"></i> Dev</span>
        <div>
          <h2 id="dev-switcher-title" class="dev__title">{{ 'auth.devSwitcher.title' | t }}</h2>
          <p class="dev__text">{{ 'auth.devSwitcher.text' | t }}</p>
        </div>
      </header>
      <fs-data-state
        [state]="users"
        skeleton="list"
        [skeletonRows]="6"
        [emptyTitle]="'auth.devSwitcher.empty' | t"
        emptyIcon="pi pi-users"
        [compact]="true"
        data-testid="auth.login.devSwitcher"
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
                  [attr.data-testid]="'auth.login.devSwitcher.' + user.email"
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
  private readonly flow = inject(LoginFlowService);

  /** Where to go after the login (validated by LoginFlowService). */
  readonly returnUrl = input<string | null | undefined>(undefined);

  protected readonly users = loadResource({ loader: () => this.devApi.listDevUsers() });
  protected readonly pendingId = signal<string | null>(null);

  protected readonly login = runMutation(
    (user: DevUser) => this.devApi.loginAsDevUser({ devLoginRequest: { userId: user.id } }),
    {
      onSuccess: (info) => this.flow.finish(info, this.returnUrl(), 'DEV'),
      onError: () => this.pendingId.set(null),
    },
  );

  protected loginAs(user: DevUser): void {
    this.pendingId.set(user.id);
    void this.login.run(user);
  }
}
