import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AutomationService, StoreRuleSummary } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimeFormatter } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { readStorage, writeStorage } from '../../../core/storage';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { RuleLine, ruleCount, summaryLines, uiRuleFormat } from '../rule-sentences';

/** States in which the card is not shown: rules off (empty), not a member (403), store gone, nothing asked yet. */
const HIDDEN = new Set(['idle', 'empty', 'forbidden', 'notFound']);

/**
 * "So kannst du dich hier eintragen" on the Pickups tab (decision 165 S2, proposal §3.2): the store's active rules as
 * plain sentences built from structured data. A collapsed `<details>`; the open state is remembered per store in
 * `localStorage` (try/catch: works without storage). Hidden while the rules are off.
 */
@Component({
  selector: 'fs-rule-summary-card',
  imports: [...DATA_STATE_IMPORTS, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <details
        class="card fs-card rules"
        [open]="open()"
        (toggle)="onToggle($event)"
        [attr.data-state]="rules.status()"
        data-testid="stores.pickups.rules"
      >
        <summary class="rules__summary" data-testid="stores.pickups.rules.toggle">
          <i class="pi pi-shield" aria-hidden="true"></i>
          <span class="rules__title">{{ 'pickups.rules.title' | t }}</span>
          @if (lines().length) {
            <span class="badge" data-testid="stores.pickups.rules.count">{{ 'pickups.rules.count' | t: { count: count() } }}</span>
          }
          <i class="pi pi-chevron-down rules__chev" aria-hidden="true"></i>
        </summary>
        <fs-data-state [state]="rules" skeleton="inline" [skeletonRows]="2" [compact]="true" testId="stores.pickups.rules.state">
          <ng-template [fsData]="rules">
            <ul class="rules__list">
              @for (line of lines(); track $index) {
                <li data-testid="stores.pickups.rules.line" [attr.data-rule]="line.rule">
                  <i [class]="'pi ' + line.icon" aria-hidden="true"></i>
                  <span>{{ line.key | t: line.params }}</span>
                </li>
              }
            </ul>
            <p class="rules__ask">
              {{ 'pickups.rules.ask' | t }}
              <a routerLink="../team" data-testid="stores.pickups.rules.team">{{ 'pickups.rules.askLink' | t }}</a>
            </p>
          </ng-template>
        </fs-data-state>
      </details>
    }
  `,
  styles: `
    .rules { padding: 0; margin-bottom: 1rem; }
    .rules__summary {
      display: flex; align-items: center; gap: 0.6rem; min-height: var(--fs-target-sm); padding: 0.8rem 1.15rem;
      cursor: pointer; list-style: none; font-weight: 650;
    }
    .rules__summary::-webkit-details-marker { display: none; }
    .rules__summary .pi-shield { color: var(--fs-primary-strong); }
    .rules__summary:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: -2px; border-radius: var(--fs-radius-md); }
    .rules__chev { margin-left: auto; color: var(--fs-text-muted); transition: transform 0.15s; }
    .rules[open] .rules__chev { transform: rotate(180deg); }
    .badge { padding: 0.05rem 0.5rem; border-radius: 999px; background: var(--fs-surface-muted); font-size: 0.75rem; font-weight: 600; }
    fs-data-state { padding: 0 1.15rem 0.9rem; }
    .rules__list { display: grid; gap: 0.5rem; margin: 0; padding: 0; list-style: none; }
    .rules__list li { display: flex; gap: 0.6rem; align-items: baseline; font-size: 0.92rem; }
    .rules__list .pi { color: var(--fs-primary-strong); }
    .rules__ask { margin: 0.75rem 0 0; color: var(--fs-text-muted); font-size: 0.88rem; }
    @media (prefers-reduced-motion: reduce) { .rules__chev { transition: none; } }
  `,
})
export class RuleSummaryCardComponent {
  private readonly api = inject(AutomationService);
  private readonly i18n = inject(I18nService);
  private readonly dates = inject(DateTimeFormatter);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly storeId = input.required<string>();
  readonly timeZone = input.required<string>();

  protected readonly rules = loadResource<StoreRuleSummary, string>({
    params: () => this.storeId(),
    loader: (storeId) => this.api.getStoreRules({ storeId }),
    isEmpty: (summary) => !summary.enabled,
  });

  protected readonly visible = computed(() => !HIDDEN.has(this.rules.status()));

  protected readonly lines = computed<RuleLine[]>(() => {
    this.i18n.locale();
    return summaryLines(
      this.rules.data(),
      uiRuleFormat(
        (key, params) => this.i18n.t(key, params),
        (value, style, timeZone) => this.dates.format(value, style, timeZone),
        this.i18n.locale(),
        this.timeZone(),
      ),
    );
  });

  protected readonly count = computed(() => ruleCount(this.lines()));

  protected readonly open = signal(false);

  constructor() {
    effect(() => this.open.set(readStorage(this.storageKey()) === '1'));
  }

  private storageKey(): string {
    return `fs.pickups.rules.open.${this.storeId()}`;
  }

  protected onToggle(event: Event): void {
    const open = (event.target as HTMLDetailsElement).open;
    this.open.set(open);
    writeStorage(this.storageKey(), open ? '1' : null);
  }

  /** Opens the card and scrolls it into view ("Alle Regeln dieses Betriebs" in the reason dialog). */
  reveal(): void {
    this.open.set(true);
    writeStorage(this.storageKey(), '1');
    queueMicrotask(() => {
      const card = this.host.nativeElement.querySelector<HTMLElement>('[data-testid="stores.pickups.rules.toggle"]');
      card?.scrollIntoView({ block: 'start', behavior: 'smooth' });
      card?.focus();
    });
  }
}
