import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Skeleton } from '@openng/optimus-ui/skeleton';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { PublicMapService, PublicRegionsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { isPostcode, nearestFsps } from '../landing-geo';
import { LandingMarkersResource } from '../landing-markers.resource';

/** What the result card shows for a postcode. */
export interface NearResult {
  readonly slug: string;
  readonly name: string;
  readonly storeCount: number;
  readonly memberCount: number;
  /** Names of the (up to) two nearest Fairteiler within 10 km of the region pin. */
  readonly fspNames: readonly string[];
}

/**
 * "In deiner Nähe" search (C17, decision 160): postcode → `suggestRegions` → first suggestion → `getPublicRegion`
 * (active cooperations = `storeCount`, members, pin) → the two nearest Fairteiler within 10 km of the pin, named
 * by `getPublicMapBubble`. A failing bubble only drops that name. No suggestion → empty ("gründe einen"); 503
 * GEOCODING_UNAVAILABLE and other failures → "Suche gerade nicht möglich" + retry; region gone → not found.
 * Invalid input (not 4 or 5 digits) shows a hint and sends no request.
 */
@Component({
  selector: 'fs-landing-near',
  imports: [...DATA_STATE_IMPORTS, RouterLink, Skeleton, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="search" role="search" (submit)="search($event)" novalidate>
      <label class="search__label" for="landing-near-postcode">{{
        'public.landing.near.search.label' | t
      }}</label>
      <div class="search__field">
        <input
          id="landing-near-postcode"
          name="postcode"
          inputmode="numeric"
          autocomplete="postal-code"
          maxlength="10"
          [placeholder]="'public.landing.near.search.placeholder' | t"
          [attr.aria-invalid]="invalid() || null"
          aria-describedby="landing-near-hint"
          data-testid="public.landing.near.search"
        />
        <button
          class="fs-btn"
          type="submit"
          [disabled]="near.isLoading()"
          data-testid="public.landing.near.search.submit"
        >
          <i
            class="pi"
            [class.pi-search]="!near.isLoading()"
            [class.pi-spin]="near.isLoading()"
            [class.pi-spinner]="near.isLoading()"
            aria-hidden="true"
          ></i>
          <span>{{ 'public.landing.near.search.submit' | t }}</span>
        </button>
      </div>
      <p
        class="search__hint"
        id="landing-near-hint"
        aria-live="polite"
        data-testid="public.landing.near.search.hint"
      >
        @if (invalid()) {
          <i class="pi pi-info-circle" aria-hidden="true"></i>
          {{ 'public.landing.near.search.invalid' | t }}
        }
      </p>
    </form>

    @if (near.status() === 'idle') {
      <div class="card card--idle fs-panel--brand" data-testid="public.landing.near.idle">
        <div class="card__head">
          <span class="fs-tile fs-tile--lg" aria-hidden="true"><i class="pi pi-search"></i></span>
          <p>{{ 'public.landing.near.idle' | t }}</p>
        </div>
      </div>
    }
    <fs-data-state [state]="near" [compact]="true" data-testid="public.landing.near.state">
      <ng-template fsEmpty>
        <div class="card card--idle fs-panel--brand">
          <div class="card__head">
            <span class="fs-tile fs-tile--lg" aria-hidden="true"
              ><i class="pi pi-map-marker"></i
            ></span>
            <p>{{ 'public.landing.near.search.noMatch' | t }}</p>
          </div>
        </div>
      </ng-template>
      <ng-template fsSkeleton>
        <div class="card fs-panel--brand" aria-hidden="true">
          <p-skeleton width="30%" height="0.8rem" />
          <p-skeleton width="55%" height="1.3rem" />
          <p-skeleton width="80%" height="0.9rem" />
        </div>
      </ng-template>
      <ng-template fsError let-error let-retry="retry">
        <fs-error-state
          [error]="error"
          [kind]="near.status() === 'offline' ? 'offline' : undefined"
          [retryable]="near.status() !== 'notFound'"
          [heading]="errorHeading() | t"
          [description]="errorText() | t"
          [compact]="true"
          testId="public.landing.near.state"
          (retry)="retry()"
        />
      </ng-template>
      <ng-template [fsData]="near" let-result>
        @if (result; as r) {
          <div
            class="card card--result fs-panel--brand"
            aria-live="polite"
            data-testid="public.landing.near.result"
          >
            <span class="fs-tile fs-tile--lg" aria-hidden="true"><i class="pi pi-users"></i></span>
            <div class="card__body">
              <span class="card__kind">{{ 'public.landing.near.result.kind' | t }}</span>
              <h3 class="card__title" data-testid="public.landing.near.result.name">
                {{ r.name }}
              </h3>
              <p class="card__facts">
                <span data-testid="public.landing.near.result.cooperations">{{
                  'public.landing.near.result.cooperations' | t: { count: r.storeCount }
                }}</span>
                <span data-testid="public.landing.near.result.members">{{
                  'public.landing.near.result.members' | t: { count: r.memberCount }
                }}</span>
              </p>
              <p class="card__fsp" data-testid="public.landing.near.result.fsps">
                <i class="pi pi-box" aria-hidden="true"></i>
                @if (r.fspNames.length) {
                  {{ 'public.landing.near.result.fsps' | t: { names: r.fspNames.join(', ') } }}
                } @else {
                  {{ 'public.landing.near.result.noFsp' | t }}
                }
              </p>
              <div class="card__actions">
                <a
                  class="fs-btn fs-btn--sm"
                  [routerLink]="['/r', r.slug]"
                  data-testid="public.landing.near.regionLink"
                  >{{ 'public.landing.near.result.regionLink' | t }}</a
                >
                <a
                  class="fs-btn fs-btn--sm fs-btn--outlined"
                  routerLink="/map"
                  data-testid="public.landing.near.openMap"
                >
                  <i class="pi pi-map-marker" aria-hidden="true"></i>
                  <span>{{ 'public.landing.near.openMap' | t }}</span>
                </a>
              </div>
            </div>
          </div>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: grid;
      align-content: start;
      gap: 0.5rem;
    }
    .search {
      display: grid;
      gap: 0.35rem;
    }
    .search__label {
      font-size: 0.9rem;
      font-weight: 650;
    }
    .search__field {
      display: flex;
      gap: 0.5rem;
    }
    .search__field input {
      flex: 1;
      min-width: 0;
      min-height: 2.75rem;
      padding: 0.5rem 0.9rem;
      border: 1.5px solid var(--fs-border-strong);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
    }
    .search__field input:focus {
      border-color: var(--fs-primary);
    }
    .search__field input[aria-invalid='true'] {
      border-color: var(--fs-danger);
    }
    .search__hint {
      display: flex;
      gap: 0.4rem;
      align-items: center;
      min-height: 1.3em;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    /* every card is a brand panel (decision 175): border, radius, gradient and shadow come from .fs-panel--brand */
    .card {
      display: grid;
      gap: 0.5rem;
      padding: 1.2rem 1.4rem;
    }
    .card--result {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
    }
    .card__head {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .card__body {
      display: grid;
      gap: 0.5rem;
      min-width: 0;
    }
    .card p {
      margin: 0;
    }
    .card--idle p {
      color: var(--fs-text-muted);
      font-size: 0.95rem;
    }
    .card__kind {
      color: var(--fs-text-muted);
      font-size: 0.78rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }
    .card__title {
      margin: 0;
      font-size: 1.25rem;
    }
    .card__facts {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem 1rem;
      font-size: 0.95rem;
      font-weight: 650;
    }
    .card__fsp {
      display: flex;
      gap: 0.4rem;
      align-items: baseline;
      font-size: 0.95rem;
    }
    .card__fsp .pi {
      color: var(--fs-primary);
    }
    .card__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-top: 0.3rem;
    }
  `,
})
export class LandingNearComponent {
  private readonly regionsApi = inject(PublicRegionsService);
  private readonly mapApi = inject(PublicMapService);
  private readonly markers = inject(LandingMarkersResource);

  protected readonly postcode = signal<string | undefined>(undefined);
  protected readonly invalid = signal(false);

  protected readonly near = loadResource<NearResult | null, string>({
    params: () => this.postcode(),
    loader: (postcode): Observable<NearResult | null> =>
      this.regionsApi.suggestRegions({ postcode }).pipe(
        switchMap((suggestions) => {
          const first = suggestions[0]?.region;
          if (!first) {
            return of(null);
          }
          return this.regionsApi.getPublicRegion({ slug: first.slug }).pipe(
            switchMap((region) => {
              const pin = region.pin;
              const names$: Observable<string[]> = !pin
                ? of([])
                : this.markers.fsps().pipe(
                    switchMap((fsps) => {
                      const nearest = nearestFsps({ lat: pin.latitude, lon: pin.longitude }, fsps);
                      if (!nearest.length) {
                        return of([]);
                      }
                      return forkJoin(
                        nearest.map((fsp) =>
                          this.mapApi.getPublicMapBubble({ kind: 'fsp', id: fsp.id }).pipe(
                            map((bubble): string | null => bubble.title),
                            catchError(() => of(null)),
                          ),
                        ),
                      ).pipe(map((titles) => titles.filter((t): t is string => !!t)));
                    }),
                  );
              return names$.pipe(
                map((fspNames): NearResult => ({
                  slug: region.slug,
                  name: region.name,
                  storeCount: region.storeCount,
                  memberCount: region.memberCount,
                  fspNames,
                })),
              );
            }),
          );
        }),
      ),
    isEmpty: (value) => value === null,
  });

  private readonly notFoundKey = 'public.landing.near.result.notFound';
  private readonly unavailableKey = 'public.landing.near.search.unavailable';
  private readonly unavailableTextKey = 'public.landing.near.search.unavailableText';

  /** Own heading for "region gone" and "search unavailable"; offline keeps the shared texts. */
  protected errorHeading(): string | undefined {
    const status = this.near.status();
    return status === 'notFound'
      ? this.notFoundKey
      : status === 'error'
        ? this.unavailableKey
        : undefined;
  }

  protected errorText(): string | undefined {
    return this.near.status() === 'error' ? this.unavailableTextKey : undefined;
  }

  protected search(event: Event): void {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    const value =
      (form.elements.namedItem('postcode') as HTMLInputElement | null)?.value.trim() ?? '';
    if (!isPostcode(value)) {
      this.invalid.set(true);
      this.postcode.set(undefined);
      return;
    }
    this.invalid.set(false);
    if (this.postcode() === value) {
      this.near.reload();
    } else {
      this.postcode.set(value);
    }
  }
}
