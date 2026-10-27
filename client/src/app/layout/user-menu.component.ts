import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Popover } from '@openng/optimus-ui/popover';

import { AuthFacadeService } from '../core/auth/auth-facade.service';
import { SessionService } from '../core/auth/session.service';
import { TPipe } from '../core/i18n/t.pipe';
import { PasskeyNudgeComponent } from '../features/auth/components/passkey-nudge.component';
import { UserAvatarComponent } from '../shared/user-chip/user-avatar.component';
import { NavContextService } from './nav-context.service';

/**
 * Avatar button with the user menu: profile (head), items with area 'user' from nav.config.ts, logout; plus the
 * passkey nudge after a login. Owned by T6 (auth/account frontend) from P-2 on.
 */
@Component({
  selector: 'fs-user-menu',
  imports: [PasskeyNudgeComponent, Popover, RouterLink, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (session.user(); as user) {
      <button
        type="button"
        class="user-btn"
        (click)="op.toggle($event)"
        aria-haspopup="true"
        data-testid="shell.userMenu"
      >
        <fs-user-avatar
          aria-hidden="true"
          [name]="user.displayName"
          [avatarUrl]="user.avatarUrl"
          [role]="user.role"
          [colorSeed]="user.id"
          size="sm"
        />
        <!-- accessible name = visible name + hidden hint (WCAG 2.5.3 label in name) -->
        <span class="user-btn__name">{{ user.displayName }}</span>
        <span class="fs-visually-hidden">{{ 'layout.userMenu.open' | t }}</span>
        <i class="pi pi-chevron-down user-btn__chevron" aria-hidden="true"></i>
      </button>
      <p-popover #op>
        <div class="um">
          <a
            class="um__head"
            routerLink="/account/profile"
            (click)="op.hide()"
            data-testid="nav.user.profile"
          >
            <fs-user-avatar
              [name]="user.displayName"
              [avatarUrl]="user.avatarUrl"
              [role]="user.role"
              [colorSeed]="user.id"
              size="lg"
            />
            <div class="um__who">
              <strong>{{ user.displayName }}</strong>
              <span>{{ user.email }}</span>
              <span class="um__role" data-testid="shell.userMenu.role">{{
                'common.role.' + user.role | t
              }}</span>
            </div>
            <i class="pi pi-chevron-right um__go" aria-hidden="true"></i>
          </a>
          <ul class="menu-list" role="menu">
            @for (item of nav.user(); track item.id) {
              <li role="none">
                <a
                  role="menuitem"
                  class="menu-list__item"
                  [routerLink]="item.route"
                  (click)="op.hide()"
                  [attr.data-testid]="'nav.user.' + item.id"
                >
                  <i [class]="item.icon" aria-hidden="true"></i>
                  <span>{{ item.labelKey | t }}</span>
                </a>
              </li>
            }
            <li role="separator" class="menu-list__separator"></li>
            <li role="none">
              <button
                type="button"
                role="menuitem"
                class="menu-list__item"
                (click)="logout(op)"
                data-testid="shell.userMenu.logout"
              >
                <i class="pi pi-sign-out" aria-hidden="true"></i>
                <span>{{ 'nav.logout' | t }}</span>
              </button>
            </li>
          </ul>
        </div>
      </p-popover>
      <fs-passkey-nudge />
    }
  `,
  styles: `
    .user-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      max-width: 14rem;
      padding: 0.25rem 0.6rem 0.25rem 0.25rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      font-weight: 600;
      cursor: pointer;
      transition:
        border-color 150ms ease,
        box-shadow 150ms ease;
    }
    .user-btn:hover {
      border-color: var(--fs-border-strong);
      box-shadow: var(--fs-shadow-sm);
    }
    .user-btn__name {
      overflow: hidden;
      font-size: 0.9rem;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .user-btn__chevron {
      color: var(--fs-text-muted);
      font-size: 0.7rem;
    }
    @media (max-width: 47.99rem) {
      .user-btn {
        padding: 0.15rem;
      }
      .user-btn__name,
      .user-btn__chevron {
        display: none;
      }
    }
    .um {
      display: grid;
      min-width: 16rem;
      gap: 0.5rem;
    }
    .um__head {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.5rem 0.5rem 0.75rem;
      border-bottom: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-sm) var(--fs-radius-sm) 0 0;
      color: var(--fs-text);
      font-weight: inherit;
      text-decoration: none;
    }
    .um__head:hover,
    .um__head:focus-visible {
      background: var(--fs-surface-muted);
    }
    .um__go {
      margin-left: auto;
      color: var(--fs-text-muted);
      font-size: 0.75rem;
    }
    .um__who {
      display: grid;
      min-width: 0;
      line-height: 1.3;
    }
    .um__who span {
      overflow: hidden;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
      text-overflow: ellipsis;
    }
    .um__who .um__role {
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
  `,
})
export class UserMenuComponent {
  protected readonly session = inject(SessionService);
  protected readonly nav = inject(NavContextService);
  private readonly auth = inject(AuthFacadeService);

  protected logout(popover: Popover): void {
    popover.hide();
    this.auth.logout().subscribe({ error: () => undefined });
  }
}
