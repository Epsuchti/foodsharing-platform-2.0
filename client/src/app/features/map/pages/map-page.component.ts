import { ChangeDetectionStrategy, Component, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import {
  FeatureToggleKey,
  GeoResult,
  MapBubble,
  MapMarker as ApiMapMarker,
  MapMarkerKind,
  MapService,
  PublicMapService,
  StoreMarker,
  StoreMarkerHelp,
  StoreMarkerScope,
  StoreMarkerStatus,
  StoreCategoriesService,
  StoreCategory,
  UserMarkerActivity,
  UserMarkerMember,
  UserMarkerRole,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { DataState, loadResource } from '../../../core/data/data-state';
import { DateTimeFormatter } from '../../../core/dates/date-time.pipe';
import { FeatureToggleService } from '../../../core/feature-toggles/feature-toggle.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MapFocus, MapMarker, MapViewComponent } from '../../../shared/map-view/map-view.component';
import { MarkerFilter, MarkerFilterBarComponent } from '../../../shared/marker-filter-bar/marker-filter-bar.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import {
  BubbleText,
  ALL_LAYERS,
  DEFAULT_STORE_FILTER,
  MapLayer,
  PUBLIC_BUBBLE_KINDS,
  StoreFilterValues,
  UserFilterValues,
  adminRegions,
  bubbleKind,
  bubbleView,
  combineStates,
  readLayers,
  storeQuery,
  storeViewMarkers,
  toViewMarkers,
  toggleLayer,
  writeLayers,
} from '../map.util';

interface LayerChip {
  id: MapLayer;
  icon: string;
}

const LAYER_ICONS: Record<MapLayer, string> = {
  fsps: 'pi pi-box',
  regions: 'pi pi-map',
  baskets: 'pi pi-shopping-cart',
  stores: 'pi pi-shopping-bag',
  events: 'pi pi-calendar',
  users: 'pi pi-user',
};

interface Selected {
  kind: MapMarkerKind;
  id: string;
}

/**
 * /map (M1, public): layer toggles remembered in `localStorage['fs.map.layers']`. Visitors see food share points,
 * regions and baskets (`listPublic*Markers`); signed-in users also stores with the filter bar
 * (`listStoreMarkers(status, help, scope, categoryId)`), events and — with MAP_USERS on, for administrators of a
 * region — that region's members (`listUserMarkers`, positions rounded by the server; the client adds no
 * precision). A pin opens its bubble (`getMapBubble` / `getPublicMapBubble`); a place search (`searchMapLocation`)
 * and "near me" (browser location) move the map.
 */
@Component({
  selector: 'fs-map-page',
  imports: [...DATA_STATE_IMPORTS, Button, MapViewComponent, MarkerFilterBarComponent, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'map.page.title' | t" [subtitle]="'map.page.subtitle' | t" icon="pi pi-map" />

    <section class="fs-card controls" data-testid="map.page.controls">
      <div class="controls__row">
        @if (loggedIn()) {
          <form class="search" role="search" (submit)="$event.preventDefault(); search()">
            <label class="fs-visually-hidden" for="map-search">{{ 'map.search.label' | t }}</label>
            <input
              id="map-search"
              class="search__input"
              type="search"
              minlength="2"
              maxlength="200"
              [placeholder]="'map.search.placeholder' | t"
              [value]="query()"
              (input)="query.set($any($event.target).value)"
              data-testid="map.page.search"
            />
            <p-button
              type="submit"
              class="phone-icon-only"
              icon="pi pi-search"
              [label]="'map.search.submit' | t"
              [ariaLabel]="'map.search.submit' | t"
              [disabled]="query().trim().length < 2"
              data-testid="map.page.searchSubmit"
            />
          </form>
        }
        <p-button
          [class.phone-icon-only]="loggedIn()"
          [ariaLabel]="(locating() ? 'map.page.locating' : 'map.page.nearMe') | t"
          [label]="(locating() ? 'map.page.locating' : 'map.page.nearMe') | t"
          icon="pi pi-compass"
          severity="secondary"
          [outlined]="true"
          [loading]="locating()"
          (onClick)="locate()"
          data-testid="map.page.nearMe"
        />
      </div>
      @if (locateError()) {
        <p class="error" role="alert" data-testid="map.page.locateError">{{ 'map.page.locateDenied' | t }}</p>
      }
      @if (searchTerm()) {
        <fs-data-state
          [state]="hits"
          skeleton="list"
          [skeletonRows]="1"
          [compact]="true"
          [emptyTitle]="'map.search.empty' | t: { query: searchTerm() }"
          emptyIcon="pi pi-search"
          data-testid="map.page.searchState"
        >
          <ng-template [fsData]="hits" let-results>
            <ul class="hits" [attr.aria-label]="'map.search.results' | t" data-testid="map.page.searchResults">
              @for (hit of results; track $index) {
                <li>
                  <button type="button" class="hit" [class.hit--on]="focusLabel() === hit.label" (click)="goTo(hit)" data-testid="map.page.searchResult">
                    <i class="pi pi-map-marker" aria-hidden="true"></i>{{ hit.label }}
                  </button>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      }

      <div class="layers" role="group" [attr.aria-label]="'map.layers.label' | t" data-testid="map.page.layers">
        @for (layer of availableLayers(); track layer.id) {
          <button
            type="button"
            class="layer"
            [class.layer--on]="layers().includes(layer.id)"
            [attr.aria-pressed]="layers().includes(layer.id)"
            (click)="toggle(layer.id)"
            [attr.data-testid]="'map.page.layer.' + layer.id"
          >
            <i [class]="layer.icon" aria-hidden="true"></i>{{ 'map.layers.' + layer.id | t }}
          </button>
        }
      </div>
      @if (!loggedIn()) {
        <p class="muted" data-testid="map.page.loginHint">
          <i class="pi pi-lock" aria-hidden="true"></i> {{ 'map.page.loginHint' | t }}
          <a routerLink="/login" [queryParams]="{ returnUrl: '/map' }" data-testid="map.page.login">{{ 'map.page.login' | t }}</a>
        </p>
      }
      @if (showLayer('stores')) {
        <fs-marker-filter-bar
          [filters]="storeFilters()"
          [values]="$any(storeFilter())"
          (valuesChange)="storeFilter.set($any($event))"
          [label]="'map.stores.filterLabel' | t"
          [collapseOnPhone]="true"
          [defaults]="$any(defaultStoreFilter)"
          testId="map.page.storeFilter"
        />
      }
      @if (showLayer('users')) {
        <fs-marker-filter-bar
          [filters]="userFilters()"
          [values]="$any(userFilter())"
          (valuesChange)="userFilter.set($any($event))"
          [label]="'map.users.filterLabel' | t"
          [collapseOnPhone]="true"
          [defaults]="defaultUserFilter"
          testId="map.page.userFilter"
        />
      }
    </section>

    <div class="stage" [class.stage--bubble]="selected()">
      <fs-map-view
        [markers]="markers()"
        [state]="layerState"
        [fitMarkers]="false"
        [focus]="focus()"
        [zoom]="8"
        height="min(38rem, 70vh)"
        [ariaLabel]="'map.page.mapLabel' | t"
        testId="map.page.map"
        (markerClick)="open($event)"
      />
      @if (selected(); as current) {
        <aside class="fs-card bubble" [attr.aria-label]="'map.bubble.label' | t" [attr.data-kind]="current.kind" data-testid="map.bubble">
          <button type="button" class="bubble__close" [attr.aria-label]="'map.bubble.close' | t" (click)="selected.set(null)" data-testid="map.bubble.close">
            <i class="pi pi-times" aria-hidden="true"></i>
          </button>
          <fs-data-state [state]="bubble" skeleton="card" [compact]="true" data-testid="map.bubble.state">
            <ng-template [fsData]="bubble">
              @if (bubbleContent(); as content) {
                @if (content.imageUrl) {
                  <img class="bubble__image" [src]="content.imageUrl" alt="" width="320" height="180" />
                }
                <p class="bubble__kind">{{ 'map.kind.' + current.kind | t }}</p>
                <h2 class="bubble__title" data-testid="map.bubble.title">{{ content.title }}</h2>
                @if (content.subtitle) {
                  <p class="bubble__subtitle" data-testid="map.bubble.subtitle">{{ content.subtitle }}</p>
                }
                @if (content.lines.length > 0) {
                  <ul class="bubble__lines" data-testid="map.bubble.lines">
                    @for (line of content.lines; track $index) {
                      <li>{{ line }}</li>
                    }
                  </ul>
                }
                <a class="fs-btn fs-btn--sm bubble__link" [routerLink]="content.link" data-testid="map.bubble.link">
                  {{ 'map.bubble.open' | t }} <i class="pi pi-arrow-right" aria-hidden="true"></i>
                </a>
              }
            </ng-template>
          </fs-data-state>
        </aside>
      }
    </div>
  `,
  styles: `
    :host {
      display: grid;
      gap: 1rem;
    }
    .controls {
      display: grid;
      gap: 0.85rem;
      padding: 1rem;
    }
    .controls__row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.6rem;
    }
    .search {
      display: flex;
      flex: 1 1 18rem;
      gap: 0.5rem;
      max-width: 32rem;
    }
    .search__input {
      flex: 1;
      min-width: 0;
      padding: 0.55rem 0.8rem;
      border: 1px solid var(--fs-border-strong, var(--fs-border));
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
    }
    .search__input:focus-visible {
      outline: 2px solid var(--fs-primary);
      outline-offset: 1px;
    }
    .hits {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .hit,
    .layer {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      min-height: var(--fs-target-sm);
      padding: 0.35rem 0.85rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      font-size: 0.88rem;
      font-weight: 600;
      cursor: pointer;
    }
    .hit--on,
    .layer--on {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .hit:focus-visible,
    .layer:focus-visible,
    .bubble__close:focus-visible {
      outline: 2px solid var(--fs-primary);
      outline-offset: 2px;
    }
    .layers {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
    }
    /*
     * Phones (design review M7 R1 #10): search, layer chips and four store filters filled the first screen. Search and
     * "near me" share one row with icon-only buttons, the layer chips are one swipeable row and the filters fold
     * behind a toggle (fs-marker-filter-bar collapseOnPhone), so the map starts within the first screen.
     */
    @media (max-width: 47.99rem) {
      .controls {
        gap: 0.65rem;
        padding: 0.75rem;
      }
      .controls__row {
        flex-wrap: nowrap;
      }
      .search {
        flex: 1 1 auto;
        min-width: 0;
      }
      .phone-icon-only ::ng-deep .p-button-label {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0 0 0 0);
        white-space: nowrap;
      }
      .phone-icon-only ::ng-deep .p-button {
        min-width: 2.75rem;
        padding-inline: 0.7rem;
      }
      .layers {
        flex-wrap: nowrap;
        margin-inline: -0.75rem;
        padding: 0.1rem 0.75rem 0.25rem;
        overflow-x: auto;
        scrollbar-width: none;
        scroll-padding-inline: 0.75rem;
      }
      .layer {
        flex: none;
      }
    }
    @media (pointer: coarse) {
      .hit,
      .layer,
      .search__input {
        min-height: 2.75rem;
      }
      .bubble__close {
        top: 0.25rem;
        right: 0.25rem;
        width: 2.75rem;
        height: 2.75rem;
      }
    }
    .muted {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .muted a {
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
    .error {
      margin: 0;
      color: var(--fs-danger);
    }
    .stage {
      display: grid;
      gap: 1rem;
      min-width: 0;
    }
    @media (min-width: 60rem) {
      .stage--bubble {
        grid-template-columns: minmax(0, 1fr) 20rem;
        align-items: start;
      }
    }
    .bubble {
      position: relative;
      display: grid;
      gap: 0.4rem;
      padding: 1rem;
    }
    .bubble__close {
      position: absolute;
      top: 0.5rem;
      right: 0.5rem;
      display: grid;
      width: 2rem;
      height: 2rem;
      place-items: center;
      border: 0;
      border-radius: 999px;
      background: transparent;
      color: var(--fs-text-muted);
      cursor: pointer;
    }
    .bubble__image {
      width: 100%;
      height: auto;
      max-height: 11rem;
      object-fit: cover;
      border-radius: var(--fs-radius-md);
    }
    .bubble__kind {
      margin: 0;
      color: var(--fs-primary-strong);
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .bubble__title {
      margin: 0;
      padding-right: 2rem;
      font-size: 1.1rem;
      overflow-wrap: anywhere;
    }
    .bubble__subtitle {
      margin: 0;
      color: var(--fs-text-muted);
      font-weight: 600;
    }
    .bubble__lines {
      display: grid;
      gap: 0.15rem;
      margin: 0;
      padding: 0;
      list-style: none;
      font-size: 0.9rem;
    }
    .bubble__link {
      justify-self: start;
      margin-top: 0.4rem;
    }
  `,
})
export class MapPageComponent {
  private readonly session = inject(SessionService);
  private readonly mapApi = inject(MapService);
  private readonly publicApi = inject(PublicMapService);
  private readonly categoriesApi = inject(StoreCategoriesService);
  private readonly toggles = inject(FeatureToggleService);
  private readonly i18n = inject(I18nService);
  private readonly dates = inject(DateTimeFormatter);

  protected readonly loggedIn = this.session.isAuthenticated;
  /** "Not filtered" values for the phone filter toggle's count (the region of the user layer is a choice, not a filter). */
  protected readonly defaultStoreFilter = DEFAULT_STORE_FILTER;
  protected readonly defaultUserFilter: Record<string, string> = {
    role: UserMarkerRole.All,
    activity: UserMarkerActivity.All,
    member: UserMarkerMember.All,
  };
  protected readonly layers = signal<MapLayer[]>(readLayers());
  protected readonly storeFilter = signal<StoreFilterValues>({ ...DEFAULT_STORE_FILTER });
  protected readonly userFilter = signal<UserFilterValues>({
    regionId: '',
    role: UserMarkerRole.All,
    activity: UserMarkerActivity.All,
    member: UserMarkerMember.All,
  });
  protected readonly selected = signal<Selected | null>(null);
  protected readonly focus = signal<MapFocus | null>(null);
  protected readonly focusLabel = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly searchTerm = signal<string | null>(null);
  protected readonly locating = signal(false);
  protected readonly locateError = signal(false);
  private readonly ownPosition = signal<[number, number] | null>(null);

  private readonly regionsToAdminister = computed(() => adminRegions(this.session.session().units));

  /** Layers this viewer can switch on (feature toggles: FSPs, baskets, map users). */
  protected readonly availableLayers = computed<LayerChip[]>(() => {
    const ids: MapLayer[] = [
      ...(this.toggles.isEnabled(FeatureToggleKey.FoodSharePoints) ? (['fsps'] as const) : []),
      'regions',
      ...(this.toggles.isEnabled(FeatureToggleKey.Baskets) ? (['baskets'] as const) : []),
      ...(this.loggedIn() ? (['stores', 'events'] as const) : []),
      ...(this.loggedIn() && this.toggles.isEnabled(FeatureToggleKey.MapUsers) && this.regionsToAdminister().length > 0 ? (['users'] as const) : []),
    ];
    return ids.map((id) => ({ id, icon: LAYER_ICONS[id] }));
  });

  /** Per layer: switched on and available (memoised, so loaders only re-run when their own layer changes). */
  private readonly shown = Object.fromEntries(
    ALL_LAYERS.map((layer) => [layer, computed(() => this.layers().includes(layer) && this.availableLayers().some((chip) => chip.id === layer))]),
  ) as Record<MapLayer, Signal<boolean>>;

  protected showLayer(layer: MapLayer): boolean {
    return this.shown[layer]();
  }

  private readonly fsps = loadResource<ApiMapMarker[], true>({
    params: () => (this.showLayer('fsps') ? true : undefined),
    loader: () => this.publicApi.listPublicFspMarkers(),
  });
  private readonly regions = loadResource<ApiMapMarker[], true>({
    params: () => (this.showLayer('regions') ? true : undefined),
    loader: () => this.publicApi.listPublicRegionMarkers(),
  });
  private readonly baskets = loadResource<ApiMapMarker[], boolean>({
    params: () => (this.showLayer('baskets') ? this.loggedIn() : undefined),
    loader: (loggedIn) => (loggedIn ? this.mapApi.listBasketMarkers() : this.publicApi.listPublicBasketMarkers()),
  });
  private readonly stores = loadResource<StoreMarker[], StoreFilterValues>({
    params: () => (this.showLayer('stores') ? this.storeFilter() : undefined),
    loader: (filter) => this.mapApi.listStoreMarkers(storeQuery(filter)),
  });
  private readonly events = loadResource<ApiMapMarker[], true>({
    params: () => (this.showLayer('events') ? true : undefined),
    loader: () => this.mapApi.listEventMarkers({}),
  });
  private readonly users = loadResource<ApiMapMarker[], UserFilterValues>({
    params: () => {
      const filter = this.userFilter();
      return this.showLayer('users') && filter.regionId ? filter : undefined;
    },
    loader: (filter) =>
      this.mapApi.listUserMarkers({
        regionId: filter.regionId,
        ...(filter.role !== UserMarkerRole.All ? { role: filter.role } : {}),
        ...(filter.activity !== UserMarkerActivity.All ? { activity: filter.activity } : {}),
        ...(filter.member !== UserMarkerMember.All ? { member: filter.member } : {}),
      }),
  });
  /** Store categories for the filter (every signed-in user may read the category names). */
  private readonly categories = loadResource<StoreCategory[], true>({
    params: () => (this.showLayer('stores') && this.loggedIn() ? true : undefined),
    loader: () => this.categoriesApi.listStoreCategories(),
  });

  /** One status line for all visible layers. */
  protected readonly layerState = combineStates(
    computed(() => {
      const states: [MapLayer, DataState<unknown>][] = [
        ['fsps', this.fsps],
        ['regions', this.regions],
        ['baskets', this.baskets],
        ['stores', this.stores],
        ['events', this.events],
        ['users', this.users],
      ];
      return states.filter(([layer]) => this.showLayer(layer)).map(([, state]) => state);
    }),
  );

  protected readonly markers = computed<MapMarker[]>(() => {
    this.i18n.language();
    const label = (kind: MapMarkerKind) => this.i18n.t(`map.kind.${kind}`);
    const shown = (layer: MapLayer, state: DataState<ApiMapMarker[]>) => (this.showLayer(layer) ? toViewMarkers(state.data() ?? [], label) : []);
    const own = this.ownPosition();
    return [
      ...shown('regions', this.regions),
      ...shown('fsps', this.fsps),
      ...shown('baskets', this.baskets),
      ...(this.showLayer('stores') ? storeViewMarkers(this.stores.data() ?? []) : []),
      ...shown('events', this.events),
      ...shown('users', this.users),
      ...(own ? [{ id: 'me', kind: 'home' as const, lat: own[0], lon: own[1], label: this.i18n.t('map.page.you') }] : []),
    ];
  });

  protected readonly storeFilters = computed<MarkerFilter[]>(() => {
    this.i18n.language();
    const t = (key: string) => this.i18n.t(key);
    const categories = this.categories.data() ?? [];
    return [
      { key: 'status', label: t('map.stores.status'), options: Object.values(StoreMarkerStatus).map((value) => ({ value, label: t(`map.stores.status.${value}`) })) },
      { key: 'help', label: t('map.stores.help'), options: Object.values(StoreMarkerHelp).map((value) => ({ value, label: t(`map.stores.help.${value}`) })) },
      { key: 'scope', label: t('map.stores.scope'), options: Object.values(StoreMarkerScope).map((value) => ({ value, label: t(`map.stores.scope.${value}`) })) },
      ...(categories.length > 0
        ? [{ key: 'categoryId', label: t('map.stores.category'), options: [{ value: '', label: t('map.stores.anyCategory') }, ...categories.map((category) => ({ value: category.id, label: category.name }))] }]
        : []),
    ];
  });

  protected readonly userFilters = computed<MarkerFilter[]>(() => {
    this.i18n.language();
    const t = (key: string) => this.i18n.t(key);
    return [
      { key: 'regionId', label: t('map.users.region'), options: this.regionsToAdminister().map((unit) => ({ value: unit.id, label: unit.name })) },
      { key: 'role', label: t('map.users.role'), options: Object.values(UserMarkerRole).map((value) => ({ value, label: t(`map.users.role.${value}`) })) },
      { key: 'activity', label: t('map.users.activity'), options: Object.values(UserMarkerActivity).map((value) => ({ value, label: t(`map.users.activity.${value}`) })) },
      { key: 'member', label: t('map.users.member'), options: Object.values(UserMarkerMember).map((value) => ({ value, label: t(`map.users.member.${value}`) })) },
    ];
  });

  protected readonly hits = loadResource<GeoResult[], string>({
    params: () => this.searchTerm() ?? undefined,
    loader: (q) => this.mapApi.searchMapLocation({ q }),
  });

  protected readonly bubble = loadResource<MapBubble, Selected>({
    params: () => this.selected() ?? undefined,
    loader: ({ kind, id }) =>
      this.loggedIn()
        ? this.mapApi.getMapBubble({ kind: kind.toLowerCase(), id })
        : this.publicApi.getPublicMapBubble({ kind: kind.toLowerCase(), id }),
    isEmpty: () => false,
  });

  protected readonly bubbleContent = computed(() => {
    const current = this.selected();
    const bubble = this.bubble.data();
    if (!current || !bubble) return null;
    this.i18n.language();
    const view = bubbleView(current.kind, bubble);
    const render = (piece: BubbleText): string => {
      if ('text' in piece) return piece.text;
      if ('key' in piece) {
        const params = piece.date ? { ...piece.params, date: this.dates.format(piece.date, 'dateTime') } : piece.params;
        return this.i18n.t(piece.key, params);
      }
      return this.dates.format(piece.date, 'dateTimeLong');
    };
    return { ...view, subtitle: view.subtitle ? render(view.subtitle) : null, lines: view.lines.map(render) };
  });

  constructor() {
    void this.toggles.load();
    // the first administered region preselects the user layer
    effect(() => {
      const first = this.regionsToAdminister()[0]?.id ?? '';
      untracked(() => {
        if (!this.userFilter().regionId && first) {
          this.userFilter.update((filter) => ({ ...filter, regionId: first }));
        }
      });
    });
    // a search moves the map to its first hit
    effect(() => {
      const results = this.hits.data();
      if (results && results.length > 0 && this.hits.status() === 'success') {
        untracked(() => this.goTo(results[0]));
      }
    });
  }

  protected toggle(layer: MapLayer): void {
    const next = toggleLayer(this.layers(), layer);
    this.layers.set(next);
    writeLayers(next);
  }

  protected search(): void {
    const q = this.query().trim();
    if (q.length < 2) return;
    if (this.searchTerm() === q) {
      this.hits.reload();
    } else {
      this.searchTerm.set(q);
    }
  }

  protected goTo(hit: GeoResult): void {
    this.focusLabel.set(hit.label);
    this.focus.set({ lat: hit.lat, lon: hit.lon, zoom: 13 });
  }

  protected open(marker: MapMarker): void {
    const kind = bubbleKind(marker.kind);
    if (!kind || (!this.loggedIn() && !PUBLIC_BUBBLE_KINDS.includes(kind))) return;
    this.selected.set({ kind, id: marker.id });
  }

  /** Browser geolocation (Permissions-Policy `geolocation=(self)`, A58); the position stays in this page. */
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
        const position: [number, number] = [result.coords.latitude, result.coords.longitude];
        this.ownPosition.set(position);
        this.focusLabel.set(null);
        this.focus.set({ lat: position[0], lon: position[1], zoom: 13 });
      },
      () => {
        this.locating.set(false);
        this.locateError.set(true);
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 600_000 },
    );
  }
}
