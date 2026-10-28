import { Injectable, inject } from '@angular/core';

import { AuditKind, AutomationService } from '../../api';
import { AuditLoader } from './components/audit-table.component';

/** Audit loaders for `fs-automation-audit-table` (AU8), fed into its loadResource. */
@Injectable({ providedIn: 'root' })
export class AutomationAuditLoaders {
  private readonly api = inject(AutomationService);

  store(storeId: () => string | undefined, kind?: AuditKind): AuditLoader {
    return (query) => this.api.listStoreAutomationAudits({ storeId: storeId()!, ...query, ...(kind ? { kind } : {}) });
  }

  region(regionId: () => string | undefined, onlyMine = false): AuditLoader {
    return (query) => this.api.listRegionAutomationAudits({ regionId: regionId()!, onlyMine, ...query });
  }
}
