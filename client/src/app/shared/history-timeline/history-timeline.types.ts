import { Observable } from 'rxjs';

import { HistoryActionInfo, HistoryEntryPage } from '../../api';

/**
 * The filter the timeline hands to its source. Dates are ISO date-times (start of the first / end of the last
 * selected day in the browser's time zone); `actions` are action codes (`LOGIN`, `FIELD_CHANGED`, …).
 */
export interface HistoryFilterValue {
  actions: string[];
  from?: string;
  to?: string;
  actorId?: string;
}

export interface HistoryPageRequest {
  /** 0-based. */
  page: number;
  size: number;
}

/**
 * Loads one page of history for a filter. Wraps a generated `HistoryService` call, e.g.
 * `(filter, page) => historyApi.listMyActivity({ ...filter, ...page })`.
 */
export type HistorySource = (
  filter: HistoryFilterValue,
  page: HistoryPageRequest,
) => Observable<HistoryEntryPage>;

/** Loads the CSV for the current filter (generated `exportHistoryCsv`); the timeline saves it as a file. */
export type HistoryCsvExport = (filter: HistoryFilterValue) => Observable<string>;

/** Loads the actions offered by the action filter (default: `listHistoryActions` for `subjectType`). */
export type HistoryActionsSource = () => Observable<HistoryActionInfo[]>;

/** An option of the actor filter (e.g. the members of a store team). */
export interface HistoryActorOption {
  id: string;
  name: string;
}

/** Which filters the bar shows. `actor` lists the people to choose from (filter hidden when empty). */
export interface HistoryTimelineFilters {
  dateRange?: boolean;
  actions?: boolean;
  actor?: readonly HistoryActorOption[];
}

/** `auto`: p-timeline from 48rem width, stacked cards below. */
export type HistoryTimelineLayout = 'auto' | 'timeline' | 'cards';

export type HistoryTone = 'primary' | 'info' | 'neutral' | 'warning' | 'danger';

export interface HistoryVisual {
  icon: string;
  tone: HistoryTone;
}
