import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { ConfirmDialog } from '@openng/optimus-ui/confirmdialog';
import { Toast } from '@openng/optimus-ui/toast';
import { filter, map } from 'rxjs';

import { SessionService } from '../core/auth/session.service';
import { TPipe } from '../core/i18n/t.pipe';
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
@Component({
  selector: 'fs-shell',
  imports: [
    ConfirmDialog,
    FooterComponent,
    HeaderComponent,
    MobileTabBarComponent,
    OfflineBannerComponent,
    RequestBarComponent,
    RouterOutlet,
    SessionExpiredDialogComponent,
    TPipe,
    Toast,
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
      <fs-header [chrome]="chrome()" />
    }
    <fs-offline-banner />
    <main id="main-content" class="shell__main" tabindex="-1">
      <router-outlet />
    </main>
    @if (chrome() === 'full') {
      <fs-footer />
    }
    @if (showTabBar()) {
      <fs-mobile-tab-bar />
    }
    <p-toast position="top-center" [breakpoints]="toastBreakpoints" data-testid="shell.toast" />
    <p-confirmdialog
      [style]="{ width: 'min(30rem, calc(100vw - 2rem))' }"
      data-testid="shell.confirm"
    />
    <fs-session-expired-dialog />
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
}
