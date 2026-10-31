import { ChangeDetectionStrategy, Component, DestroyRef, inject, input } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter, skip } from 'rxjs';

import { Journey, JourneyStepStatus } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { JourneyLoader } from '../onboarding.resource';

export interface JourneyProgress {
  /** 1-based number of the current step (the first step that is not done). */
  current: number;
  total: number;
  /** Share of done steps, 0–1. */
  done: number;
}

/** Progress of the main journey steps (region extra steps are not counted, like the journey bar's segments). */
export function journeyProgress(journey: Pick<Journey, 'steps' | 'currentStep'>): JourneyProgress {
  const total = journey.steps.length;
  const doneCount = journey.steps.filter((step) => step.status === JourneyStepStatus.Done).length;
  const currentIndex = journey.steps.findIndex((step) => step.key === journey.currentStep);
  const current = currentIndex >= 0 ? currentIndex + 1 : Math.min(total, doneCount + 1);
  return { current, total, done: total ? doneCount / total : 0 };
}

/**
 * Journey progress in the desktop side menu (decision 133: the meter on "Mein Einstieg" replaces the journey bar
 * above the content): "3/6" at the end of the item and a 3px meter under its label. A sibling of the item's link
 * (its retry button must not sit inside the link); the link points at the hidden "Step 3 of 6" text with
 * aria-describedby. After each navigation the journey is fetched again in the background (a step may have changed).
 */
@Component({
  selector: 'fs-journey-meter',
  imports: [...DATA_STATE_IMPORTS, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'journey-meter' },
  template: `
    <fs-data-state [state]="journey" [compact]="true" data-testid="nav.onboarding.state">
      <ng-template fsSkeleton>
        <span class="sn-meter" aria-hidden="true"></span>
      </ng-template>
      <ng-template fsEmpty></ng-template>
      <ng-template fsError let-retry="retry">
        <button
          type="button"
          class="jm__retry"
          [attr.aria-label]="'onboarding.meter.retry' | t"
          (click)="retry()"
          data-testid="nav.onboarding.retry"
        >
          <i class="pi pi-refresh" aria-hidden="true"></i>
        </button>
      </ng-template>
      <ng-template [fsData]="journey" let-j>
        @if (!j.complete) {
          @let p = progress(j);
          <span class="sn-link__count jm__count" aria-hidden="true"
            >{{ p.current }}/{{ p.total }}</span
          >
          <span class="fs-visually-hidden" [id]="describedById()">{{
            'onboarding.meter.progress' | t: { current: p.current, total: p.total }
          }}</span>
          <span
            class="sn-meter"
            aria-hidden="true"
            data-testid="nav.onboarding.progress"
            [attr.data-current]="j.currentStep"
            [attr.data-step]="p.current"
            [attr.data-total]="p.total"
            ><span [style.width.%]="p.done * 100"></span
          ></span>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      position: absolute;
      inset: 0;
      pointer-events: none;
    }
    /* the meter and the count are positioned in the item's box */
    fs-data-state {
      height: 100%;
    }
    .jm__count {
      position: absolute;
      top: 50%;
      right: 0.75rem;
      transform: translateY(-60%);
    }
    .jm__retry {
      position: absolute;
      top: 50%;
      right: 0.25rem;
      display: grid;
      place-items: center;
      width: 1.75rem;
      height: 1.75rem;
      transform: translateY(-50%);
      border: 0;
      border-radius: var(--fs-radius-sm);
      background: transparent;
      color: var(--fs-text-muted);
      cursor: pointer;
      pointer-events: auto;
    }
  `,
})
export class JourneyMeterComponent {
  private readonly loader = inject(JourneyLoader);
  /** Id of the hidden progress text (the item link's aria-describedby). */
  readonly describedById = input('sn-journey-progress');

  protected readonly journey = loadResource<Journey, true>({
    params: () => true,
    loader: () => this.loader.load(),
    isEmpty: () => false,
    keepDataOnError: true,
  });

  protected readonly progress = journeyProgress;

  constructor() {
    const subscription = inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        // the first navigation end is the page the meter was loaded on: no second request
        skip(1),
      )
      .subscribe(() => {
        // a quiet update: the side menu shows no "refreshing" line; a failed update keeps the last progress
        if (this.journey.data()) {
          this.loader
            .load()
            .subscribe({ next: (value) => this.journey.set(value), error: () => undefined });
        }
      });
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }
}
