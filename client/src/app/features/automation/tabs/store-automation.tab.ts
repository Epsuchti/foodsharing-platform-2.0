import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { activeRuleCount } from '../policy-sentences';
import { StoreAutomationContext } from '../store-automation.context';

export const STORE_AUTOMATION_SECTIONS = ['policy', 'auto-accept', 'adverts', 'signout-alerts', 'extra-slot', 'channels', 'audits'] as const;

/**
 * Store tab "Automation" (`/stores/:storeId/automation`, AU1–AU6, AU8, AU10): status strip (rules An/Aus, active
 * rules, auto-accept, adverts, Telegram chats; decision 165 S4), sub-navigation and the active section (default Regeln).
 * The tab is offered to store editors (mayEditStore, decision 165); the API decides (403 → forbidden state).
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
        <div class="strip" role="group" [attr.aria-label]="'automation.store.summary' | t" data-testid="automation.store.summary">
          <span class="pill" [class.pill--on]="summary().on" [class.pill--off]="!summary().on" data-testid="automation.store.summary.mode" [attr.data-mode]="summary().on ? 'ON' : 'OFF'">
            <i class="pi" [class.pi-shield]="summary().on" [class.pi-circle]="!summary().on" aria-hidden="true"></i>
            {{ 'automation.store.summary.mode' | t: { state: ((summary().on ? 'automation.common.on' : 'automation.common.off') | t) } }}
          </span>
          <span class="pill" data-testid="automation.store.summary.rules" [attr.data-count]="summary().rules">
            <i class="pi pi-shield" aria-hidden="true"></i>{{ 'automation.store.summary.rules' | t: { count: summary().rules } }}
          </span>
          <span class="pill" [class.pill--off]="!summary().autoAccept" data-testid="automation.store.summary.autoAccept">
            <i class="pi pi-user-plus" aria-hidden="true"></i>{{ 'automation.store.summary.autoAccept' | t: { state: ((summary().autoAccept ? 'automation.common.on' : 'automation.common.off') | t) } }}
          </span>
          <span class="pill" data-testid="automation.store.summary.adverts" [attr.data-count]="summary().adverts">
            <i class="pi pi-bell" aria-hidden="true"></i>{{ 'automation.store.summary.adverts' | t: { count: summary().adverts } }}
          </span>
          <span class="pill" data-testid="automation.store.summary.channels" [attr.data-count]="summary().channels">
            <i class="pi pi-send" aria-hidden="true"></i>
            @if (summary().channels) {
              {{ 'automation.store.summary.channels' | t: { count: summary().channels } }}
            } @else {
              {{ 'automation.store.summary.channelsRegion' | t }}
            }
          </span>
        </div>
        <nav class="subnav" [attr.aria-label]="'automation.store.nav' | t" data-testid="automation.store.nav">
          @for (section of sections; track section) {
            <a
              class="subnav__item"
              [routerLink]="section"
              routerLinkActive="subnav__item--active"
              ariaCurrentWhenActive="page"
              [attr.data-testid]="'automation.store.nav.' + section"
              >{{ 'automation.section.' + section | t }}
              @if (counts()[section]; as n) {
                <span class="subnav__count">{{ n }}</span>
              }</a
            >
          }
        </nav>
        <router-outlet />
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../automation.scss',
  styles: `
    :host { display: grid; gap: 1rem; }
    .strip {
      display: flex; flex-wrap: wrap; gap: 0.4rem; margin-bottom: 0.75rem; padding: 0.6rem;
      border-radius: var(--fs-radius-md); background: var(--fs-surface-raised);
    }
    .subnav__count {
      display: inline-grid; place-items: center; min-width: 1.3rem; height: 1.3rem; margin-left: 0.35rem; padding: 0 0.3rem;
      border-radius: 999px; background: var(--fs-surface-muted); font-size: 0.75rem;
    }
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
    /* phones: one swipeable row that sticks right below the store tabs (never slides under the sticky bars) */
    @media (max-width: 47.99rem) {
      .subnav {
        position: sticky; z-index: 4; top: var(--fs-sticky-offset, var(--fs-header-h));
        flex-wrap: nowrap; overflow-x: auto; scrollbar-width: none;
        margin-bottom: 0.5rem; padding: 0.4rem 0;
        border-bottom: 1px solid var(--fs-border);
        background: var(--fs-bg);
      }
      .subnav__item { flex: 0 0 auto; white-space: nowrap; }
    }
  `,
})
export class StoreAutomationTab {
  protected readonly ctx = inject(StoreAutomationContext);
  protected readonly sections = STORE_AUTOMATION_SECTIONS;

  protected readonly summary = computed(() => {
    const data = this.ctx.data();
    return {
      on: !!data?.pickupPolicy?.enabled,
      rules: activeRuleCount(data?.pickupPolicy),
      autoAccept: !!data?.requestPolicy?.enabled,
      adverts: (data?.adverts ?? []).filter((advert) => advert.enabled).length,
      channels: data?.telegramChatCount ?? 0,
    };
  });

  /** Counts in the sub-navigation (proposal §3.1): enabled adverts and store chats. */
  protected readonly counts = computed<Partial<Record<string, number>>>(() => ({
    adverts: this.summary().adverts,
    channels: this.summary().channels,
  }));
}
