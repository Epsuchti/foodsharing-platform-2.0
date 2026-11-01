import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface MetricTotal {
  key: string;
  label: string;
  total: number;
}

/** `fs-metric-totals` (AU9): ranked totals of one metric group as horizontal bars, relative to the largest total. */
@Component({
  selector: 'fs-metric-totals',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="totals" [attr.aria-label]="label()" [attr.data-testid]="testId()">
      @for (row of rows(); track row.key) {
        <li class="row" [attr.data-testid]="testId() + '.row'" [attr.data-key]="row.key" [attr.data-total]="row.total">
          <span class="label">{{ row.label }}</span>
          <span class="track" aria-hidden="true"><span class="bar" [style.width.%]="row.width"></span></span>
          <strong class="total">{{ row.total }}</strong>
        </li>
      }
    </ol>
  `,
  styles: `
    .totals { display: grid; gap: 0.4rem; margin: 0; padding: 0; list-style: none; font-size: 0.9rem; }
    .row { display: grid; grid-template-columns: minmax(8rem, 2fr) minmax(4rem, 3fr) auto; gap: 0.6rem; align-items: center; }
    .label { min-width: 0; overflow-wrap: anywhere; }
    .track { height: 0.75rem; border-radius: var(--fs-radius-sm); background: var(--fs-surface-muted); overflow: hidden; }
    .bar { display: block; height: 100%; min-width: 2px; background: var(--fs-primary); }
    .total { font-variant-numeric: tabular-nums; text-align: right; }
    @media (max-width: 480px) {
      .row { grid-template-columns: 1fr auto; }
      .track { grid-column: 1 / -1; grid-row: 2; }
    }
  `,
})
export class MetricTotalsComponent {
  /** Rows in display order (the caller sorts them). */
  readonly totals = input.required<readonly MetricTotal[]>();
  readonly label = input.required<string>();
  readonly testId = input('automation.metrics.totals');

  protected readonly rows = computed(() => {
    const max = Math.max(1, ...this.totals().map((t) => t.total));
    return this.totals().map((t) => ({ ...t, width: (100 * t.total) / max }));
  });
}
