import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { SessionService } from '../core/auth/session.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TPipe } from '../core/i18n/t.pipe';
import { StartupService } from '../core/startup/startup.service';
import { FeatureToggleKey } from '../api';
import { FeatureToggleService } from '../core/feature-toggles/feature-toggle.service';
import { BellButtonComponent } from '../features/bells/components/bell-button.component';
import { GlobalSearchComponent } from '../features/search/components/global-search.component';
import { WhatsNewDotComponent } from '../features/content/components/whats-new-dot.component';
import { BrandComponent } from './brand.component';
import { LanguageMenuComponent } from './language-menu.component';
import { M6_PAGES_READY } from './nav.config';
import { NavBadgeComponent } from './nav-badge.component';
import { NavContextService } from './nav-context.service';
import { RegionSwitcherComponent } from './region-switcher.component';
import { ScrollHintDirective } from '../shared/scroll-hint/scroll-hint.directive';
import { ThemeMenuComponent } from './theme-menu.component';
import { UserMenuComponent } from './user-menu.component';

export type ChromeMode = 'full' | 'minimal' | 'none';

/** Sticky app header: brand, main navigation (desktop), language + theme menus, user menu or login. */
@Component({
  selector: 'fs-header',
  imports: [
    BellButtonComponent,
    RegionSwitcherComponent,
    BrandComponent,
    GlobalSearchComponent,
    LanguageMenuComponent,
    NavBadgeComponent,
    RouterLink,
    RouterLinkActive,
    ScrollHintDirective,
    TPipe,
    ThemeMenuComponent,
    UserMenuComponent,
    WhatsNewDotComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-header', '[attr.data-chrome]': 'chrome()' },
  template: `
    <div class="hdr">
      <fs-brand [link]="session.isAuthenticated() ? '/dashboard' : '/'" />
      @if (chrome() === 'full') {
        <nav
          class="hdr__nav"
          fsScrollHint
          [class.hdr__nav--compact]="compactNav()"
          [attr.aria-label]="'layout.header.mainNav' | t"
        >
          @for (item of mainItems(); track item.id) {
            <a
              class="hdr__link"
              [class.hdr__link--labelled]="labelled().has(item.id)"
              [routerLink]="item.route"
              routerLinkActive="hdr__link--active"
              [routerLinkActiveOptions]="{ exact: !!item.exact }"
              [attr.data-testid]="'nav.' + item.id"
              [attr.title]="item.labelKey | t"
            >
              <i [class]="item.icon" aria-hidden="true"></i>
              <span>{{ item.labelKey | t }}</span>
              <fs-nav-badge [item]="item" />
            </a>
          }
        </nav>
      }
      <div class="hdr__actions">
        @if (chrome() === 'full' && searchEnabled()) {
          <!-- C5: replaces the "search" nav link from 48rem; phones reach /search through the drawer -->
          <fs-global-search class="hdr__search" />
        }
        @if (chrome() === 'full' && session.isAuthenticated()) {
          <fs-region-switcher />
        }
        @if (chrome() === 'full') {
          <!-- C4: the dot (newest release note not seen yet) loads after the first page and never shifts the header -->
          <a
            class="hdr-btn hdr__whats-new"
            routerLink="/release-notes"
            [attr.aria-label]="'layout.header.whatsNew' | t"
            [attr.title]="'layout.header.whatsNew' | t"
            data-testid="shell.whatsNew"
          >
            <i class="pi pi-gift" aria-hidden="true"></i>
            @defer (when startup.pageShown()) {
              <fs-whats-new-dot />
            }
          </a>
        }
        <fs-language-menu />
        <fs-theme-menu />
        @if (chrome() === 'full') {
          @if (session.isAuthenticated()) {
            <fs-bell-button />
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
    <!-- journey bar of newcomers (M2 T22 fills it from the shell) -->
    <ng-content select="[fsJourneyBar]" />
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
    /*
     * Links never shrink below a 40px target (axe target-size, D11.5.3): with many items (ORGA) the nav scrolls
     * horizontally instead of squeezing them.
     */
    .hdr__nav {
      display: none;
      align-items: center;
      gap: 0.25rem;
      min-width: 0;
      overflow-x: auto;
      scrollbar-width: none;
    }
    @media (min-width: 48rem) {
      .hdr__nav {
        display: flex;
      }
    }
    .hdr__link {
      /*
       * Containing block of the visually hidden label: without it the absolutely positioned spans of links scrolled
       * out of the nav escaped its overflow and widened the page (ORGA: 220–350px horizontal scroll, design review M7 R1).
       */
      position: relative;
      display: inline-flex;
      flex: none;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      min-width: 2.5rem;
      min-height: 2.5rem;
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
    .hdr__link span {
      white-space: nowrap;
    }
    /*
     * Icons only (the label stays the link name and the tooltip) on tablets / small laptops and whenever a role has
     * more than five main items (ORGA, ambassadors) or long labels, so labels never wrap and links never shrink below 24px.
     */
    .hdr__nav--compact .hdr__link {
      padding: 0.5rem 0.65rem;
    }
    .hdr__nav--compact .hdr__link span {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
    }
    /* wide screens: the first items keep their label in the compact nav (design review M7 R2: 9 unlabelled icons) */
    @media (min-width: 75rem) {
      .hdr__nav--compact .hdr__link--labelled {
        padding: 0.5rem 0.8rem;
      }
      .hdr__nav--compact .hdr__link--labelled span {
        position: static;
        width: auto;
        height: auto;
        overflow: visible;
        clip: auto;
      }
    }
    @media (min-width: 48rem) and (max-width: 74.99rem) {
      .hdr__link span {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0 0 0 0);
      }
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
    .hdr__search {
      width: clamp(9rem, 17vw, 17rem);
      margin-right: 0.35rem;
    }
    @media (max-width: 47.99rem) {
      .hdr__search {
        display: none;
      }
    }
    .hdr__whats-new {
      position: relative;
      text-decoration: none;
    }
    @media (max-width: 47.99rem) {
      /* phones: the release notes stay reachable from the footer and the about page */
      .hdr__whats-new {
        display: none;
      }
    }
    .hdr__login {
      gap: 0.5rem;
      margin-left: 0.35rem;
      text-decoration: none;
    }
    @media (max-width: 47.99rem) {
      /* the mobile tab bar shows the bell badge on "Menu" and the drawer links /bells (N3) */
      fs-bell-button {
        display: none;
      }
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
  protected readonly startup = inject(StartupService);
  private readonly i18n = inject(I18nService);
  private readonly toggles = inject(FeatureToggleService);
  /** Header search (C5): logged in and GLOBAL_SEARCH on (off until the toggles are loaded). */
  protected readonly searchEnabled = computed(
    () => this.session.isAuthenticated() && M6_PAGES_READY.search && this.toggles.isEnabled(FeatureToggleKey.GlobalSearch),
  );
  /** The search box stands in for the "search" link of the main navigation. */
  protected readonly mainItems = computed(() =>
    this.searchEnabled() ? this.nav.main().filter((item) => item.id !== 'search') : this.nav.main(),
  );
  /** Icons only with more than five items or when five long labels (fr/it/de) would collide with the region switcher. */
  protected readonly compactNav = computed(() => {
    const items = this.mainItems();
    const chars = items.reduce((sum, item) => sum + this.i18n.t(item.labelKey).length, 0);
    return items.length > 5 || chars > 56;
  });
  /**
   * Compact nav: the leading items (dashboard and the role's main item, e.g. the newcomer journey or pickups) keep
   * their label on wide screens — at most two and at most 24 characters together, so the nav keeps its width budget.
   */
  protected readonly labelled = computed(() => {
    const ids = new Set<string>();
    let chars = 0;
    for (const item of this.mainItems()) {
      chars += this.i18n.t(item.labelKey).length;
      if (ids.size >= 2 || chars > 24) break;
      ids.add(item.id);
    }
    return ids;
  });
  readonly chrome = input<ChromeMode>('full');
}
