import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, map, switchMap, tap, throwError } from 'rxjs';

import { ApiErrorCode, PublicUsersService, UsersService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { ApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';

/** What the page does with the mailed token. */
export type EmailTokenAction = 'confirm' | 'abort';

const MISSING_TOKEN: ApiErrorView = {
  status: 400,
  code: ApiErrorCode.EmailChangeTokenInvalid,
  message: 'The link has no token',
  retryable: false,
  fieldErrors: [],
};

/** Result shown on success: the new address after confirming, nothing after aborting. */
interface Outcome {
  email?: string;
}

/**
 * `/account/email/confirm?token=` (logged in, `confirmEmailChange`: the address changes and other sessions end) and
 * `/account/email/abort?token=` (public, `abortEmailChangeByToken`: link in the notice to the old address, works
 * logged out). Both run once when opened, like the magic-link page; a used, aborted or expired link shows
 * `EMAIL_CHANGE_TOKEN_INVALID` without retry.
 */
@Component({
  selector: 'fs-email-token-page',
  imports: [...DATA_STATE_IMPORTS, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="token fs-card" aria-live="polite" [attr.data-testid]="'account.email.' + action() + '.page'">
      <fs-data-state [state]="result" [attr.data-testid]="'account.email.' + action() + '.state'">
        <ng-template fsSkeleton>
          <div class="token__body" data-skeleton>
            <span class="token__icon" aria-hidden="true"><i class="pi pi-spin pi-spinner"></i></span>
            <h1>{{ 'account.email.' + action() + '.loading' | t }}</h1>
          </div>
        </ng-template>

        <ng-template fsError let-error let-retry="retry">
          @if (isTokenError(error)) {
            <div class="token__body" [attr.data-testid]="'account.email.' + action() + '.tokenError'">
              <span class="token__icon token__icon--warn" aria-hidden="true"><i class="pi pi-link"></i></span>
              <h1>{{ 'account.email.' + action() + '.failedTitle' | t }}</h1>
              <p>{{ 'errors.' + error?.code | t }}</p>
              <a class="fs-btn fs-btn--outlined" [routerLink]="homeLink()">{{ 'account.email.toSettings' | t }}</a>
            </div>
          } @else {
            <h1 class="fs-visually-hidden">{{ 'account.email.' + action() + '.failedTitle' | t }}</h1>
            <fs-error-state
              [error]="error"
              [heading]="'account.email.' + action() + '.failedTitle' | t"
              [retryable]="true"
              [testId]="'account.email.' + action() + '.state'"
              (retry)="retry()"
            />
          }
        </ng-template>

        <ng-template [fsData]="result" let-outcome>
          <div class="token__body" [attr.data-testid]="'account.email.' + action() + '.success'">
            <span class="token__icon" aria-hidden="true"><i class="pi pi-check"></i></span>
            <h1>{{ 'account.email.' + action() + '.successTitle' | t }}</h1>
            <p>{{ 'account.email.' + action() + '.successText' | t: { email: outcome.email ?? '' } }}</p>
            <a class="fs-btn" [routerLink]="homeLink()" [attr.data-testid]="'account.email.' + action() + '.continue'">
              {{ 'account.email.toSettings' | t }}
            </a>
          </div>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styles: `
    :host {
      display: block;
      padding: 1rem 0;
    }
    .token {
      width: min(100%, 32rem);
      margin: 0 auto;
      padding: clamp(1.5rem, 1rem + 3vw, 2.5rem);
    }
    .token__body {
      display: grid;
      justify-items: center;
      gap: 0.75rem;
      text-align: center;
    }
    .token__body p {
      margin: 0;
      color: var(--fs-text-muted);
      overflow-wrap: anywhere;
    }
    h1 {
      margin: 0.25rem 0 0;
      font-size: clamp(1.3rem, 1.15rem + 0.8vw, 1.6rem);
      font-weight: 800;
      text-wrap: balance;
    }
    .token__icon {
      display: grid;
      width: 4rem;
      height: 4rem;
      place-items: center;
      border-radius: 50%;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.6rem;
    }
    .token__icon > .pi {
      font-size: 1.6rem;
    }
    .token__icon--warn {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
  `,
})
export class EmailTokenPageComponent {
  private readonly usersApi = inject(UsersService);
  private readonly publicApi = inject(PublicUsersService);
  private readonly session = inject(SessionService);

  readonly token = input<string | null | undefined>(undefined);
  /** From the route data (`data: { emailToken: 'abort' }`). */
  readonly emailToken = input<EmailTokenAction | undefined>(undefined);
  protected readonly action = computed<EmailTokenAction>(() => this.emailToken() ?? 'confirm');
  protected readonly homeLink = computed(() => (this.session.user() ? '/account/settings' : '/'));

  protected readonly result = loadResource<Outcome, { token: string; action: EmailTokenAction }>({
    params: () => ({ token: this.token() ?? '', action: this.action() }),
    loader: ({ token, action }): Observable<Outcome> => {
      if (!token) {
        return throwError(() => MISSING_TOKEN);
      }
      if (action === 'abort') {
        return this.publicApi.abortEmailChangeByToken({ tokenBody: { token } }).pipe(map(() => ({})));
      }
      // the new address is read back so the header menu and the success text show it
      return this.usersApi.confirmEmailChange({ tokenBody: { token } }).pipe(
        switchMap(() => this.usersApi.getCurrentUser()),
        tap((user) => this.session.patchUser({ email: user.email })),
        map((user) => ({ email: user.email })),
      );
    },
    isEmpty: () => false,
    keepDataOnError: false,
  });

  protected isTokenError(error: ApiErrorView | undefined): boolean {
    return error?.code === ApiErrorCode.EmailChangeTokenInvalid;
  }
}
