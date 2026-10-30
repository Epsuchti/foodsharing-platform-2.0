import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Popover } from '@openng/optimus-ui/popover';

import { AuthFacadeService } from '../core/auth/auth-facade.service';
import { SessionService } from '../core/auth/session.service';
import { TPipe } from '../core/i18n/t.pipe';
import { PasskeyNudgeComponent } from '../features/auth/components/passkey-nudge.component';
import { UserAvatarComponent } from '../shared/user-chip/user-avatar.component';
import { DisplayPrefsComponent } from './display-prefs.component';
import { NavContextService } from './nav-context.service';
import { LazyPopover, PopoverLike } from './lazy-popover';
import { SidenavCardDirective, SidenavPopovers } from './sidenav-popover';
import { SidenavStateService } from './sidenav-state';

/**
 * Avatar button with the user menu: profile (head), items with area 'user' from nav.config.ts, logout; plus the
 * passkey nudge after a login. Owned by T6 (auth/account frontend) from P-2 on.
 */
@Component({
  selector: 'fs-user-menu',
  imports: [DisplayPrefsComponent, PasskeyNudgeComponent, Popover, RouterLink, SidenavCardDirective, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (session.user(); as user) {
      @if (variant() === 'sidenav') {
      <!-- desktop side menu (decision 133): the bottom block opens a floating card, bottom aligned -->
      <button
        type="button"
        class="sn-row sn-user"
        #sideTrigger
        (click)="popovers?.toggle('user', sideTrigger)"
        aria-haspopup="true"
        [attr.aria-expanded]="cardOpen()"
        aria-controls="sn-card-user"
        [attr.data-label]="user.displayName"
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
        <span class="sn-user__who">
          <strong>{{ user.displayName }}</strong>
          <span data-testid="shell.userMenu.role">{{ 'common.role.' + user.role | t }}</span>
        </span>
        <span class="fs-visually-hidden">{{ 'layout.userMenu.open' | t }}</span>
      </button>
      <div id="sn-card-user" fsSidenavCard="user" anchor="bottom" data-testid="shell.userMenu.card">
        <div class="sn-card__body">
          <a class="um__head" [routerLink]="profileRoute()" (click)="popovers?.close(true)" data-testid="nav.user.profile">
            <fs-user-avatar
              [name]="user.displayName"
              [avatarUrl]="user.avatarUrl"
              [role]="user.role"
              [colorSeed]="user.id"
              size="md"
            />
            <div class="um__who">
              <strong>{{ user.displayName }}</strong>
              <span>{{ user.email }}</span>
              <span class="um__role">{{ 'common.role.' + user.role | t }}</span>
            </div>
          </a>
          <ul class="sn__list">
            @for (item of menuItems(); track item.id) {
              <li>
                <a class="sn-row" [routerLink]="item.route" (click)="popovers?.close(true)" [attr.data-testid]="'nav.user.' + item.id">
                  <i [class]="item.icon" aria-hidden="true"></i>
                  <span class="sn-row__label">{{ item.labelKey | t }}</span>
                </a>
              </li>
            }
          </ul>
          <div class="sn-card__sep"></div>
          <fs-display-prefs testIdPrefix="shell.userMenu" [disclosure]="true" />
          <div class="sn-card__sep"></div>
          <h3 class="sn-card__h">{{ 'layout.sidenav.shortcuts' | t }}</h3>
          <div class="sn-keys" data-testid="shell.userMenu.shortcuts">
            <span><kbd>Ctrl</kbd> / <kbd>⌘</kbd> <kbd>K</kbd> {{ 'layout.sidenav.shortcutSearch' | t }}</span>
            @if (sidenav.canCollapse()) {
              <span><kbd>[</kbd> {{ 'layout.sidenav.shortcutToggle' | t }}</span>
            }
          </div>
          <div class="sn-card__sep"></div>
          <button type="button" class="sn-row" (click)="logoutFromCard()" data-testid="shell.userMenu.logout">
            <i class="pi pi-sign-out" aria-hidden="true"></i>
            <span class="sn-row__label">{{ 'nav.logout' | t }}</span>
          </button>
        </div>
      </div>
      } @else {
      <button
        type="button"
        class="user-btn"
        #menuTrigger
        (click)="menu.toggle($event)"
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
      <!-- code prefetched after the first page, rendered on the first click (LazyPopover, D11.5.3) -->
      @defer (on interaction(menuTrigger); prefetch when menu.prefetch()) {
      <p-popover #op>
        <div class="um">
          <a
            class="um__head"
            [routerLink]="profileRoute()"
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
            @for (item of menuItems(); track item.id) {
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
      }
      <fs-passkey-nudge />
    }
  `,
  styles: `
    .user-btn {
      position: relative;
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
    /*
     * laptops: avatar only, the name stays in the button's accessible name — the main navigation needs the room
     * (design review M7 R3: at 1280px the nav kept one link next to search, region and the named user menu)
     */
    @media (min-width: 48rem) and (max-width: 89.99rem) {
      .user-btn__name {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0 0 0 0);
      }
    }
    @media (max-width: 47.99rem) {
      .user-btn {
        padding: 0.15rem;
      }
      @media (pointer: coarse) {
        .user-btn {
          min-width: 2.75rem;
          min-height: 2.75rem;
          justify-content: center;
        }
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
  private readonly popover = viewChild<PopoverLike>('op');
  protected readonly menu = new LazyPopover(this.popover);
  protected readonly session = inject(SessionService);
  protected readonly nav = inject(NavContextService);
  private readonly auth = inject(AuthFacadeService);
  /** Only inside fs-sidenav. */
  protected readonly popovers = inject(SidenavPopovers, { optional: true });
  protected readonly sidenav = inject(SidenavStateService);
  readonly variant = input<'header' | 'sidenav'>('header');
  protected readonly cardOpen = computed(() => this.popovers?.openId() === 'user');

  /** The head is the profile entry: the public profile (`profile` item, M6) once it is visible, else the account. */
  protected readonly profileRoute = computed(
    () => this.nav.user().find((item) => item.id === 'profile')?.route ?? '/account/profile',
  );
  /** Items below the head (the `profile` item is rendered as the head itself). */
  protected readonly menuItems = computed(() => this.nav.user().filter((item) => item.id !== 'profile'));

  protected logoutFromCard(): void {
    this.popovers?.close(false);
    this.auth.logout().subscribe({ error: () => undefined });
  }

  protected logout(popover: PopoverLike): void {
    popover.hide();
    this.auth.logout().subscribe({ error: () => undefined });
  }
}
