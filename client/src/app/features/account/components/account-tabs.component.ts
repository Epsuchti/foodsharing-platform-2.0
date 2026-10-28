import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { TPipe } from '../../../core/i18n/t.pipe';

interface AccountTab {
  id: string;
  route: string;
  icon: string;
  labelKey: string;
}

const TABS: readonly AccountTab[] = [
  { id: 'settings', route: '/account/settings', icon: 'pi pi-cog', labelKey: 'nav.settings' },
  { id: 'sessions', route: '/account/sessions', icon: 'pi pi-desktop', labelKey: 'nav.sessions' },
  {
    id: 'securityLog',
    route: '/account/security-log',
    icon: 'pi pi-shield',
    labelKey: 'nav.securityLog',
  },
  // M4 F2: notification settings live in features/notifications (route account/notifications)
  { id: 'notifications', route: '/account/notifications', icon: 'pi pi-bell', labelKey: 'nav.notifications' },
];

/** Switches between the account pages (scrolls horizontally on narrow phones). */
@Component({
  selector: 'fs-account-tabs',
  imports: [RouterLink, RouterLinkActive, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="tabs" [attr.aria-label]="'account.tabs.label' | t">
      @for (tab of tabs; track tab.id) {
        <a
          class="tab"
          [routerLink]="tab.route"
          routerLinkActive="tab--active"
          ariaCurrentWhenActive="page"
          [attr.data-testid]="'account.tabs.' + tab.id"
        >
          <i [class]="tab.icon" aria-hidden="true"></i>
          <span>{{ tab.labelKey | t }}</span>
        </a>
      }
    </nav>
  `,
  styles: `
    :host {
      display: block;
      margin: -0.25rem 0 1.5rem;
    }
    .tabs {
      display: flex;
      gap: 0.35rem;
      overflow-x: auto;
      padding: 0.3rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
      scrollbar-width: none;
    }
    .tabs::-webkit-scrollbar {
      display: none;
    }
    .tab {
      display: inline-flex;
      flex: 1 0 auto;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.55rem 0.9rem;
      border-radius: var(--fs-radius-sm);
      color: var(--fs-text-muted);
      font-size: 0.92rem;
      font-weight: 650;
      text-decoration: none;
      white-space: nowrap;
    }
    .tab:hover {
      color: var(--fs-text);
    }
    .tab--active,
    .tab--active:hover {
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-sm);
      color: var(--fs-primary-strong);
    }
  `,
})
export class AccountTabsComponent {
  protected readonly tabs = TABS;
}
