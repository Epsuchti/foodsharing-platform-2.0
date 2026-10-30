import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  PLATFORM_ID,
  afterNextRender,
  computed,
  inject,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { ConfirmDialog } from '@openng/optimus-ui/confirmdialog';
import { Toast } from '@openng/optimus-ui/toast';
import { filter, map, take } from 'rxjs';

import { SessionService } from '../core/auth/session.service';
import { warmWebFont, whenIdle } from '../core/theme/web-font';
import { DonationModalComponent } from '../features/content/components/donation-modal.component';
import { PetitionBannerComponent } from '../features/content/components/petition-banner.component';
import { JourneyBarComponent } from '../features/onboarding/components/journey-bar.component';
import { TPipe } from '../core/i18n/t.pipe';
import { RealtimeService } from '../core/realtime/realtime.service';
import { StartupService } from '../core/startup/startup.service';
import { I18nService } from '../core/i18n/i18n.service';
import { RealtimeStatusComponent } from '../core/realtime/realtime-status.component';
import { OfflineBannerComponent } from '../shared/offline-banner/offline-banner.component';
import { SessionExpiredDialogComponent } from '../shared/session-expired-dialog/session-expired-dialog.component';
import { FooterComponent } from './footer.component';
import { ChromeMode, HeaderComponent } from './header.component';
import { MobileTabBarComponent } from './mobile-tab-bar.component';
import { RequestBarComponent } from './request-bar.component';

/** Route data key: `data: { chrome: 'minimal' }` hides navigation, footer and tab bar (login, surveys). */
export const CHROME_DATA_KEY = 'chrome';

export function chromeOf(root: ActivatedRouteSnapshot | null): ChromeMode {
  let mode: ChromeMode = 'full';
  for (let route = root; route; route = route.firstChild) {
    const value = route.data?.[CHROME_DATA_KEY] as ChromeMode | undefined;
    if (value) {
      mode = value;
    }
  }
  return mode;
}

/** Application frame: request bar, header, offline banner, page, footer, mobile tab bar, toasts, dialogs. */
/** Delay after the first page rendered (and the browser went idle) before overlays and the web font load. */
const AFTER_FIRST_PAGE_MS = 1500;

@Component({
  selector: 'fs-shell',
  imports: [
    ConfirmDialog,
    FooterComponent,
    HeaderComponent,
    JourneyBarComponent,
    MobileTabBarComponent,
    OfflineBannerComponent,
    PetitionBannerComponent,
    DonationModalComponent,
    RequestBarComponent,
    RouterOutlet,
    SessionExpiredDialogComponent,
    TPipe,
    Toast,
    RealtimeStatusComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-shell',
    '[attr.data-chrome]': 'chrome()',
    '[class.fs-shell--tabbar]': 'showTabBar()',
  },
  template: `
    <a class="skip-link" href="#main-content" data-testid="shell.skipLink">{{
      'layout.shell.skip' | t
    }}</a>
    <fs-request-bar />
    @if (chrome() !== 'none') {
      <fs-header [chrome]="chrome()"><fs-journey-bar fsJourneyBar /></fs-header>
    }
    <fs-offline-banner />
    <fs-realtime-status />
    <main id="main-content" class="shell__main" tabindex="-1">
      <router-outlet />
    </main>
    @if (chrome() === 'full') {
      <!-- C8: in the page flow below the fold (main is at least one viewport high), so it never shifts content -->
      @defer (when firstPageShown()) {
        <fs-petition-banner />
        <!-- C7: donation modal per the hours setting (DONATION_MODAL toggle) -->
        <fs-donation-modal />
      }
      <fs-footer />
    }
    @if (showTabBar()) {
      <fs-mobile-tab-bar />
    }
    <!-- overlays load after the first page has rendered: not part of the start-up bundle (D11.5.3) -->
    @defer (when firstPageShown()) {
      <!-- autoZIndex off: a toast must not become the topmost overlay, or open dialogs ignore Escape -->
      <p-toast position="top-center" [breakpoints]="toastBreakpoints" [autoZIndex]="false" style="z-index: 2000" data-testid="shell.toast" />
      <p-confirmdialog
        [style]="{ width: 'min(30rem, calc(100vw - 2rem))' }"
        data-testid="shell.confirm"
      />
      <fs-session-expired-dialog />
    }
  `,
  styles: `
    :host {
      display: flex;
      min-height: 100vh;
      min-height: 100dvh;
      flex-direction: column;
    }
    .shell__main {
      flex: 1 0 auto;
      /* the footer starts below the fold, so content arriving later never pushes it around (CLS, D11.5.3) */
      min-height: 100vh;
      width: min(100%, var(--fs-page-max));
      margin: 0 auto;
      padding: clamp(1.25rem, 1rem + 1.5vw, 2.5rem) var(--fs-gutter) 2rem;
      outline: none;
    }
    @media (max-width: 47.99rem) {
      :host(.fs-shell--tabbar) {
        padding-bottom: calc(var(--fs-tabbar-h) + env(safe-area-inset-bottom));
      }
    }
    .skip-link {
      position: absolute;
      z-index: 100;
      top: 0.5rem;
      left: 0.5rem;
      padding: 0.6rem 1rem;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-primary);
      color: #fff;
      font-weight: 700;
      transform: translateY(-200%);
    }
    .skip-link:focus {
      transform: none;
    }
  `,
})
export class ShellComponent {
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  protected readonly toastBreakpoints = {
    '640px': { width: 'calc(100vw - 2rem)', left: '1rem', right: '1rem' },
  };

  private readonly navigationEnd = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => chromeOf(this.router.routerState.snapshot.root)),
    ),
    { initialValue: chromeOf(this.router.routerState.snapshot.root) },
  );

  readonly chrome = computed<ChromeMode>(() => this.navigationEnd());
  readonly showTabBar = computed(() => this.chrome() === 'full' && this.session.isAuthenticated());

  /**
   * True once the first page has rendered and the browser is idle: the overlays and the web font load then, after
   * the content the visitor came for (D11.5.3 — they would otherwise share the start-up bandwidth).
   */
  private readonly startup = inject(StartupService);
  private readonly i18n = inject(I18nService);
  protected readonly firstPageShown = this.startup.pageShown;

  constructor() {
    // the realtime connection follows the session (connects on login after the first page, closes on logout; A32)
    inject(RealtimeService);
    const document = inject(DOCUMENT);
    const injector = inject(Injector);
    const view = document.defaultView;
    if (!isPlatformBrowser(inject(PLATFORM_ID)) || !view) {
      return;
    }
    const subscription = this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        take(1),
      )
      .subscribe(() =>
        afterNextRender(
          () =>
            // the page's own data usually arrives right after its first render: leave it the bandwidth
            whenIdle(view, () => {
              setTimeout(() => {
                // the rest of the translations (start-up loads only the start-up part, I18nService.ensureComplete)
                void this.i18n.ensureComplete();
                this.startup.markPageShown();
                warmWebFont(document);
              }, AFTER_FIRST_PAGE_MS);
            }),
          { injector },
        ),
      );
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }
}
