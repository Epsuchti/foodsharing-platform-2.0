import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { BasketStatus, BasketSummary, Role, UserSummary } from '../../../../api';
import { BasketCardComponent } from '../../../baskets/components/basket-card.component';

const SARAH: UserSummary = { id: 'u6', displayName: 'Sarah Müller', role: Role.Foodsaver, verified: true };
const MARCO: UserSummary = {
  id: 'u8',
  displayName: 'Marco Bernasconi',
  role: Role.Foodsaver,
  verified: true,
  sleepMode: true,
  sleepMessage: 'Bis Ende Monat in den Ferien.',
};

/** A plain square as photo (data: URI, allowed by the CSP img-src). */
const PHOTO =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="96" height="96" fill="#e8b04a"/><circle cx="48" cy="52" r="26" fill="#c0392b"/><rect x="45" y="18" width="6" height="14" fill="#6b4226"/></svg>',
  );

/** Fixed dates far in the future/past so the screenshots stay stable. */
const NEARBY: BasketSummary = {
  id: 'b1',
  description: 'Zwei Brote und Gipfeli vom Vortag',
  photoUrl: PHOTO,
  lat: 47.389,
  lon: 8.548,
  distanceKm: 1.4,
  createdAt: '2026-10-31T09:00:00Z',
  expiresAt: '2099-12-31T22:00:00Z',
  owner: SARAH,
  status: BasketStatus.Active,
};
const NO_PHOTO: BasketSummary = {
  ...NEARBY,
  id: 'b2',
  description: 'Gemüsekiste: Rüebli, Lauch, Kartoffeln – bitte heute noch abholen, der Lauch hält nicht mehr lange.',
  photoUrl: undefined,
  distanceKm: 12.6,
  owner: MARCO,
};
const OWN_TAKEN: BasketSummary = { ...NEARBY, id: 'b3', status: BasketStatus.Taken, requestCount: 2, distanceKm: undefined };

/** Food basket cards (plan-m7 Q5, B1/B2): nearby with photo and distance, without photo, own taken basket. */
@Component({
  selector: 'fs-gallery-baskets-section',
  imports: [BasketCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-baskets">Food baskets</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.basketCard.nearby">
        <header class="specimen__head"><code>fs-basket-card (nearby, highlighted)</code></header>
        <fs-basket-card [basket]="nearby" [highlighted]="true" />
        <fs-basket-card [basket]="noPhoto" />
      </section>
      <section class="specimen" data-testid="gallery.basketCard.own">
        <header class="specimen__head"><code>fs-basket-card (own, taken, request count)</code></header>
        <fs-basket-card [basket]="ownTaken" [showOwner]="false" [showRequests]="true" />
      </section>
    </div>
  `,
  styles: `
    .specimen fs-basket-card + fs-basket-card { margin-top: 0.75rem; }
  `,
})
export class BasketsGallerySection {
  protected readonly nearby = NEARBY;
  protected readonly noPhoto = NO_PHOTO;
  protected readonly ownTaken = OWN_TAKEN;
}

export const BASKETS_GALLERY_SECTIONS: readonly Type<unknown>[] = [BasketsGallerySection];
