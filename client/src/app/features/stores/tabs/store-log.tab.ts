import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { HistorySubjectType, Role } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { HistoryResources } from '../../history/history.resource';
import { StorePageContext } from '../store-page.context';
import { STORE_LOG_PRESETS, StoreResources } from '../stores.resource';

/**
 * /stores/:storeId/log (S12, maySeeStoreLog): the store log as a history timeline with date range, action filter and
 * one-click action groups (team / pickups / wall / settings); CSV export for ORGA.
 */
@Component({
  selector: 'fs-store-log-tab',
  imports: [HistoryTimelineComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (source(); as src) {
      <fs-history-timeline
        [source]="src"
        [subjectType]="subjectType"
        [presets]="presets"
        [csvExport]="csvExport()"
        [showExportButton]="!!csvExport()"
        exportFileName="store-log.csv"
        [viewerId]="userId()"
        [emptyTitle]="'stores.log.empty' | t"
        [emptyText]="'stores.log.emptyText' | t"
        [askWho]="'stores.page.askWho' | t"
        testId="stores.log"
      />
    }
  `,
})
export class StoreLogTab {
  private readonly ctx = inject(StorePageContext);
  private readonly resources = inject(StoreResources);
  private readonly history = inject(HistoryResources);
  private readonly session = inject(SessionService);

  protected readonly subjectType = HistorySubjectType.Store;
  protected readonly presets = STORE_LOG_PRESETS;
  protected readonly userId = computed(() => this.session.user()?.id);
  protected readonly source = computed(() => {
    const storeId = this.ctx.storeId();
    // no permission gate: the server answers 403 (forbidden state) without maySeeStoreLog
    return storeId ? this.resources.storeLog(storeId) : null;
  });
  /** The CSV export endpoint is ORGA only. */
  protected readonly csvExport = computed(() => {
    const storeId = this.ctx.storeId();
    return storeId && this.session.hasRole(Role.Orga)
      ? this.history.subjectCsv(HistorySubjectType.Store, storeId)
      : undefined;
  });
}
