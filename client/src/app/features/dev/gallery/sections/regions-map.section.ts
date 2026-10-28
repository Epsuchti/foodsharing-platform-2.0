import { ChangeDetectionStrategy, Component, signal } from '@angular/core';

import { RegionRef } from '../../../../api';
import { MapMarker, MapViewComponent } from '../../../../shared/map-view/map-view.component';
import { RegionPickerComponent } from '../../../../shared/region-picker/region-picker.component';

const PINS: MapMarker[] = [
  { id: 'zh', kind: 'region-pin', lat: 47.3769, lon: 8.5417, label: 'Zürich Stadt' },
  { id: 'store', kind: 'store', lat: 47.3901, lon: 8.5153, label: 'Bäckerei Beispiel' },
  { id: 'fsp', kind: 'fsp', lat: 47.3667, lon: 8.55, label: 'Fairteiler Kreis 8' },
];

/** RegionPicker (real public endpoints of the e2e/local seed) and MapView (markers, tile failure). */
@Component({
  selector: 'fs-gallery-regions-map-section',
  imports: [MapViewComponent, RegionPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-regions">RegionPicker &amp; MapView</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.regionPicker.postcode">
        <header class="specimen__head"><code>postcode (empty)</code></header>
        <fs-region-picker (regionSelected)="picked.set($event)" testId="gallery.regionPickerPostcode" />
        @if (picked(); as region) {
          <p data-testid="gallery.regionPicker.picked"><code>{{ region.slug }}</code></p>
        }
      </section>
      <section class="specimen" data-testid="gallery.regionPicker.tree">
        <header class="specimen__head"><code>tree (seed regions)</code></header>
        <fs-region-picker initialMode="tree" rootSlug="schweiz" testId="gallery.regionPickerTree" />
      </section>
      <section class="specimen" data-testid="gallery.map.markers">
        <header class="specimen__head"><code>markers</code></header>
        <fs-map-view [markers]="pins" height="16rem" testId="gallery.mapMarkers" />
      </section>
      <section class="specimen" data-testid="gallery.map.tileError">
        <header class="specimen__head"><code>tiles failed</code></header>
        <fs-map-view
          [markers]="pins.slice(0, 1)"
          height="16rem"
          tileUrl="/does-not-exist/{z}/{x}/{y}.png"
          testId="gallery.mapTileError"
        />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class RegionsMapGallerySection {
  protected readonly pins = PINS;
  protected readonly picked = signal<RegionRef | null>(null);
}
