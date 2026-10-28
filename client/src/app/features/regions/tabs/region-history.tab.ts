import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { HistorySubjectType } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { RegionPageContext } from '../region-page.context';
import { RegionResources } from '../regions.resource';

/** History tab (R7, ambassadors/ORGA): the region's history timeline with date and action filters. */
@Component({
  selector: 'fs-region-history-tab',
  imports: [HistoryTimelineComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (source(); as src) {
      <fs-history-timeline
        [source]="src"
        [subjectType]="subjectType"
        [viewerId]="userId()"
        [emptyTitle]="'regions.history.empty' | t"
        [emptyText]="'regions.history.emptyText' | t"
        testId="regions.history"
      />
    }
  `,
})
export class RegionHistoryTab {
  private readonly ctx = inject(RegionPageContext);
  private readonly resources = inject(RegionResources);
  private readonly session = inject(SessionService);

  protected readonly subjectType = HistorySubjectType.Region;
  protected readonly userId = computed(() => this.session.user()?.id);
  protected readonly source = computed(() => {
    const regionId = this.ctx.regionId();
    return regionId ? this.resources.regionHistory(regionId) : null;
  });
}
