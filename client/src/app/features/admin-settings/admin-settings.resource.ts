import { Injectable, inject } from '@angular/core';

import { HistoryService, HistorySubjectType } from '../../api';
import { HistorySource } from '../../shared/history-timeline/history-timeline.types';
import { SYSTEM_SUBJECT_ID } from './admin-settings.util';

/** Timeline sources of the admin settings (wrapped in loadResource by `fs-history-timeline`). */
@Injectable({ providedIn: 'root' })
export class AdminSettingsResources {
  private readonly history = inject(HistoryService);

  /** SYSTEM history restricted to configuration changes (C14; the drawer has no action filter). */
  readonly configurationHistory: HistorySource = (filter, page) =>
    this.history.listHistory({
      subjectType: HistorySubjectType.System,
      subjectId: SYSTEM_SUBJECT_ID,
      actions: ['ADMIN_CONFIGURATION_CHANGED'],
      from: filter.from,
      to: filter.to,
      ...page,
    });
}
