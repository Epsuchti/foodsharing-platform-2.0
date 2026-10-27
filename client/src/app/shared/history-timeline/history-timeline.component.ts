import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { DatePicker } from '@openng/optimus-ui/datepicker';
import { MultiSelect } from '@openng/optimus-ui/multiselect';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';
import { Select } from '@openng/optimus-ui/select';
import { Skeleton } from '@openng/optimus-ui/skeleton';
import { Timeline } from '@openng/optimus-ui/timeline';
import { of } from 'rxjs';

import {
  HistoryActionInfo,
  HistoryEntry,
  HistoryEntryPage,
  HistoryService,
  HistorySubjectType,
} from '../../api';
import { loadResource } from '../../core/data/data-state';
import { runMutation } from '../../core/data/mutation';
import { DEFAULT_TIME_ZONE, DateTimeFormatter, DateTimePipe } from '../../core/dates/date-time.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../data-state';
import { HistoryEntryComponent } from './history-entry.component';
import { HistoryIconComponent } from './history-icon.component';
import { HistoryLabeler } from './history-labels';
import {
  HistoryActionsSource,
  HistoryCsvExport,
  HistoryFilterValue,
  HistorySource,
  HistoryTimelineFilters,
  HistoryTimelineLayout,
} from './history-timeline.types';

export interface HistoryDayGroup {
  /** `YYYY-MM-DD` in the display time zone. */
  key: string;
  label: string;
  entries: HistoryEntry[];
}

interface ActionOption {
  label: string;
  value: string;
}

const WIDE_QUERY = '(min-width: 48rem)';
const DAY_KEY = new Map<string, Intl.DateTimeFormat>();

/** `YYYY-MM-DD` of an instant in a time zone. */
export function dayKey(value: string | Date, timeZone = DEFAULT_TIME_ZONE): string {
  let format = DAY_KEY.get(timeZone);
  if (!format) {
    format = new Intl.DateTimeFormat('en-CA', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
    DAY_KEY.set(timeZone, format);
  }
  return format.format(value instanceof Date ? value : new Date(value));
}

/** Start of the first and end of the last selected day (browser time zone) as ISO instants. */
export function rangeToFilter(range: (Date | null)[] | null | undefined): {
  from?: string;
  to?: string;
} {
  const [start, end] = range ?? [];
  if (!start) {
    return {};
  }
  const from = new Date(start);
  from.setHours(0, 0, 0, 0);
  const to = new Date(end ?? start);
  to.setHours(23, 59, 59, 999);
  return { from: from.toISOString(), to: to.toISOString() };
}

/**
 * The shared history view (plan §10.2, every history view uses it). It owns filter + paging state and asks
 * `source` for one page at a time; all states go through `fs-data-state`.
 *
 * ```html
 * <fs-history-timeline [source]="source" [filters]="{ dateRange: true, actions: true }"
 *                      [subjectType]="'USER'" [csvExport]="isOrga() ? exportCsv : undefined"
 *                      testId="history.myActivity" />
 * ```
 *
 * Layout: `p-timeline` grouped by day from 48rem, stacked cards below (or forced via `layout`); `compact` drops
 * the filter bar and reasons/facts for cards in side panels (security log, store sidebar).
 */
@Component({
  selector: 'fs-history-timeline',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DatePicker,
    DateTimePipe,
    FormsModule,
    HistoryEntryComponent,
    HistoryIconComponent,
    MultiSelect,
    Paginator,
    Select,
    Skeleton,
    TPipe,
    Timeline,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-history-timeline',
    '[class.fs-history-timeline--compact]': 'compact()',
    '[attr.data-layout]': 'effectiveLayout()',
  },
  templateUrl: './history-timeline.component.html',
  styleUrl: './history-timeline.component.scss',
})
export class HistoryTimelineComponent {
  private readonly historyApi = inject(HistoryService);
  private readonly labeler = inject(HistoryLabeler);
  private readonly i18n = inject(I18nService);
  private readonly dates = inject(DateTimeFormatter);
  private readonly document = inject(DOCUMENT);
  private static nextId = 0;

  /** Loads a page for a filter (wrap a generated HistoryService call). */
  readonly source = input.required<HistorySource>();
  /** Which filters to show; `{}` hides the bar. */
  readonly filters = input<HistoryTimelineFilters>({ dateRange: true, actions: true });
  /** Limits the action filter to one subject type (`listHistoryActions?subjectType=`). */
  readonly subjectType = input<HistorySubjectType | undefined>(undefined);
  /** Limits the action filter to these codes (e.g. the login & security log). */
  readonly actionCodes = input<readonly string[] | undefined>(undefined);
  /** Overrides where the action filter gets its options from. */
  readonly actionsSource = input<HistoryActionsSource | undefined>(undefined);
  /** Shows the CSV export button (pass it only for roles that may export, i.e. ORGA). */
  readonly csvExport = input<HistoryCsvExport | undefined>(undefined);
  readonly exportFileName = input('history.csv');
  readonly compact = input(false);
  readonly layout = input<HistoryTimelineLayout>('auto');
  readonly pageSize = input(20);
  /** The viewing user: own actions read "by you". */
  readonly viewerId = input<string | null | undefined>(undefined);
  readonly emptyTitle = input<string | undefined>(undefined);
  readonly emptyText = input<string | undefined>(undefined);
  /** Translated "who to ask" for 403. */
  readonly askWho = input<string | undefined>(undefined);
  readonly backLink = input<string | unknown[] | undefined>(undefined);
  /** Prefix of every data-testid inside (`<prefix>.list`, `.entry`, `.filter.actions`, `.export`, …). */
  readonly testId = input('shared.historyTimeline');

  /** The applied filter (after every change; page goes back to 0). */
  readonly filterChange = output<HistoryFilterValue>();
  /** 0-based page after paging. */
  readonly pageChange = output<number>();

  protected readonly uid = `fs-history-${HistoryTimelineComponent.nextId++}`;
  protected readonly filter = signal<HistoryFilterValue>({ actions: [] });
  protected readonly page = signal(0);
  protected readonly skeletonRows = [0, 1, 2, 3];

  // filter controls
  protected readonly range = signal<Date[] | null>(null);
  protected readonly selectedActions = signal<string[]>([]);
  protected readonly selectedActor = signal<string | null>(null);

  protected readonly state = loadResource<
    HistoryEntryPage,
    { source: HistorySource; filter: HistoryFilterValue; page: number; size: number }
  >({
    params: () => ({
      source: this.source(),
      filter: this.filter(),
      page: this.page(),
      size: this.pageSize(),
    }),
    loader: ({ source, filter, page, size }) => source(filter, { page, size }),
  });

  protected readonly showFilters = computed(() => {
    const filters = this.filters();
    return (
      !this.compact() && !!(filters.dateRange || filters.actions || filters.actor?.length)
    );
  });

  protected readonly actionsState = loadResource<HistoryActionInfo[], { subjectType?: HistorySubjectType }>({
    params: () =>
      this.filters().actions && !this.compact() ? { subjectType: this.subjectType() } : undefined,
    loader: ({ subjectType }) => {
      const custom = this.actionsSource();
      if (custom) {
        return custom();
      }
      return this.historyApi.listHistoryActions(subjectType ? { subjectType } : {});
    },
  });

  protected readonly actionOptions = computed<ActionOption[]>(() => {
    this.i18n.language();
    const infos = this.actionsState.data() ?? [];
    const allowed = this.actionCodes();
    const seen = new Set<string>();
    const options: ActionOption[] = [];
    for (const info of infos) {
      if (seen.has(info.action) || (allowed && !allowed.includes(info.action))) {
        continue;
      }
      seen.add(info.action);
      options.push({ value: info.action, label: this.labeler.actionLabel(info.subjectType, info.action) });
    }
    return options.sort((a, b) => a.label.localeCompare(b.label, this.i18n.locale()));
  });

  protected readonly actorOptions = computed(() =>
    (this.filters().actor ?? []).map((actor) => ({ label: actor.name, value: actor.id })),
  );

  protected readonly activeFilterCount = computed(() => {
    const filter = this.filter();
    return (filter.from ? 1 : 0) + (filter.actions.length > 0 ? 1 : 0) + (filter.actorId ? 1 : 0);
  });

  private readonly wide = signal(true);
  protected readonly wideLayout = this.wide.asReadonly();
  protected readonly effectiveLayout = computed<'timeline' | 'cards'>(() => {
    const layout = this.layout();
    if (layout !== 'auto') {
      return layout;
    }
    return this.wide() && !this.compact() ? 'timeline' : 'cards';
  });

  protected readonly groups = computed<HistoryDayGroup[]>(() => {
    this.i18n.language();
    const items = this.state.data()?.items ?? [];
    const today = dayKey(new Date());
    const yesterday = dayKey(new Date(Date.now() - 24 * 3600 * 1000));
    const thisYear = today.slice(0, 4);
    const groups: HistoryDayGroup[] = [];
    for (const entry of items) {
      const key = dayKey(entry.occurredAt);
      let group = groups[groups.length - 1];
      if (!group || group.key !== key) {
        let label: string;
        if (key === today) {
          label = this.i18n.t('shared.historyTimeline.today');
        } else if (key === yesterday) {
          label = this.i18n.t('shared.historyTimeline.yesterday');
        } else {
          label = this.dates.format(
            entry.occurredAt,
            key.startsWith(thisYear) ? 'weekday' : 'dateLong',
          );
        }
        group = { key, label, entries: [] };
        groups.push(group);
      }
      group.entries.push(entry);
    }
    return groups;
  });

  protected readonly pageMeta = computed(() => this.state.data()?.page);

  protected readonly exporter = runMutation(
    (filter: HistoryFilterValue) => this.csvExport()?.(filter) ?? of(''),
    {
      successToastKey: 'shared.historyTimeline.exported',
      onSuccess: (csv) => this.download(csv),
    },
  );

  constructor() {
    const view = this.document.defaultView;
    const media = view?.matchMedia?.(WIDE_QUERY);
    if (media) {
      this.wide.set(media.matches);
      const listener = (event: MediaQueryListEvent) => this.wide.set(event.matches);
      media.addEventListener?.('change', listener);
      inject(DestroyRef).onDestroy(() => media.removeEventListener?.('change', listener));
    }
  }

  protected onRangeChange(range: Date[] | null): void {
    this.range.set(range);
    const [start, end] = range ?? [];
    // Wait for the second click of a range; a cleared picker (null) applies immediately.
    if (start && !end) {
      return;
    }
    this.apply({ ...this.filter(), ...{ from: undefined, to: undefined }, ...rangeToFilter(range) });
  }

  protected onRangeClose(): void {
    // A single selected day (picker closed after the first click) filters that day.
    const [start, end] = this.range() ?? [];
    if (start && !end) {
      this.apply({ ...this.filter(), ...rangeToFilter([start, null]) });
    }
  }

  protected onActionsChange(actions: string[] | null): void {
    this.selectedActions.set(actions ?? []);
    this.apply({ ...this.filter(), actions: actions ?? [] });
  }

  protected onActorChange(actorId: string | null): void {
    this.selectedActor.set(actorId);
    this.apply({ ...this.filter(), actorId: actorId ?? undefined });
  }

  protected resetFilters(): void {
    this.range.set(null);
    this.selectedActions.set([]);
    this.selectedActor.set(null);
    this.apply({ actions: [] });
  }

  protected onPage(event: PaginatorState): void {
    const page = event.page ?? 0;
    if (page !== this.page()) {
      this.page.set(page);
      this.pageChange.emit(page);
    }
  }

  private apply(next: HistoryFilterValue): void {
    const clean: HistoryFilterValue = { actions: next.actions };
    if (next.from) clean.from = next.from;
    if (next.to) clean.to = next.to;
    if (next.actorId) clean.actorId = next.actorId;
    const current = this.filter();
    if (JSON.stringify(current) === JSON.stringify(clean)) {
      return;
    }
    this.filter.set(clean);
    this.page.set(0);
    this.filterChange.emit(clean);
  }

  private download(csv: string): void {
    const view = this.document.defaultView;
    if (!view?.URL?.createObjectURL) {
      return;
    }
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = view.URL.createObjectURL(blob);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = this.exportFileName();
    link.rel = 'noopener';
    link.style.display = 'none';
    this.document.body.appendChild(link);
    link.click();
    link.remove();
    view.setTimeout(() => view.URL.revokeObjectURL(url), 1000);
  }
}
