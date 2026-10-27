import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';

import { HistoryActorType, HistoryEntry } from '../../api';
import { DateTimePipe } from '../../core/dates/date-time.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';
import { UserChipComponent } from '../user-chip/user-chip.component';
import { HistoryIconComponent } from './history-icon.component';
import { HistoryLabeler, historyVisual } from './history-labels';

/**
 * One history entry: label, actor, change (field: old → new), reason, short facts and an expandable details
 * list (all values, metadata, ids, trace id). Used inside `fs-history-timeline` for both layouts.
 */
@Component({
  selector: 'fs-history-entry',
  imports: [DateTimePipe, HistoryIconComponent, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-history-entry',
    '[class.fs-history-entry--compact]': 'compact()',
    '[class.fs-history-entry--card]': 'showMarker()',
    '[attr.data-action]': 'entry().subjectType + "." + entry().action',
    '[attr.data-tone]': 'visual().tone',
    '[attr.data-occurred-at]': 'entry().occurredAt',
  },
  template: `
    <div class="he__head">
      @if (showMarker()) {
        <fs-history-icon [action]="entry().action" />
      }
      <div class="he__title">
        <p class="he__label" [attr.data-testid]="testId() + '.label'">{{ label() }}</p>
        <p class="he__meta">
          @if (showTime()) {
            <time [attr.datetime]="entry().occurredAt" class="he__time">
              {{ entry().occurredAt | fsDate: (showDate() ? 'dateTime' : 'time') }}
            </time>
          }
          @if (isOwn()) {
            <span class="he__by">{{ 'shared.historyTimeline.byYou' | t }}</span>
          } @else if (actorKind() === 'SYSTEM') {
            <span class="he__by he__by--system">
              <i class="pi pi-server" aria-hidden="true"></i>
              {{ 'shared.historyTimeline.actor.system' | t }}
            </span>
          } @else if (actorKind() === 'AUTOMATION') {
            <span class="he__by he__by--system">
              <i class="pi pi-bolt" aria-hidden="true"></i>
              {{
                'shared.historyTimeline.actor.automation'
                  | t: { name: entry().actor?.displayName ?? '' }
              }}
            </span>
          }
        </p>
      </div>
    </div>

    @if (!isOwn() && actorKind() === 'USER' && entry().actor; as actor) {
      <fs-user-chip
        class="he__actor"
        [name]="actor.displayName"
        [userId]="actor.id"
        [avatarUrl]="actor.avatarUrl"
        size="xs"
        [attr.data-testid]="testId() + '.actor'"
      />
    }

    @if (change(); as change) {
      <p class="he__change" [attr.data-testid]="testId() + '.change'">
        <span class="he__field">{{ change.field }}</span>
        <span class="he__values">
          <span class="he__old">{{ change.oldValue }}</span>
          <i class="pi pi-arrow-right" aria-hidden="true"></i>
          <span class="fs-visually-hidden">{{ 'shared.historyTimeline.changedTo' | t }}</span>
          <span class="he__new">{{ change.newValue }}</span>
        </span>
      </p>
    }

    @if (entry().reason && !compact()) {
      <blockquote class="he__reason">{{ entry().reason }}</blockquote>
    }

    @if (facts().length > 0) {
      <ul class="he__facts">
        @for (fact of facts(); track fact.key) {
          <li [attr.data-fact]="fact.key">
            <span class="he__fact-label">{{ fact.label }}</span>
            <span class="he__fact-value">{{ fact.value }}</span>
          </li>
        }
      </ul>
    }

    <button
      type="button"
      class="he__toggle"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="detailsId"
      (click)="open.set(!open())"
      [attr.data-testid]="testId() + '.details'"
    >
      <i class="pi" [class.pi-chevron-down]="!open()" [class.pi-chevron-up]="open()" aria-hidden="true"></i>
      {{ (open() ? 'shared.historyTimeline.hideDetails' : 'shared.historyTimeline.showDetails') | t }}
    </button>

    @if (open()) {
      <dl class="he__details" [id]="detailsId" [attr.data-testid]="testId() + '.detailsPanel'">
        <div>
          <dt>{{ 'shared.historyTimeline.detail.when' | t }}</dt>
          <dd>{{ entry().occurredAt | fsDate: 'dateTimeLong' }}</dd>
        </div>
        <div>
          <dt>{{ 'shared.historyTimeline.detail.action' | t }}</dt>
          <dd><code>{{ entry().subjectType }}.{{ entry().action }}</code></dd>
        </div>
        @if (entry().field) {
          <div>
            <dt>{{ 'shared.historyTimeline.detail.field' | t }}</dt>
            <dd>{{ change()?.field }} <code>{{ entry().field }}</code></dd>
          </div>
          <div>
            <dt>{{ 'shared.historyTimeline.detail.oldValue' | t }}</dt>
            <dd>{{ change()?.oldValue }}</dd>
          </div>
          <div>
            <dt>{{ 'shared.historyTimeline.detail.newValue' | t }}</dt>
            <dd>{{ change()?.newValue }}</dd>
          </div>
        }
        @if (entry().reason) {
          <div>
            <dt>{{ 'shared.historyTimeline.detail.reason' | t }}</dt>
            <dd>{{ entry().reason }}</dd>
          </div>
        }
        @for (item of metadata(); track item.key) {
          <div>
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}</dd>
          </div>
        }
        <div>
          <dt>{{ 'shared.historyTimeline.detail.actor' | t }}</dt>
          <dd>{{ actorText() }}</dd>
        </div>
        @if (entry().traceId) {
          <div>
            <dt>{{ 'shared.historyTimeline.detail.traceId' | t }}</dt>
            <dd><code data-screenshot-mask>{{ entry().traceId }}</code></dd>
          </div>
        }
      </dl>
    }
  `,
  styleUrl: './history-entry.component.scss',
})
export class HistoryEntryComponent {
  private readonly labeler = inject(HistoryLabeler);
  private readonly i18n = inject(I18nService);
  private static nextId = 0;

  readonly entry = input.required<HistoryEntry>();
  readonly compact = input(false);
  /** Own icon circle (stacked-card layout); the p-timeline layout draws the icon as its marker. */
  readonly showMarker = input(false);
  /** Time in the entry head (the timeline layout shows it in the opposite column). */
  readonly showTime = input(true);
  /** Date + time instead of time only (when entries are not grouped by day). */
  readonly showDate = input(false);
  /** The viewing user: their own actions say "by you" instead of showing their chip. */
  readonly viewerId = input<string | null | undefined>(undefined);
  readonly testId = input('shared.historyTimeline.entry');

  protected readonly detailsId = `fs-history-details-${HistoryEntryComponent.nextId++}`;
  protected readonly open = signal(false);

  protected readonly visual = computed(() => historyVisual(this.entry().action));
  protected readonly label = computed(() => {
    this.i18n.language();
    return this.labeler.label(this.entry());
  });
  protected readonly change = computed(() => {
    this.i18n.language();
    return this.labeler.change(this.entry());
  });
  protected readonly facts = computed(() => {
    this.i18n.language();
    return this.compact() ? [] : this.labeler.facts(this.entry());
  });
  protected readonly metadata = computed(() => {
    this.i18n.language();
    return this.labeler.metadata(this.entry());
  });
  protected readonly actorKind = computed<HistoryActorType>(
    () => this.entry().actor?.type ?? this.entry().actorType,
  );
  protected readonly isOwn = computed(() => {
    const viewer = this.viewerId();
    return !!viewer && this.actorKind() === 'USER' && this.entry().actor?.id === viewer;
  });
  protected readonly actorText = computed(() => {
    this.i18n.language();
    const entry = this.entry();
    switch (this.actorKind()) {
      case 'SYSTEM':
        return this.i18n.t('shared.historyTimeline.actor.system');
      case 'AUTOMATION':
        return this.i18n.t('shared.historyTimeline.actor.automation', {
          name: entry.actor?.displayName ?? '',
        });
      default:
        return entry.actor?.displayName ?? this.i18n.t('shared.historyTimeline.actor.unknown');
    }
  });
}
