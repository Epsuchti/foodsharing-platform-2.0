import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';

import { RegionStatistics, RegionsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { RegionPageContext } from '../region-page.context';

interface Bar {
  key: string;
  label: string;
  count: number;
  percent: number;
}

function bars(entries: [string, number][], label: (key: string) => string): Bar[] {
  const max = Math.max(1, ...entries.map(([, count]) => count));
  return entries.map(([key, count]) => ({ key, label: label(key), count, percent: Math.round((count / max) * 100) }));
}

/**
 * Statistics tab (R3, R13): counters of the nightly statistics cache, gender list and age bands. Counters of
 * modules that do not exist yet (pickups, baskets, food-share points) are 0 until their milestone.
 */
@Component({
  selector: 'fs-region-statistics-tab',
  imports: [...DATA_STATE_IMPORTS, DateTimePipe, DecimalPipe, TPipe],
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
          <li class="fs-card counter"><span class="counter__value number">{{ s.activeFoodSharePoints | number }}</span><span class="counter__label">{{ 'regions.statistics.fsps' | t }}</span></li>
          <li class="fs-card counter"><span class="counter__value number">{{ s.foodBasketsLastMonth | number }}</span><span class="counter__label">{{ 'regions.statistics.baskets' | t }}</span></li>
        </ul>
        <div class="cols">
          <section class="fs-card card" data-testid="regions.statistics.gender">
            <h2 class="card__title"><i class="pi pi-users" aria-hidden="true"></i>{{ 'regions.statistics.gender' | t }}</h2>
            <ul class="bars">
              @for (bar of genderBars(); track bar.key) {
                <li class="bar">
                  <span class="bar__label">{{ bar.label }}</span>
                  <span class="bar__track" aria-hidden="true"><span class="bar__fill" [style.width.%]="bar.percent"></span></span>
                  <span class="bar__value number">{{ bar.count | number }}</span>
                </li>
              }
            </ul>
          </section>
          <section class="fs-card card" data-testid="regions.statistics.age">
            <h2 class="card__title"><i class="pi pi-chart-bar" aria-hidden="true"></i>{{ 'regions.statistics.age' | t }}</h2>
            <ul class="bars">
              @for (bar of ageBars(); track bar.key) {
                <li class="bar">
                  <span class="bar__label">{{ bar.label }}</span>
                  <span class="bar__track" aria-hidden="true"><span class="bar__fill" [style.width.%]="bar.percent"></span></span>
                  <span class="bar__value number">{{ bar.count | number }}</span>
                </li>
              }
            </ul>
          </section>
        </div>
        @if (s.lastModified) {
          <p class="muted">{{ 'regions.statistics.updated' | t: { date: (s.lastModified | fsDate: 'dateTime') } }}</p>
        }
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: './tab.scss',
  styles: `
    .counters {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr));
      gap: 0.75rem;
      margin: 0 0 1.25rem;
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
  private readonly i18n = inject(I18nService);

  protected readonly stats = loadResource<RegionStatistics, string>({
    params: () => this.ctx.regionId() ?? undefined,
    loader: (regionId) => this.api.getRegionStatistics({ regionId }),
    isEmpty: () => false,
  });

  protected readonly genderBars = computed(() => {
    this.i18n.language();
    const counts = this.stats.data()?.genderCounts ?? {};
    return bars(Object.entries(counts), (key) => this.i18n.t(`regions.gender.${key}`));
  });

  protected readonly ageBars = computed(() =>
    bars((this.stats.data()?.ageBands ?? []).map((band) => [band.label, band.count]), (label) => label),
  );
}
