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
import type { PMTiles } from 'pmtiles';

import { DataState } from '../../core/data/data-state';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';
import { ThemeService } from '../../core/theme/theme.service';
import { DATA_STATE_IMPORTS } from '../data-state';
import { BASEMAP_URL, OSM_ATTRIBUTION, basemapFlavor, basemapLang } from './basemap';

export type MapMarkerKind =
  | 'region-pin'
  | 'region'
  | 'store'
  | 'store-area'
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
  /** Shown as a bubble with this number instead of a pin (store counts per city, S17). */
  count?: number;
}

/** A point the map moves to (`focus` input); zoom defaults to at least 13. */
export interface MapFocus {
  lat: number;
  lon: number;
  zoom?: number;
}

/** A readable PMTiles archive and its highest zoom with data (overzoomed above). */
interface Basemap {
  archive: PMTiles;
  maxZoom: number;
}
const ZURICH: [number, number] = [47.3769, 8.5417];

const KIND_ICON: Record<MapMarkerKind, string> = {
  'region-pin': 'pi-map-marker',
  region: 'pi-map',
  store: 'pi-shopping-bag',
  'store-area': 'pi-shopping-bag',
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
 * Background (MT1, decision 159): the self-hosted vector basemap `/tiles/basemap.pmtiles` (protomaps-leaflet, light
 * or dark with the theme, labels in the UI language). A raster template in `tileUrl` or `window.__fsTileUrl`
 * (screenshots: a local stub PNG) replaces it.
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
    '[attr.data-basemap]': 'basemapMode()',
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
    :host ::ng-deep .fs-map-marker__count {
      display: grid;
      place-items: center;
      width: 2.25rem;
      height: 2.25rem;
      border: 2px solid var(--fs-surface);
      border-radius: 50%;
      background: var(--fs-primary);
      color: var(--fs-on-primary, #fff);
      font-size: 0.85rem;
      font-weight: 700;
      box-shadow: 0 1px 4px rgb(0 0 0 / 30%);
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
  /** Raster tile URL template instead of the vector basemap (default: `window.__fsTileUrl`, set by screenshots). */
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
  /** `raster` with a tile URL template, else the flavor of the vector basemap (tests read `data-basemap`). */
  protected readonly basemapMode = signal<'light' | 'dark' | 'raster' | null>(null);
  private readonly theme = inject(ThemeService);
  private readonly i18n = inject(I18nService);
  /** Current centre as `lat,lon` (3 decimals), updated after every move (tests read it from `data-center`). */
  protected readonly viewCenter = signal<string | null>(null);
  private readonly mapEl = viewChild.required<ElementRef<HTMLElement>>('mapEl');
  private map: Leaflet.Map | null = null;
  private tiles: Leaflet.GridLayer | null = null;
  private basemap: Basemap | null = null;
  /** Counts basemap probes: an answer to an older probe (before a retry) is dropped. */
  private probes = 0;
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
    // a theme or language switch redraws the vector basemap in the new flavor / label language
    effect(() => {
      this.theme.isDark();
      this.i18n.language();
      untracked(() => {
        if (this.basemap) {
          void this.showBasemap(this.basemap);
        }
      });
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
    if (this.basemapMode() === 'raster') {
      this.tiles?.redraw();
    } else {
      void this.loadBasemap();
    }
  }

  /** Reads the archive header; a missing or broken file shows the tile error (markers stay usable). */
  protected async probeBasemap(): Promise<Basemap> {
    const { PMTiles } = await import('pmtiles');
    const archive = new PMTiles(BASEMAP_URL);
    const header = await archive.getHeader();
    return { archive, maxZoom: header.maxZoom };
  }

  private async loadBasemap(): Promise<void> {
    const probe = ++this.probes;
    try {
      const basemap = await this.probeBasemap();
      if (probe === this.probes) {
        this.basemap = basemap;
        await this.showBasemap(basemap);
      }
    } catch {
      if (probe === this.probes) {
        this.tiles?.remove();
        this.tiles = null;
        this.tileState.set('error');
      }
    }
  }

  /** (Re)draws the vector basemap in the current flavor and label language. */
  private async showBasemap(basemap: Basemap): Promise<void> {
    const L = this.leaflet;
    if (!L || !this.map) {
      return;
    }
    // protomaps-leaflet extends the global `L.GridLayer`
    (globalThis as { L?: typeof Leaflet }).L ??= L;
    const { leafletLayer } = await import('protomaps-leaflet');
    const flavor = basemapFlavor(this.theme.isDark());
    const layer = leafletLayer({
      url: basemap.archive,
      flavor,
      lang: basemapLang(this.i18n.language()),
      attribution: OSM_ATTRIBUTION,
      maxDataZoom: basemap.maxZoom,
      maxZoom: 19,
    }) as unknown as Leaflet.GridLayer;
    layer.on('load', () => {
      if (this.tileState() !== 'error') {
        this.tileState.set('ok');
      }
    });
    this.tiles?.remove();
    this.tiles = layer;
    layer.addTo(this.map);
    layer.bringToBack();
    this.basemapMode.set(flavor);
  }

  private async init(): Promise<void> {
    // the ESM build of Leaflet (named exports); the `leaflet` main entry is CommonJS (src/types/leaflet-esm.d.ts)
    const L: typeof Leaflet = await import('leaflet/dist/leaflet-src.esm.js');
    this.leaflet = L;
    const rasterUrl = this.tileUrl() ?? (globalThis as { __fsTileUrl?: string }).__fsTileUrl;
    this.map = L.map(this.mapEl().nativeElement, {
      center: this.center() ?? ZURICH,
      zoom: this.zoom(),
      scrollWheelZoom: false,
      attributionControl: true,
    });
    if (rasterUrl) {
      this.basemapMode.set('raster');
      this.tiles = L.tileLayer(rasterUrl, { maxZoom: 19, attribution: OSM_ATTRIBUTION })
        .on('tileerror', () => this.tileState.set('error'))
        .on('load', () => {
          if (this.tileState() !== 'error') {
            this.tileState.set('ok');
          }
        })
        .addTo(this.map);
    } else {
      void this.loadBasemap();
    }
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
        html:
          marker.count === undefined
            ? `<span class="fs-map-marker__pin"><i class="pi ${KIND_ICON[marker.kind]}" aria-hidden="true"></i></span>`
            : `<span class="fs-map-marker__count">${marker.count}</span>`,
        iconSize: marker.count === undefined ? [32, 32] : [36, 36],
        // the pin's tip (bottom corner of the rotated inner square) is the position; a count bubble is centred on it
        iconAnchor: marker.count === undefined ? [16, 36] : [18, 18],
      });
      // count bubbles stay on top: under the pins of the same city their number can't be read
      const zIndexOffset = marker.count === undefined ? 0 : 1000;
      L.marker([marker.lat, marker.lon], { icon, title: marker.label, alt: marker.label, keyboard: true, zIndexOffset })
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
