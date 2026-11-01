import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';

/**
 * "✓ 2 von 3 bestätigt · ⧗ 1 unbestätigt" for one pickup (PU3 store tab, PU7 options page). The icons are
 * `aria-hidden`; the text is read as is. `data-confirmed` / `data-pending` carry the counts for tests.
 *
 * ```html
 * <fs-pickup-summary [confirmed]="2" [unconfirmed]="1" [total]="3" testId="stores.pickups.summary" />
 * ```
 */
@Component({
  selector: 'fs-pickup-summary',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'pickup-summary',
    '[attr.data-testid]': 'testId()',
    '[attr.data-confirmed]': 'confirmed()',
    '[attr.data-pending]': 'unconfirmed()',
  },
  template: `
    <span class="part part--confirmed">
      <i class="pi pi-check" aria-hidden="true"></i>{{ 'pickups.tab.summary.confirmed' | t: { confirmed: confirmed(), total: total() } }}
    </span>
    @if (unconfirmed() > 0) {
      <span class="dot" aria-hidden="true">·</span>
      <span class="part part--unconfirmed">
        <i class="pi pi-hourglass" aria-hidden="true"></i>{{ 'pickups.tab.summary.unconfirmed' | t: { count: unconfirmed() } }}
      </span>
    }
    <ng-content />
  `,
  styles: `
    :host {
      display: inline-flex; flex-wrap: wrap; align-items: center; gap: 0.4rem;
      color: var(--fs-text-muted); font-size: 0.82rem; font-variant-numeric: tabular-nums;
    }
    .part { display: inline-flex; align-items: center; gap: 0.25rem; }
    .part .pi { font-size: 0.9em; }
    .part--confirmed { color: var(--fs-primary-strong); }
    .part--unconfirmed { color: var(--fs-warning-text); font-weight: 650; }
    .dot { color: var(--fs-border-strong); }
  `,
})
export class PickupSummaryComponent {
  readonly confirmed = input.required<number>();
  readonly unconfirmed = input.required<number>();
  readonly total = input.required<number>();
  readonly testId = input<string | undefined>(undefined);
}
