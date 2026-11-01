import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';

export interface ChartSeries {
  key: string;
  label: string;
  points: readonly { day: string; value: number }[];
}

const W = 600;
const H = 180;
const PAD = 24;
const COLORS = ['#25834a', '#2f6fb4', '#c2410c', '#7c3aed', '#be185d', '#0f766e', '#a16207', '#475569'];

/** Unit of one chart point: a day, or a week labelled with its first day. */
export type ChartBucket = 'day' | 'week';

/**
 * `fs-daily-line-chart` (AU9): one SVG polyline per series over the same days (or weeks), legend with totals and a
 * visually hidden data table (series as columns, days/weeks as rows) for screen readers.
 */
@Component({
  selector: 'fs-daily-line-chart',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="chart" [attr.data-testid]="testId()">
      <svg [attr.viewBox]="'0 0 ' + w + ' ' + h" role="img" [attr.aria-label]="label()" preserveAspectRatio="none">
        <line [attr.x1]="pad" [attr.y1]="h - pad" [attr.x2]="w - pad" [attr.y2]="h - pad" class="axis" />
        <text [attr.x]="pad" y="14" class="tick">{{ max() }}</text>
        @for (line of lines(); track line.key) {
          <polyline [attr.points]="line.points" [attr.stroke]="line.color" fill="none" stroke-width="2" vector-effect="non-scaling-stroke" />
        }
      </svg>
      <figcaption>
        <ul class="legend">
          @for (line of lines(); track line.key) {
            <li [attr.data-testid]="testId() + '.legend'" [attr.data-key]="line.key" [attr.data-total]="line.total">
              <span class="swatch" [style.background]="line.color"></span>{{ line.label }}: <strong>{{ line.total }}</strong>
            </li>
          }
        </ul>
        @if (days().length) {
          <span class="range" [attr.data-testid]="testId() + '.range'">
            {{ days()[0] }} – {{ days()[days().length - 1] }},
            @if (bucket() === 'week') {
              {{ 'automation.metrics.perWeek' | t }}
            } @else {
              {{ 'automation.metrics.perDay' | t }}
            }
          </span>
        }
      </figcaption>
      <table class="fs-visually-hidden" [attr.data-testid]="testId() + '.table'">
        <caption>
          {{ label() }}: {{ 'automation.metrics.tableCaption' | t }},
          @if (bucket() === 'week') {
            {{ 'automation.metrics.perWeek' | t }}
          } @else {
            {{ 'automation.metrics.perDay' | t }}
          }
        </caption>
        <thead>
          <tr>
            <td></td>
            @for (line of lines(); track line.key) {
              <th scope="col">{{ line.label }}</th>
            }
          </tr>
        </thead>
        <tbody>
          @for (day of days(); track day; let i = $index) {
            <tr>
              <th scope="row">{{ day }}</th>
              @for (s of series(); track s.key) {
                <td>{{ s.points[i]?.value ?? 0 }}</td>
              }
            </tr>
          }
        </tbody>
      </table>
    </figure>
  `,
  styles: `
    .chart { margin: 0; display: grid; gap: 0.5rem; }
    svg { width: 100%; height: 12rem; background: var(--fs-surface-muted); border-radius: var(--fs-radius-md); }
    .axis { stroke: var(--fs-border-strong); }
    .tick { fill: var(--fs-text-muted); font-size: 11px; }
    .legend { display: flex; flex-wrap: wrap; gap: 0.4rem 1rem; margin: 0; padding: 0; list-style: none; font-size: 0.88rem; }
    .swatch { display: inline-block; width: 0.8rem; height: 0.8rem; margin-right: 0.35rem; border-radius: 2px; vertical-align: middle; }
    .range { color: var(--fs-text-muted); font-size: 0.8rem; }
  `,
})
export class DailyLineChartComponent {
  readonly series = input.required<readonly ChartSeries[]>();
  readonly label = input.required<string>();
  readonly testId = input('automation.chart');
  /** What one point stands for; shown in the range caption and the table caption. */
  readonly bucket = input<ChartBucket>('day');

  protected readonly w = W;
  protected readonly h = H;
  protected readonly pad = PAD;

  protected readonly days = computed(() => this.series()[0]?.points.map((p) => p.day) ?? []);
  protected readonly max = computed(() => Math.max(1, ...this.series().flatMap((s) => s.points.map((p) => p.value))));

  protected readonly lines = computed(() => {
    const max = this.max();
    return this.series().map((s, index) => {
      const n = Math.max(1, s.points.length - 1);
      const points = s.points
        .map((p, i) => {
          const x = PAD + ((W - 2 * PAD) * i) / n;
          const y = H - PAD - ((H - 2 * PAD) * p.value) / max;
          return `${x.toFixed(1)},${y.toFixed(1)}`;
        })
        .join(' ');
      return { key: s.key, label: s.label, color: COLORS[index % COLORS.length], points, total: s.points.reduce((sum, p) => sum + p.value, 0) };
    });
  });
}
