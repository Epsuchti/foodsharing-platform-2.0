import { ChangeDetectionStrategy, Component, Type, signal } from '@angular/core';

import { MapMarker, MapViewComponent } from '../../../../shared/map-view/map-view.component';
import { MarkerFilter, MarkerFilterBarComponent } from '../../../../shared/marker-filter-bar/marker-filter-bar.component';

const STORE_FILTERS: MarkerFilter[] = [
  {
    key: 'status',
    label: 'Cooperation',
    options: [
      { value: 'ALL', label: 'All' },
      { value: 'COOPERATING', label: 'Cooperating' },
      { value: 'NOT_COOPERATING', label: 'Not (yet) cooperating' },
    ],
  },
  {
    key: 'help',
    label: 'Team',
    options: [
      { value: 'ALL', label: 'All' },
      { value: 'OPEN', label: 'Open for applications' },
      { value: 'SEARCHING', label: 'Urgently needs help' },
    ],
  },
  {
    key: 'scope',
    label: 'Scope',
    options: [
      { value: 'ALL', label: 'All stores' },
      { value: 'REGION', label: 'My regions' },
      { value: 'MEMBER', label: 'My teams' },
    ],
  },
];

/** One pin of every kind the /map page draws (Zürich). */
const PINS: MapMarker[] = [
  { id: 'r1', kind: 'region-pin', lat: 47.3769, lon: 8.5417, label: 'Zürich Stadt' },
  { id: 'f1', kind: 'fsp', lat: 47.3721, lon: 8.5172, label: 'Fairteiler Wiedikon' },
  { id: 'b1', kind: 'basket', lat: 47.389, lon: 8.548, label: 'Brot vom Vortag' },
  { id: 's1', kind: 'store', lat: 47.366, lon: 8.545, label: 'Bäckerei Kreis 8' },
  { id: 'e1', kind: 'event', lat: 47.381, lon: 8.53, label: 'Stammtisch' },
  { id: 'u1', kind: 'user', lat: 47.395, lon: 8.52, label: 'Member' },
];

/** Map page parts (plan-m7 Q7, M1): the marker filter bar and the pin kinds of the layers. */
@Component({
  selector: 'fs-gallery-map-section',
  imports: [MapViewComponent, MarkerFilterBarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-map">Map</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.map.filterBar">
        <header class="specimen__head"><code>fs-marker-filter-bar</code></header>
        <fs-marker-filter-bar [filters]="filters" [(values)]="values" label="Filter stores" testId="gallery.map.filter" />
      </section>
      <section class="specimen" data-testid="gallery.map.layers">
        <header class="specimen__head"><code>fs-map-view (all layer kinds)</code></header>
        <fs-map-view [markers]="pins" height="16rem" ariaLabel="Map with every pin kind" testId="gallery.map.view" />
      </section>
    </div>
  `,
})
export class MapGallerySection {
  protected readonly filters = STORE_FILTERS;
  protected readonly values = signal<Record<string, string>>({ status: 'COOPERATING', help: 'ALL', scope: 'ALL' });
  protected readonly pins = PINS;
}

export const MAP_GALLERY_SECTIONS: readonly Type<unknown>[] = [MapGallerySection];
