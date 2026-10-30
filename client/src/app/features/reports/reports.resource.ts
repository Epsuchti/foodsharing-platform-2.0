import { Injectable, inject } from '@angular/core';

import { ReportsService } from '../../api';
import { HistorySource } from '../../shared/history-timeline/history-timeline.types';
import { localDay } from './reports.util';

/** Timeline sources of the reports module (wrapped in loadResource by `fs-history-timeline`). */
@Injectable({ providedIn: 'root' })
export class ReportsResources {
  private readonly api = inject(ReportsService);

  /**
   * Report log of a region (D10.3, `getReportLog`): every REPORT history entry, report handlers only. The endpoint
   * takes calendar days; the timeline's instants are the bounds of the days picked in the browser's time zone.
   */
  reportLog(regionId: string): HistorySource {
    return (filter, page) =>
      this.api.getReportLog({ regionId, from: localDay(filter.from), to: localDay(filter.to), ...page });
  }
}
