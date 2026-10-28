import { ChangeDetectionStrategy, Component } from '@angular/core';

import {
  DEMO_JOURNEY_IN_PROGRESS,
  DEMO_JOURNEY_PREVIEW,
  DEMO_JOURNEY_WAITING,
} from '../../../../shared/journey-stepper/testing/journey-demo';
import { JourneyStepperComponent } from '../../../../shared/journey-stepper/journey-stepper.component';

/** JourneyStepper in its three modes (§11). */
@Component({
  selector: 'fs-gallery-journey-section',
  imports: [JourneyStepperComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-journey">JourneyStepper</h2>
    <section class="specimen" data-testid="gallery.journey.compact">
      <header class="specimen__head"><code>compact (header bar)</code></header>
      <fs-journey-stepper mode="compact" [journey]="inProgress" testId="gallery.journeyCompact" />
    </section>
    <div class="grid">
      <section class="specimen" data-testid="gallery.journey.full">
        <header class="specimen__head"><code>full (in progress, blocked note)</code></header>
        <fs-journey-stepper [journey]="inProgress" testId="gallery.journeyFull" />
      </section>
      <section class="specimen" data-testid="gallery.journey.waiting">
        <header class="specimen__head"><code>full (waiting for others)</code></header>
        <fs-journey-stepper [journey]="waiting" testId="gallery.journeyWaiting" />
      </section>
      <section class="specimen" data-testid="gallery.journey.preview">
        <header class="specimen__head"><code>preview (explainer)</code></header>
        <fs-journey-stepper mode="preview" [preview]="preview" testId="gallery.journeyPreview" />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class JourneyGallerySection {
  protected readonly inProgress = DEMO_JOURNEY_IN_PROGRESS;
  protected readonly waiting = DEMO_JOURNEY_WAITING;
  protected readonly preview = DEMO_JOURNEY_PREVIEW;
}
