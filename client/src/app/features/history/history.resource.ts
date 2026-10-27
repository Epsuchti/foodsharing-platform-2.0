import { Injectable, inject } from '@angular/core';

import { HistoryService, HistorySubjectType } from '../../api';
import {
  HistoryCsvExport,
  HistorySource,
} from '../../shared/history-timeline/history-timeline.types';

/**
 * Sources for `fs-history-timeline` built from the generated HistoryService (the timeline wraps them in
 * loadResource / runMutation). Later history views (store log, region admin log, …) add their sources here
 * or in their own `*.resource.ts`.
 */
@Injectable({ providedIn: 'root' })
export class HistoryResources {
  private readonly api = inject(HistoryService);

  /** "My activity": entries the current user performed or that concern them. */
  readonly myActivity: HistorySource = (filter, { page, size }) =>
    this.api.listMyActivity({
      actions: filter.actions.length > 0 ? filter.actions : undefined,
      from: filter.from,
      to: filter.to,
      page,
      size,
    });

  /** History of one subject (access decided by the subject type's rule on the server). */
  subject(subjectType: HistorySubjectType, subjectId: string): HistorySource {
    return (filter, { page, size }) =>
      this.api.listHistory({
        subjectType,
        subjectId,
        actorId: filter.actorId,
        actions: filter.actions.length > 0 ? filter.actions : undefined,
        from: filter.from,
        to: filter.to,
        page,
        size,
      });
  }

  /** CSV of one subject's history (ORGA only). Accept text/csv so the body is read as text, not JSON. */
  subjectCsv(subjectType: HistorySubjectType, subjectId: string): HistoryCsvExport {
    return (filter) =>
      this.api.exportHistoryCsv(
        {
          subjectType,
          subjectId,
          actorId: filter.actorId,
          actions: filter.actions.length > 0 ? filter.actions : undefined,
          from: filter.from,
          to: filter.to,
        },
        'body',
        false,
        { httpHeaderAccept: 'text/csv' },
      );
  }
}
