import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Journey, Role, SessionUser } from '../../../api';
import { ROLE_ORDER, SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { JourneyStepperComponent } from '../../../shared/journey-stepper/journey-stepper.component';
import { currentStepOf } from '../../onboarding/pages/journey-page.component';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { UpcomingEventsCardComponent } from '../../events/components/upcoming-events-card.component';
import { HelpNeededCardComponent } from '../../pickups/components/help-needed-card.component';
import { MyNextPickupsCardComponent } from '../../pickups/components/my-next-pickups-card.component';
import { PushOptInCardComponent } from '../../notifications/components/push-opt-in-card.component';
import { JourneyLoader } from '../../onboarding/onboarding.resource';
import { BounceBannerComponent } from '../../account/components/bounce-banner.component';
import { ActivityFeedCardComponent } from '../components/activity-feed-card.component';
import { NearbyBasketsCardComponent } from '../components/nearby-baskets-card.component';
import { WaitingCardComponent } from '../components/waiting-card.component';
import { YourStoresCardComponent } from '../components/your-stores-card.component';

/** The one thing still missing (owner feedback 2026-11-04: only when something is missing). */
export interface DashboardNextStep {
  id: 'passkey' | 'profile';
  icon: string;
  link: string;
  query?: Record<string, string>;
}

/**
 * Foodsharers: add a passkey. Foodsavers and up: complete the profile, but only while the avatar or the phone
 * number is missing; otherwise there is no next step and no card.
 */
export function nextStepFor(user: Pick<SessionUser, 'role' | 'avatarUrl' | 'phone'> | null | undefined): DashboardNextStep | null {
  if (!user || ROLE_ORDER.indexOf(user.role) < ROLE_ORDER.indexOf(Role.Foodsaver)) {
    return { id: 'passkey', icon: 'pi pi-key', link: '/account/settings', query: { addPasskey: '1' } };
  }
  if (!user.avatarUrl || !user.phone) {
    return { id: 'profile', icon: 'pi pi-user-edit', link: '/account/profile' };
  }
  return null;
}

/**
 * Home (A19, plan-m7 Q7, owner feedback 2026-11-04, docs/design/ui-polish-batch-proposal.md §3): welcome with role and
 * region, the next step only when something is missing (or the journey's step for newcomers), my next pickups, help
 * needed soon and the activity feed on the left; waiting for you, upcoming events, your stores and nearby baskets on
 * the right. On phones one column in the order of the proposal (§6). Cards below the fold load when they come into
 * view (decision 84 start-up budget).
 */
@Component({
  selector: 'fs-dashboard-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    ActivityFeedCardComponent,
    BounceBannerComponent,
    HelpNeededCardComponent,
    JourneyStepperComponent,
    MyNextPickupsCardComponent,
    NearbyBasketsCardComponent,
    PushOptInCardComponent,
    RouterLink,
    TPipe,
    UpcomingEventsCardComponent,
    UserAvatarComponent,
    WaitingCardComponent,
    YourStoresCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (session.user(); as user) {
      <div class="main">
        <section class="welcome o-welcome" data-testid="dashboard.page.welcome">
          <fs-user-avatar
            [name]="user.displayName"
            [avatarUrl]="user.avatarUrl"
            [role]="user.role"
            [colorSeed]="user.id"
            size="xl"
          />
          <div class="welcome__text">
            <p class="welcome__greeting">{{ greetingKey() | t }}</p>
            <h1 class="welcome__name" data-testid="dashboard.page.name">{{ user.displayName }}</h1>
            <div class="welcome__chips">
              <span class="welcome__role" data-testid="dashboard.page.role">{{ 'common.role.' + user.role | t }}</span>
              @if (homeRegion(); as region) {
                <span class="welcome__region" data-testid="dashboard.page.homeRegion">
                  <i class="pi pi-map-marker" aria-hidden="true"></i>{{ region }}
                </span>
              }
            </div>
            <!-- A18: mails to the own address bounce (hidden otherwise) -->
            <fs-bounce-banner class="welcome__bounce" />
          </div>
        </section>

        @defer (on idle) {
          <fs-push-opt-in-card class="o-push" />
        }
        @if (onboarding()) {
          <fs-data-state [state]="journey" skeleton="card" [skeletonRows]="1" [compact]="true" class="card--next o-next" data-testid="dashboard.page.journeyState">
            <ng-template [fsData]="journey" let-j>
              @let current = currentStep(j);
              <section class="card card--next" data-testid="dashboard.page.nextStep" data-step="journey" [attr.data-journey-step]="current?.key">
                <div class="card__icon"><i class="pi pi-flag" aria-hidden="true"></i></div>
                <p class="card__eyebrow">{{ 'dashboard.page.journey.eyebrow' | t }}</p>
                <h2>{{ current?.title ?? (current ? (current.titleKey ?? 'onboarding.step.' + current.key + '.title') : 'dashboard.page.journey.title') | t }}</h2>
                <fs-journey-stepper mode="compact" [journey]="j" [linkToJourney]="undefined" [progressOnly]="true" testId="dashboard.page.journey" />
                @if (current?.nextAction; as next) {
                  <a class="fs-btn card__action" [routerLink]="next.route" data-testid="dashboard.page.nextStepAction">
                    <span>{{ next.labelKey | t }}</span>
                    <i class="pi pi-arrow-right" aria-hidden="true"></i>
                  </a>
                }
                <a class="card__more" routerLink="/onboarding" data-testid="dashboard.page.journeyLink">{{ 'dashboard.page.journey.open' | t }}</a>
              </section>
            </ng-template>
          </fs-data-state>
        } @else if (nextStep(); as step) {
          <section class="card card--next o-next" data-testid="dashboard.page.nextStep" [attr.data-step]="step.id">
            <div class="card__icon"><i [class]="step.icon" aria-hidden="true"></i></div>
            <p class="card__eyebrow">{{ 'dashboard.page.nextStepTitle' | t }}</p>
            <h2>{{ 'dashboard.page.step.' + step.id + '.title' | t }}</h2>
            <p>{{ 'dashboard.page.step.' + step.id + '.text' | t }}</p>
            <a class="fs-btn card__action" [routerLink]="step.link" [queryParams]="step.query" data-testid="dashboard.page.nextStepAction">
              <span>{{ 'dashboard.page.step.' + step.id + '.action' | t }}</span>
              <i class="pi pi-arrow-right" aria-hidden="true"></i>
            </a>
          </section>
        }
        <fs-my-next-pickups-card class="o-pickups" />
        @defer (on viewport) {
          <fs-help-needed-card class="o-help" />
        } @placeholder {
          <div class="defer-slot o-help" aria-hidden="true"></div>
        }
        <!-- A19: activity of my forums, store walls, mailboxes, events, FSPs and buddies -->
        <fs-activity-feed-card class="o-activity" />
      </div>
      <div class="side">
        <fs-waiting-card class="o-waiting" />
        @defer (on viewport) {
          <fs-upcoming-events-card class="o-events" />
        } @placeholder {
          <div class="defer-slot o-events" aria-hidden="true"></div>
        }
        @defer (on viewport) {
          <fs-your-stores-card class="o-stores" />
        } @placeholder {
          <div class="defer-slot o-stores" aria-hidden="true"></div>
        }
        <fs-nearby-baskets-card class="o-baskets" />
      </div>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 1.25rem;
    }
    .welcome {
      display: flex;
      align-items: center;
      gap: 1.25rem;
      padding: clamp(1.25rem, 1rem + 1.5vw, 2rem);
      border-radius: var(--fs-radius-xl);
      background:
        radial-gradient(
          circle at 100% 0%,
          color-mix(in srgb, var(--fs-accent) 22%, transparent),
          transparent 55%
        ),
        linear-gradient(135deg, var(--fs-primary-soft), var(--fs-surface));
      border: 1px solid var(--fs-border);
    }
    .welcome__text {
      display: grid;
      min-width: 0;
      gap: 0.15rem;
    }
    .welcome__bounce {
      margin-top: 0.75rem;
    }
    .welcome__greeting {
      margin: 0;
      color: var(--fs-text-muted);
      font-weight: 600;
    }
    .welcome__name {
      overflow: hidden;
      margin: 0;
      font-size: clamp(1.6rem, 1.2rem + 1.6vw, 2.3rem);
      font-weight: 800;
      letter-spacing: -0.03em;
      text-overflow: ellipsis;
    }
    .welcome__chips {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem 0.5rem;
      margin-top: 0.35rem;
    }
    .welcome__region {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.2rem 0.65rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      color: var(--fs-text);
      font-size: 0.8rem;
      font-weight: 650;
    }
    .welcome__region .pi {
      color: var(--fs-primary-strong);
      font-size: 0.75rem;
    }
    .welcome__role {
      justify-self: start;
      padding: 0.2rem 0.65rem;
      border: 1px solid color-mix(in srgb, var(--fs-primary) 30%, transparent);
      border-radius: 999px;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 0.8rem;
      font-weight: 700;
    }
    .main,
    .side {
      display: grid;
      gap: 1.25rem;
      min-width: 0;
      align-content: start;
    }
    /* cards that render nothing for this user (push opt-in, empty home cards, pickups of newcomers) leave no gap */
    .main > :empty:not(.defer-slot),
    .side > :empty:not(.defer-slot) {
      display: none;
    }
    /* placeholder of a card that loads on viewport: 1px to be observed, its grid gap taken back */
    .defer-slot {
      height: 1px;
      margin-top: -1.25rem;
    }
    /* Phones: one column in the proposal's order (§6); the feed, open-ended, comes last. */
    @media (max-width: 55.99rem) {
      .main,
      .side {
        display: contents;
      }
      .o-welcome { order: 1; }
      .o-push { order: 2; }
      .o-next { order: 3; }
      .o-waiting { order: 4; }
      .o-pickups { order: 5; }
      .o-help { order: 6; }
      .o-events { order: 7; }
      .o-stores { order: 8; }
      .o-baskets { order: 9; }
      .o-activity { order: 10; }
    }
    /* Desktop: two columns (left wider). */
    @media (min-width: 56rem) {
      :host {
        grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
        align-items: start;
        column-gap: 1.5rem;
      }
    }
    .card {
      position: relative;
      display: grid;
      gap: 0.5rem;
      padding: 1.5rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-sm);
    }
    .card h2 {
      margin: 0.5rem 0 0;
      font-size: 1.2rem;
    }
    .card p {
      margin: 0;
      color: var(--fs-text-muted);
    }
    .card__icon {
      display: grid;
      width: 3rem;
      height: 3rem;
      place-items: center;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary);
      color: var(--fs-on-primary);
      font-size: 1.3rem;
    }
    .card__eyebrow {
      margin: 0.5rem 0 -0.35rem;
      color: var(--fs-primary-strong);
      font-size: 0.78rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .card .card__eyebrow {
      color: var(--fs-primary-strong);
    }
    .card__more {
      color: var(--fs-primary-strong);
      font-weight: 650;
      font-size: 0.9rem;
    }
    @media (pointer: coarse) {
      .card__more {
        display: inline-flex;
        align-items: center;
        justify-self: start;
        min-height: 2.75rem;
      }
    }
    .card__action {
      justify-self: start;
      margin-top: 0.75rem;
    }
  `,
})
export class DashboardPageComponent {
  private readonly journeyLoader = inject(JourneyLoader);
  protected readonly session = inject(SessionService);
  protected readonly nextStep = computed(() => nextStepFor(this.session.user()));
  /** Newcomers with an incomplete journey get "your next step" from the journey (§11, A19) instead of the role card. */
  protected readonly onboarding = computed(() => this.session.session().onboarding?.complete === false);
  protected readonly journey = loadResource<Journey, true | undefined>({
    params: () => (this.onboarding() ? true : undefined),
    loader: () => this.journeyLoader.load(),
    isEmpty: () => false,
  });
  protected readonly currentStep = currentStepOf;
  /** Name of the home region (session units, M2); hidden until the user has one. */
  protected readonly homeRegion = computed(
    () => this.session.session().units?.find((unit) => unit.homeRegion)?.name ?? null,
  );

  protected readonly greetingKey = computed(() => {
    const hour = Number(
      new Intl.DateTimeFormat('en-GB', {
        hour: 'numeric',
        hour12: false,
        timeZone: 'Europe/Zurich',
      }).format(new Date()),
    );
    if (hour < 11) {
      return 'dashboard.page.greetingMorning';
    }
    return hour < 18 ? 'dashboard.page.greetingDay' : 'dashboard.page.greetingEvening';
  });
}
