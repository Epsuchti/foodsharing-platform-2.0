import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { PublicContentService, PublicStatistics } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { StatisticsTilesComponent } from '../components/statistics-tiles.component';

/**
 * /statistics (C3, public): the platform totals of the latest daily aggregation as seven counters that count up
 * (`fs-animated-counter`, final values at once with reduced motion) and the time of the aggregation.
 */
@Component({
  selector: 'fs-statistics-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    DateTimePipe,
    PageHeaderComponent,
    StatisticsTilesComponent,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.statistics.title' | t"
      [subtitle]="'content.statistics.subtitle' | t"
      icon="pi pi-chart-bar"
    />
    <fs-data-state [state]="stats" skeleton="card" data-testid="content.statistics.state">
      <ng-template [fsData]="stats" let-s>
        <fs-statistics-tiles [stats]="s" />
        <p class="as-of" data-testid="content.statistics.asOf" data-screenshot-mask>
          {{ 'content.statistics.asOf' | t: { date: (s.asOf | fsDate: 'dateTime') } }}
        </p>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
    }
    .as-of {
      margin: 1rem 0 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
  `,
})
export class StatisticsPageComponent {
  private readonly api = inject(PublicContentService);

  protected readonly stats = loadResource<PublicStatistics>({
    loader: () => this.api.getPublicStatistics(),
  });
}
