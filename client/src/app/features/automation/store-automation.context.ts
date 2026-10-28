import { Injectable, computed, inject } from '@angular/core';

import { AutomationService, StoreAutomation } from '../../api';
import { loadResource } from '../../core/data/data-state';
import { StorePageContext } from '../stores/store-page.context';

/**
 * State of the store automation tab (`/stores/:storeId/automation/**`, M4 F3/F4): `getStoreAutomation` (policies,
 * adverts, sign-out alert, extra slot, permissions). Provided by StoreAutomationTab, injected by its sections.
 */
@Injectable()
export class StoreAutomationContext {
  private readonly api = inject(AutomationService);
  readonly store = inject(StorePageContext);

  readonly storeId = computed(() => this.store.storeId() ?? undefined);
  readonly regionId = computed(() => this.store.data()?.region.id);
  readonly timeZone = this.store.timeZone;

  readonly automation = loadResource<StoreAutomation, string>({
    params: () => this.storeId(),
    loader: (storeId) => this.api.getStoreAutomation({ storeId }),
    isEmpty: () => false,
  });

  readonly data = computed(() => this.automation.data());
  readonly mayEdit = computed(() => !!this.data()?.permissions.mayEdit);

  reload(): void {
    this.automation.reload();
  }
}
