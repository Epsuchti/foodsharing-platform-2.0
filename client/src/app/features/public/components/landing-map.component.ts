import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { MapBubble, MapMarker as ApiMapMarker, MapMarkerKind, PublicMapService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { mapViewQuery } from '../../../shared/map-view/map-view-query';
// types only here: MapViewComponent is used in the inner @defer alone, so its chunk stays lazy
import type {
  MapFocus,
  MapMarker,
  MapMarkerKind as ViewKind,
} from '../../../shared/map-view/map-view.component';
import { MapViewComponent } from '../../../shared/map-view/map-view.component';
import { LandingMarkersResource } from '../landing-markers.resource';

/** The marker kinds of `/` and how MapView draws them (FSP: the accent pin with `pi-box`, decision 160 O1). */
const VIEW_KIND: Partial<Record<MapMarkerKind, ViewKind>> = {
  [MapMarkerKind.Region]: 'region',
  [MapMarkerKind.RegionPin]: 'region-pin',
  [MapMarkerKind.Fsp]: 'fsp',
};

/** The open pin: its API kind (`getPublicMapBubble` takes it in lower case) and id. */
export interface LandingSelection {
  readonly kind: MapMarkerKind;
  readonly id: string;
}

/** What the popup shows: kind label key, title, the FSP address line and the link. */
interface PopupContent {
  readonly kindKey: string;
  readonly title: string;
  readonly address: string | null;
  readonly link: string;
}

/** DACH on the first view (D9): zoom 6 from 48rem, 5 on phones. */
export const LANDING_MAP_CENTER: [number, number] = [47.9, 10.4];

export function landingMapZoom(wide: boolean): number {
  return wide ? 6 : 5;
}

/**
 * The real map in "In deiner Nähe" (C17, decision 179, amends decision 160 map option 1): the shared MapView on the
 * self-hosted basemap with the public region and Fairteiler pins of LandingMarkersResource. A second
 * `@defer (on viewport)` keeps the Leaflet, PMTiles and protomaps chunks and every tile request back until the map
 * frame is in view; the placeholder is a plain frame of the same height. On phones one finger scrolls the page and
 * two move the map (`cooperative`); the pins are no Tab stops (the keyboard path is the postcode search and
 * "Karte öffnen"). A pin opens a small popup (`getPublicMapBubble`, all DataState states). A postcode hit moves the
 * map (`LandingMarkersResource.focus`). "Karte öffnen" opens `/map?lat&lon&z` at the current view.
 */
@Component({
  selector: 'fs-landing-map',
  imports: [...DATA_STATE_IMPORTS, MapViewComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="block" data-testid="public.landing.near.mapBlock">
      @defer (on viewport) {
        <div class="stage">
          <fs-map-view
            [markers]="pins()"
            [state]="markers.state"
            [center]="center"
            [zoom]="zoom"
            [fitMarkers]="false"
            [focus]="markers.focus()"
            [cooperative]="true"
            [markerTabStops]="false"
            [ariaLabel]="mapLabel()"
            [height]="height"
            testId="public.landing.near.map"
            (markerClick)="open($event)"
            (viewChange)="view.set($event)"
          >
            <a
              fsMapTilesAction
              class="fs-btn fs-btn--sm"
              routerLink="/map"
              [queryParams]="openMapQuery()"
              data-testid="public.landing.near.map.tilesOpenMap"
              >{{ 'public.landing.near.map.openMap' | t }}</a
            >
          </fs-map-view>
          @if (selected(); as current) {
            <aside
              #popup
              class="popup fs-card"
              tabindex="-1"
              [attr.aria-label]="'public.landing.near.map.bubble.label' | t"
              [attr.data-kind]="current.kind"
              (keydown.escape)="close()"
              data-testid="public.landing.near.map.bubble"
            >
              <button
                type="button"
                class="popup__close"
                [attr.aria-label]="'public.landing.near.map.bubble.close' | t"
                (click)="close()"
                data-testid="public.landing.near.map.bubble.close"
              >
                <i class="pi pi-times" aria-hidden="true"></i>
              </button>
              <fs-data-state
                [state]="bubble"
                skeleton="card"
                [skeletonRows]="2"
                [compact]="true"
                data-testid="public.landing.near.map.bubble.state"
              >
                <ng-template fsError let-error let-retry="retry">
                  <fs-error-state
                    [error]="error"
                    [kind]="bubble.status() === 'offline' ? 'offline' : undefined"
                    [retryable]="bubble.status() !== 'notFound'"
                    [heading]="
                      bubble.status() === 'notFound'
                        ? ('public.landing.near.map.bubble.gone' | t)
                        : undefined
                    "
                    [compact]="true"
                    testId="public.landing.near.map.bubble.state"
                    (retry)="retry()"
                  />
                </ng-template>
                <ng-template [fsData]="bubble">
                  @if (content(); as c) {
                    <p class="popup__kind">{{ c.kindKey | t }}</p>
                    <h3
                      #popupTitle
                      class="popup__title"
                      tabindex="-1"
                      data-testid="public.landing.near.map.bubble.title"
                    >
                      {{ c.title }}
                    </h3>
                    @if (c.address) {
                      <p class="popup__address" data-testid="public.landing.near.map.bubble.address">
                        {{ c.address }}
                      </p>
                    }
                    <a
                      class="fs-btn fs-btn--sm popup__link"
                      [routerLink]="c.link"
                      data-testid="public.landing.near.map.bubble.open"
                    >
                      {{ 'public.landing.near.map.bubble.open' | t }}
                      <i class="pi pi-arrow-right" aria-hidden="true"></i>
                    </a>
                  }
                </ng-template>
              </fs-data-state>
            </aside>
          }
        </div>
      } @placeholder {
        <div
          class="placeholder"
          [style.height]="height"
          data-testid="public.landing.near.map.placeholder"
        ></div>
      } @error {
        <div
          class="placeholder unavailable"
          [style.min-height]="height"
          role="status"
          data-testid="public.landing.near.map.unavailable"
        >
          <i class="pi pi-map" aria-hidden="true"></i>
          <p>{{ 'public.landing.near.map.unavailable' | t }}</p>
          <a class="fs-btn fs-btn--sm" routerLink="/map" [queryParams]="openMapQuery()">{{
            'public.landing.near.map.openMap' | t
          }}</a>
        </div>
      }
      <div class="footer">
        <p data-testid="public.landing.near.map.note">
          @if (counts(); as c) {
            {{ 'public.landing.near.map.note' | t: c }}
          } @else {
            {{ 'public.landing.near.map.noteNoCount' | t }}
          }
        </p>
        <a
          class="fs-btn"
          routerLink="/map"
          [queryParams]="openMapQuery()"
          data-testid="public.landing.near.map.openMap"
        >
          <i class="pi pi-map-marker" aria-hidden="true"></i>
          <span>{{ 'public.landing.near.map.openMap' | t }}</span>
        </a>
      </div>
    </div>
  `,
  styles: `
    :host {
      /* MapView colours from the public tokens (decision 179, D8); inside main.fs-public they are the reskin's */
      --fs-map-surface: var(--fs-pub-map-land);
      --fs-map-pin-region-bg: var(--fs-primary);
      --fs-map-pin-region-fg: var(--fs-on-primary);
      /* the only accent besides the warm bubble blob, and always with the fridge glyph (decision 160, O1) */
      --fs-map-pin-fsp-bg: var(--fs-accent);
      --fs-map-pin-fsp-fg: #1a2a20;
      display: block;
      min-width: 0;
    }
    .block {
      display: grid;
      gap: 0.75rem;
    }
    .stage {
      position: relative;
      display: grid;
      gap: 0.75rem;
      min-width: 0;
    }
    .placeholder {
      box-sizing: border-box;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-pub-map-land);
    }
    .unavailable {
      display: grid;
      place-content: center;
      justify-items: center;
      gap: 0.6rem;
      padding: 1.5rem;
      color: var(--fs-text-muted);
      text-align: center;
    }
    .unavailable .pi {
      font-size: 1.6rem;
    }
    .unavailable p {
      margin: 0;
      color: var(--fs-text);
      font-weight: 650;
    }
    .popup {
      position: relative;
      display: grid;
      gap: 0.35rem;
      padding: 0.9rem 1rem;
    }
    .popup:focus {
      outline: none;
    }
    @media (min-width: 48rem) {
      .popup {
        position: absolute;
        z-index: 700;
        bottom: 0.75rem;
        left: 0.75rem;
        width: min(19rem, calc(100% - 1.5rem));
        box-shadow: var(--fs-shadow-md);
      }
    }
    .popup__close {
      z-index: 1;
      position: absolute;
      top: 0.4rem;
      right: 0.4rem;
      display: grid;
      width: 2.75rem;
      height: 2.75rem;
      place-items: center;
      border: 0;
      border-radius: 999px;
      background: transparent;
      color: var(--fs-text-muted);
      cursor: pointer;
    }
    .popup__close:focus-visible,
    .popup__title:focus-visible {
      outline: 2px solid var(--fs-primary);
      outline-offset: 2px;
    }
    .popup__kind {
      margin: 0;
      padding-right: 2.5rem;
      color: var(--fs-primary-strong);
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .popup__title {
      margin: 0;
      padding-right: 2.5rem;
      font-size: 1.05rem;
      overflow-wrap: anywhere;
    }
    .popup__address {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .popup__link {
      justify-self: start;
      margin-top: 0.3rem;
    }
    .footer {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.6rem 1rem;
    }
    .footer p {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
  `,
})
export class LandingMapComponent {
  protected readonly markers = inject(LandingMarkersResource);
  private readonly api = inject(PublicMapService);
  private readonly i18n = inject(I18nService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly height = 'clamp(16rem, 36vw, 24rem)';
  protected readonly center = LANDING_MAP_CENTER;
  protected readonly zoom = landingMapZoom(
    globalThis.matchMedia?.('(min-width: 48rem)').matches ?? true,
  );

  /** The map's view after its last move; `null` before the first one (then the D9 view counts). */
  readonly view = signal<MapFocus | null>(null);
  /** The pin whose popup is open. */
  readonly selected = signal<LandingSelection | null>(null);

  protected readonly openMapQuery = computed(() =>
    mapViewQuery(this.view() ?? { lat: this.center[0], lon: this.center[1], zoom: this.zoom }),
  );

  private readonly loaded = computed(() =>
    this.markers.state.status() === 'success' ? this.markers.state.data() : undefined,
  );

  protected readonly pins = computed<MapMarker[]>(() => {
    const set = this.loaded();
    this.i18n.language();
    return [...toPins(set?.regions ?? [], this.i18n), ...toPins(set?.fsps ?? [], this.i18n)];
  });

  /** `{ regions, fsps }` once both lists are loaded; `null` when one failed or is still loading. */
  protected readonly counts = computed(() => {
    const set = this.loaded();
    return set?.regions && set.fsps ? { regions: set.regions.length, fsps: set.fsps.length } : null;
  });

  /** The map's accessible name carries the counts (the pins themselves are no Tab stops, D7). */
  protected readonly mapLabel = computed(() => {
    this.i18n.language();
    const label = this.i18n.t('public.landing.near.map.label');
    const counts = this.counts();
    return counts ? `${label}: ${this.i18n.t('public.landing.near.map.note', counts)}` : label;
  });

  protected readonly bubble = loadResource<MapBubble, LandingSelection>({
    params: () => this.selected() ?? undefined,
    loader: ({ kind, id }) => this.api.getPublicMapBubble({ kind: kind.toLowerCase(), id }),
    isEmpty: () => false,
  });

  protected readonly content = computed<PopupContent | null>(() => {
    const current = this.selected();
    const bubble = this.bubble.data();
    if (!current || !bubble) {
      return null;
    }
    const fsp = current.kind === MapMarkerKind.Fsp;
    // an FSP's lines are the address, then the follower count (server BubbleService)
    const address = fsp ? bubble.lines.slice(0, -1).join(', ') : '';
    return {
      kindKey: fsp ? 'public.landing.near.map.kind.fsp' : 'public.landing.near.map.kind.region',
      title: bubble.title,
      address: address || null,
      link: bubble.link,
    };
  });

  private readonly popup = viewChild<ElementRef<HTMLElement>>('popup');
  private readonly popupTitle = viewChild<ElementRef<HTMLElement>>('popupTitle');
  /** Focus goes into the popup once per opening: the heading when loaded, else the popup itself. */
  private focusPending = false;

  constructor() {
    effect(() => {
      const popup = this.popup();
      const title = this.popupTitle();
      const status = this.bubble.status();
      untracked(() => {
        if (!this.focusPending || !popup) {
          return;
        }
        if (title) {
          title.nativeElement.focus();
          this.focusPending = false;
        } else if (!['idle', 'loading', 'refreshing', 'success'].includes(status)) {
          // error, not found, offline: no heading, so the popup itself
          popup.nativeElement.focus();
          this.focusPending = false;
        }
      });
    });
  }

  /** A pin click opens its popup (public for tests). */
  open(marker: MapMarker): void {
    const kind = apiKind(marker.kind);
    if (!kind) {
      return;
    }
    this.focusPending = true;
    this.selected.set({ kind, id: marker.id });
  }

  protected close(): void {
    this.selected.set(null);
    this.focusPending = false;
    this.host.nativeElement.querySelector<HTMLElement>('.leaflet-container')?.focus();
  }
}

function apiKind(kind: ViewKind): MapMarkerKind | null {
  for (const [api, view] of Object.entries(VIEW_KIND)) {
    if (view === kind) {
      return api as MapMarkerKind;
    }
  }
  return null;
}

function toPins(markers: readonly ApiMapMarker[], i18n: I18nService): MapMarker[] {
  const pins: MapMarker[] = [];
  for (const marker of markers) {
    const kind = VIEW_KIND[marker.kind];
    if (kind) {
      pins.push({
        id: marker.id,
        kind,
        lat: marker.lat,
        lon: marker.lon,
        label: i18n.t(
          kind === 'fsp' ? 'public.landing.near.map.kind.fsp' : 'public.landing.near.map.kind.region',
        ),
      });
    }
  }
  return pins;
}
