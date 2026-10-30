import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { StoreAutomationContext } from '../store-automation.context';

export const STORE_AUTOMATION_SECTIONS = ['policy', 'auto-accept', 'adverts', 'signout-alerts', 'extra-slot', 'audits'] as const;

/**
 * Store tab "Automation" (`/stores/:storeId/automation`, AU1–AU6, AU8): sub-navigation and the active section.
 * The tab is offered to store editors and CAN_USE_AUTOMATIONS holders; the API decides (403 → forbidden state).
 */
@Component({
  selector: 'fs-store-automation-tab',
  imports: [...DATA_STATE_IMPORTS, RouterLink, RouterLinkActive, RouterOutlet, TPipe],
  providers: [StoreAutomationContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state
      [state]="ctx.automation"
      skeleton="form"
      [askWho]="'automation.store.askWho' | t"
      testId="automation.store.state"
      data-testid="automation.store.state"
    >
      <ng-template [fsData]="ctx.automation">
        <nav class="subnav" [attr.aria-label]="'automation.store.nav' | t" data-testid="automation.store.nav">
          @for (section of sections; track section) {
            <a
              class="subnav__item"
              [routerLink]="section"
              routerLinkActive="subnav__item--active"
              ariaCurrentWhenActive="page"
              [attr.data-testid]="'automation.store.nav.' + section"
              >{{ 'automation.section.' + section | t }}</a
            >
          }
        </nav>
        <router-outlet />
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 1rem; }
    .subnav { display: flex; flex-wrap: wrap; gap: 0.35rem; margin-bottom: 1rem; }
    .subnav__item {
      display: inline-flex; align-items: center; min-height: var(--fs-target-sm);
      padding: 0.35rem 0.8rem;
      border-radius: 999px;
      border: 1px solid var(--fs-border);
      color: inherit;
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 600;
    }
    .subnav__item--active { background: var(--fs-primary-soft); border-color: var(--fs-primary); color: var(--fs-primary-strong); }
  `,
})
export class StoreAutomationTab {
  protected readonly ctx = inject(StoreAutomationContext);
  protected readonly sections = STORE_AUTOMATION_SECTIONS;
}
