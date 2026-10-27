import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Drawer } from '@openng/optimus-ui/drawer';

import { AuthFacadeService } from '../core/auth/auth-facade.service';
import { SessionService } from '../core/auth/session.service';
import { I18nService } from '../core/i18n/i18n.service';
import { LANGUAGES, UiLanguage } from '../core/i18n/languages';
import { TPipe } from '../core/i18n/t.pipe';
import { THEME_PREFERENCES, ThemePreference, ThemeService } from '../core/theme/theme.service';
import { UserAvatarComponent } from '../shared/user-chip/user-avatar.component';
import { NavContextService } from './nav-context.service';

/**
 * Mobile bottom tab bar (below 48rem, logged in): up to four tabs from nav.config.ts (`mobileTab`) plus
 * "Menu", which opens a drawer with all navigation, language, theme and logout.
 */
@Component({
  selector: 'fs-mobile-tab-bar',
  imports: [Drawer, RouterLink, RouterLinkActive, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="tabs" [attr.aria-label]="'layout.tabBar.label' | t" data-testid="shell.tabBar">
      @for (item of nav.tabs(); track item.id) {
        <a
          class="tabs__tab"
          [routerLink]="item.route"
          routerLinkActive="tabs__tab--active"
          #rla="routerLinkActive"
          [attr.aria-current]="rla.isActive ? 'page' : null"
          [attr.data-testid]="'nav.tab.' + item.id"
        >
          <i [class]="item.icon" aria-hidden="true"></i>
          <span>{{ item.labelKey | t }}</span>
        </a>
      }
      <button
        type="button"
        class="tabs__tab"
        [class.tabs__tab--active]="menuOpen()"
        [attr.aria-expanded]="menuOpen()"
        (click)="menuOpen.set(true)"
        data-testid="nav.tab.menu"
      >
        <i class="pi pi-bars" aria-hidden="true"></i>
        <span>{{ 'nav.menu' | t }}</span>
      </button>
    </nav>

    <p-drawer
      [visible]="menuOpen()"
      (visibleChange)="menuOpen.set($event)"
      position="right"
      appendTo="body"
      [header]="'nav.menu' | t"
      [ariaCloseLabel]="'common.close' | t"
      styleClass="fs-mobile-menu"
      data-testid="shell.mobileMenu"
    >
      @if (session.user(); as user) {
        <div class="mm__user">
          <fs-user-avatar
            [name]="user.displayName"
            [avatarUrl]="user.avatarUrl"
            [role]="user.role"
            [colorSeed]="user.id"
            size="lg"
          />
          <div class="mm__who">
            <strong>{{ user.displayName }}</strong>
            <span>{{ 'common.role.' + user.role | t }}</span>
          </div>
        </div>
      }
      <ul class="menu-list">
        @for (item of nav.main(); track item.id) {
          <li>
            <a
              class="menu-list__item"
              [routerLink]="item.route"
              (click)="menuOpen.set(false)"
              [attr.data-testid]="'nav.drawer.' + item.id"
            >
              <i [class]="item.icon" aria-hidden="true"></i>
              <span>{{ item.labelKey | t }}</span>
            </a>
          </li>
        }
      </ul>
      <!-- Two lists with a rule in between: a separator <li> would break the list semantics (axe "list"). -->
      <hr class="menu-list__separator mm__rule" />
      <ul class="menu-list">
        @for (item of nav.user(); track item.id) {
          <li>
            <a
              class="menu-list__item"
              [routerLink]="item.route"
              (click)="menuOpen.set(false)"
              [attr.data-testid]="'nav.drawer.' + item.id"
            >
              <i [class]="item.icon" aria-hidden="true"></i>
              <span>{{ item.labelKey | t }}</span>
            </a>
          </li>
        }
      </ul>

      <section class="mm__section">
        <h2 class="mm__heading">{{ 'layout.header.language' | t }}</h2>
        <div class="mm__chips">
          @for (language of languages; track language) {
            <button
              type="button"
              class="mm__chip"
              [class.mm__chip--active]="language === i18n.language()"
              [attr.aria-pressed]="language === i18n.language()"
              (click)="setLanguage(language)"
              [attr.data-testid]="'shell.drawer.language.' + language"
            >
              {{ 'lang.' + language | t }}
            </button>
          }
        </div>
      </section>
      <section class="mm__section">
        <h2 class="mm__heading">{{ 'layout.header.theme' | t }}</h2>
        <div class="mm__chips">
          @for (mode of themes; track mode) {
            <button
              type="button"
              class="mm__chip"
              [class.mm__chip--active]="mode === theme.preference()"
              [attr.aria-pressed]="mode === theme.preference()"
              (click)="theme.setPreference(mode)"
              [attr.data-testid]="'shell.drawer.theme.' + mode"
            >
              {{ 'theme.' + mode | t }}
            </button>
          }
        </div>
      </section>
      <button
        type="button"
        class="menu-list__item mm__logout"
        (click)="logout()"
        data-testid="shell.drawer.logout"
      >
        <i class="pi pi-sign-out" aria-hidden="true"></i>
        <span>{{ 'nav.logout' | t }}</span>
      </button>
    </p-drawer>
  `,
  styles: `
    :host {
      position: fixed;
      z-index: 45;
      right: 0;
      bottom: 0;
      left: 0;
      display: block;
    }
    @media (min-width: 48rem) {
      :host {
        display: none;
      }
    }
    .tabs {
      display: grid;
      grid-auto-columns: 1fr;
      grid-auto-flow: column;
      height: calc(var(--fs-tabbar-h) + env(safe-area-inset-bottom));
      padding-bottom: env(safe-area-inset-bottom);
      border-top: 1px solid var(--fs-header-border);
      background: var(--fs-header-bg);
      backdrop-filter: saturate(1.4) blur(14px);
      -webkit-backdrop-filter: saturate(1.4) blur(14px);
    }
    .tabs__tab {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.2rem;
      min-width: 0;
      border: 0;
      background: none;
      color: var(--fs-text-muted);
      font: inherit;
      font-size: 0.7rem;
      font-weight: 650;
      text-decoration: none;
      cursor: pointer;
    }
    .tabs__tab .pi {
      display: grid;
      width: 3.25rem;
      height: 1.85rem;
      place-items: center;
      border-radius: 999px;
      font-size: 1.15rem;
      transition: background-color 150ms ease;
    }
    .tabs__tab--active {
      color: var(--fs-primary-strong);
    }
    .tabs__tab--active .pi {
      background: var(--fs-primary-soft);
    }
    .mm__user {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 1rem;
      padding: 0.85rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
    }
    .mm__who {
      display: grid;
    }
    .mm__who span {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .mm__section {
      margin-top: 1.25rem;
    }
    .mm__rule {
      border: 0;
    }
    .mm__heading {
      margin: 0 0 0.5rem;
      color: var(--fs-text-muted);
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .mm__chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
    }
    .mm__chip {
      padding: 0.45rem 0.8rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
    }
    .mm__chip--active {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .mm__logout {
      margin-top: 1.5rem;
    }
  `,
})
export class MobileTabBarComponent {
  protected readonly nav = inject(NavContextService);
  protected readonly session = inject(SessionService);
  protected readonly i18n = inject(I18nService);
  protected readonly theme = inject(ThemeService);
  private readonly auth = inject(AuthFacadeService);

  protected readonly languages = LANGUAGES;
  protected readonly themes: readonly ThemePreference[] = THEME_PREFERENCES;
  protected readonly menuOpen = signal(false);

  protected setLanguage(language: UiLanguage): void {
    void this.i18n.setLanguage(language);
  }

  protected logout(): void {
    this.menuOpen.set(false);
    this.auth.logout().subscribe({ error: () => undefined });
  }
}
