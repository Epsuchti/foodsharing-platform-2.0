import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { IntroPickupCategory, IntroPickupSlot, Role, SlotStatus } from '../../../../api';
import { EinAbCardComponent, EinAbCardData } from '../../../intro-pickups/components/einab-card.component';
import { EinAbSlotsComponent } from '../../../intro-pickups/components/einab-slots.component';

const GUIDE = { id: 'g', displayName: 'Julien Favre', role: Role.Foodsaver, verified: true };

/** EinAb card (public / bookable) and the guide's slot list in every status. */
@Component({
  selector: 'fs-gallery-intro-pickups-section',
  imports: [EinAbCardComponent, EinAbSlotsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-intro-pickups">Intro pickups</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.einabCard">
        <header class="specimen__head"><code>fs-einab-card</code></header>
        <fs-einab-card [einab]="card" timeZone="Europe/Zurich" />
      </section>
      <section class="specimen" data-testid="gallery.einabSlots">
        <header class="specimen__head"><code>fs-einab-slots</code></header>
        <fs-einab-slots [slots]="slots" [past]="false" />
      </section>
    </div>
  `,
})
export class IntroPickupsGallerySection {
  protected readonly card: EinAbCardData = {
    category: IntroPickupCategory.Supermarket,
    startAt: '2026-11-04T17:00:00Z',
    guide: GUIDE,
    businessName: 'Coop Stauffacher',
    publicGeneralLocation: 'Kreis 4',
    publicInfo: 'Bitte Taschen mitbringen.',
    visitFairteiler: true,
    minimumPickupCount: 1,
    freeSlots: 2,
  };
  protected readonly slots: IntroPickupSlot[] = [
    { id: '1', status: SlotStatus.Booked, bookingUser: { id: 'u', displayName: 'Luca Meier', role: Role.Foodsharer, verified: false, foodsaverNumber: 100004 }, commentCount: 1 },
    { id: '2', status: SlotStatus.Available },
    { id: '3', status: SlotStatus.Cancelled },
  ];
}

/** Gallery sections of the intro-pickups feature (one file per module, no conflicts). */
export const INTRO_PICKUPS_GALLERY_SECTIONS: readonly Type<unknown>[] = [IntroPickupsGallerySection];
