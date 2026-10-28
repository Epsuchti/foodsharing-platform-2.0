import { Injectable, inject } from '@angular/core';

import { RegionsService } from '../../api';
import { HistorySource } from '../../shared/history-timeline/history-timeline.types';

/** Timeline sources of the regions feature (wrapped in loadResource by `fs-history-timeline`). */
@Injectable({ providedIn: 'root' })
export class RegionResources {
  private readonly regionsApi = inject(RegionsService);

  /** History of a region (R7): membership, options, pin, settings, functions. */
  regionHistory(regionId: string): HistorySource {
    return (filter, page) =>
      this.regionsApi.getRegionHistory({
        regionId,
        actions: filter.actions.length ? filter.actions : undefined,
        from: filter.from,
        to: filter.to,
        ...page,
      });
  }
}
