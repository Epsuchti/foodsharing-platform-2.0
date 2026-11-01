import { ChangeDetectionStrategy, Component } from '@angular/core';

import { NavigateButtonComponent } from '../../../../shared/navigate-button/navigate-button.component';
import { navigationLinks } from '../../../../shared/navigate-button/navigate.util';

/** "Navigate" dropdown (resume item 15): plain, and with the approximate-location note of HOME baskets. */
@Component({
  selector: 'fs-gallery-navigate-section',
  imports: [NavigateButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-navigate">Navigate button</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.navigate.states">
        <header class="specimen__head"><code>plain / small / with note</code></header>
        <div class="stack">
          <fs-navigate-button [links]="links" testId="gallery.navigate" />
          <fs-navigate-button [links]="links" testId="gallery.navigateSmall" [small]="true" />
          <fs-navigate-button [links]="links" testId="gallery.navigateNote" note="Ungefährer Standort, auf etwa 100 m genau" />
        </div>
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class NavigateGallerySection {
  protected readonly links = navigationLinks({ lat: 47.3779, lon: 8.5427, label: 'Migros City' })!;
}
