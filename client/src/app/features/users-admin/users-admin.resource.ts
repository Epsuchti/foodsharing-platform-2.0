import { Injectable, inject } from '@angular/core';

import { AdminUsersService } from '../../api';
import { HistorySource } from '../../shared/history-timeline/history-timeline.types';

/** Timeline sources of user management (wrapped in loadResource by `fs-history-timeline`). */
@Injectable({ providedIn: 'root' })
export class UsersAdminResources {
  private readonly api = inject(AdminUsersService);

  /** Change history of one user (A4: profile changes by the user and by admins, activation). */
  changeHistory(userId: string): HistorySource {
    return (filter, page) =>
      this.api.getUserChangeHistory({
        userId,
        actions: filter.actions.length ? filter.actions : undefined,
        from: filter.from,
        to: filter.to,
        ...page,
      });
  }
}
