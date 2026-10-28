import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { RegionCardComponent } from '../../../onboarding/components/region-card.component';

/** Seed region Zürich Stadt (docs/seed-ids.md) and an id that does not exist (error state). */
const ZUERICH = '00000010-0001-4000-8000-000000000040';
const UNKNOWN = '00000010-0001-4000-8000-0000000fffff';

/** Region card of the journey (§11.1 step 2): live seed region with EinAb dates, and the error state. */
@Component({
  selector: 'fs-gallery-onboarding-section',
  imports: [RegionCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-onboarding">Onboarding</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.regionCard">
        <header class="specimen__head"><code>fs-region-card</code></header>
        <fs-region-card [regionId]="zuerich" testId="gallery.regionCardZh" />
      </section>
      <section class="specimen" data-testid="gallery.regionCardMissing">
        <header class="specimen__head"><code>fs-region-card</code> unknown region</header>
        <fs-region-card [regionId]="unknown" testId="gallery.regionCardUnknown" />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class OnboardingGallerySection {
  protected readonly zuerich = ZUERICH;
  protected readonly unknown = UNKNOWN;
}

/** Gallery sections of the onboarding feature (one file per module, no conflicts). */
export const ONBOARDING_GALLERY_SECTIONS: readonly Type<unknown>[] = [OnboardingGallerySection];
