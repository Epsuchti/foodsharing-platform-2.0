import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ForumService, HistorySubjectType } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { HistorySource } from '../../../shared/history-timeline/history-timeline.types';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { moderationLogSource } from '../forum.resource';

/** Forum moderation actions (A37 `ForumAction`), the timeline's action filter is applied to the loaded page. */
export const MODERATION_ACTIONS = [
  'THREAD_ACTIVATED',
  'THREAD_CLOSED',
  'THREAD_OPENED',
  'THREAD_STICKY_CHANGED',
  'THREAD_DELETED',
  'POST_HIDDEN',
  'POST_UNHIDDEN',
  'POST_DELETED',
] as const;

/** /regions/:regionId/moderation-log (G3, D10.3): moderation history of the region forums for moderators and ORGA. */
@Component({
  selector: 'fs-moderation-log-page',
  imports: [HistoryTimelineComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'forum.log.title' | t" [subtitle]="regionName()" icon="pi pi-history" [backLink]="['/regions', regionId(), 'forum']" [backLabel]="'forum.title' | t" />
    <fs-history-timeline [source]="source()" [subjectType]="subject" [actionCodes]="actions" [filters]="{ dateRange: true, actions: true }" data-testid="forum.log.timeline" />
  `,
  styles: `:host { display: grid; gap: 0.75rem; width: min(100%, 56rem); margin: 0 auto; }`,
})
export class ModerationLogPageComponent {
  private readonly api = inject(ForumService);
  private readonly session = inject(SessionService);

  readonly regionId = input.required<string>();
  protected readonly actions = MODERATION_ACTIONS;
  protected readonly subject = HistorySubjectType.Thread;
  protected readonly regionName = computed(
    () => (this.session.session().units ?? []).find((unit) => unit.id === this.regionId())?.name,
  );

  protected readonly source = computed<HistorySource>(() => moderationLogSource(this.api, this.regionId()));
}
