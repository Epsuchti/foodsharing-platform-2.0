import { Injectable, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable, catchError, filter, forkJoin, map, of, take } from 'rxjs';

import { MapMarker, PublicMapService } from '../../api';
import { loadResource } from '../../core/data/data-state';
import type { MapFocus } from '../../shared/map-view/map-view.component';

/** Marker lists of the landing page; `null` when that list could not be loaded. */
export interface LandingMarkerSet {
  readonly regions: readonly MapMarker[] | null;
  readonly fsps: readonly MapMarker[] | null;
}

/**
 * The public region and Fairteiler markers for `/` (C17, decisions 160 and 176), loaded once per page: the landing
 * map draws them as pins, the postcode search picks the nearest Fairteiler from them. Each list fails on its own
 * (`catchError → null`), so the resource itself never errors — by design, a missing list only drops its pins and
 * the counts. `focus` is where the postcode search sends the map (the found region's pin at zoom 11). Provided by
 * the landing page (`providers: [LandingMarkersResource]`), so both children share one instance.
 */
@Injectable()
export class LandingMarkersResource {
  private readonly api = inject(PublicMapService);

  /** Set by the postcode search when its region has a pin; the landing map moves there. */
  readonly focus = signal<MapFocus | null>(null);

  readonly state = loadResource<LandingMarkerSet>({
    loader: () =>
      forkJoin({
        regions: this.api.listPublicRegionMarkers().pipe(catchError(() => of(null))),
        fsps: this.api.listPublicFspMarkers().pipe(catchError(() => of(null))),
      }),
    isEmpty: () => false,
    autoRetryWhenOnline: false,
  });

  private readonly settled$ = toObservable(this.state.status).pipe(
    filter((status) => status !== 'loading' && status !== 'idle' && status !== 'refreshing'),
    take(1),
  );

  /** The Fairteiler markers once loaded; `[]` when they failed. */
  fsps(): Observable<readonly MapMarker[]> {
    const loaded = this.state.data();
    if (loaded) {
      return of(loaded.fsps ?? []);
    }
    return this.settled$.pipe(map(() => this.state.data()?.fsps ?? []));
  }
}
