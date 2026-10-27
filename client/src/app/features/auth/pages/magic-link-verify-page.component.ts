import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { tap, throwError } from 'rxjs';

import { ApiErrorCode, AuthService } from '../../../api';
import { safeReturnUrl } from '../../../core/auth/return-url';
import { ApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { LoginFlowService } from '../login-flow.service';

/** Errors after which the same link can never work again: offer a new one instead of "retry". */
export const TOKEN_ERRORS: readonly string[] = [
  ApiErrorCode.InvalidLoginToken,
  ApiErrorCode.LoginTokenExpiredOrUsed,
];

const MISSING_TOKEN: ApiErrorView = {
  status: 400,
  code: ApiErrorCode.InvalidLoginToken,
  message: 'The link has no token',
  retryable: false,
  fieldErrors: [],
};

/**
 * /login/verify?token=…&returnUrl=… — the target of the magic link. Exchanges the token for a session
 * (POST /api/auth/magic-link/verify) and continues to the return URL; expired or used links get a
 * "request a new link" action.
 */
@Component({
  selector: 'fs-magic-link-verify-page',
  imports: [...DATA_STATE_IMPORTS, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="verify" aria-live="polite">
      <fs-data-state [state]="session" data-testid="auth.verify.state">
        <ng-template fsSkeleton>
          <div class="verify__progress" data-skeleton>
            <span class="verify__spinner" aria-hidden="true">
              <i class="pi pi-spin pi-spinner"></i>
            </span>
            <h1 class="verify__title">{{ 'auth.verify.loadingTitle' | t }}</h1>
            <p class="verify__text">{{ 'auth.verify.loadingText' | t }}</p>
          </div>
        </ng-template>

        <ng-template fsError let-error let-retry="retry">
          @if (isTokenError(error)) {
            <div class="verify__expired" data-testid="auth.verify.tokenError">
              <span class="verify__icon" aria-hidden="true"><i class="pi pi-link"></i></span>
              <h1 class="verify__title">{{ 'auth.verify.expiredTitle' | t }}</h1>
              <p class="verify__text">{{ 'errors.' + error?.code | t }}</p>
              <a
                class="fs-btn fs-btn--lg"
                routerLink="/login"
                [queryParams]="{ method: 'email', returnUrl: safeReturn() }"
                data-testid="auth.verify.newLink"
              >
                <i class="pi pi-envelope" aria-hidden="true"></i>
                <span>{{ 'auth.verify.newLink' | t }}</span>
              </a>
            </div>
          } @else {
            <h1 class="fs-visually-hidden">{{ 'auth.verify.failedTitle' | t }}</h1>
            <fs-error-state
              [error]="error"
              [heading]="'auth.verify.failedTitle' | t"
              [retryable]="true"
              testId="auth.verify.state"
              (retry)="retry()"
            />
            <a class="verify__back" routerLink="/login" data-testid="auth.verify.backToLogin">
              <i class="pi pi-arrow-left" aria-hidden="true"></i>
              {{ 'auth.verify.backToLogin' | t }}
            </a>
          }
        </ng-template>

        <ng-template fsData>
          <div class="verify__progress" data-testid="auth.verify.success">
            <span class="verify__icon verify__icon--ok" aria-hidden="true">
              <i class="pi pi-check"></i>
            </span>
            <h1 class="verify__title">{{ 'auth.verify.successTitle' | t }}</h1>
            <p class="verify__text">{{ 'auth.verify.successText' | t }}</p>
          </div>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .verify {
      width: min(100%, 32rem);
      margin: 0 auto;
      padding: clamp(1.5rem, 1rem + 3vw, 2.75rem) clamp(1.1rem, 0.8rem + 2vw, 2.5rem);
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-xl);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-md);
    }
    .verify__progress,
    .verify__expired {
      display: grid;
      justify-items: center;
      gap: 0.75rem;
      text-align: center;
    }
    .verify__spinner,
    .verify__icon {
      display: grid;
      width: 4.5rem;
      height: 4.5rem;
      place-items: center;
      border-radius: 50%;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.8rem;
    }
    .verify__spinner > .pi,
    .verify__icon > .pi {
      font-size: 1.8rem;
    }
    .verify__expired .verify__icon {
      background: var(--fs-warning-soft);
      color: var(--fs-warning);
    }
    .verify__title {
      margin: 0.25rem 0 0;
      font-size: clamp(1.4rem, 1.2rem + 1vw, 1.8rem);
      font-weight: 800;
      letter-spacing: -0.02em;
      text-wrap: balance;
    }
    .verify__text {
      max-width: 26rem;
      margin: 0 0 0.5rem;
      color: var(--fs-text-muted);
    }
    .verify__back {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      margin-top: 0.5rem;
      text-decoration: none;
    }
  `,
})
export class MagicLinkVerifyPageComponent {
  private readonly authApi = inject(AuthService);
  private readonly flow = inject(LoginFlowService);

  readonly token = input<string | null | undefined>(undefined);
  readonly returnUrl = input<string | null | undefined>(undefined);
  protected readonly safeReturn = computed(() => safeReturnUrl(this.returnUrl()));

  protected readonly session = loadResource({
    params: () => ({ token: this.token() ?? '' }),
    loader: ({ token }) =>
      token
        ? this.authApi
            .verifyMagicLink({ magicLinkVerifyRequest: { token } })
            .pipe(tap((info) => this.flow.finish(info, this.safeReturn(), 'EMAIL')))
        : throwError(() => MISSING_TOKEN),
    keepDataOnError: false,
    autoRetryWhenOnline: true,
  });

  protected isTokenError(error: ApiErrorView | undefined): boolean {
    return !!error && TOKEN_ERRORS.includes(error.code);
  }
}
