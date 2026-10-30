import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { HistorySubjectType } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { RegionPageContext } from '../../regions/region-page.context';
import { ReportsResources } from '../reports.resource';

/**
 * /regions/:regionId/report-log (D10.3 report-log piece, `getReportLog`): every change to the region's reports
 * (created, status, consequence, reminder, thread, wall, deletion, mediation requests) on the shared history
 * timeline with a date filter. Report handlers only; others see the forbidden state.
 */
@Component({
  selector: 'fs-region-report-log-tab',
  imports: [Button, HistoryTimelineComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <p class="muted">{{ 'reports.log.intro' | t }}</p>
      <p-button
        [label]="'reports.log.reports' | t"
        icon="pi pi-flag"
        severity="secondary"
        [outlined]="true"
        routerLink="../reports"
        data-testid="reports.log.reports"
      />
    </div>
    @if (source(); as s) {
      <fs-history-timeline
        [source]="s"
        [filters]="{ dateRange: true, actions: false }"
        [subjectType]="subjectType"
        [viewerId]="viewerId()"
        [emptyTitle]="'reports.log.empty' | t"
        testId="reports.log"
      />
    }
  `,
  styles: `
    :host { display: block; }
    .head { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: center; justify-content: space-between; margin-bottom: 1rem; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; max-width: 40rem; }
  `,
})
export class RegionReportLogTab {
  private readonly ctx = inject(RegionPageContext);
  private readonly resources = inject(ReportsResources);
  private readonly session = inject(SessionService);

  protected readonly subjectType = HistorySubjectType.Report;
  protected readonly viewerId = computed(() => this.session.user()?.id);
  protected readonly source = computed(() => {
    const regionId = this.ctx.regionId();
    return regionId ? this.resources.reportLog(regionId) : null;
  });
}
