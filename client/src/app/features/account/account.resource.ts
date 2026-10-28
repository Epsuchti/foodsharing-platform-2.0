import { Injectable, inject } from '@angular/core';

import { AuthService } from '../../api';
import { HistorySource } from '../../shared/history-timeline/history-timeline.types';

/** Timeline sources of the account feature (wrapped in loadResource by `fs-history-timeline`). */
@Injectable({ providedIn: 'root' })
export class AccountResources {
  private readonly authApi = inject(AuthService);

  /** Login & security log (A22, plan §10.2): pages only, the endpoint has no filters. */
  readonly securityLog: HistorySource = (_filter, { page, size }) =>
    this.authApi.getSecurityLog({ page, size });
}
