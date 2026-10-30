import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { TPipe } from '../../../core/i18n/t.pipe';

/** The settings pages behind the "Settings & toggles" nav entry, as links (each page is its own route). */
export const SETTINGS_TABS = [
  { id: 'toggles', route: '/admin/feature-toggles', icon: 'pi pi-power-off' },
  { id: 'configuration', route: '/admin/configuration', icon: 'pi pi-cog' },
  { id: 'donations', route: '/admin/donations', icon: 'pi pi-heart' },
  { id: 'blocklist', route: '/admin/email-blocklist', icon: 'pi pi-ban' },
  { id: 'ipBlocks', route: '/admin/ip-blocks', icon: 'pi pi-shield' },
] as const;

@Component({
  selector: 'fs-settings-tabs',
  imports: [RouterLink, RouterLinkActive, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="tabs" [attr.aria-label]="'adminSettings.tabs.label' | t" data-testid="adminSettings.tabs">
      @for (tab of tabs; track tab.id) {
        <a
          class="tab"
          [routerLink]="tab.route"
          routerLinkActive="tab--active"
          ariaCurrentWhenActive="page"
          [attr.data-testid]="'adminSettings.tabs.' + tab.id"
        >
          <i [class]="tab.icon" aria-hidden="true"></i>
          {{ 'adminSettings.' + tab.id + '.tab' | t }}
        </a>
      }
    </nav>
  `,
  styles: `
    /* min-width 0: as a grid/flex item the nowrap tabs must scroll, not widen the page on phones (F6 sweep) */
    :host {
      display: block;
      min-width: 0;
      max-width: 100%;
    }
    .tabs {
      display: flex;
      gap: 0.25rem;
      overflow-x: auto;
      border-bottom: 1px solid var(--fs-border);
    }
    .tab {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.6rem 0.85rem;
      border-bottom: 2px solid transparent;
      color: var(--fs-text-muted);
      font-weight: 600;
      text-decoration: none;
      white-space: nowrap;
    }
    .tab:hover {
      color: var(--fs-text);
    }
    .tab--active {
      border-bottom-color: var(--fs-primary);
      color: var(--fs-primary-strong);
    }
  `,
})
export class SettingsTabsComponent {
  protected readonly tabs = SETTINGS_TABS;
}
