import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SessionService } from '../../../core/auth/session.service';
import { TPipe } from '../../../core/i18n/t.pipe';

/** Primary action of an error page; default: home (dashboard when logged in). */
export interface ErrorPageAction {
  labelKey: string;
  icon: string;
  link: string;
  queryParams?: Record<string, string>;
}

/**
 * Centered full-page message used by the forbidden and not-found pages, always with the same two buttons:
 * primary (home, or e.g. login) and secondary "back". Test ids: `<testId>.primary`, `<testId>.back`.
 */
@Component({
  selector: 'fs-error-page-layout',
  imports: [RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ep">
      <div class="ep__art" aria-hidden="true">
        <span class="ep__code">{{ code() }}</span>
        <span class="ep__icon"><i [class]="icon()"></i></span>
      </div>
      <h1 class="ep__title">{{ title() }}</h1>
      <p class="ep__text">{{ text() }}</p>
      <div class="ep__actions">
        <a
          class="fs-btn"
          [routerLink]="primaryAction().link"
          [queryParams]="primaryAction().queryParams"
          [attr.data-testid]="testId() + '.primary'"
        >
          <i [class]="primaryAction().icon" aria-hidden="true"></i>
          <span>{{ primaryAction().labelKey | t }}</span>
        </a>
        <button
          type="button"
          class="fs-btn fs-btn--outlined"
          (click)="location.back()"
          [attr.data-testid]="testId() + '.back'"
        >
          <i class="pi pi-arrow-left" aria-hidden="true"></i>
          <span>{{ 'errorPages.back' | t }}</span>
        </button>
      </div>
    </div>
  `,
  styles: `
    .ep {
      display: grid;
      justify-items: center;
      gap: 0.9rem;
      max-width: 34rem;
      margin: clamp(1rem, 6vh, 4rem) auto;
      text-align: center;
    }
    .ep__art {
      position: relative;
      display: grid;
      place-items: center;
      margin-bottom: 0.5rem;
    }
    .ep__code {
      color: var(--fs-primary-soft);
      font-size: clamp(5rem, 4rem + 6vw, 8rem);
      font-weight: 900;
      letter-spacing: -0.05em;
      line-height: 1;
    }
    .ep__icon {
      position: absolute;
      display: grid;
      width: 4rem;
      height: 4rem;
      place-items: center;
      border-radius: 50%;
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-md);
      color: var(--fs-primary-strong);
      font-size: 1.6rem;
    }
    .ep__title {
      margin: 0;
      font-size: clamp(1.5rem, 1.2rem + 1.2vw, 2rem);
      font-weight: 800;
      letter-spacing: -0.025em;
    }
    .ep__text {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 1.05rem;
    }
    .ep__actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 0.75rem;
      margin-top: 0.75rem;
    }
  `,
})
export class ErrorPageLayoutComponent {
  readonly code = input.required<string>();
  readonly icon = input.required<string>();
  readonly title = input.required<string>();
  readonly text = input.required<string>();
  readonly testId = input.required<string>();
  /** Overrides the home button (e.g. "log in" for anonymous visitors of a protected page). */
  readonly primary = input<ErrorPageAction | undefined>(undefined);

  private readonly session = inject(SessionService);
  protected readonly location = inject(Location);
  protected readonly primaryAction = computed<ErrorPageAction>(
    () =>
      this.primary() ?? {
        labelKey: 'errorPages.home',
        icon: 'pi pi-home',
        link: this.session.isAuthenticated() ? '/dashboard' : '/',
      },
  );
}
