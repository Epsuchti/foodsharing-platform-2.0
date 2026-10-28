import { Injectable, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

import { RegionDetail, RegionsService } from '../../api';
import { hasPermission, isOrga } from '../../core/auth/permission.util';
import { SessionService } from '../../core/auth/session.service';
import { loadResource } from '../../core/data/data-state';
import { RegionContextService } from '../../core/region/region-context.service';

/**
 * State of one `/regions/:regionId` page, provided by RegionPageComponent and injected by its tabs: the region
 * (`getRegion`), who may do what in it, and `reload()` after a change (join/leave, settings).
 */
@Injectable()
export class RegionPageContext {
  private readonly api = inject(RegionsService);
  private readonly session = inject(SessionService);
  private readonly regionContext = inject(RegionContextService);

  readonly regionId = toSignal(inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('regionId'))), {
    initialValue: null,
  });

  readonly region = loadResource<RegionDetail, string>({
    params: () => this.regionId() ?? undefined,
    loader: (regionId) => this.api.getRegion({ regionId }),
  });

  readonly data = computed(() => this.region.data());
  readonly isMember = computed(() => !!this.data()?.membership.member);
  /** Ambassador of this region (or ORGA): members, options, settings, pin (with PR function), history. */
  readonly isAdmin = computed(() => !!this.data()?.membership.admin || isOrga(this.session.session()));
  readonly isOrga = computed(() => isOrga(this.session.session()));
  readonly manageUsers = computed(
    () => this.isAdmin() || hasPermission(this.session.session(), 'CAN_MANAGE_USERS', this.regionId()),
  );

  constructor() {
    // dates on region pages use the region's time zone (A15)
    effect(() => {
      const region = this.data();
      if (region) {
        this.regionContext.registerTimeZone(region.id, region.timeZone);
      }
    });
  }

  reload(): void {
    this.region.reload();
  }
}
