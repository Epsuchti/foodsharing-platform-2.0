import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

import {
  Journey,
  JourneyNextAction,
  JourneyStep,
  JourneyStepPreview,
  JourneyStepStatus,
} from '../../api';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';
import { UserChipComponent } from '../user-chip/user-chip.component';

export type JourneyStepperMode = 'compact' | 'full' | 'preview';

const STATUS_ICON: Record<JourneyStepStatus, string> = {
  LOCKED: 'pi pi-lock',
  AVAILABLE: 'pi pi-arrow-right',
  IN_PROGRESS: 'pi pi-play',
  WAITING_FOR_OTHERS: 'pi pi-hourglass',
  DONE: 'pi pi-check',
};

/**
 * JourneyStepper (§11, §12.4): the newcomer journey in three sizes.
 * - `compact`: one line for the header journey bar (progress, current step, next action).
 * - `full`: every step with status, progress, next action, blocked reason and who it is waiting for.
 * - `preview`: the steps of the public explainer page (no status).
 *
 * Step texts: `titleKey` from the server (`onboarding.step.<KEY>.title`) or the step's own `title` (region steps),
 * descriptions `onboarding.step.<KEY>.description`. Next actions are router links and are also emitted (`action`).
 */
@Component({
  selector: 'fs-journey-stepper',
  imports: [RouterLink, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-journey-stepper',
    '[attr.data-mode]': 'mode()',
    '[attr.data-testid]': 'testId()',
  },
  template: `
    @switch (mode()) {
      @case ('compact') {
        @if (journey(); as data) {
          <div class="compact">
            <div
              class="meter"
              role="progressbar"
              aria-valuemin="0"
              [attr.aria-valuemax]="steps().length"
              [attr.aria-valuenow]="doneCount()"
              [attr.aria-label]="'shared.journey.progress' | t: { done: doneCount(), total: steps().length }"
            >
              @for (step of steps(); track step.key) {
                <span class="meter__seg" [attr.data-status]="step.status"></span>
              }
            </div>
            <span class="compact__text">
              @if (data.complete) {
                <strong>{{ 'shared.journey.complete' | t }}</strong>
              } @else if (current(); as step) {
                <span class="compact__count">{{ 'shared.journey.stepOf' | t: { n: currentIndex() + 1, total: steps().length } }}</span>
                @if (!progressOnly()) {
                  <strong>{{ title(step) }}</strong>
                }
              }
            </span>
            @if (progressOnly()) {
              <!-- the host card shows the step title and the action itself -->
            } @else if (current()?.nextAction; as next) {
              <a class="fs-btn fs-btn--sm compact__action" [routerLink]="next.route" (click)="action.emit(next)" [attr.data-testid]="testId() + '.next'">
                {{ next.labelKey | t }}
                <i class="pi pi-arrow-right" aria-hidden="true"></i>
              </a>
            } @else if (linkToJourney()) {
              <a class="compact__link" [routerLink]="linkToJourney()" [attr.data-testid]="testId() + '.open'">
                {{ 'shared.journey.open' | t }}
              </a>
            }
          </div>
        }
      }
      @case ('preview') {
        <ol class="steps steps--preview">
          @for (step of preview(); track step.key; let i = $index) {
            <li class="step" data-status="AVAILABLE" [attr.data-step]="step.key">
              <span class="step__marker" aria-hidden="true">{{ i + 1 }}</span>
              <div class="step__body">
                <h3 class="step__title">{{ step.titleKey | t }}</h3>
                @if (step.descriptionKey) {
                  <p class="step__text">{{ step.descriptionKey | t }}</p>
                }
                <p class="step__meta"><i class="pi pi-clock" aria-hidden="true"></i> {{ durationText(step.estimatedDuration) }}</p>
              </div>
            </li>
          }
        </ol>
      }
      @default {
        <ol class="steps">
          @for (step of steps(); track step.key; let i = $index) {
            <li
              class="step"
              [attr.data-status]="step.status"
              [attr.data-step]="step.key"
              [attr.aria-current]="step.key === current()?.key ? 'step' : null"
              [attr.data-testid]="testId() + '.step.' + step.key"
            >
              <span class="step__marker" aria-hidden="true">
                @if (step.status === 'DONE' || step.status === 'LOCKED' || step.status === 'WAITING_FOR_OTHERS') {
                  <i [class]="icon(step)"></i>
                } @else {
                  {{ i + 1 }}
                }
              </span>
              <div class="step__body">
                <div class="step__head">
                  <h3 class="step__title">{{ title(step) }}</h3>
                  <span class="step__status">{{ 'shared.journey.status.' + step.status | t }}</span>
                  @if (step.optional) {
                    <span class="step__status">{{ 'shared.journey.optional' | t }}</span>
                  }
                </div>
                @if (step.status !== 'LOCKED' && step.status !== 'DONE') {
                  @if (description(step); as text) {
                    <p class="step__text">{{ text }}</p>
                  }
                  @if (step.progress; as p) {
                    <div class="progress" [attr.data-testid]="testId() + '.progress.' + step.key">
                      <div class="progress__bar" role="progressbar" aria-valuemin="0" [attr.aria-valuemax]="p.required" [attr.aria-valuenow]="p.done" [attr.aria-label]="'shared.journey.progressDetail' | t: { done: p.done, booked: p.booked, required: p.required }">
                        <span class="progress__done" [style.width.%]="percent(p.done, p.required)"></span>
                        <span class="progress__booked" [style.width.%]="percent(p.booked, p.required)"></span>
                      </div>
                      <span class="progress__text">{{ 'shared.journey.progressDetail' | t: { done: p.done, booked: p.booked, required: p.required } }}</span>
                    </div>
                  }
                  @if (step.blockedReason) {
                    <p class="step__blocked" role="note">
                      <i class="pi pi-info-circle" aria-hidden="true"></i>
                      {{ 'onboarding.blocked.' + step.blockedReason | t: step.blockedArgs }}
                    </p>
                  }
                  @if (step.waitingFor; as waiting) {
                    <div class="waiting">
                      <p class="waiting__text">
                        <i class="pi pi-hourglass" aria-hidden="true"></i>
                        {{ waiting.labelKey | t }}
                        @if (waiting.typicalDays) {
                          · {{ 'shared.journey.typicalDays' | t: { days: waiting.typicalDays } }}
                        }
                      </p>
                      @if (waiting.contacts.length > 0) {
                        <ul class="waiting__contacts">
                          @for (contact of waiting.contacts; track contact.id) {
                            <li><fs-user-chip [name]="contact.displayName" [userId]="contact.id" [avatarUrl]="contact.avatarUrl" [role]="contact.role" [showRole]="true" size="sm" /></li>
                          }
                        </ul>
                      }
                    </div>
                  }
                  <div class="step__foot">
                    @if (step.nextAction; as next) {
                      <a class="fs-btn fs-btn--sm" [routerLink]="next.route" (click)="action.emit(next)" [attr.data-testid]="testId() + '.action.' + step.key">
                        {{ next.labelKey | t }}
                        <i class="pi pi-arrow-right" aria-hidden="true"></i>
                      </a>
                    }
                    @if (step.estimatedDuration) {
                      <span class="step__meta"><i class="pi pi-clock" aria-hidden="true"></i> {{ durationText(step.estimatedDuration) }}</span>
                    }
                  </div>
                }
              </div>
            </li>
          }
        </ol>
      }
    }
  `,
  styleUrl: './journey-stepper.component.scss',
})
export class JourneyStepperComponent {
  private readonly i18n = inject(I18nService);

  readonly mode = input<JourneyStepperMode>('full');
  readonly journey = input<Journey | null | undefined>(undefined);
  /** Steps of the public explainer (`mode="preview"`). */
  readonly preview = input<readonly JourneyStepPreview[]>([]);
  /** Compact mode without a next action links here (the onboarding page). */
  readonly linkToJourney = input<string | undefined>('/onboarding');
  readonly testId = input('shared.journey');
  /**
   * Compact mode: only the progress meter and "step n of m" (no title, no action), for cards that show the current
   * step's title and action themselves (dashboard; design review M7 R1: the action appeared twice in one card).
   */
  readonly progressOnly = input(false);
  readonly action = output<JourneyNextAction>();

  protected readonly steps = computed<JourneyStep[]>(() => [
    ...(this.journey()?.steps ?? []),
    ...(this.journey()?.extraSteps ?? []),
  ]);
  protected readonly current = computed(
    () =>
      this.steps().find((step) => step.key === this.journey()?.currentStep) ??
      this.steps().find((step) => step.status !== 'DONE' && step.status !== 'LOCKED') ??
      null,
  );
  protected readonly currentIndex = computed(() =>
    Math.max(0, this.steps().findIndex((step) => step.key === this.current()?.key)),
  );
  protected readonly doneCount = computed(
    () => this.steps().filter((step) => step.status === 'DONE').length,
  );

  protected icon(step: JourneyStep): string {
    return STATUS_ICON[step.status];
  }

  protected title(step: JourneyStep): string {
    this.i18n.language();
    if (step.title) {
      return step.title;
    }
    return this.i18n.t(step.titleKey ?? `onboarding.step.${step.key}.title`);
  }

  protected description(step: JourneyStep): string | null {
    const key = `onboarding.step.${step.key}.description`;
    return this.i18n.has(key) ? this.i18n.t(key) : null;
  }

  protected percent(value: number, total: number): number {
    return total > 0 ? Math.min(100, Math.round((value / total) * 100)) : 0;
  }

  /** ISO-8601 durations (`PT15M`, `P2D`, `P1W`) in words; other strings as they are. */
  protected durationText(duration: string): string {
    const match = /^P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?$/.exec(duration ?? '');
    if (!match) {
      return duration;
    }
    const [, weeks, days, hours, minutes] = match;
    if (weeks) return this.i18n.t('shared.journey.duration.weeks', { count: Number(weeks) });
    if (days) return this.i18n.t('shared.journey.duration.days', { count: Number(days) });
    if (hours) return this.i18n.t('shared.journey.duration.hours', { count: Number(hours) });
    return this.i18n.t('shared.journey.duration.minutes', { count: Number(minutes ?? 0) });
  }
}
