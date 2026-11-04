import { Injectable, inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable, catchError, filter, forkJoin, map, of, take } from 'rxjs';

import { MapMarker, PublicMapService } from '../../api';
import { loadResource } from '../../core/data/data-state';

/** Marker lists of the landing page; `null` when that list could not be loaded. */
export interface LandingMarkerSet {
  readonly regions: readonly MapMarker[] | null;
  readonly fsps: readonly MapMarker[] | null;
}

/**
 * The public region and Fairteiler markers for `/` (C17, decision 160), loaded once per page: the static preview
 * draws them as pins, the postcode search picks the nearest Fairteiler from them. Each list fails on its own
 * (`catchError → null`), so the resource itself never errors and the preview never shows a DataState — by design,
 * a missing list only drops its pins. Provided by the landing page (`providers: [LandingMarkersResource]`).
 */
@Injectable()
export class LandingMarkersResource {
  private readonly api = inject(PublicMapService);

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
