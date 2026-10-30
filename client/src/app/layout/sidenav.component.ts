import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, NavigationStart, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';

import { FeatureToggleKey } from '../api';
import { SessionService } from '../core/auth/session.service';
import { FeatureToggleService } from '../core/feature-toggles/feature-toggle.service';
import { TPipe } from '../core/i18n/t.pipe';
import { StartupService } from '../core/startup/startup.service';
import { BellButtonComponent } from '../features/bells/components/bell-button.component';
import { WhatsNewDotComponent } from '../features/content/components/whats-new-dot.component';
import { JourneyMeterComponent } from '../features/onboarding/components/journey-meter.component';
import { GlobalSearchComponent } from '../features/search/components/global-search.component';
import { BrandComponent } from './brand.component';
import { DisplayPrefsComponent } from './display-prefs.component';
import {
  M6_PAGES_READY,
  NavItem,
  NavSection,
  SidenavSection,
  activeNavId,
  sidenavSections,
} from './nav.config';
import { NavBadgesService } from './nav-badges.service';
import { NavContextService } from './nav-context.service';
import { RegionSwitcherComponent } from './region-switcher.component';
import { SidenavCardDirective, SidenavPopovers } from './sidenav-popover';
import {
  SectionChoices,
  SidenavStateService,
  foldedSections,
  isCollapseShortcut,
  isSearchShortcut,
  isSectionOpen,
  nextFoldCount,
  openCurrentSection,
} from './sidenav-state';
import { UserMenuComponent } from './user-menu.component';

/**
 * Desktop side menu (decision 133, docs/design/sidenav-proposal.md §9 and sidenav-mockup.html): replaces the top
 * header from 48rem for everybody, signed in or not, and always sits next to the content (the shell reserves its
 * column). From 75rem it is always expanded; between 48 and 75rem a toggle (and `[`) collapses it to an icon rail.
 * Sections come from nav.config (`section`); Community is open by default, Orga closed unless its page is current.
 * In the rail the sections fold into flyouts, from the bottom up, until the rail fits the viewport (measured).
 * The region switcher, search (Ctrl/⌘ K), bell, user menu, visitor preferences and rail flyouts open as raised cards
 * outside the menu's right edge (SidenavPopovers); nothing is persisted.
 */
@Component({
  selector: 'fs-sidenav',
  imports: [
    BellButtonComponent,
    BrandComponent,
    DisplayPrefsComponent,
    GlobalSearchComponent,
    JourneyMeterComponent,
    NgTemplateOutlet,
    RegionSwitcherComponent,
    RouterLink,
    SidenavCardDirective,
    TPipe,
    UserMenuComponent,
    WhatsNewDotComponent,
  ],
  providers: [SidenavPopovers],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'sn',
    'data-testid': 'nav.sidenav',
    '[attr.data-mode]': 'mode()',
    '[attr.data-rail-scroll]': "railScroll() ? '' : null",
    '(document:keydown)': 'onKeydown($event)',
    '(document:click)': 'onDocumentClick($event)',
    '(window:resize)': 'popovers.relayout()',
  },
  template: `
    <div class="sn__top">
      <fs-brand
        [link]="signedIn() ? '/dashboard' : '/'"
        [markOnly]="mode() === 'rail'"
        [attr.data-label]="'foodsharing'"
      />
      @if (state.canCollapse()) {
        <button
          type="button"
          class="sn__toggle"
          [attr.aria-expanded]="mode() === 'sidebar'"
          aria-controls="sn-nav"
          aria-keyshortcuts="["
          [attr.aria-label]="
            (mode() === 'sidebar' ? 'layout.sidenav.collapse' : 'layout.sidenav.expand') | t
          "
          [attr.data-label]="
            ((mode() === 'sidebar' ? 'layout.sidenav.collapse' : 'layout.sidenav.expand') | t) +
            ' ([)'
          "
          (click)="state.toggle()"
          data-testid="nav.sidenav.toggle"
        >
          <i
            [class]="mode() === 'sidebar' ? 'pi pi-angle-double-left' : 'pi pi-bars'"
            aria-hidden="true"
          ></i>
        </button>
      }
    </div>

    @if (signedIn()) {
      <div class="sn__switcher">
        <fs-region-switcher variant="sidenav" />
      </div>
    }
    @if (searchEnabled()) {
      @if (mode() === 'rail') {
        <div class="sn__search">
          <button
            type="button"
            class="sn-row"
            #searchTile
            aria-haspopup="dialog"
            [attr.aria-expanded]="popovers.openId() === 'search'"
            aria-controls="sn-card-search"
            aria-keyshortcuts="Control+K Meta+K"
            [attr.aria-label]="'layout.header.searchOpen' | t"
            [attr.data-label]="('layout.header.searchOpen' | t) + ' (Ctrl K)'"
            (click)="popovers.toggle('search', searchTile)"
            data-testid="nav.sidenav.search"
          >
            <i class="pi pi-search" aria-hidden="true"></i>
          </button>
          <div
            id="sn-card-search"
            class="sn-card--search"
            fsSidenavCard="search"
            role="dialog"
            [attr.aria-label]="'layout.header.searchOpen' | t"
          >
            <div class="sn-card__body">
              <fs-global-search popupAlign="start" />
            </div>
          </div>
        </div>
      } @else {
        <div class="sn__search sn__search--field">
          <fs-global-search popupAlign="start" />
          <kbd class="sn-row__kbd sn__search-kbd" aria-hidden="true">Ctrl K</kbd>
        </div>
      }
    } @else if (searchPending()) {
      <!-- the feature toggles arrive after the first page: keep the search row, so the list does not jump -->
      <div class="sn__search" aria-hidden="true"></div>
    }

    <div
      class="sn__navwrap"
      [attr.data-more-start]="moreStart() ? '' : null"
      [attr.data-more-end]="moreEnd() ? '' : null"
    >
      <nav
        class="sn__nav"
        id="sn-nav"
        #navEl
        [attr.aria-label]="'layout.header.mainNav' | t"
        (scroll)="onNavScroll()"
      >
        @for (section of sections(); track section.def.id) {
          @let id = section.def.id;
          <section
            class="sn__section"
            [class.sn__section--current]="currentSection() === id"
            [attr.data-section]="id"
          >
            @if (!section.def.collapsible || (mode() === 'rail' && !folded().has(id))) {
              <!-- the daily group, and unfolded sections in the rail: a hidden heading for the outline, tiles below -->
              <h2 class="fs-visually-hidden">
                {{
                  (signedIn()
                    ? section.def.labelKey
                    : (section.def.visitorLabelKey ?? section.def.labelKey)
                  ) | t
                }}
              </h2>
              <ul class="sn__list" [attr.data-testid]="'nav.section.' + id + '.list'">
                <ng-container *ngTemplateOutlet="items; context: { $implicit: section }" />
              </ul>
            } @else if (folded().has(id)) {
              <h2 class="sn__heading">
                <button
                  type="button"
                  class="sn__section-btn sn__flyout-btn"
                  #fly
                  [attr.aria-expanded]="popovers.openId() === 'fly-' + id"
                  [attr.aria-controls]="'sn-fly-' + id"
                  [attr.data-label]="section.def.labelKey | t"
                  (click)="popovers.toggle('fly-' + id, fly)"
                  [attr.data-testid]="'nav.flyout.' + id"
                >
                  <i [class]="section.def.icon + ' sn__section-ico'" aria-hidden="true"></i>
                  <span class="sn__section-label">{{ section.def.labelKey | t }}</span>
                  <i class="pi pi-chevron-right sn-row__chev" aria-hidden="true"></i>
                  @if (unread(section)) {
                    <span
                      class="sn-dot sn__section-dot"
                      aria-hidden="true"
                      [attr.data-testid]="'nav.section.' + id + '.unread'"
                    ></span>
                  }
                </button>
              </h2>
              <div [id]="'sn-fly-' + id" [fsSidenavCard]="'fly-' + id">
                <div class="sn-card__body">
                  <ul
                    class="sn__list"
                    [attr.aria-label]="section.def.labelKey | t"
                    [attr.data-testid]="'nav.flyout.' + id + '.list'"
                  >
                    <ng-container *ngTemplateOutlet="items; context: { $implicit: section }" />
                  </ul>
                </div>
              </div>
            } @else {
              <h2 class="sn__heading">
                <button
                  type="button"
                  class="sn__section-btn"
                  [attr.aria-expanded]="isOpen(id)"
                  [attr.aria-controls]="'sn-sec-' + id"
                  (click)="toggleSection(id)"
                  [attr.data-testid]="'nav.section.' + id"
                >
                  <span class="sn__section-label">{{ section.def.labelKey | t }}</span>
                  @if (!isOpen(id) && unread(section)) {
                    <span
                      class="sn-dot sn__section-dot"
                      aria-hidden="true"
                      [attr.data-testid]="'nav.section.' + id + '.unread'"
                    ></span>
                  }
                  <i class="pi pi-chevron-down sn-row__chev" aria-hidden="true"></i>
                </button>
              </h2>
              <ul
                class="sn__list"
                [id]="'sn-sec-' + id"
                [hidden]="!isOpen(id)"
                [attr.data-testid]="'nav.section.' + id + '.list'"
              >
                <ng-container *ngTemplateOutlet="items; context: { $implicit: section }" />
              </ul>
            }
          </section>
        }
      </nav>
    </div>

    <div class="sn__bottom">
      @if (signedIn()) {
        <a
          class="sn-row"
          routerLink="/release-notes"
          [attr.data-label]="'layout.header.whatsNew' | t"
          data-testid="shell.whatsNew"
        >
          <i class="pi pi-gift" aria-hidden="true"></i>
          <span class="sn-row__label">{{ 'layout.header.whatsNew' | t }}</span>
          <!-- C4: the dot loads after the first page and never shifts the row -->
          @defer (when startup.pageShown()) {
            <span class="sn__whats-new-dot"><fs-whats-new-dot /></span>
          }
        </a>
        <fs-user-menu variant="sidenav" />
      } @else {
        <button
          type="button"
          class="sn-row"
          #prefsTrigger
          aria-haspopup="true"
          [attr.aria-expanded]="popovers.openId() === 'prefs'"
          aria-controls="sn-card-prefs"
          [attr.data-label]="'layout.sidenav.prefs' | t"
          (click)="popovers.toggle('prefs', prefsTrigger)"
          data-testid="shell.prefs"
        >
          <i class="pi pi-globe" aria-hidden="true"></i>
          <span class="sn-row__label">{{ 'layout.sidenav.prefs' | t }}</span>
          <i class="pi pi-chevron-down sn-row__chev" aria-hidden="true"></i>
        </button>
        <div
          id="sn-card-prefs"
          fsSidenavCard="prefs"
          anchor="bottom"
          role="group"
          [attr.aria-label]="'layout.sidenav.prefs' | t"
        >
          <div class="sn-card__body">
            <fs-display-prefs testIdPrefix="shell.prefs" />
          </div>
        </div>
        <div class="sn__cta">
          <a
            class="fs-btn fs-btn--sm"
            routerLink="/login"
            [attr.data-label]="'nav.login' | t"
            data-testid="shell.login"
          >
            <i class="pi pi-sign-in" aria-hidden="true"></i>
            <span>{{ 'nav.login' | t }}</span>
          </a>
          <a
            class="fs-btn fs-btn--sm fs-btn--outlined"
            routerLink="/login"
            [attr.data-label]="'layout.sidenav.join' | t"
            data-testid="shell.register"
          >
            <i class="pi pi-user-plus" aria-hidden="true"></i>
            <span>{{ 'layout.sidenav.join' | t }}</span>
          </a>
        </div>
      }
    </div>

    <ng-template #items let-section>
      @for (item of section.items; track item.id) {
        <li>
          @if (item.id === 'bells') {
            <fs-bell-button variant="sidenav" [attr.data-nav-section]="section.def.id" />
          } @else {
            <a
              class="sn-row sn-link"
              [class.sn-link--meter]="item.id === 'onboarding'"
              [routerLink]="item.route"
              [attr.aria-current]="activeId() === item.id ? 'page' : null"
              [attr.aria-describedby]="item.id === 'onboarding' ? 'sn-journey-progress' : null"
              [attr.data-label]="item.labelKey | t"
              [attr.data-nav-section]="section.def.id"
              [attr.data-testid]="'nav.' + item.id"
              (click)="popovers.close(false)"
            >
              <i [class]="item.icon" aria-hidden="true"></i>
              <span class="sn-row__label">{{ item.labelKey | t }}</span>
              @if (badgeCount(item); as count) {
                <span
                  class="sn-badge"
                  aria-hidden="true"
                  [attr.data-testid]="'nav.badge.' + item.id"
                  [attr.data-count]="count"
                  >{{ count > 99 ? '99+' : count }}</span
                >
                <span class="fs-visually-hidden">{{ 'layout.nav.unread' | t: { count } }}</span>
              }
            </a>
            @if (item.id === 'onboarding') {
              @defer (on idle) {
                <fs-journey-meter />
              }
            }
          }
        </li>
      }
    </ng-template>
  `,
  styles: `
    .sn__search--field {
      position: relative;
    }
    .sn__search-kbd {
      position: absolute;
      top: 50%;
      right: 0.6rem;
      transform: translateY(-50%);
      pointer-events: none;
    }
    .sn__search--field:focus-within .sn__search-kbd {
      display: none;
    }
    .sn__whats-new-dot {
      position: absolute;
      top: 50%;
      right: 0.6rem;
      width: 1.4rem;
      height: 1.4rem;
      transform: translateY(-50%);
    }
    :host([data-mode='rail']) .sn__whats-new-dot {
      top: 0.1rem;
      right: 0;
      transform: none;
    }
    .sn__section-dot {
      margin-inline-start: 0.25rem;
    }
    :host([data-mode='rail']) .sn__section-dot {
      position: absolute;
      top: 0.4rem;
      right: 0.4rem;
      margin: 0;
    }
  `,
})
export class SidenavComponent {
  protected readonly state = inject(SidenavStateService);
  protected readonly popovers = inject(SidenavPopovers);
  protected readonly startup = inject(StartupService);
  private readonly session = inject(SessionService);
  private readonly nav = inject(NavContextService);
  private readonly badges = inject(NavBadgesService);
  private readonly toggles = inject(FeatureToggleService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly navEl = viewChild.required<ElementRef<HTMLElement>>('navEl');

  protected readonly mode = this.state.mode;
  protected readonly signedIn = this.session.isAuthenticated;
  protected readonly searchEnabled = computed(
    () =>
      this.signedIn() &&
      M6_PAGES_READY.search &&
      this.toggles.isEnabled(FeatureToggleKey.GlobalSearch),
  );

  /** Signed in, toggles not loaded yet: the search may still appear. */
  protected readonly searchPending = computed(() => this.signedIn() && !this.toggles.loaded());

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly sections = computed<SidenavSection[]>(() =>
    sidenavSections(this.nav.context()),
  );
  private readonly allItems = computed(() => this.sections().flatMap((section) => section.items));
  /** The visible item ids (a string, so equal item sets do not re-trigger the rail folding). */
  private readonly itemKey = computed(() => this.allItems().map((item) => item.id).join(','));
  protected readonly activeId = computed(() => activeNavId(this.url(), this.allItems()));
  protected readonly currentSection = computed<NavSection | null>(() => {
    const id = this.activeId();
    return (
      this.sections().find((section) => section.items.some((item) => item.id === id))?.def.id ??
      null
    );
  });

  /** Open/closed choices of this page view (not persisted, decision 133). */
  private readonly choices = signal<SectionChoices>({});
  /** Rail: how many collapsible sections are folded into flyouts (measured, from the bottom up). */
  private readonly foldCount = signal(0);
  /** Rail: even with every section folded it does not fit (very low windows): the list scrolls after all. */
  protected readonly railScroll = signal(false);
  private readonly collapsibleIds = computed(() =>
    this.sections()
      .filter((section) => section.def.collapsible)
      .map((section) => section.def.id),
  );
  protected readonly folded = computed<ReadonlySet<NavSection>>(() =>
    this.mode() === 'rail' ? foldedSections(this.collapsibleIds(), this.foldCount()) : new Set(),
  );

  protected readonly moreStart = signal(false);
  protected readonly moreEnd = signal(false);

  constructor() {
    // the section of the current page opens on navigation (the user may close it again)
    effect(() => {
      const current = this.currentSection();
      untracked(() => this.choices.update((choices) => openCurrentSection(choices, current)));
    });

    // a navigation closes an open card. Not the app's first navigation: the side menu may already be there while it
    // is still running, and a card opened meanwhile must stay open when it ends.
    const starts = this.router.events
      .pipe(filter((event) => event instanceof NavigationStart && this.router.navigated))
      .subscribe(() => this.popovers.close(false));
    inject(DestroyRef).onDestroy(() => starts.unsubscribe());

    // after a navigation the current item comes into view
    effect(() => {
      this.url();
      afterNextRender(
        () => {
          this.navEl()
            .nativeElement.querySelector<HTMLElement>('[aria-current="page"]')
            ?.scrollIntoView?.({ block: 'nearest' });
          this.updateScrollHint();
        },
        { injector: this.injector },
      );
    });

    // a width change closes an open card
    effect(() => {
      this.mode();
      untracked(() => this.popovers.close(false));
    });
    // rail folding: start unfolded whenever the mode or the set of items changes, then fold until it fits (a new
    // session object with the same items, e.g. after a language change, keeps the folding and an open card)
    effect(() => {
      this.mode();
      this.itemKey();
      untracked(() => {
        this.foldCount.set(0);
        this.railScroll.set(false);
      });
      afterNextRender(() => this.measure(), { injector: this.injector });
    });

    const observer =
      typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => this.remeasure());
    afterNextRender(() => observer?.observe(this.host.nativeElement));
    inject(DestroyRef).onDestroy(() => observer?.disconnect());
  }

  protected isOpen(id: NavSection): boolean {
    const def = this.sections().find((section) => section.def.id === id)?.def;
    return !!def && isSectionOpen(def, this.choices());
  }

  protected toggleSection(id: NavSection): void {
    const open = this.isOpen(id);
    this.choices.update((choices) => ({ ...choices, [id]: !open }));
    afterNextRender(() => this.updateScrollHint(), { injector: this.injector });
  }

  protected badgeCount(item: NavItem): number {
    return item.badge ? this.badges.count(item.badge) : 0;
  }

  /** A section hides an item with an unread count (closed heading or folded flyout). */
  protected unread(section: SidenavSection): boolean {
    return section.items.some((item) => this.badgeCount(item) > 0);
  }

  protected onNavScroll(): void {
    this.updateScrollHint();
    this.popovers.relayout();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      if (this.popovers.openId()) {
        this.popovers.close(true);
      }
      return;
    }
    if (isCollapseShortcut(event) && this.state.canCollapse()) {
      event.preventDefault();
      this.state.toggle();
      return;
    }
    if (isSearchShortcut(event) && this.searchEnabled()) {
      event.preventDefault();
      this.focusSearch();
    }
  }

  /** A click outside the open card and its trigger closes the card. */
  protected onDocumentClick(event: MouseEvent): void {
    const open = this.popovers.openId();
    if (!open) return;
    const target = event.target as Node | null;
    if (!target || !target.isConnected) return;
    const card = this.host.nativeElement.querySelector(`.sn-card:not([hidden])`);
    if (card?.contains(target) || this.popovers.openTrigger?.contains(target)) return;
    this.popovers.close(false);
  }

  private focusSearch(): void {
    const root = this.host.nativeElement;
    if (this.mode() === 'rail') {
      const tile = root.querySelector<HTMLElement>('[data-testid="nav.sidenav.search"]');
      if (tile && this.popovers.openId() !== 'search') this.popovers.toggle('search', tile);
      // the card focuses its first control (the search field) once it is shown
      return;
    }
    root.querySelector<HTMLInputElement>('.sn__search input')?.focus();
  }

  private remeasure(): void {
    if (this.mode() !== 'rail') {
      this.updateScrollHint();
      return;
    }
    this.foldCount.set(0);
    this.railScroll.set(false);
    afterNextRender(() => this.measure(), { injector: this.injector });
  }

  /** Folds one more section while the rail overflows (each step renders, then measures again). */
  private measure(): void {
    this.updateScrollHint();
    if (this.mode() !== 'rail') return;
    // the rail's list does not scroll (overflow visible): it overflows its box until enough sections are folded
    const nav = this.navEl().nativeElement;
    const overflowing = nav.scrollHeight > nav.clientHeight + 1;
    const next = nextFoldCount(this.foldCount(), overflowing, this.collapsibleIds().length);
    if (next !== this.foldCount()) {
      this.foldCount.set(next);
      afterNextRender(() => this.measure(), { injector: this.injector });
    } else if (overflowing) {
      this.railScroll.set(true);
    }
  }

  private updateScrollHint(): void {
    const nav = this.navEl().nativeElement;
    const scrollable = this.mode() !== 'rail';
    this.moreStart.set(scrollable && nav.scrollTop > 4);
    this.moreEnd.set(scrollable && nav.scrollTop + nav.clientHeight < nav.scrollHeight - 4);
  }
}
