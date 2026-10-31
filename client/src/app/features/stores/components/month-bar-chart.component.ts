import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { I18nService } from '../../../core/i18n/i18n.service';

export interface MonthBar {
  /** `YYYY-MM` (or `YYYY-MM-DD`, only year and month are used). */
  month: string;
  value: number;
}

/** Short month label (`Okt. 26`) in the UI locale. */
export function monthLabel(month: string, locale: string): string {
  const [year, mon] = month.split('-').map(Number);
  return new Intl.DateTimeFormat(locale, { month: 'short', year: '2-digit', timeZone: 'UTC' }).format(
    new Date(Date.UTC(year, (mon || 1) - 1, 1)),
  );
}

/**
 * `<fs-month-bar-chart [bars]="…" [label]="…" unit="kg" />` (S14): accessible bar chart, one bar per month (CSS, no
 * chart library). The values are also exposed as a list for screen readers.
 */
let nextChartId = 0;

@Component({
  selector: 'fs-month-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="chart" [attr.data-testid]="testId()">
      <figcaption class="chart__caption" [id]="captionId">{{ label() }}</figcaption>
      <!-- the months scroll sideways on phones: the scroll area is focusable so keyboards can scroll it (axe) -->
      <div class="chart__scroll" tabindex="0" role="group" [attr.aria-labelledby]="captionId" [attr.data-testid]="testId() + '.scroll'">
      <ol class="chart__bars" [attr.aria-label]="label()">
        @for (bar of view(); track bar.month) {
          <li class="chart__item" [attr.data-month]="bar.month" [attr.data-value]="bar.value" [attr.data-testid]="testId() + '.bar'">
            <span class="chart__value">{{ bar.text }}</span>
            <span class="chart__track" aria-hidden="true"><span class="chart__bar" [style.height.%]="bar.height"></span></span>
            <span class="chart__month">{{ bar.label }}</span>
          </li>
        }
      </ol>
      </div>
    </figure>
  `,
  styles: `
    :host { display: block; }
    .chart { margin: 0; }
    .chart__caption { margin-bottom: 0.5rem; font-weight: 600; }
    .chart__bars {
      display: grid; grid-auto-flow: column; grid-auto-columns: minmax(2.2rem, 1fr); gap: 0.35rem;
      margin: 0; padding: 0 0 0.25rem; list-style: none;
    }
    .chart__scroll { overflow-x: auto; border-radius: var(--fs-radius-sm); }
    .chart__scroll:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
    .chart__item { display: grid; grid-template-rows: auto 9rem auto; gap: 0.25rem; justify-items: center; }
    .chart__value { font-size: 0.75rem; font-variant-numeric: tabular-nums; color: var(--fs-text-muted); }
    .chart__track { display: flex; align-items: flex-end; width: 100%; height: 100%; }
    .chart__bar { width: 100%; min-height: 2px; border-radius: 0.3rem 0.3rem 0 0; background: var(--fs-primary); }
    .chart__month { font-size: 0.75rem; white-space: nowrap; }
  `,
})
export class MonthBarChartComponent {
  private readonly i18n = inject(I18nService);
  protected readonly captionId = `fs-month-chart-${nextChartId++}`;

  readonly bars = input.required<readonly MonthBar[]>();
  readonly label = input.required<string>();
  readonly unit = input<string>('');
  readonly testId = input('stores.chart');

  protected readonly view = computed(() => {
    const bars = this.bars();
    const max = Math.max(1, ...bars.map((bar) => bar.value));
    const locale = this.i18n.locale();
    const number = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
    return bars.map((bar) => ({
      month: bar.month,
      value: bar.value,
      height: (bar.value / max) * 100,
      label: monthLabel(bar.month, locale),
      text: this.unit() ? `${number.format(bar.value)} ${this.unit()}` : number.format(bar.value),
    }));
  });
}
