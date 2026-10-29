import { Injectable, inject } from '@angular/core';

import { AdminUsersService } from '../../api';
import { HistorySource } from '../../shared/history-timeline/history-timeline.types';

/** Timeline sources of the profile (wrapped in loadResource by `fs-history-timeline`). */
@Injectable({ providedIn: 'root' })
export class ProfilesResources {
  private readonly adminUsers = inject(AdminUsersService);

  /** Profile change history (A8 profile piece; `getUserChangeHistory`, administrators of the user and the user). */
  changeHistory(userId: string): HistorySource {
    return (filter, page) =>
      this.adminUsers.getUserChangeHistory({
        userId,
        actions: filter.actions.length ? filter.actions : undefined,
        from: filter.from,
        to: filter.to,
        ...page,
      });
  }
}
