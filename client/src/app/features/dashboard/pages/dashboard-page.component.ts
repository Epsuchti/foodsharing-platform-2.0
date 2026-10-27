import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';

/** M1 dashboard: welcome, "next step" placeholder (the onboarding journey arrives in M2) and quick links. */
@Component({
  selector: 'fs-dashboard-page',
  imports: [RouterLink, TPipe, UserAvatarComponent],
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
          <span class="welcome__role">{{ 'common.role.' + user.role | t }}</span>
        </div>
      </section>

      <div class="grid">
        <section class="card card--next" data-testid="dashboard.page.nextStep">
          <div class="card__icon"><i class="pi pi-compass" aria-hidden="true"></i></div>
          <h2>{{ 'dashboard.page.nextStepTitle' | t }}</h2>
          <p>{{ 'dashboard.page.nextStepText' | t }}</p>
          <span class="card__badge">{{ 'dashboard.page.comingSoon' | t }}</span>
        </section>

        <nav class="links" [attr.aria-label]="'dashboard.page.quickLinks' | t">
          <a class="link" routerLink="/account/sessions" data-testid="dashboard.page.sessionsLink">
            <i class="pi pi-desktop" aria-hidden="true"></i>
            <span class="link__text">
              <strong>{{ 'dashboard.page.sessionsTitle' | t }}</strong>
              <span>{{ 'dashboard.page.sessionsText' | t }}</span>
            </span>
            <i class="pi pi-chevron-right link__chevron" aria-hidden="true"></i>
          </a>
          <a class="link" routerLink="/me/activity" data-testid="dashboard.page.activityLink">
            <i class="pi pi-history" aria-hidden="true"></i>
            <span class="link__text">
              <strong>{{ 'dashboard.page.activityTitle' | t }}</strong>
              <span>{{ 'dashboard.page.activityText' | t }}</span>
            </span>
            <i class="pi pi-chevron-right link__chevron" aria-hidden="true"></i>
          </a>
          <a class="link" routerLink="/account/settings" data-testid="dashboard.page.settingsLink">
            <i class="pi pi-cog" aria-hidden="true"></i>
            <span class="link__text">
              <strong>{{ 'dashboard.page.settingsTitle' | t }}</strong>
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
    .welcome__role {
      justify-self: start;
      margin-top: 0.35rem;
      padding: 0.2rem 0.65rem;
      border-radius: 999px;
      background: var(--fs-surface);
      color: var(--fs-primary-strong);
      font-size: 0.8rem;
      font-weight: 700;
    }
    .grid {
      display: grid;
      gap: 1.25rem;
    }
    @media (min-width: 56rem) {
      .grid {
        grid-template-columns: 1.2fr 1fr;
        align-items: start;
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
    .card__badge {
      position: absolute;
      top: 1.25rem;
      right: 1.25rem;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
      font-size: 0.75rem;
      font-weight: 700;
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
