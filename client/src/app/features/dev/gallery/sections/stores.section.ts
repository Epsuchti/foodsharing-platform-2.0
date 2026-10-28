import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { CooperationStatus, FreeSlotStatus, TeamSearchStatus, UserTeamStatus } from '../../../../api';
import { AddressFormComponent, addressFormGroup } from '../../../../shared/address-form/address-form.component';
import {
  CooperationStatusBadgeComponent,
  FreeSlotDotComponent,
  MembershipBadgeComponent,
  TeamStatusBadgeComponent,
} from '../../../../shared/store-badges/store-badges.component';

/** M3 store badges (cooperation, team, membership, free slots) and the address form. */
@Component({
  selector: 'fs-gallery-stores-section',
  imports: [
    AddressFormComponent,
    CooperationStatusBadgeComponent,
    FreeSlotDotComponent,
    MembershipBadgeComponent,
    TeamStatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-stores">Stores</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.storeBadges">
        <header class="specimen__head"><code>fs-cooperation-status-badge · fs-team-status-badge · fs-membership-badge</code></header>
        <div style="display: flex; flex-wrap: wrap; gap: 0.5rem">
          @for (status of cooperation; track status) {
            <fs-cooperation-status-badge [status]="status" />
          }
        </div>
        <div style="display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 0.75rem">
          @for (status of team; track status) {
            <fs-team-status-badge [status]="status" />
          }
          @for (status of membership; track status) {
            <fs-membership-badge [status]="status" />
          }
        </div>
      </section>
      <section class="specimen" data-testid="gallery.freeSlotDot">
        <header class="specimen__head"><code>fs-free-slot-dot</code></header>
        <div style="display: grid; gap: 0.5rem">
          @for (status of slots; track status) {
            <fs-free-slot-dot [status]="status" [count]="$index * 2" [showLabel]="true" />
          }
        </div>
      </section>
      <section class="specimen" data-testid="gallery.addressForm">
        <header class="specimen__head"><code>fs-address-form</code></header>
        <fs-address-form [group]="address" testId="gallery.address" />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class StoresGallerySection {
  protected readonly cooperation = Object.values(CooperationStatus);
  protected readonly team = Object.values(TeamSearchStatus);
  protected readonly membership = Object.values(UserTeamStatus);
  protected readonly slots = Object.values(FreeSlotStatus);
  protected readonly address = (() => {
    const group = addressFormGroup();
    group.setValue({
      street: 'Bahnhofbrücke 1',
      postcode: '8001',
      city: 'Zürich',
      country: 'CH',
      latitude: 47.3779,
      longitude: 8.5427,
    });
    return group;
  })();
}

/** Gallery sections of the stores feature (one file per module, no conflicts). */
export const STORES_GALLERY_SECTIONS: readonly Type<unknown>[] = [StoresGallerySection];
