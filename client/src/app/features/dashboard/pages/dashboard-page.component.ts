import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Journey, OnboardingService, Role } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { JourneyStepperComponent } from '../../../shared/journey-stepper/journey-stepper.component';
import { currentStepOf } from '../../onboarding/pages/journey-page.component';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { MyNextPickupsCardComponent } from '../../pickups/components/my-next-pickups-card.component';

/** The one thing to do next, by role, until the onboarding journey (M2) takes over this card. */
export interface DashboardNextStep {
  id: 'passkey' | 'profile' | 'activity';
  icon: string;
  link: string;
  query?: Record<string, string>;
}

export function nextStepFor(role: Role | null | undefined): DashboardNextStep {
  switch (role) {
    case Role.Foodsaver:
    case Role.StoreManager:
      return { id: 'profile', icon: 'pi pi-user-edit', link: '/account/profile' };
    case Role.Ambassador:
    case Role.Orga:
      return { id: 'activity', icon: 'pi pi-history', link: '/me/activity' };
    default:
      return {
        id: 'passkey',
        icon: 'pi pi-key',
        link: '/account/settings',
        query: { addPasskey: '1' },
      };
  }
}

/** M1 dashboard: welcome, an actionable next step per role (the onboarding journey replaces it in M2) and quick links. */
@Component({
  selector: 'fs-dashboard-page',
<<<<<<< HEAD
  imports: [...DATA_STATE_IMPORTS, JourneyStepperComponent, RouterLink, TPipe, UserAvatarComponent],
=======
  imports: [RouterLink, TPipe, UserAvatarComponent, MyNextPickupsCardComponent],
>>>>>>> dd427dc
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (session.user(); as user) {
      <section class="welcome" data-testid="dashboard.page.welcome">
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
            <span class="welcome__role" data-testid="dashboard.page.role">{{
              'common.role.' + user.role | t
            }}</span>
            @if (homeRegion(); as region) {
              <span class="welcome__region" data-testid="dashboard.page.homeRegion">
                <i class="pi pi-map-marker" aria-hidden="true"></i>{{ region }}
              </span>
            }
          </div>
          <p class="welcome__hint" data-testid="dashboard.page.roleHint">
            {{ 'dashboard.page.roleHint.' + user.role | t }}
          </p>
        </div>
      </section>

      <div class="grid">
<<<<<<< HEAD
        @if (onboarding()) {
          <fs-data-state [state]="journey" skeleton="card" [skeletonRows]="1" [compact]="true" class="card--next" data-testid="dashboard.page.journeyState">
            <ng-template [fsData]="journey" let-j>
              @let current = currentStep(j);
              <section class="card card--next" data-testid="dashboard.page.nextStep" data-step="journey" [attr.data-journey-step]="current?.key">
                <div class="card__icon"><i class="pi pi-flag" aria-hidden="true"></i></div>
                <p class="card__eyebrow">{{ 'dashboard.page.journey.eyebrow' | t }}</p>
                <h2>{{ current?.title ?? (current ? (current.titleKey ?? 'onboarding.step.' + current.key + '.title') : 'dashboard.page.journey.title') | t }}</h2>
                <fs-journey-stepper mode="compact" [journey]="j" [linkToJourney]="undefined" testId="dashboard.page.journey" />
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
        } @else {
          @let step = nextStep();
          <section
            class="card card--next"
            data-testid="dashboard.page.nextStep"
            [attr.data-step]="step.id"
=======
        <fs-my-next-pickups-card />
        @let step = nextStep();
        <section
          class="card card--next"
          data-testid="dashboard.page.nextStep"
          [attr.data-step]="step.id"
        >
          <div class="card__icon"><i [class]="step.icon" aria-hidden="true"></i></div>
          <p class="card__eyebrow">{{ 'dashboard.page.nextStepTitle' | t }}</p>
          <h2>{{ 'dashboard.page.step.' + step.id + '.title' | t }}</h2>
          <p>{{ 'dashboard.page.step.' + step.id + '.text' | t }}</p>
          <a
            class="fs-btn card__action"
            [routerLink]="step.link"
            [queryParams]="step.query"
            data-testid="dashboard.page.nextStepAction"
>>>>>>> dd427dc
          >
            <div class="card__icon"><i [class]="step.icon" aria-hidden="true"></i></div>
            <p class="card__eyebrow">{{ 'dashboard.page.nextStepTitle' | t }}</p>
            <h2>{{ 'dashboard.page.step.' + step.id + '.title' | t }}</h2>
            <p>{{ 'dashboard.page.step.' + step.id + '.text' | t }}</p>
            <a
              class="fs-btn card__action"
              [routerLink]="step.link"
              [queryParams]="step.query"
              data-testid="dashboard.page.nextStepAction"
            >
              <span>{{ 'dashboard.page.step.' + step.id + '.action' | t }}</span>
              <i class="pi pi-arrow-right" aria-hidden="true"></i>
            </a>
          </section>
        }

        @if (isAdmin()) {
          <section class="card card--admin" data-testid="dashboard.page.adminCard">
            <div class="card__icon card__icon--muted">
              <i class="pi pi-sitemap" aria-hidden="true"></i>
            </div>
            <h2>{{ 'dashboard.page.admin.title' | t }}</h2>
            <p>{{ 'dashboard.page.admin.text' | t }}</p>
          </section>
        }
        <nav class="links" [attr.aria-label]="'dashboard.page.quickLinks' | t">
          <a class="link" routerLink="/account/sessions" data-testid="dashboard.page.sessionsLink">
            <i class="pi pi-desktop" aria-hidden="true"></i>
            <span class="link__text">
              <strong>{{ 'nav.sessions' | t }}</strong>
              <span>{{ 'dashboard.page.sessionsText' | t }}</span>
            </span>
            <i class="pi pi-chevron-right link__chevron" aria-hidden="true"></i>
          </a>
          <a class="link" routerLink="/me/activity" data-testid="dashboard.page.activityLink">
            <i class="pi pi-history" aria-hidden="true"></i>
            <span class="link__text">
              <strong>{{ 'nav.activity' | t }}</strong>
              <span>{{ 'dashboard.page.activityText' | t }}</span>
            </span>
            <i class="pi pi-chevron-right link__chevron" aria-hidden="true"></i>
          </a>
          <a class="link" routerLink="/account/settings" data-testid="dashboard.page.settingsLink">
            <i class="pi pi-cog" aria-hidden="true"></i>
            <span class="link__text">
              <strong>{{ 'nav.settings' | t }}</strong>
              <span>{{ 'dashboard.page.settingsText' | t }}</span>
            </span>
            <i class="pi pi-chevron-right link__chevron" aria-hidden="true"></i>
          </a>
        </nav>
      </div>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 1.75rem;
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
          color-mix(in srgb, #f2b84b 22%, transparent),
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
    .welcome__hint {
      max-width: 34rem;
      margin: 0.35rem 0 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .card--admin {
      border-style: dashed;
      background: var(--fs-surface-muted);
      box-shadow: none;
    }
    .card__icon--muted {
      background: var(--fs-surface);
      color: var(--fs-text-muted);
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
    .grid {
      display: grid;
      gap: 1.25rem;
    }
    /* Desktop: greeting and next step on the left, quick links beside them (no empty half-width hero). */
    @media (min-width: 56rem) {
      :host {
        grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
        grid-template-areas:
          'welcome links'
          'next links'
          'admin links';
        align-items: start;
        column-gap: 1.5rem;
        row-gap: 1.25rem;
      }
      .grid {
        display: contents;
      }
      .welcome {
        grid-area: welcome;
      }
      .card--next {
        grid-area: next;
      }
      .card--admin {
        grid-area: admin;
      }
      .links {
        grid-area: links;
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
      color: #fff;
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
    .card__action {
      justify-self: start;
      margin-top: 0.75rem;
    }
    .links {
      display: grid;
      overflow: hidden;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-sm);
    }
    .link {
      display: flex;
      align-items: center;
      gap: 0.9rem;
      padding: 1rem 1.25rem;
      color: var(--fs-text);
      text-decoration: none;
      transition: background-color 150ms ease;
    }
    .link + .link {
      border-top: 1px solid var(--fs-border);
    }
    .link:hover {
      background: var(--fs-surface-muted);
    }
    .link > .pi:first-child {
      display: grid;
      width: 2.4rem;
      height: 2.4rem;
      place-items: center;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .link__text {
      display: grid;
      flex: 1;
      min-width: 0;
    }
    .link__text span {
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
    .link__chevron {
      color: var(--fs-text-muted);
      font-size: 0.8rem;
    }
  `,
})
export class DashboardPageComponent {
  protected readonly session = inject(SessionService);
  private readonly onboardingApi = inject(OnboardingService);
  protected readonly nextStep = computed(() => nextStepFor(this.session.role()));
  /** Newcomers with an incomplete journey get "your next step" from the journey (§11, A19) instead of the role card. */
  protected readonly onboarding = computed(() => this.session.session().onboarding?.complete === false);
  protected readonly journey = loadResource<Journey, true | undefined>({
    params: () => (this.onboarding() ? true : undefined),
    loader: () => this.onboardingApi.getMyJourney(),
    isEmpty: () => false,
  });
  protected readonly currentStep = currentStepOf;
  /** Name of the home region (session units, M2); hidden until the user has one. */
  protected readonly homeRegion = computed(
    () => this.session.session().units?.find((unit) => unit.homeRegion)?.name ?? null,
  );
  /** Ambassadors and orga: a muted card announcing the admin tools (they arrive with M2's pages). */
  protected readonly isAdmin = computed(() => this.session.hasRole(Role.Ambassador));

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
