import { Injectable, inject } from '@angular/core';

import { StoresService } from '../../api';
import { HistoryFilterPreset, HistorySource } from '../../shared/history-timeline/history-timeline.types';

/** Action groups of the store log filter (S12): team, pickups, wall, settings (codes of `StoreLogAction`). */
export const STORE_LOG_PRESETS: readonly HistoryFilterPreset[] = [
  {
    id: 'team',
    labelKey: 'stores.log.group.team',
    actions: [
      'REQUEST_TO_JOIN',
      'REQUEST_DECLINED',
      'REQUEST_APPROVED',
      'REQUEST_CANCELLED',
      'ADDED_WITHOUT_REQUEST',
      'MOVED_TO_JUMPER',
      'MOVED_TO_TEAM',
      'REMOVED_FROM_STORE',
      'LEFT_STORE',
      'APPOINT_STORE_MANAGER',
      'REMOVED_AS_STORE_MANAGER',
      'INVITED_TO_TEAM',
      'INVITATION_WITHDRAWN',
      'INVITATION_ACCEPTED',
      'INVITATION_DECLINED',
    ],
  },
  {
    id: 'pickups',
    labelKey: 'stores.log.group.pickups',
    actions: [
      'SIGN_UP_SLOT',
      'SIGN_OUT_SLOT',
      'REMOVED_FROM_SLOT',
      'SLOT_CONFIRMED',
      'UPDATE_SLOT_COUNT',
      'UPDATE_SLOT_DESCRIPTION',
      'UPDATE_REGULAR_PICKUP_SETTINGS',
      'UPDATE_REGULAR_PICKUP_INTERVAL',
      'REGION_PICKUP_RULE_ENABLED',
      'REGION_PICKUP_RULE_DISABLED',
      'ALL_FUTURE_PICKUPS_DELETED',
    ],
  },
  { id: 'wall', labelKey: 'stores.log.group.wall', actions: ['WALL_POST_ADDED', 'DELETED_FROM_WALL'] },
  {
    id: 'settings',
    labelKey: 'stores.log.group.settings',
    actions: [
      'STORE_CREATED',
      'STORE_UPDATED',
      'FIELD_CHANGED',
      'MOVED_TO_REGION',
      'TEAM_STATUS_CHANGED',
      'COOPERATION_STATUS_CHANGED',
      'DELETE_STORE',
    ],
  },
];

/** Timeline sources of the stores feature (wrapped in loadResource by `fs-history-timeline`). */
@Injectable({ providedIn: 'root' })
export class StoreResources {
  private readonly api = inject(StoresService);

  /** Store log (S12, A21): team, slot, wall and settings actions of one store. */
  storeLog(storeId: string): HistorySource {
    return (filter, page) =>
      this.api.getStoreLog({
        storeId,
        actions: filter.actions.length ? filter.actions : undefined,
        from: filter.from,
        to: filter.to,
        ...page,
      });
  }
}
