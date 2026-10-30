import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  PLATFORM_ID,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';

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
import { M6_PAGES_READY, orderHeaderNav, splitHeaderNav } from './nav.config';
import { NavBadgeComponent } from './nav-badge.component';
import { NavBadgesService } from './nav-badges.service';
import { NavContextService } from './nav-context.service';
import { RegionSwitcherComponent } from './region-switcher.component';
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
    TPipe,
    ThemeMenuComponent,
    UserMenuComponent,
    WhatsNewDotComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-header',
    '[attr.data-chrome]': 'chrome()',
    // the "More" menu closes on a click elsewhere and on Escape
    '(document:click)': 'onDocumentClick($event)',
    '(keydown.escape)': 'closeMore(true)',
  },
  template: `
    <div class="hdr">
      <fs-brand [link]="session.isAuthenticated() ? '/dashboard' : '/'" />
      @if (chrome() === 'full') {
        <!--
          design review M7 R3: the links wrap inside a one-row nav; the ones that do not fit the header (measured,
          see measureNav) are hidden there and listed in the "More" menu instead. A link carries its test id
          nav.<id> where it is reachable: in the header or in the menu.
        -->
        <nav
          class="hdr__nav"
          #navEl
          [class.hdr__nav--compact]="compactNav()"
          [attr.aria-label]="'layout.header.mainNav' | t"
        >
          @for (item of ordered(); track item.id; let i = $index) {
            <a
              class="hdr__link"
              [class.hdr__link--labelled]="labelled().has(item.id)"
              [class.hdr__link--overflow]="i >= inlineCount()"
              [attr.inert]="i >= inlineCount() ? '' : null"
              [routerLink]="item.route"
              routerLinkActive="hdr__link--active"
              [routerLinkActiveOptions]="{ exact: !!item.exact }"
              [attr.data-testid]="i < inlineCount() ? 'nav.' + item.id : null"
              [attr.title]="item.labelKey | t"
            >
              <i [class]="item.icon" aria-hidden="true"></i>
              <span>{{ item.labelKey | t }}</span>
              <fs-nav-badge [item]="item" />
            </a>
          }
        </nav>
        @if (split().more.length) {
          <!-- a disclosure with a plain link list: no popover code in the start-up bundle -->
          <div
            class="hdr__more"
            routerLinkActive="hdr__more--active"
            (focusout)="onMoreFocusOut($event)"
          >
            <button
              type="button"
              class="hdr__link hdr__more-btn"
              [attr.aria-expanded]="moreOpen()"
              aria-controls="hdr-more-list"
              (click)="moreOpen.set(!moreOpen())"
              data-testid="nav.more"
            >
              <i class="pi pi-ellipsis-h" aria-hidden="true"></i>
              <span class="hdr__more-label">{{ 'layout.header.more' | t }}</span>
              <i class="pi pi-chevron-down hdr__more-chevron" aria-hidden="true"></i>
              @if (moreUnread()) {
                <!-- an item with unread messages/mails sits in the menu: its count shows there -->
                <span class="hdr__more-dot" aria-hidden="true" data-testid="nav.more.unread"></span>
              }
            </button>
            <ul
              id="hdr-more-list"
              class="menu-list hdr__more-list"
              [hidden]="!moreOpen()"
              [attr.aria-label]="'layout.header.more' | t"
              data-testid="nav.more.list"
            >
              @for (item of split().more; track item.id) {
                <li>
                  <a
                    class="menu-list__item"
                    [routerLink]="item.route"
                    routerLinkActive="menu-list__item--active hdr__link--active"
                    #rla="routerLinkActive"
                    [routerLinkActiveOptions]="{ exact: !!item.exact }"
                    [attr.aria-current]="rla.isActive ? 'page' : null"
                    [attr.data-testid]="'nav.' + item.id"
                    (click)="closeMore(false)"
                  >
                    <i [class]="item.icon" aria-hidden="true"></i>
                    <span>{{ item.labelKey | t }}</span>
                    <fs-nav-badge [item]="item" />
                  </a>
                </li>
              }
            </ul>
          </div>
        }
      }
      <div class="hdr__actions">
        @if (chrome() === 'full' && searchEnabled()) {
          <!-- C5: replaces the "search" nav link from 48rem; on phones the search icon opens it below the header -->
          <button
            type="button"
            class="hdr-btn hdr__search-toggle"
            aria-controls="hdr-search"
            [attr.aria-expanded]="searchOpen()"
            [attr.aria-label]="(searchOpen() ? 'layout.header.searchClose' : 'layout.header.searchOpen') | t"
            (click)="toggleSearch()"
            data-testid="shell.searchToggle"
          >
            <i [class]="searchOpen() ? 'pi pi-times' : 'pi pi-search'" aria-hidden="true"></i>
          </button>
          <fs-global-search
            id="hdr-search"
            class="hdr__search"
            [class.hdr__search--open]="searchOpen()"
            (keydown.escape)="closeSearchIfEmpty($event)"
          />
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
     * Links never shrink below a 40px target (axe target-size, D11.5.3). The nav takes the free space of the header
     * and shows one row: links that do not fit wrap into a clipped second row and move to "More" (design review M7
     * R3; before, the nav scrolled sideways and hid most links at 1280px). The padding keeps focus rings unclipped.
     */
    .hdr__nav {
      display: none;
      flex: 1 1 0;
      flex-wrap: wrap;
      align-content: flex-start;
      align-items: center;
      gap: 1rem 0.25rem;
      min-width: 0;
      height: 3rem;
      padding: 0.25rem;
      overflow: hidden;
    }
    .hdr__link--overflow {
      visibility: hidden;
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
    /*
     * wide screens: the first items keep their label in the compact nav (design review M7 R2: 9 unlabelled icons);
     * from R3 only at 90rem: below, two labels left room for one link at 1280px, so icons + "More" (labelled list)
     */
    @media (min-width: 90rem) {
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
    .hdr__more {
      position: relative;
      display: none;
      flex: none;
      /* sits right after the nav: undo most of the header gap */
      margin-left: -1.5rem;
    }
    @media (min-width: 48rem) {
      .hdr__more {
        display: block;
      }
    }
    .hdr__more-btn {
      border: 0;
      background: transparent;
      cursor: pointer;
      font: inherit;
      font-size: 0.92rem;
      font-weight: 600;
    }
    .hdr__more-btn[aria-expanded='true'] {
      background: var(--fs-surface-muted);
      color: var(--fs-text);
    }
    .hdr__more--active .hdr__more-btn {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .hdr__more-chevron {
      font-size: 0.7rem;
    }
    .hdr__more-dot {
      position: absolute;
      top: 0.4rem;
      right: 0.4rem;
      width: 0.55rem;
      height: 0.55rem;
      border-radius: 999px;
      background: var(--fs-danger);
    }
    /* icon and chevron (the name stays for screen readers); the word "More" on wide screens */
    .hdr__more-label {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
    @media (min-width: 90rem) {
      .hdr__more-label {
        position: static;
        width: auto;
        height: auto;
        overflow: visible;
        clip: auto;
      }
    }
    .hdr__more-list {
      position: absolute;
      z-index: 50;
      top: calc(100% + 0.4rem);
      left: 0;
      min-width: 15rem;
      max-height: calc(100dvh - var(--fs-header-h) - 2rem);
      margin: 0;
      padding: 0.4rem;
      overflow-y: auto;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-lg);
      list-style: none;
    }
    .hdr__more-list[hidden] {
      display: none;
    }
    .hdr__actions {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      margin-left: auto;
    }
    .hdr__search {
      width: clamp(9rem, 15vw, 17rem);
      margin-right: 0.35rem;
    }
    .hdr__search-toggle {
      display: none;
    }
    @media (max-width: 47.99rem) {
      .hdr__search-toggle {
        display: inline-grid;
      }
      .hdr__search {
        display: none;
      }
      /* phones: the open search spans the screen right below the header */
      .hdr__search--open {
        position: absolute;
        top: 100%;
        right: 0;
        left: 0;
        display: block;
        width: auto;
        margin: 0;
        padding: 0.5rem var(--fs-gutter);
        border-bottom: 1px solid var(--fs-header-border);
        background: var(--fs-header-bg);
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
  /** Main items in header order: primary items first, the occasional ones last (design review M7 R3). */
  protected readonly ordered = computed(() => orderHeaderNav(this.mainItems(), this.nav.context()));
  /** How many links fit the header row (measured in the browser; everything until then). */
  protected readonly fit = signal(Number.MAX_SAFE_INTEGER);
  /** Header links and the "More" menu (design review M7 R3: long navs scrolled out of the header). */
  protected readonly split = computed(() => splitHeaderNav(this.ordered(), this.fit()));
  protected readonly inlineCount = computed(() => this.split().inline.length);
  private readonly badges = inject(NavBadgesService);
  /** Some item in the "More" menu has an unread count. */
  protected readonly moreUnread = computed(() =>
    this.split().more.some((item) => !!item.badge && this.badges.count(item.badge) > 0),
  );
  protected readonly moreOpen = signal(false);
  /** Phones: the header search is open below the header (C5). */
  protected readonly searchOpen = signal(false);

  protected toggleSearch(): void {
    const open = !this.searchOpen();
    this.searchOpen.set(open);
    if (open) {
      // focus the input once it is shown (the first focus also loads the search index)
      afterNextRender(
        () => this.host.nativeElement.querySelector<HTMLInputElement>('#hdr-search input')?.focus(),
        { injector: this.injector },
      );
    }
  }

  /** Escape in an empty search closes it on phones and returns focus to the toggle. */
  protected closeSearchIfEmpty(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    if (this.searchOpen() && !input?.value) {
      this.searchOpen.set(false);
      this.host.nativeElement.querySelector<HTMLButtonElement>('[data-testid="shell.searchToggle"]')?.focus();
    }
  }
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly navEl = viewChild<ElementRef<HTMLElement>>('navEl');
  /** Icons only with more than five items or when five long labels (fr/it/de) would collide with the region switcher. */
  protected readonly compactNav = computed(() => {
    const items = this.ordered();
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
    for (const item of this.ordered()) {
      chars += this.i18n.t(item.labelKey).length;
      if (ids.size >= 2 || chars > 24) break;
      ids.add(item.id);
    }
    return ids;
  });
  readonly chrome = input<ChromeMode>('full');

  constructor() {
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.moreOpen.set(false);
        this.searchOpen.set(false);
      });

    // measure which links fit whenever the nav changes size (window, "More" appearing) or its links change
    const view = inject(DOCUMENT).defaultView;
    if (!isPlatformBrowser(inject(PLATFORM_ID)) || !view || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(() => this.measureNav());
    inject(DestroyRef).onDestroy(() => observer.disconnect());
    let observed: HTMLElement | undefined;
    effect(() => {
      const nav = this.navEl()?.nativeElement;
      // links, labels and language change the widths
      this.ordered();
      this.labelled();
      this.compactNav();
      this.i18n.language();
      if (nav !== observed) {
        if (observed) observer.unobserve(observed);
        if (nav) observer.observe(nav);
        observed = nav;
      }
      if (nav) afterNextRender(() => this.measureNav(), { injector: this.injector });
    });
  }


  /** Counts the links on the nav's first row (the others wrapped into the clipped second row). */
  private measureNav(): void {
    const nav = this.navEl()?.nativeElement;
    if (!nav) return;
    const links = Array.from(nav.querySelectorAll<HTMLElement>('.hdr__link'));
    const top = links[0]?.offsetTop ?? 0;
    const fit = links.filter((link) => link.offsetTop === top).length;
    if (fit !== this.fit()) this.fit.set(fit);
  }

  /** Closes the "More" menu; with `refocus` (Escape) the focus goes back to its button. */
  protected closeMore(refocus: boolean): void {
    if (!this.moreOpen()) return;
    this.moreOpen.set(false);
    if (refocus) this.host.nativeElement.querySelector<HTMLElement>('[data-testid="nav.more"]')?.focus();
  }

  /** A click outside the menu closes it (also where buttons take no focus on click, Safari). */
  protected onDocumentClick(event: MouseEvent): void {
    if (!this.moreOpen()) return;
    const more = this.host.nativeElement.querySelector('.hdr__more');
    if (more && !more.contains(event.target as Node)) this.closeMore(false);
  }

  /** Tabbing out of the menu closes it. */
  protected onMoreFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (!next || !(event.currentTarget as HTMLElement).contains(next)) this.closeMore(false);
  }
}
