import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { SessionService } from '../core/auth/session.service';
import { TPipe } from '../core/i18n/t.pipe';
import { BrandComponent } from './brand.component';
import { LanguageMenuComponent } from './language-menu.component';
import { NavContextService } from './nav-context.service';
import { ThemeMenuComponent } from './theme-menu.component';
import { UserMenuComponent } from './user-menu.component';

export type ChromeMode = 'full' | 'minimal' | 'none';

/** Sticky app header: brand, main navigation (desktop), language + theme menus, user menu or login. */
@Component({
  selector: 'fs-header',
  imports: [
    BrandComponent,
    LanguageMenuComponent,
    RouterLink,
    RouterLinkActive,
    TPipe,
    ThemeMenuComponent,
    UserMenuComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-header', '[attr.data-chrome]': 'chrome()' },
  template: `
    <div class="hdr">
      <fs-brand [link]="session.isAuthenticated() ? '/dashboard' : '/'" />
      @if (chrome() === 'full') {
        <nav class="hdr__nav" [attr.aria-label]="'layout.header.mainNav' | t">
          @for (item of nav.main(); track item.id) {
            <a
              class="hdr__link"
              [routerLink]="item.route"
              routerLinkActive="hdr__link--active"
              [routerLinkActiveOptions]="{ exact: !!item.exact }"
              [attr.data-testid]="'nav.' + item.id"
            >
              <i [class]="item.icon" aria-hidden="true"></i>
              <span>{{ item.labelKey | t }}</span>
            </a>
          }
        </nav>
      }
      <div class="hdr__actions">
        <fs-language-menu />
        <fs-theme-menu />
        @if (chrome() === 'full') {
          @if (session.isAuthenticated()) {
            <fs-user-menu />
          } @else {
            <a class="fs-btn fs-btn--sm hdr__login" routerLink="/login" data-testid="shell.login">
              <i class="pi pi-sign-in" aria-hidden="true"></i>
              <span>{{ 'nav.login' | t }}</span>
            </a>
          }
        }
      </div>
    </div>
  `,
  styles: `
    :host {
      position: sticky;
      z-index: 40;
      top: 0;
      display: block;
      border-bottom: 1px solid var(--fs-header-border);
      background: var(--fs-header-bg);
      backdrop-filter: saturate(1.4) blur(14px);
      -webkit-backdrop-filter: saturate(1.4) blur(14px);
    }
    .hdr {
      display: flex;
      align-items: center;
      gap: 1.5rem;
      width: min(100%, var(--fs-page-max));
      height: var(--fs-header-h);
      margin: 0 auto;
      padding: 0 var(--fs-gutter);
    }
    .hdr__nav {
      display: none;
      align-items: center;
      gap: 0.25rem;
      min-width: 0;
    }
    @media (min-width: 48rem) {
      .hdr__nav {
        display: flex;
      }
    }
    .hdr__link {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.8rem;
      border-radius: var(--fs-radius-sm);
      color: var(--fs-text-muted);
      font-size: 0.92rem;
      font-weight: 600;
      text-decoration: none;
      transition:
        background-color 150ms ease,
        color 150ms ease;
    }
    .hdr__link .pi {
      font-size: 0.95rem;
    }
    .hdr__link:hover {
      background: var(--fs-surface-muted);
      color: var(--fs-text);
    }
    .hdr__link--active,
    .hdr__link--active:hover {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .hdr__actions {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      margin-left: auto;
    }
    .hdr__login {
      gap: 0.5rem;
      margin-left: 0.35rem;
      text-decoration: none;
    }
    @media (max-width: 30rem) {
      /* icon only on narrow phones; the label stays for screen readers (link name) */
      .hdr__login span {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0 0 0 0);
        white-space: nowrap;
      }
    }
  `,
})
export class HeaderComponent {
  protected readonly session = inject(SessionService);
  protected readonly nav = inject(NavContextService);
  readonly chrome = input<ChromeMode>('full');
}
