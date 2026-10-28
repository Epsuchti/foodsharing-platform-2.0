import { Injectable, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

import { StoreDetail, StorePermissions, StoresService } from '../../api';
import { loadResource } from '../../core/data/data-state';
import { RegionContextService } from '../../core/region/region-context.service';

/**
 * State of one `/stores/:storeId` page, provided by StorePageComponent and injected by its tabs: the store
 * (`getStore`, including the viewer's StorePermissions, A19) and `reload()` after a change.
 */
@Injectable()
export class StorePageContext {
  private readonly api = inject(StoresService);
  private readonly regionContext = inject(RegionContextService);

  readonly storeId = toSignal(inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('storeId'))), {
    initialValue: null,
  });

  readonly store = loadResource<StoreDetail, string>({
    params: () => this.storeId() ?? undefined,
    loader: (storeId) => this.api.getStore({ storeId }),
  });

  readonly data = computed(() => this.store.data());
  readonly permissions = computed<StorePermissions | undefined>(() => this.data()?.permissions);
  /** Store time zone (pickups are shown in the store region's zone, A20). */
  readonly timeZone = computed(() => this.data()?.timeZone ?? 'Europe/Zurich');

  constructor() {
    effect(() => {
      const store = this.data();
      if (store) {
        this.regionContext.registerTimeZone(store.region.id, store.timeZone);
      }
    });
  }

  may(permission: keyof Omit<StorePermissions, 'teamStatus'>): boolean {
    return !!this.permissions()?.[permission];
  }

  reload(): void {
    this.store.reload();
  }
}
