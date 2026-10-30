import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { Resource, ResourceOpenness, Role } from '../../../../api';
import { ResourceCardComponent } from '../../../resources/components/resource-card.component';

const TOOLS = { id: 'c1', name: 'Werkzeug' };
const KITCHEN = { id: 'c2', name: 'Küche' };
const TRANSPORT = { id: 'c3', name: 'Transport' };

const CARGO_BIKE: Resource = {
  id: 'r1',
  name: 'Lastenvelo',
  description: 'Elektro-Lastenvelo mit Kiste, ideal für Grossabholungen. Schlüssel bei mir im Kreis 4 abholen.',
  categories: [TRANSPORT],
  owner: { id: 'u6', displayName: 'Sarah Müller', role: Role.Foodsaver, verified: true },
  private: false,
  openness: ResourceOpenness.AskFirst,
  images: [],
  favourite: true,
  mayEdit: true,
  mayDelete: true,
};
const COOLBOX: Resource = {
  id: 'r7',
  name: 'Kühlbox 40 l',
  description: 'Gehört dem Bezirk. Bitte nach Gebrauch reinigen.',
  categories: [KITCHEN, TRANSPORT],
  private: false,
  openness: ResourceOpenness.FreeToBorrow,
  images: [],
  favourite: false,
  mayEdit: false,
  mayDelete: false,
};
const DRILL: Resource = {
  id: 'r3',
  name: 'Akkubohrmaschine',
  categories: [TOOLS],
  owner: { id: 'u7', displayName: 'Jonas Weber', role: Role.Foodsaver, verified: true },
  private: true,
  openness: ResourceOpenness.GiveAway,
  images: [],
  favourite: false,
  mayEdit: false,
  mayDelete: false,
};

/** Resource cards (plan-m7 Q12, C6): own resource (favourite, edit), commons of the region, private buddy resource. */
@Component({
  selector: 'fs-gallery-resources-section',
  imports: [ResourceCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-resources">Resources</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.resourceCard.own">
        <header class="specimen__head"><code>fs-resource-card (own, favourite)</code></header>
        <fs-resource-card [resource]="cargoBike" />
      </section>
      <section class="specimen" data-testid="gallery.resourceCard.commons">
        <header class="specimen__head"><code>fs-resource-card (commons / private)</code></header>
        <fs-resource-card [resource]="coolbox" />
        <fs-resource-card [resource]="drill" />
      </section>
    </div>
  `,
  styles: `
    .specimen fs-resource-card + fs-resource-card { margin-top: 0.75rem; }
  `,
})
export class ResourcesGallerySection {
  protected readonly cargoBike = CARGO_BIKE;
  protected readonly coolbox = COOLBOX;
  protected readonly drill = DRILL;
}

export const RESOURCES_GALLERY_SECTIONS: readonly Type<unknown>[] = [ResourcesGallerySection];
