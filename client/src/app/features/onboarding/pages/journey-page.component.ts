import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Journey, JourneyStep, JourneyStepKey, OnboardingService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { JourneyStepperComponent } from '../../../shared/journey-stepper/journey-stepper.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { JourneyLoader } from '../onboarding.resource';

/** The step the journey is on (main steps and region extra steps). */
export function currentStepOf(journey: Journey): JourneyStep | undefined {
  const steps = [...journey.steps, ...journey.extraSteps];
  return steps.find((step) => step.key === journey.currentStep) ?? steps.find((step) => step.status !== 'DONE');
}

/**
 * `/onboarding` (§11.1, D11.5.2): where the newcomer stands and the one next action. Big call to action for the
 * current step (or who we are waiting for), the EinAb meter, every step with status (`fs-journey-stepper` full)
 * and the further tracks (hygiene, store manager, guide, ambassador).
 */
@Component({
  selector: 'fs-journey-page',
  imports: [...DATA_STATE_IMPORTS, JourneyStepperComponent, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'onboarding.journey.title' | t" [subtitle]="'onboarding.journey.subtitle' | t" icon="pi pi-flag" />

    <fs-data-state [state]="journey" skeleton="card" [skeletonRows]="3" data-testid="onboarding.journey.state">
      <ng-template [fsData]="journey" let-j>
        <div class="stack">
        @if (j.complete) {
          <section class="fs-card card hero hero--done" data-testid="onboarding.journey.complete">
            <h2 class="hero__title"><i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'onboarding.journey.completeTitle' | t }}</h2>
            <p>{{ 'onboarding.journey.completeText' | t }}</p>
            <div class="actions">
              <a routerLink="/dashboard" class="fs-btn" data-testid="onboarding.journey.toDashboard">{{ 'onboarding.journey.toDashboard' | t }}</a>
            </div>
          </section>
        } @else if (current(j); as step) {
          <section class="fs-card card hero" data-testid="onboarding.journey.current" [attr.data-step]="step.key" [attr.data-status]="step.status">
            <p class="hero__eyebrow">{{ 'onboarding.journey.stepOf' | t: { n: indexOf(j, step) + 1, total: total(j) } }}</p>
            <h2 class="hero__title">{{ titleOf(step) }}</h2>
            @if (step.nextAction; as next) {
              <div class="actions">
                <a class="fs-btn fs-btn--lg" [routerLink]="next.route" data-testid="onboarding.journey.cta">
                  {{ next.labelKey | t }}<i class="pi pi-arrow-right" aria-hidden="true"></i>
                </a>
              </div>
            } @else if (step.waitingFor; as waiting) {
              <p class="waiting" data-testid="onboarding.journey.waiting"><i class="pi pi-hourglass" aria-hidden="true"></i>{{ waiting.labelKey | t }}</p>
            }
          </section>
        }

        @if (introProgress(j); as p) {
          <section class="fs-card card" data-testid="onboarding.journey.introMeter" [attr.data-done]="p.done" [attr.data-required]="p.required">
            <h2 class="card__title"><i class="pi pi-calendar" aria-hidden="true"></i>{{ 'onboarding.journey.introTitle' | t }}</h2>
            <div class="meter" role="meter" aria-valuemin="0" [attr.aria-valuemax]="p.required" [attr.aria-valuenow]="p.done" [attr.aria-label]="'onboarding.journey.introTitle' | t">
              @for (seg of segments(p.required); track $index) {
                <span class="meter__seg" [class.meter__seg--done]="$index < p.done" [class.meter__seg--booked]="$index >= p.done && $index < p.done + p.booked"></span>
              }
            </div>
            <ul class="legend">
              <li><span class="dot dot--done"></span>{{ 'onboarding.journey.introDone' | t: { count: p.done } }}</li>
              <li><span class="dot dot--booked"></span>{{ 'onboarding.journey.introBooked' | t: { count: p.booked } }}</li>
              <li><span class="dot"></span>{{ 'onboarding.journey.introOpen' | t: { count: open(p) } }}</li>
            </ul>
          </section>
        }

        <h2 class="section-title"><i class="pi pi-list-check" aria-hidden="true"></i>{{ 'onboarding.journey.allSteps' | t }}</h2>
        <fs-journey-stepper mode="full" [journey]="j" [omitActionFor]="current(j)?.key" testId="onboarding.journey.stepper" />

        @if (j.tracks?.length) {
          <h2 class="section-title"><i class="pi pi-compass" aria-hidden="true"></i>{{ 'onboarding.journey.tracks' | t }}</h2>
          <p class="muted">{{ 'onboarding.journey.tracksText' | t }}</p>
          <ul class="grid">
            @for (track of j.tracks; track track.key) {
              <li class="fs-card card" data-testid="onboarding.journey.track" [attr.data-track]="track.key">
                <ul class="track">
                  @for (s of track.steps; track s.key) {
                    <li [attr.data-status]="s.status">
                      <span>{{ titleOf(s) }}</span>
                      <span class="tag" [class.tag--ok]="s.status === 'DONE'">{{ 'shared.journey.status.' + s.status | t }}</span>
                      @if (s.nextAction; as next) {
                        <a [routerLink]="next.route" class="track__link">{{ next.labelKey | t }}</a>
                      }
                    </li>
                  }
                </ul>
              </li>
            }
          </ul>
        }
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../onboarding.scss',
  styles: `
    .hero {
      border-left: 4px solid var(--fs-primary);
    }
    .hero--done {
      border-left-color: var(--fs-primary-strong);
    }
    .hero__eyebrow {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
      font-weight: 650;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .hero__title {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      font-size: 1.5rem;
    }
    .waiting {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
    }
    .meter {
      display: flex;
      gap: 0.35rem;
    }
    .meter__seg {
      flex: 1;
      height: 0.7rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      box-shadow: inset 0 0 0 1px var(--fs-border);
    }
    .meter__seg--done,
    .dot--done {
      background: var(--fs-primary);
      box-shadow: none;
    }
    .meter__seg--booked,
    .dot--booked {
      background: var(--fs-primary-soft);
      box-shadow: inset 0 0 0 1px var(--fs-primary);
    }
    .legend {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem 1rem;
      margin: 0;
      padding: 0;
      list-style: none;
      font-size: 0.9rem;
    }
    .legend li {
      display: flex;
      align-items: center;
      gap: 0.35rem;
    }
    .dot {
      width: 0.75rem;
      height: 0.75rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      box-shadow: inset 0 0 0 1px var(--fs-border);
    }
    .track {
      display: grid;
      gap: 0.4rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .track li {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem 0.6rem;
    }
    .track__link {
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
  `,
})
export class JourneyPageComponent {
  private readonly journeyLoader = inject(JourneyLoader);
  private readonly api = inject(OnboardingService);
  private readonly i18n = inject(I18nService);

  protected readonly journey = loadResource<Journey>({
    loader: () => this.journeyLoader.load(),
    isEmpty: () => false,
  });

  protected readonly current = currentStepOf;
  protected readonly language = computed(() => this.i18n.language());

  protected total(journey: Journey): number {
    return journey.steps.length + journey.extraSteps.length;
  }

  protected indexOf(journey: Journey, step: JourneyStep): number {
    return [...journey.steps, ...journey.extraSteps].findIndex((candidate) => candidate.key === step.key);
  }

  protected titleOf(step: JourneyStep): string {
    this.language();
    return step.title ?? this.i18n.t(step.titleKey ?? `onboarding.step.${step.key}.title`);
  }

  protected introProgress(journey: Journey) {
    return journey.steps.find((step) => step.key === JourneyStepKey.IntroPickups)?.progress;
  }

  protected segments(required: number): number[] {
    return Array.from({ length: Math.max(required, 1) });
  }

  protected open(progress: { done: number; booked: number; required: number }): number {
    return Math.max(progress.required - progress.done - progress.booked, 0);
  }
}
