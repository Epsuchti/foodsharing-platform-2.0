import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { BasketSummary, BasketsService, UserProfile, UsersService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MapMarker, MapViewComponent } from '../../../shared/map-view/map-view.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { BasketCardComponent } from '../components/basket-card.component';
import { DEFAULT_DISTANCE_KM, MAX_DISTANCE_KM, basketMarkers, clampDistance, profilePosition } from '../baskets.util';

interface NearbyParams {
  lat: number;
  lon: number;
  distanceKm: number;
}

/**
 * /baskets (B2): open baskets around the user's position — the profile coordinates, or the browser location when
 * the profile has none (or the user asks for it) — within a radius of 1..50 km (`listNearbyBaskets`, nearest first).
 * List and map side by side; on narrow screens a switch shows one of them. A map pin highlights its card.
 */
@Component({
  selector: 'fs-baskets-page',
  imports: [...DATA_STATE_IMPORTS, BasketCardComponent, Button, MapViewComponent, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'baskets.nearby.title' | t" [subtitle]="'baskets.nearby.subtitle' | t" icon="pi pi-shopping-cart">
      <p-button [label]="'baskets.nearby.mine' | t" icon="pi pi-list" severity="secondary" [outlined]="true" routerLink="/baskets/mine" data-testid="baskets.nearby.mine" />
      <p-button [label]="'baskets.nearby.offer' | t" icon="pi pi-plus" routerLink="/baskets/new" data-testid="baskets.nearby.offer" />
    </fs-page-header>

    <fs-data-state [state]="me" skeleton="card" data-testid="baskets.nearby.profileState">
      <ng-template [fsData]="me">
        @if (position(); as pos) {
          <div class="toolbar fs-card">
            <div class="radius">
              <label for="baskets-radius" class="radius__label">{{ 'baskets.nearby.distance' | t: { km: draftDistance() } }}</label>
              <input
                id="baskets-radius"
                type="range"
                min="1"
                [max]="maxDistance"
                step="1"
                [value]="draftDistance()"
                [attr.aria-valuetext]="'baskets.nearby.distance' | t: { km: draftDistance() }"
                (input)="draftDistance.set(clamp(+$any($event.target).value))"
                (change)="distance.set(clamp(+$any($event.target).value))"
                data-testid="baskets.nearby.distance"
              />
            </div>
            @if (devicePosition()) {
              <p class="muted" data-testid="baskets.nearby.fromDevice"><i class="pi pi-compass" aria-hidden="true"></i> {{ 'baskets.nearby.fromDevice' | t }}</p>
            }
            <div class="switch" role="group" [attr.aria-label]="'baskets.nearby.viewLabel' | t">
              <button type="button" class="switch__btn" [class.switch__btn--on]="view() === 'list'" [attr.aria-pressed]="view() === 'list'" (click)="view.set('list')" data-testid="baskets.nearby.showList">
                <i class="pi pi-list" aria-hidden="true"></i> {{ 'baskets.nearby.list' | t }}
              </button>
              <button type="button" class="switch__btn" [class.switch__btn--on]="view() === 'map'" [attr.aria-pressed]="view() === 'map'" (click)="view.set('map')" data-testid="baskets.nearby.showMap">
                <i class="pi pi-map" aria-hidden="true"></i> {{ 'baskets.nearby.map' | t }}
              </button>
            </div>
          </div>

          <div class="split" [attr.data-view]="view()">
            <div class="split__list">
              <fs-data-state
                [state]="nearby"
                skeleton="list"
                [emptyTitle]="'baskets.nearby.empty' | t: { km: distance() }"
                [emptyText]="'baskets.nearby.emptyHint' | t"
                emptyIcon="pi pi-shopping-cart"
                [emptyActionLabel]="'baskets.nearby.offer' | t"
                emptyActionIcon="pi pi-plus"
                (emptyAction)="offer()"
                data-testid="baskets.nearby.state"
              >
                <ng-template [fsData]="nearby" let-baskets>
                  <ul class="list" data-testid="baskets.nearby.list">
                    @for (basket of baskets; track basket.id) {
                      <li><fs-basket-card [basket]="basket" [highlighted]="selected() === basket.id" /></li>
                    }
                  </ul>
                </ng-template>
              </fs-data-state>
            </div>
            <div class="split__map">
              <fs-map-view
                [markers]="markers()"
                [state]="nearby"
                [center]="pos"
                height="min(32rem, 70vh)"
                [ariaLabel]="'baskets.nearby.mapLabel' | t"
                testId="baskets.nearby.map"
                (markerClick)="pick($event)"
              />
            </div>
          </div>
        } @else {
          <section class="fs-card position" data-testid="baskets.nearby.position">
            <h2 class="position__title"><i class="pi pi-map-marker" aria-hidden="true"></i> {{ 'baskets.nearby.positionTitle' | t }}</h2>
            <p class="muted">{{ 'baskets.nearby.positionText' | t }}</p>
            @if (locateError()) {
              <p class="error" role="alert" data-testid="baskets.nearby.locateError">{{ 'baskets.nearby.locateDenied' | t }}</p>
            }
            <div class="position__actions">
              <p-button
                [label]="(locating() ? 'baskets.nearby.locating' : 'baskets.nearby.locate') | t"
                icon="pi pi-compass"
                [loading]="locating()"
                (onClick)="locate()"
                data-testid="baskets.nearby.locate"
              />
              <p-button [label]="'baskets.nearby.editAddress' | t" icon="pi pi-user-edit" severity="secondary" [outlined]="true" routerLink="/account/profile" data-testid="baskets.nearby.editAddress" />
            </div>
          </section>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .error { margin: 0; color: var(--fs-danger); }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem 1.5rem;
      margin-bottom: 1rem;
      padding: 0.85rem 1rem;
    }
    .radius { display: grid; gap: 0.3rem; flex: 1 1 14rem; max-width: 24rem; }
    .radius__label { font-size: 0.9rem; font-weight: 650; }
    .radius input { width: 100%; accent-color: var(--fs-primary); }
    .switch { display: inline-flex; margin-left: auto; border: 1px solid var(--fs-border); border-radius: 999px; overflow: hidden; }
    .switch__btn {
      display: inline-flex;
      gap: 0.35rem;
      align-items: center;
      padding: 0.4rem 0.9rem;
      border: 0;
      background: transparent;
      color: var(--fs-text);
      font: inherit;
      cursor: pointer;
    }
    .switch__btn--on { background: var(--fs-primary); color: #fff; }
    .switch__btn:focus-visible { outline: 2px solid var(--fs-primary); outline-offset: -2px; }
    .split { display: grid; gap: 1rem; }
    .split[data-view='list'] .split__map, .split[data-view='map'] .split__list { display: none; }
    @media (min-width: 60rem) {
      .switch { display: none; }
      .split { grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr); align-items: start; }
      .split[data-view] .split__map, .split[data-view] .split__list { display: block; }
      .split__map { position: sticky; top: calc(var(--fs-header-h, 4rem) + 1rem); }
    }
    .list { display: grid; gap: 0.75rem; margin: 0; padding: 0; list-style: none; }
    .position { display: grid; gap: 0.75rem; max-width: 40rem; }
    .position__title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.1rem; }
    .position__title .pi { color: var(--fs-primary-strong); }
    .position__actions { display: flex; flex-wrap: wrap; gap: 0.5rem; }
  `,
})
export class BasketsPageComponent {
  private readonly api = inject(BasketsService);
  private readonly users = inject(UsersService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  protected readonly maxDistance = MAX_DISTANCE_KM;
  protected readonly clamp = clampDistance;

  /** Radius used for loading (changes on release of the slider) and the one shown while dragging. */
  protected readonly distance = signal(DEFAULT_DISTANCE_KM);
  protected readonly draftDistance = signal(DEFAULT_DISTANCE_KM);
  protected readonly view = signal<'list' | 'map'>('list');
  protected readonly selected = signal<string | null>(null);

  protected readonly devicePosition = signal<[number, number] | null>(null);
  protected readonly locating = signal(false);
  protected readonly locateError = signal(false);

  /** Own profile for the home coordinates (A20 address). */
  protected readonly me = loadResource<UserProfile, true>({
    params: () => true,
    loader: () => this.users.getCurrentUser(),
    isEmpty: () => false,
  });

  protected readonly position = computed(() => this.devicePosition() ?? profilePosition(this.me.data()));

  protected readonly nearby = loadResource<BasketSummary[], NearbyParams>({
    params: () => {
      const pos = this.position();
      return pos ? { lat: pos[0], lon: pos[1], distanceKm: this.distance() } : undefined;
    },
    loader: (params) => this.api.listNearbyBaskets(params),
  });

  protected readonly markers = computed<MapMarker[]>(() => {
    const pos = this.position();
    const pins = basketMarkers(this.nearby.data() ?? []);
    return pos ? [...pins, { id: 'me', kind: 'home', lat: pos[0], lon: pos[1], label: this.i18n.t('baskets.nearby.you') }] : pins;
  });

  protected pick(marker: MapMarker): void {
    if (marker.kind !== 'basket') return;
    this.selected.set(marker.id);
    this.view.set('list');
    queueMicrotask(() =>
      document.querySelector(`fs-basket-card[data-basket-id="${marker.id}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }),
    );
  }

  protected offer(): void {
    void this.router.navigate(['/baskets/new']);
  }

  /** Browser geolocation (Permissions-Policy allows `geolocation=(self)`, A58). */
  protected locate(): void {
    const geo = globalThis.navigator?.geolocation;
    this.locateError.set(false);
    if (!geo) {
      this.locateError.set(true);
      return;
    }
    this.locating.set(true);
    geo.getCurrentPosition(
      (result) => {
        this.locating.set(false);
        this.devicePosition.set([result.coords.latitude, result.coords.longitude]);
      },
      () => {
        this.locating.set(false);
        this.locateError.set(true);
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 600_000 },
    );
  }
}
