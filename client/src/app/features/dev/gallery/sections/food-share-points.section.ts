import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { FspStatus, FspSummary } from '../../../../api';
import { FspCardComponent } from '../../../food-share-points/components/fsp-card.component';

/** A plain square as photo (data: URI, allowed by the CSP img-src). */
const PHOTO =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="96" height="96" fill="#cfe8d6"/><rect x="26" y="18" width="44" height="62" rx="4" fill="#25834a"/><rect x="31" y="24" width="34" height="22" fill="#e8f5ec"/><rect x="31" y="52" width="34" height="22" fill="#e8f5ec"/></svg>',
  );

const WIEDIKON: FspSummary = {
  id: 'f1',
  name: 'Fairteiler Wiedikon',
  photoUrl: PHOTO,
  city: 'Zürich',
  status: FspStatus.Active,
  followerCount: 12,
  following: true,
};
const HOTTINGEN: FspSummary = { ...WIEDIKON, id: 'f4', name: 'Fairteiler Hottingen', photoUrl: undefined, followerCount: 0, following: false };
const ALTSTETTEN: FspSummary = { ...HOTTINGEN, id: 'f5', name: 'Fairteiler Altstetten', status: FspStatus.Inactive, followerCount: 1 };

/** Food share point cards (plan-m7 Q6, FS1): followed with photo, without photo, awaiting activation. */
@Component({
  selector: 'fs-gallery-food-share-points-section',
  imports: [FspCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-food-share-points">Food share points</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.fspCard.active">
        <header class="specimen__head"><code>fs-fsp-card (following, highlighted / no photo)</code></header>
        <fs-fsp-card [fsp]="wiedikon" [highlighted]="true" />
        <fs-fsp-card [fsp]="hottingen" />
      </section>
      <section class="specimen" data-testid="gallery.fspCard.inactive">
        <header class="specimen__head"><code>fs-fsp-card (awaiting activation)</code></header>
        <fs-fsp-card [fsp]="altstetten" />
      </section>
    </div>
  `,
  styles: `
    .specimen fs-fsp-card + fs-fsp-card { margin-top: 0.75rem; }
  `,
})
export class FoodSharePointsGallerySection {
  protected readonly wiedikon = WIEDIKON;
  protected readonly hottingen = HOTTINGEN;
  protected readonly altstetten = ALTSTETTEN;
}

export const FOOD_SHARE_POINTS_GALLERY_SECTIONS: readonly Type<unknown>[] = [FoodSharePointsGallerySection];
