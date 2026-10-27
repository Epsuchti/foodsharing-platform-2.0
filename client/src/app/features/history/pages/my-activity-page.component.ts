import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { HistorySubjectType, Role } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { HistoryResources } from '../history.resource';

/**
 * /me/activity (D10.3 "My activity"): the user's own history with date and action filters. ORGA additionally
 * gets the CSV export of their account's history. The JSON export (A21, GDPR) arrives in M6.
 */
@Component({
  selector: 'fs-my-activity-page',
  imports: [HistoryTimelineComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [eyebrow]="'history.myActivity.eyebrow' | t"
      [title]="'history.myActivity.title' | t"
      [subtitle]="'history.myActivity.subtitle' | t"
      icon="pi pi-history"
    />

    <fs-history-timeline
      [source]="resources.myActivity"
      [filters]="{ dateRange: true, actions: true }"
      [subjectType]="HistorySubjectType.User"
      [csvExport]="csvExport()"
      exportFileName="my-activity.csv"
      [viewerId]="userId()"
      [emptyTitle]="'history.myActivity.emptyTitle' | t"
      [emptyText]="'history.myActivity.emptyText' | t"
      [askWho]="'history.myActivity.askWho' | t"
      backLink="/dashboard"
      testId="history.myActivity"
    />

    <p class="retention" data-testid="history.myActivity.retention">
      <i class="pi pi-info-circle" aria-hidden="true"></i>
      <span>{{ 'history.myActivity.retention' | t }}</span>
    </p>
  `,
  styles: `
    :host {
      display: block;
      max-width: 56rem;
    }
    .retention {
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      margin: 2rem 0 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .retention i {
      margin-top: 0.15rem;
    }
  `,
})
export class MyActivityPageComponent {
  private readonly session = inject(SessionService);
  protected readonly resources = inject(HistoryResources);
  protected readonly HistorySubjectType = HistorySubjectType;

  protected readonly userId = computed(() => this.session.user()?.id ?? null);

  /** CSV export of the own account's history; the export endpoint is ORGA only. */
  protected readonly csvExport = computed(() => {
    const id = this.userId();
    return id && this.session.hasRole(Role.Orga)
      ? this.resources.subjectCsv(HistorySubjectType.User, id)
      : undefined;
  });
}
