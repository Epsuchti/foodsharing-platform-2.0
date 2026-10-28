import { ChangeDetectionStrategy, Component, computed, inject, input, viewChild } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';
import { Tooltip } from '@openng/optimus-ui/tooltip';

import { HistorySubjectType, Role } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { HistoryFilterPreset } from '../../../shared/history-timeline/history-timeline.types';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { HistoryResources } from '../history.resource';

/** One-click filters of "my activity" (`?preset=<id>` selects one, e.g. from the security log). */
export const MY_ACTIVITY_PRESETS: readonly HistoryFilterPreset[] = [
  {
    id: 'logins',
    labelKey: 'history.myActivity.preset.logins',
    actions: [
      'LOGIN',
      'LOGOUT',
      'LOGIN_FAILED',
      'SESSION_REVOKED',
      'SESSIONS_REVOKED_ALL',
      'MAGIC_LINK_REQUESTED',
    ],
  },
  {
    id: 'changes',
    labelKey: 'history.myActivity.preset.changes',
    actions: [
      'SETTINGS_CHANGED',
      'FIELD_CHANGED',
      'PROFILE_UPDATED',
      'PROFILE_UPDATED_BY_ADMIN',
      'ROLE_CHANGED',
      'HOME_REGION_CHANGED',
      'PHONE_CHANGED',
    ],
  },
];

/**
 * /me/activity (D10.3 "My activity"): the user's own history with presets, date and action filters. ORGA
 * additionally gets the CSV export of their account's history (icon button in the page header). The JSON
 * export (A21, GDPR) arrives in M6.
 */
@Component({
  selector: 'fs-my-activity-page',
  imports: [Button, HistoryTimelineComponent, PageHeaderComponent, TPipe, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [eyebrow]="'history.myActivity.eyebrow' | t"
      [title]="'history.myActivity.title' | t"
      [subtitle]="'history.myActivity.subtitle' | t"
      icon="pi pi-history"
    >
      @if (csvExport()) {
        <p-button
          icon="pi pi-download"
          severity="secondary"
          [outlined]="true"
          [rounded]="true"
          [ariaLabel]="'history.myActivity.export' | t"
          [pTooltip]="'history.myActivity.export' | t"
          tooltipPosition="bottom"
          [loading]="timeline()?.exporting() ?? false"
          (onClick)="timeline()?.exportCsv()"
          data-testid="history.myActivity.export"
        />
      }
    </fs-page-header>

    <fs-history-timeline
      [source]="resources.myActivity"
      [filters]="{ dateRange: true, actions: true }"
      [subjectType]="HistorySubjectType.User"
      [presets]="presets"
      [initialPreset]="preset()"
      [csvExport]="csvExport()"
      [showExportButton]="false"
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
  protected readonly presets = MY_ACTIVITY_PRESETS;
  protected readonly timeline = viewChild(HistoryTimelineComponent);

  /** `?preset=logins` (query param through component input binding). */
  readonly preset = input<string | undefined>(undefined);

  protected readonly userId = computed(() => this.session.user()?.id ?? null);

  /** CSV export of the own account's history; the export endpoint is ORGA only. */
  protected readonly csvExport = computed(() => {
    const id = this.userId();
    return id && this.session.hasRole(Role.Orga)
      ? this.resources.subjectCsv(HistorySubjectType.User, id)
      : undefined;
  });
}
