import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter, skip } from 'rxjs';

import { Journey, OnboardingService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { JourneyStepperComponent } from '../../../shared/journey-stepper/journey-stepper.component';
import { JourneyLoader } from '../onboarding.resource';

/**
 * Journey bar in the header (§11, D11.5.2): newcomers always see which step they are on and the one next action.
 * Desktop: compact stepper with progress segments; mobile: one slim line "Step 4 of 8: Quiz" with the action.
 * Only rendered while the session says the journey is incomplete; reloads after every navigation (a step may have
 * changed) and disappears once the journey is complete.
 */
@Component({
  selector: 'fs-journey-bar',
  imports: [...DATA_STATE_IMPORTS, JourneyStepperComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.hidden]': '!visible()' },
  template: `
    @if (visible()) {
      <div class="bar" data-testid="onboarding.bar">
        <fs-data-state
          [state]="journey"
          skeleton="list"
          [skeletonRows]="1"
          [compact]="true"
          data-testid="onboarding.bar.state"
        >
          <ng-template [fsData]="journey" let-j>
            @if (!j.complete) {
              <fs-journey-stepper
                mode="compact"
                [journey]="j"
                testId="onboarding.bar.stepper"
                [attr.data-current]="j.currentStep"
              />
            }
          </ng-template>
        </fs-data-state>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    :host(.hidden) {
      display: none;
    }
    .bar {
      width: min(100%, 80rem);
      margin: 0 auto;
      padding: 0.4rem 1rem 0.5rem;
      border-top: 1px solid var(--fs-header-border);
    }
    @media (max-width: 47.99rem) {
      .bar {
        padding: 0.3rem 0.75rem;
      }
    }
  `,
})
export class JourneyBarComponent {
  private readonly journeyLoader = inject(JourneyLoader);
  private readonly api = inject(OnboardingService);
  private readonly session = inject(SessionService);

  protected readonly visible = computed(
    () => this.session.isAuthenticated() && this.session.session().onboarding?.complete === false,
  );

  protected readonly journey = loadResource<Journey, true | undefined>({
    params: () => (this.visible() ? true : undefined),
    loader: () => this.journeyLoader.load(),
    isEmpty: () => false,
    keepDataOnError: true,
  });

  constructor() {
    const subscription = inject(Router)
      // the first navigation end is the page the bar was just loaded on: no second request
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        skip(1),
      )
      .subscribe(() => {
        if (this.visible()) {
          this.journey.reload();
        }
      });
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }
}
