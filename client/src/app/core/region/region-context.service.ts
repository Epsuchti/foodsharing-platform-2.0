import { Injectable, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { UnitMembership } from '../../api';
import { homeRegion, memberRegions } from '../auth/permission.util';
import { SessionService } from '../auth/session.service';
import { DEFAULT_TIME_ZONE } from '../dates/time-zone';
import { readStorage, writeStorage } from '../storage';

export const REGION_STORAGE_KEY = 'fs.region';

/** Deepest value of a route param along the active route. */
function paramOf(root: ActivatedRouteSnapshot | null, name: string): string | null {
  let value: string | null = null;
  for (let route = root; route; route = route.firstChild) {
    value = route.paramMap.get(name) ?? value;
  }
  return value;
}

/**
 * The region the user is "in" (conventions A15): the `:regionId` of the current route, else the region chosen in
 * the header switcher (remembered per device), else the home region. Pages that know a region's time zone register it
 * (`registerTimeZone`), so `fsDate` shows times in the region's zone (default Europe/Zurich).
 */
@Injectable({ providedIn: 'root' })
export class RegionContextService {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  private readonly chosen = signal<string | null>(readStorage(REGION_STORAGE_KEY));
  private readonly timeZones = signal<ReadonlyMap<string, string>>(new Map());

  /** `:regionId` of the active route (null outside region pages). */
  readonly routeRegionId = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      startWith(null),
      map(() => paramOf(this.router.routerState.snapshot.root, 'regionId')),
    ),
    { initialValue: null },
  );

  /** Regions (not groups) of the user, home region first; the choices of the header switcher. */
  readonly regions = computed<UnitMembership[]>(() => memberRegions(this.session.session()));

  readonly activeRegionId = computed<string | null>(() => {
    const fromRoute = this.routeRegionId();
    if (fromRoute) {
      return fromRoute;
    }
    const chosen = this.chosen();
    if (chosen && this.regions().some((region) => region.id === chosen)) {
      return chosen;
    }
    return homeRegion(this.session.session())?.id ?? null;
  });

  /** Membership entry of the active region (null when the user is not a member, e.g. a public region page). */
  readonly activeRegion = computed<UnitMembership | null>(
    () => this.regions().find((region) => region.id === this.activeRegionId()) ?? null,
  );

  readonly timeZone = computed(() => {
    const id = this.activeRegionId();
    return (id && this.timeZones().get(id)) || DEFAULT_TIME_ZONE;
  });

  /** Header region switcher: remembered on this device; route regions still win while on a region page. */
  choose(regionId: string | null): void {
    this.chosen.set(regionId);
    writeStorage(REGION_STORAGE_KEY, regionId);
  }

  /** Called by pages that loaded a region (RegionDetail.timeZone). */
  registerTimeZone(regionId: string, timeZone: string | null | undefined): void {
    if (!timeZone || this.timeZones().get(regionId) === timeZone) {
      return;
    }
    const next = new Map(this.timeZones());
    next.set(regionId, timeZone);
    this.timeZones.set(next);
  }
}
