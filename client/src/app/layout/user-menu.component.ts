import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Popover } from '@openng/optimus-ui/popover';

import { AuthFacadeService } from '../core/auth/auth-facade.service';
import { SessionService } from '../core/auth/session.service';
import { TPipe } from '../core/i18n/t.pipe';
import { UserAvatarComponent } from '../shared/user-chip/user-avatar.component';
import { NavContextService } from './nav-context.service';

/**
 * Avatar button with the user menu (items with area 'user' from nav.config.ts + logout).
 * Owned by T6 (auth/account frontend) from P-2 on.
 */
@Component({
  selector: 'fs-user-menu',
  imports: [Popover, RouterLink, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (session.user(); as user) {
      <button
        type="button"
        class="user-btn"
        (click)="op.toggle($event)"
        [attr.aria-label]="user.displayName + ' – ' + ('layout.userMenu.open' | t)"
        aria-haspopup="true"
        data-testid="shell.userMenu"
      >
        <fs-user-avatar
          [name]="user.displayName"
          [avatarUrl]="user.avatarUrl"
          [role]="user.role"
          [colorSeed]="user.id"
          size="sm"
        />
        <span class="user-btn__name">{{ user.displayName }}</span>
        <i class="pi pi-chevron-down user-btn__chevron" aria-hidden="true"></i>
      </button>
      <p-popover #op>
        <div class="um">
          <div class="um__head">
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
              <span class="um__role">{{ 'common.role.' + user.role | t }}</span>
            </div>
          </div>
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
      padding: 0.25rem 0.25rem 0.75rem;
      border-bottom: 1px solid var(--fs-border);
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
