import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { PublicIntroPickup } from '../../../../api';
import { IntroPickupDatesComponent } from '../../../regions/shared/intro-pickup-dates.component';

const PICKUPS: PublicIntroPickup[] = [
  {
    id: 'p1',
    startAt: '2026-11-03T17:30:00Z',
    online: false,
    city: 'Zürich',
    freeSlots: 2,
  },
  {
    id: 'p2',
    startAt: '2026-11-05T09:00:00Z',
    online: true,
    freeSlots: 1,
  },
];

/** IntroPickupDates (next intro pickups on region pages): with dates and empty. */
@Component({
  selector: 'fs-gallery-regions-section',
  imports: [IntroPickupDatesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-regions-pages">Region pages</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.introDates.list">
        <header class="specimen__head"><code>fs-intro-pickup-dates</code></header>
        <fs-intro-pickup-dates [pickups]="pickups" testId="gallery.introDates" />
      </section>
      <section class="specimen" data-testid="gallery.introDates.empty">
        <header class="specimen__head"><code>fs-intro-pickup-dates (empty)</code></header>
        <fs-intro-pickup-dates [pickups]="[]" testId="gallery.introDatesEmpty" />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class RegionsGallerySection {
  protected readonly pickups = PICKUPS;
}

/** Gallery sections of the regions feature (one file per module, no conflicts). */
export const REGIONS_GALLERY_SECTIONS: readonly Type<unknown>[] = [RegionsGallerySection];
