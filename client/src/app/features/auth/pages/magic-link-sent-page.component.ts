import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { AuthService, Language } from '../../../api';
import { AppInfoService } from '../../../core/app-info.service';
import { safeReturnUrl } from '../../../core/auth/return-url';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';

/** Mailpit's web UI of the local profile (server/start_smtp_server.sh). */
export const MAILPIT_URL = 'http://localhost:8025';

/** /login/sent?email=… — "check your inbox", with resend and (dev) a link to Mailpit. */
@Component({
  selector: 'fs-magic-link-sent-page',
  imports: [Button, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="sent" data-testid="auth.sent.page" aria-labelledby="sent-title">
      <div class="sent__art" aria-hidden="true">
        <span class="sent__ring"></span>
        <i class="pi pi-envelope"></i>
        <span class="sent__check"><i class="pi pi-check"></i></span>
      </div>
      <h1 id="sent-title" class="sent__title">{{ 'auth.sent.title' | t }}</h1>
      @if (email()) {
        <p class="sent__text">
          {{ 'auth.sent.textBefore' | t }}
          <strong class="sent__email" data-testid="auth.sent.email">{{ email() }}</strong>
        </p>
      } @else {
        <p class="sent__text">{{ 'auth.sent.textNoEmail' | t }}</p>
      }
      <p class="sent__validity">
        <i class="pi pi-clock" aria-hidden="true"></i>
        {{ 'auth.sent.validity' | t }}
      </p>

      @if (appInfo.devMode()) {
        <a
          class="fs-btn sent__mailpit"
          [href]="mailpitUrl"
          target="_blank"
          rel="noopener"
          data-testid="auth.sent.mailpit"
        >
          <i class="pi pi-inbox" aria-hidden="true"></i>
          <span>{{ 'auth.sent.openMailpit' | t }}</span>
          <i class="pi pi-external-link" aria-hidden="true"></i>
        </a>
      }

      <div class="sent__help">
        <h2>{{ 'auth.sent.noMailTitle' | t }}</h2>
        <ul>
          <li>{{ 'auth.sent.tipSpam' | t }}</li>
          <li>{{ 'auth.sent.tipTypo' | t }}</li>
        </ul>
        <div class="sent__actions">
          @if (email()) {
            <p-button
              [label]="'auth.sent.resend' | t"
              icon="pi pi-refresh"
              severity="secondary"
              [outlined]="true"
              [loading]="resend.busy()"
              [fluid]="true"
              (onClick)="resend.run()"
              data-testid="auth.sent.resend"
            />
          }
          <a
            class="fs-btn fs-btn--outlined"
            routerLink="/login"
            [queryParams]="{ method: 'email', returnUrl: safeReturn() }"
            data-testid="auth.sent.otherEmail"
          >
            <i class="pi pi-arrow-left" aria-hidden="true"></i>
            <span>{{ 'auth.sent.otherEmail' | t }}</span>
          </a>
        </div>
      </div>
    </section>
  `,
  styles: `
    :host {
      display: block;
      --p-button-text-danger-color: var(--fs-danger);
      --p-button-outlined-danger-color: var(--fs-danger);
      --p-button-text-secondary-color: var(--fs-text-muted);
      --p-button-outlined-secondary-color: var(--fs-text);
    }
    .sent {
      display: grid;
      justify-items: center;
      gap: 0.85rem;
      width: min(100%, 34rem);
      margin: 0 auto;
      padding: clamp(1.5rem, 1rem + 3vw, 2.75rem) clamp(1.1rem, 0.8rem + 2vw, 2.5rem);
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-xl);
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-md);
      text-align: center;
    }
    .sent__art {
      position: relative;
      display: grid;
      width: 5.5rem;
      height: 5.5rem;
      margin-bottom: 0.4rem;
      place-items: center;
      border-radius: 50%;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 2.2rem;
    }
    .sent__art > .pi {
      font-size: 2.2rem;
    }
    .sent__ring {
      position: absolute;
      inset: -0.5rem;
      border: 2px dashed color-mix(in srgb, var(--fs-primary) 35%, transparent);
      border-radius: 50%;
    }
    .sent__check {
      position: absolute;
      right: -0.15rem;
      bottom: 0.1rem;
      display: grid;
      width: 1.9rem;
      height: 1.9rem;
      place-items: center;
      border: 3px solid var(--fs-surface);
      border-radius: 50%;
      background: var(--fs-primary);
      color: #fff;
      font-size: 0.8rem;
    }
    :host-context(.fs-dark) .sent__check {
      background: #25834a;
    }
    .sent__title {
      margin: 0;
      font-size: clamp(1.6rem, 1.3rem + 1.4vw, 2.1rem);
      font-weight: 800;
      letter-spacing: -0.03em;
    }
    .sent__text {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 1.05rem;
    }
    .sent__email {
      display: block;
      margin-top: 0.2rem;
      color: var(--fs-text);
      overflow-wrap: anywhere;
    }
    .sent__validity {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      margin: 0;
      padding: 0.35rem 0.85rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
      font-size: 0.88rem;
      font-weight: 600;
    }
    .sent__mailpit {
      margin-top: 0.4rem;
    }
    .sent__help {
      display: grid;
      gap: 0.6rem;
      width: 100%;
      margin-top: 0.75rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--fs-border);
      text-align: left;
    }
    .sent__help h2 {
      margin: 0;
      font-size: 1rem;
    }
    .sent__help ul {
      display: grid;
      gap: 0.3rem;
      margin: 0;
      padding-left: 1.2rem;
      color: var(--fs-text-muted);
      font-size: 0.92rem;
    }
    .sent__actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.6rem;
      margin-top: 0.4rem;
    }
    @media (max-width: 30rem) {
      .sent__actions > * {
        flex: 1 1 100%;
      }
      .sent__actions .fs-btn {
        white-space: normal;
      }
    }
  `,
})
export class MagicLinkSentPageComponent {
  private readonly authApi = inject(AuthService);
  private readonly i18n = inject(I18nService);
  protected readonly appInfo = inject(AppInfoService);
  protected readonly mailpitUrl = MAILPIT_URL;

  readonly email = input<string | null | undefined>(undefined);
  readonly returnUrl = input<string | null | undefined>(undefined);
  protected readonly safeReturn = computed(() => safeReturnUrl(this.returnUrl()));

  protected readonly resend = runMutation(
    () =>
      this.authApi.requestMagicLink({
        magicLinkRequest: {
          email: this.email() ?? '',
          returnUrl: this.safeReturn() ?? undefined,
          language: this.i18n.language() as Language,
        },
      }),
    { successToastKey: 'auth.sent.resent' },
  );
}
