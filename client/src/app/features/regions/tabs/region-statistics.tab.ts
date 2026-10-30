import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { AgeBand, GenderCount, MonthlyPickups, RegionStatistics, RegionsService, StatisticsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MonthBarChartComponent } from '../../stores/components/month-bar-chart.component';
import { RegionPageContext } from '../region-page.context';

interface Bar {
  key: string;
  label: string;
  count: number;
  percent: number;
}

export type PickupMetric = 'pickups' | 'kg';
/** Months of the pickup chart (`getRegionPickupStatistics(months)`, 1..36). */
export const PICKUP_MONTHS = 12;

export function bars(entries: [string, number][], label: (key: string) => string): Bar[] {
  const max = Math.max(1, ...entries.map(([, count]) => count));
  return entries.map(([key, count]) => ({ key, label: label(key), count, percent: Math.round((count / max) * 100) }));
}

/** Chart bars of the monthly pickup statistics for one metric (oldest month first, as the server sends them). */
export function pickupBars(months: readonly MonthlyPickups[], metric: PickupMetric): { month: string; value: number }[] {
  return months.map((month) => ({ month: month.month, value: metric === 'kg' ? month.kg : month.pickups }));
}

interface Demographics {
  regionId: string;
  onlyHomeRegion: boolean;
}

/**
 * Statistics tab (R3, R13): counters of the nightly statistics cache (members, food share points, baskets, …), the
 * monthly pickups/kg chart (`getRegionPickupStatistics`) and the gender list and age bands
 * (`getRegionGenderStatistics` / `getRegionAgeBands`) of all members or only those with this home region.
 */
@Component({
  selector: 'fs-region-statistics-tab',
  imports: [...DATA_STATE_IMPORTS, DateTimePipe, DecimalPipe, FormsModule, MonthBarChartComponent, TPipe, ToggleSwitch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="stats" skeleton="card" testId="regions.statistics.state" data-testid="regions.statistics.state">
      <ng-template [fsData]="stats" let-s>
        <ul class="counters" data-testid="regions.statistics.counters">
          <li class="fs-card counter"><span class="counter__value number">{{ s.memberCount | number }}</span><span class="counter__label">{{ 'regions.statistics.members' | t }}</span></li>
          <li class="fs-card counter"><span class="counter__value number">{{ s.activeHomeRegionFoodsavers | number }}</span><span class="counter__label">{{ 'regions.statistics.activeFoodsavers' | t }}</span></li>
          <li class="fs-card counter"><span class="counter__value number">{{ s.activeCooperations | number }}</span><span class="counter__label">{{ 'regions.statistics.cooperations' | t }}</span></li>
          <li class="fs-card counter"><span class="counter__value number">{{ s.pickupsLastMonth | number }}</span><span class="counter__label">{{ 'regions.statistics.pickups' | t }}</span></li>
          <li class="fs-card counter"><span class="counter__value number">{{ s.savedFoodWeightLastMonth | number: '1.0-0' }} kg</span><span class="counter__label">{{ 'regions.statistics.saved' | t }}</span></li>
          <li class="fs-card counter" data-testid="regions.statistics.fsps"><span class="counter__value number">{{ s.activeFoodSharePoints | number }}</span><span class="counter__label">{{ 'regions.statistics.fsps' | t }}</span></li>
          <li class="fs-card counter" data-testid="regions.statistics.baskets"><span class="counter__value number">{{ s.foodBasketsLastMonth | number }}</span><span class="counter__label">{{ 'regions.statistics.baskets' | t }}</span></li>
        </ul>
        @if (s.lastModified) {
          <p class="muted">{{ 'regions.statistics.updated' | t: { date: (s.lastModified | fsDate: 'dateTime') } }}</p>
        }
      </ng-template>
    </fs-data-state>

    <section class="fs-card card block" data-testid="regions.statistics.pickupChart">
      <div class="block__head">
        <h2 class="card__title"><i class="pi pi-chart-bar" aria-hidden="true"></i>{{ 'regions.statistics.monthly' | t: { months } }}</h2>
        <div class="chips" role="group" [attr.aria-label]="'regions.statistics.metric' | t">
          @for (m of metrics; track m) {
            <button type="button" class="chip" [class.chip--active]="metric() === m" [attr.aria-pressed]="metric() === m" (click)="metric.set(m)" [attr.data-testid]="'regions.statistics.metric.' + m">
              {{ 'regions.statistics.metric.' + m | t }}
            </button>
          }
        </div>
      </div>
      <fs-data-state [state]="pickups" skeleton="card" [compact]="true" [emptyTitle]="'regions.statistics.noPickups' | t" emptyIcon="pi pi-chart-bar" data-testid="regions.statistics.pickupState">
        <ng-template [fsData]="pickups">
          <fs-month-bar-chart
            [bars]="chartBars()"
            [label]="'regions.statistics.chart.' + metric() | t"
            [unit]="metric() === 'kg' ? 'kg' : ''"
            testId="regions.statistics.chart"
          />
        </ng-template>
      </fs-data-state>
    </section>

    <div class="switch-row only-home">
      <p-toggleswitch inputId="stats-only-home" [ngModel]="onlyHomeRegion()" (ngModelChange)="onlyHomeRegion.set($event)" data-testid="regions.statistics.onlyHomeRegion" />
      <span class="switch-row__text">
        <label class="switch-row__label" for="stats-only-home">{{ 'regions.statistics.onlyHomeRegion' | t }}</label>
        <small class="muted">{{ 'regions.statistics.onlyHomeRegionHint' | t }}</small>
      </span>
    </div>
    <div class="cols">
      <section class="fs-card card" data-testid="regions.statistics.gender">
        <h2 class="card__title"><i class="pi pi-users" aria-hidden="true"></i>{{ 'regions.statistics.gender' | t }}</h2>
        <fs-data-state [state]="genders" skeleton="list" [skeletonRows]="3" [compact]="true" [emptyTitle]="'regions.statistics.noMembers' | t" emptyIcon="pi pi-users" data-testid="regions.statistics.genderState">
          <ng-template [fsData]="genders">
            <ul class="bars" data-testid="regions.statistics.genderList">
              @for (bar of genderBars(); track bar.key) {
                <li class="bar" [attr.data-gender]="bar.key" data-testid="regions.statistics.genderRow">
                  <span class="bar__label">{{ bar.label }}</span>
                  <span class="bar__track" aria-hidden="true"><span class="bar__fill" [style.width.%]="bar.percent"></span></span>
                  <span class="bar__value number">{{ bar.count | number }}</span>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </section>
      <section class="fs-card card" data-testid="regions.statistics.age">
        <h2 class="card__title"><i class="pi pi-chart-bar" aria-hidden="true"></i>{{ 'regions.statistics.age' | t }}</h2>
        <fs-data-state [state]="ages" skeleton="list" [skeletonRows]="3" [compact]="true" [emptyTitle]="'regions.statistics.noMembers' | t" emptyIcon="pi pi-users" data-testid="regions.statistics.ageState">
          <ng-template [fsData]="ages">
            <ul class="bars">
              @for (bar of ageBars(); track bar.key) {
                <li class="bar" data-testid="regions.statistics.ageRow">
                  <span class="bar__label">{{ bar.label }}</span>
                  <span class="bar__track" aria-hidden="true"><span class="bar__fill" [style.width.%]="bar.percent"></span></span>
                  <span class="bar__value number">{{ bar.count | number }}</span>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </section>
    </div>
  `,
  styleUrl: './tab.scss',
  styles: `
    :host {
      display: grid;
      gap: 1.25rem;
    }
    .counters {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr));
      gap: 0.75rem;
      margin: 0 0 0.75rem;
      padding: 0;
      list-style: none;
    }
    .counter {
      display: grid;
      gap: 0.15rem;
      padding: 0.9rem 1rem;
    }
    .counter__value {
      font-size: 1.5rem;
      font-weight: 800;
      letter-spacing: -0.02em;
    }
    .counter__label {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .block {
      display: grid;
      gap: 0.75rem;
    }
    .block__head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem 1rem;
    }
    .block__head .card__title {
      margin: 0;
    }
    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .chip {
      min-height: 2rem;
      padding: 0.25rem 0.8rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
    }
    .chip--active {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .chip:focus-visible {
      outline: 2px solid var(--fs-primary);
      outline-offset: 2px;
    }
    .only-home {
      border-top: 0;
      padding-top: 0;
    }
    .bars {
      display: grid;
      gap: 0.5rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .bar {
      display: grid;
      grid-template-columns: minmax(5.5rem, 8rem) minmax(0, 1fr) 3rem;
      align-items: center;
      gap: 0.75rem;
      font-size: 0.88rem;
    }
    .bar__track {
      height: 0.65rem;
      overflow: hidden;
      border-radius: 999px;
      background: var(--fs-surface-muted);
    }
    .bar__fill {
      display: block;
      height: 100%;
      border-radius: 999px;
      background: var(--fs-primary);
    }
    .bar__value {
      text-align: right;
    }
  `,
})
export class RegionStatisticsTab {
  private readonly ctx = inject(RegionPageContext);
  private readonly api = inject(RegionsService);
  private readonly statisticsApi = inject(StatisticsService);
  private readonly i18n = inject(I18nService);

  protected readonly months = PICKUP_MONTHS;
  protected readonly metrics: readonly PickupMetric[] = ['pickups', 'kg'];
  protected readonly metric = signal<PickupMetric>('pickups');
  protected readonly onlyHomeRegion = signal(false);

  protected readonly stats = loadResource<RegionStatistics, string>({
    params: () => this.ctx.regionId() ?? undefined,
    loader: (regionId) => this.api.getRegionStatistics({ regionId }),
    isEmpty: () => false,
  });

  protected readonly pickups = loadResource<MonthlyPickups[], string>({
    params: () => this.ctx.regionId() ?? undefined,
    loader: (regionId) => this.statisticsApi.getRegionPickupStatistics({ regionId, months: PICKUP_MONTHS }),
  });

  private readonly demographics = computed<Demographics | undefined>(() => {
    const regionId = this.ctx.regionId();
    return regionId ? { regionId, onlyHomeRegion: this.onlyHomeRegion() } : undefined;
  });

  protected readonly genders = loadResource<GenderCount[], Demographics>({
    params: () => this.demographics(),
    loader: (params) => this.statisticsApi.getRegionGenderStatistics(params),
  });

  protected readonly ages = loadResource<AgeBand[], Demographics>({
    params: () => this.demographics(),
    loader: (params) => this.statisticsApi.getRegionAgeBands(params),
  });

  protected readonly chartBars = computed(() => pickupBars(this.pickups.data() ?? [], this.metric()));

  protected readonly genderBars = computed(() => {
    this.i18n.language();
    return bars(
      (this.genders.data() ?? []).map((entry) => [entry.gender, entry.count]),
      (key) => this.i18n.t(`regions.gender.${key}`),
    );
  });

  protected readonly ageBars = computed(() =>
    bars((this.ages.data() ?? []).map((band) => [band.label, band.count]), (label) => label),
  );
}
