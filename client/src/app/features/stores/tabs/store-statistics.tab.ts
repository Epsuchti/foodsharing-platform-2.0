import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { StoreStatistics, StoresService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { MonthBarChartComponent } from '../components/month-bar-chart.component';
import { StorePageContext } from '../store-page.context';

type StatsRange = 'year' | 'all';
type Metric = 'pickups' | 'kg';

/** First day the "all time" range asks for (older than any foodsharing pickup). */
const ALL_TIME_FROM = '2012-01-01';

/**
 * /stores/:storeId/statistics (S14, team members): totals, a month bar chart of pickups or kg (kg = pickups × the
 * average of the store's weight category) and, for managers, pickups per member.
 */
@Component({
  selector: 'fs-store-statistics-tab',
  imports: [...DATA_STATE_IMPORTS, DecimalPipe, MonthBarChartComponent, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toolbar">
      <div class="chips" role="group" [attr.aria-label]="'stores.statistics.range' | t">
        @for (r of ranges; track r) {
          <button type="button" class="chip" [class.chip--active]="range() === r" [attr.aria-pressed]="range() === r" (click)="range.set(r)" [attr.data-testid]="'stores.statistics.range.' + r">
            {{ 'stores.statistics.range.' + r | t }}
          </button>
        }
      </div>
      <div class="chips" role="group" [attr.aria-label]="'stores.statistics.metric' | t">
        @for (m of metrics; track m) {
          <button type="button" class="chip" [class.chip--active]="metric() === m" [attr.aria-pressed]="metric() === m" (click)="metric.set(m)" [attr.data-testid]="'stores.statistics.metric.' + m">
            {{ 'stores.statistics.metric.' + m | t }}
          </button>
        }
      </div>
    </div>

    <fs-data-state [state]="stats" skeleton="card" [askWho]="'stores.page.askWho' | t" testId="stores.statistics.state" data-testid="stores.statistics.state">
      <ng-template [fsData]="stats" let-s>
        <div class="tiles">
          <div class="fs-card tile" data-testid="stores.statistics.totalPickups">
            <span class="tile__value">{{ s.totalPickups | number }}</span>
            <span class="tile__label">{{ 'stores.statistics.totalPickups' | t }}</span>
          </div>
          <div class="fs-card tile" data-testid="stores.statistics.totalKg">
            <span class="tile__value">{{ s.totalKg | number: '1.0-1' }} kg</span>
            <span class="tile__label">{{ 'stores.statistics.totalKg' | t }}</span>
          </div>
        </div>

        <section class="fs-card card">
          <fs-month-bar-chart
            [bars]="bars()"
            [label]="('stores.statistics.chart.' + metric()) | t"
            [unit]="metric() === 'kg' ? 'kg' : ''"
            testId="stores.statistics.chart"
          />
          <p class="hint" data-testid="stores.statistics.weightHint">
            <i class="pi pi-info-circle" aria-hidden="true"></i>
            @if (s.weightCategory; as category) {
              {{ 'stores.statistics.weightHint' | t: { category: category.label, kg: (category.averageKg | number: '1.0-1') ?? '' } }}
            } @else {
              {{ 'stores.statistics.noWeight' | t }}
            }
          </p>
        </section>

        @if (s.perMember?.length) {
          <section class="fs-card card" data-testid="stores.statistics.members">
            <h2 class="card__title">{{ 'stores.statistics.perMember' | t }}</h2>
            <table class="members">
              <thead>
                <tr>
                  <th scope="col">{{ 'stores.statistics.member' | t }}</th>
                  <th scope="col" class="num">{{ 'stores.statistics.metric.pickups' | t }}</th>
                </tr>
              </thead>
              <tbody>
                @for (row of s.perMember; track row.user.id) {
                  <tr data-testid="stores.statistics.member" [attr.data-user]="row.user.displayName">
                    <td><fs-user-chip [name]="row.user.displayName" [userId]="row.user.id" [avatarUrl]="row.user.avatarUrl" /></td>
                    <td class="num">{{ row.pickups | number }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 1rem; }
    .toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.5rem; }
    .chips { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .chip {
      padding: 0.3rem 0.8rem; border: 1px solid var(--fs-border); border-radius: 999px; background: var(--fs-surface);
      color: inherit; font: inherit; font-size: 0.88rem; cursor: pointer;
    }
    /* soft fill + strong text: white on --fs-primary fails contrast in the dark theme (F6 sweep) */
    .chip--active { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: 0.75rem; margin-bottom: 1rem; }
    .tile { display: grid; gap: 0.2rem; padding: 1rem; }
    .tile__value { font-size: 1.6rem; font-weight: 700; font-variant-numeric: tabular-nums; }
    .tile__label { color: var(--fs-text-muted); }
    .card { display: grid; gap: 0.75rem; padding: 1rem; margin-bottom: 1rem; }
    .card__title { margin: 0; font-size: 1.05rem; }
    .hint { display: flex; gap: 0.4rem; align-items: baseline; margin: 0; color: var(--fs-text-muted); font-size: 0.88rem; }
    .members { width: 100%; border-collapse: collapse; }
    .members th, .members td { padding: 0.4rem 0.25rem; border-bottom: 1px solid var(--fs-border); text-align: left; }
    .members .num { text-align: right; font-variant-numeric: tabular-nums; }
  `,
})
export class StoreStatisticsTab {
  protected readonly ctx = inject(StorePageContext);
  private readonly api = inject(StoresService);

  protected readonly ranges: StatsRange[] = ['year', 'all'];
  protected readonly metrics: Metric[] = ['pickups', 'kg'];
  protected readonly range = signal<StatsRange>('year');
  protected readonly metric = signal<Metric>('pickups');

  protected readonly stats = loadResource<StoreStatistics, { storeId: string; range: StatsRange }>({
    params: () => {
      const storeId = this.ctx.storeId();
      return storeId ? { storeId, range: this.range() } : undefined;
    },
    // the server's default is the last 12 months up to its own today
    loader: ({ storeId, range }) =>
      this.api.getStoreStatistics({ storeId, ...(range === 'all' ? { from: ALL_TIME_FROM } : {}) }),
    isEmpty: () => false,
  });

  protected readonly bars = computed(() => {
    const metric = this.metric();
    const months = this.stats.data()?.months ?? [];
    // "all time" starts long before the store's first pickup: drop the empty months before it
    const used = this.range() === 'all' ? months.findIndex((month) => month.pickups > 0) : 0;
    const first = used < 0 ? months.length - 12 : used;
    return months.slice(Math.max(0, first)).map((month) => ({ month: month.month, value: month[metric] }));
  });
}
