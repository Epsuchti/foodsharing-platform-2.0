import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import type * as Leaflet from 'leaflet';

import { DataState } from '../../core/data/data-state';
import { TPipe } from '../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../data-state';

export type MapMarkerKind =
  | 'region-pin'
  | 'region'
  | 'store'
  | 'fsp'
  | 'basket'
  | 'user'
  | 'event'
  | 'pickup'
  | 'home';

/** Kinds whose click asks the host for a detail bubble (M6 map: food-share points, baskets, users, stores). */
export const BUBBLE_KINDS: ReadonlySet<MapMarkerKind> = new Set<MapMarkerKind>(['fsp', 'basket', 'user', 'store']);

export interface MapMarker {
  id: string;
  kind: MapMarkerKind;
  lat: number;
  lon: number;
  /** Tooltip / accessible name. */
  label: string;
}

/** A point the map moves to (`focus` input); zoom defaults to at least 13. */
export interface MapFocus {
  lat: number;
  lon: number;
  zoom?: number;
}

/** Default OSM tiles; e2e and screenshots set `window.__fsTileUrl` to a local stub. */
export const OSM_TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ZURICH: [number, number] = [47.3769, 8.5417];

const KIND_ICON: Record<MapMarkerKind, string> = {
  'region-pin': 'pi-map-marker',
  region: 'pi-map',
  store: 'pi-shopping-bag',
  fsp: 'pi-box',
  basket: 'pi-shopping-cart',
  user: 'pi-user',
  event: 'pi-calendar',
  pickup: 'pi-truck',
  home: 'pi-home',
};

/**
 * Leaflet map (§12.4 MapView) with typed markers drawn as `pi` icons (no image assets). Leaflet is loaded lazily.
 * Wrap marker loading in `[state]` (DataState) to get skeleton/error/offline; a tile failure shows an overlay with
 * retry while markers stay usable.
 *
 * `<fs-map-view [markers]="pins" [state]="regions" height="20rem" (markerClick)="open($event)" />`
 */
@Component({
  selector: 'fs-map-view',
  imports: [...DATA_STATE_IMPORTS, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-map-view',
    '[style.--map-height]': 'height()',
    '[attr.data-tiles]': 'tileState()',
    '[attr.data-testid]': 'testId()',
    '[attr.data-center]': 'viewCenter()',
  },
  template: `
    @if (state(); as markerState) {
      <fs-data-state [state]="markerState" skeleton="card" [compact]="true" [testId]="testId() + '.state'" [attr.data-testid]="testId() + '.state'" />
    }
    <div class="frame">
      <div #mapEl class="map" role="region" [attr.aria-label]="ariaLabel() ?? ('shared.map.label' | t)"></div>
      @if (tileState() === 'error') {
        <div class="tiles-error" role="alert" [attr.data-testid]="testId() + '.tileError'">
          <i class="pi pi-map" aria-hidden="true"></i>
          <span>{{ 'shared.map.tilesFailed' | t }}</span>
          <button type="button" class="fs-btn fs-btn--outlined fs-btn--sm" (click)="reloadTiles()">
            <i class="pi pi-refresh" aria-hidden="true"></i>
            {{ 'shared.map.retry' | t }}
          </button>
        </div>
      }
    </div>
    @if (markers().length > 0) {
      <ul class="fs-visually-hidden">
        @for (marker of markers(); track marker.id) {
          <li>{{ marker.label }}</li>
        }
      </ul>
    }
  `,
  styles: `
    :host {
      --map-height: 18rem;
      display: grid;
      gap: 0.5rem;
      min-width: 0;
    }
    .frame {
      position: relative;
      overflow: hidden;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
    }
    .map {
      height: var(--map-height);
      background: var(--fs-surface-muted);
    }
    .tiles-error {
      position: absolute;
      z-index: 500;
      inset: auto 0.75rem 0.75rem;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem 0.75rem;
      padding: 0.6rem 0.8rem;
      border: 1px solid var(--fs-warning-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
      font-size: 0.88rem;
      font-weight: 600;
    }
    /* the Leaflet marker element is an unrotated 32px hit box (axe target-size ≥ 24 px in every engine);
       the visible pin is drawn by the rotated inner span */
    :host ::ng-deep .fs-map-marker {
      display: grid;
      place-items: center;
      box-sizing: border-box;
      min-width: 32px;
      min-height: 32px;
      background: transparent;
    }
    :host ::ng-deep .fs-map-marker__pin {
      display: grid;
      place-items: center;
      width: 28px;
      height: 28px;
      box-sizing: border-box;
      border: 2px solid #fff;
      border-radius: 50% 50% 50% 0;
      background: #25834a;
      box-shadow: 0 2px 6px rgb(0 0 0 / 0.35);
      color: #fff;
      transform: rotate(-45deg);
      pointer-events: none;
    }
    :host ::ng-deep .fs-map-marker__pin i {
      font-size: 0.85rem;
      transform: rotate(45deg);
    }
    /* Leaflet's attribution and zoom controls in our tokens (contrast in both themes) */
    :host ::ng-deep .leaflet-control-attribution {
      background: color-mix(in srgb, var(--fs-surface) 92%, transparent);
      color: var(--fs-text);
    }
    :host ::ng-deep .leaflet-control-attribution a {
      color: var(--fs-primary-strong);
    }
    :host ::ng-deep .leaflet-bar a {
      border-color: var(--fs-border);
      background: var(--fs-surface);
      color: var(--fs-text);
    }
    :host ::ng-deep .fs-map-marker--edit {
      cursor: grab;
    }
    :host ::ng-deep .fs-map-marker--edit .fs-map-marker__pin {
      background: #b5532d;
    }
    :host ::ng-deep .fs-map-marker[data-kind='store'] .fs-map-marker__pin {
      background: #1c6f91;
    }
    :host ::ng-deep .fs-map-marker[data-kind='home'] .fs-map-marker__pin {
      background: #b5532d;
    }
    :host ::ng-deep .fs-map-marker[data-kind='fsp'] .fs-map-marker__pin {
      background: #8a4fb0;
    }
    :host ::ng-deep .fs-map-marker[data-kind='basket'] .fs-map-marker__pin {
      background: #9a5b0b;
    }
    :host ::ng-deep .fs-map-marker[data-kind='user'] .fs-map-marker__pin {
      background: #3d5aa8;
    }
  `,
})
export class MapViewComponent {
  readonly markers = input<readonly MapMarker[]>([]);
  /** Optional DataState of the marker data (skeleton/error/offline above the map). */
  readonly state = input<DataState<unknown> | undefined>(undefined);
  readonly center = input<[number, number] | undefined>(undefined);
  readonly zoom = input(12);
  /** Zoom to all markers when there are several. */
  readonly fitMarkers = input(true);
  readonly height = input('18rem');
  readonly ariaLabel = input<string | undefined>(undefined);
  readonly testId = input('shared.map');
  /** Tile URL template (default: `window.__fsTileUrl` in tests, else OSM). */
  readonly tileUrl = input<string | undefined>(undefined);
  readonly markerClick = output<MapMarker>();
  /** A click on an fsp/basket/user/store marker: the host shows its detail bubble (also emits `markerClick`). */
  readonly bubble = output<MapMarker>();
  /** Editable position (pin editor): a draggable marker; a click on the map moves it too. */
  readonly position = input<[number, number] | null | undefined>(undefined);
  readonly editable = input(false);
  readonly positionChange = output<[number, number]>();
  /** Moves the map here whenever it changes (search result, "near me"); markers are no longer fitted afterwards. */
  readonly focus = input<MapFocus | null | undefined>(undefined);

  protected readonly tileState = signal<'loading' | 'ok' | 'error'>('loading');
  /** Current centre as `lat,lon` (3 decimals), updated after every move (tests read it from `data-center`). */
  protected readonly viewCenter = signal<string | null>(null);
  private readonly mapEl = viewChild.required<ElementRef<HTMLElement>>('mapEl');
  private map: Leaflet.Map | null = null;
  private tiles: Leaflet.TileLayer | null = null;
  private layer: Leaflet.LayerGroup | null = null;
  private positionMarker: Leaflet.Marker | null = null;
  private leaflet: typeof Leaflet | null = null;

  constructor() {
    afterNextRender(() => void this.init());
    effect(() => {
      const position = this.position();
      untracked(() => this.drawPosition(position));
    });
    effect(() => {
      const markers = this.markers();
      this.center();
      untracked(() => this.drawMarkers(markers));
    });
    effect(() => {
      const focus = this.focus();
      untracked(() => this.applyFocus(focus));
    });
    inject(DestroyRef).onDestroy(() => this.map?.remove());
  }

  /** Marker click: always `markerClick`, plus `bubble` for the bubble kinds (public for tests). */
  onMarkerClick(marker: MapMarker): void {
    this.markerClick.emit(marker);
    if (BUBBLE_KINDS.has(marker.kind)) {
      this.bubble.emit(marker);
    }
  }

  protected reloadTiles(): void {
    this.tileState.set('loading');
    this.tiles?.redraw();
  }

  private async init(): Promise<void> {
    // Leaflet is CommonJS: the browser build exposes it as `default`, the test runner as named exports
    const module = (await import('leaflet')) as typeof Leaflet & { default?: typeof Leaflet };
    const L = module.default ?? module;
    this.leaflet = L;
    const tileUrl =
      this.tileUrl() ?? (globalThis as { __fsTileUrl?: string }).__fsTileUrl ?? OSM_TILE_URL;
    this.map = L.map(this.mapEl().nativeElement, {
      center: this.center() ?? ZURICH,
      zoom: this.zoom(),
      scrollWheelZoom: false,
      attributionControl: true,
    });
    this.tiles = L.tileLayer(tileUrl, {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    })
      .on('tileerror', () => this.tileState.set('error'))
      .on('load', () => {
        if (this.tileState() !== 'error') {
          this.tileState.set('ok');
        }
      })
      .addTo(this.map);
    this.layer = L.layerGroup().addTo(this.map);
    this.map.on('click', (event: Leaflet.LeafletMouseEvent) => {
      if (this.editable()) {
        this.positionChange.emit([event.latlng.lat, event.latlng.lng]);
      }
    });
    this.map.on('moveend', () => this.updateViewCenter());
    this.drawMarkers(this.markers());
    this.drawPosition(this.position());
    this.applyFocus(this.focus());
    this.updateViewCenter();
  }

  private updateViewCenter(): void {
    const center = this.map?.getCenter();
    this.viewCenter.set(center ? `${center.lat.toFixed(3)},${center.lng.toFixed(3)}` : null);
  }

  private applyFocus(focus: MapFocus | null | undefined): void {
    if (focus && this.map) {
      this.map.setView([focus.lat, focus.lon], focus.zoom ?? Math.max(this.map.getZoom(), 13));
    }
  }

  private drawPosition(position: [number, number] | null | undefined): void {
    const L = this.leaflet;
    if (!L || !this.map) {
      return;
    }
    if (!position) {
      this.positionMarker?.remove();
      this.positionMarker = null;
      return;
    }
    if (!this.positionMarker) {
      const icon = L.divIcon({
        className: 'fs-map-marker fs-map-marker--edit',
        html: '<span class="fs-map-marker__pin"><i class="pi pi-map-marker" aria-hidden="true"></i></span>',
        iconSize: [32, 32],
        iconAnchor: [16, 36],
      });
      this.positionMarker = L.marker(position, { icon, draggable: this.editable(), keyboard: true })
        .on('dragend', () => {
          const latLng = this.positionMarker!.getLatLng();
          this.positionChange.emit([latLng.lat, latLng.lng]);
        })
        .addTo(this.map);
      this.positionMarker.getElement()?.setAttribute('data-kind', 'position');
      // keyboard markers are buttons: give the position pin the map's accessible name (axe aria-command-name)
      this.positionMarker.getElement()?.setAttribute('aria-label', this.ariaLabel() ?? 'Position');
      this.map.setView(position, Math.max(this.map.getZoom(), this.zoom()));
    } else {
      this.positionMarker.setLatLng(position);
    }
  }

  private drawMarkers(markers: readonly MapMarker[]): void {
    const L = this.leaflet;
    if (!L || !this.map || !this.layer) {
      return;
    }
    this.layer.clearLayers();
    for (const marker of markers) {
      const icon = L.divIcon({
        className: 'fs-map-marker',
        html: `<span class="fs-map-marker__pin"><i class="pi ${KIND_ICON[marker.kind]}" aria-hidden="true"></i></span>`,
        iconSize: [32, 32],
        // the pin's tip (bottom corner of the rotated inner square) is the position
        iconAnchor: [16, 36],
      });
      L.marker([marker.lat, marker.lon], { icon, title: marker.label, alt: marker.label, keyboard: true })
        .on('click', () => this.onMarkerClick(marker))
        // Leaflet's own Enter handler only opens popups: keyboard users open the marker like a click
        .on('keydown', (event: Leaflet.LeafletKeyboardEvent) => {
          if (event.originalEvent.key === 'Enter' || event.originalEvent.key === ' ') {
            event.originalEvent.preventDefault();
            this.onMarkerClick(marker);
          }
        })
        .addTo(this.layer)
        .getElement()
        ?.setAttribute('data-kind', marker.kind);
    }
    const center = this.center();
    if (this.focus()) {
      return;
    }
    if (this.fitMarkers() && markers.length > 1) {
      this.map.fitBounds(L.latLngBounds(markers.map((m) => [m.lat, m.lon] as [number, number])), {
        padding: [48, 48],
        maxZoom: 14,
      });
    } else if (center) {
      this.map.setView(center, this.zoom());
    } else if (markers.length === 1) {
      this.map.setView([markers[0].lat, markers[0].lon], this.zoom());
    }
  }
}
