import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';

import { ConsentRequest, OauthService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { OAuthScopeListComponent } from '../components/scope-list.component';
import { OAUTH_REDIRECT } from '../oauth-redirect';

/**
 * `/oauth/consent?request=<id>` (C13, A44/A56), minimal chrome: `/oauth/authorize` sends the signed-in user here
 * when no remembered consent covers the requested scopes. Shows the application, what each scope shares and a
 * "remember" checkbox; approve/decline post the decision and leave the app for the returned redirect URI (a code,
 * or `error=access_denied`). A region-restricted client the user may not use (`regionAllowed=false`,
 * OAUTH_REGION_REQUIRED) names the regions it requires (`requiredRegions`) and only offers going back to the
 * application. An answered or expired request is 404
 * OAUTH_REQUEST_EXPIRED (not-found state).
 */
@Component({
  selector: 'fs-oauth-consent-page',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, DateTimePipe, FormsModule, OAuthScopeListComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      @if (request()) {
        <fs-data-state
          [state]="consent"
          skeleton="form"
          backLink="/dashboard"
          testId="oauth.consent.state"
          data-testid="oauth.consent.state"
        >
          <ng-template [fsData]="consent" let-c>
            <article class="fs-card sheet" data-testid="oauth.consent.card" [attr.data-client]="c.client.clientId">
              <header class="head">
                <span class="app-icon" aria-hidden="true"><i class="pi pi-th-large"></i></span>
                <h1 data-testid="oauth.consent.title">
                  {{ 'oauth.consent.title' | t: { client: c.client.name } }}
                </h1>
                @if (userName(); as name) {
                  <p class="muted" data-testid="oauth.consent.user">{{ 'oauth.consent.signedInAs' | t: { name } }}</p>
                }
              </header>

              @if (!c.regionAllowed) {
                <div class="notice notice--warn" role="alert" data-testid="oauth.consent.regionRequired">
                  <i class="pi pi-lock" aria-hidden="true"></i>
                  <div>
                    <p class="notice__title">{{ 'oauth.consent.regionRequiredTitle' | t }}</p>
                    <p>{{ 'oauth.consent.regionRequiredText' | t: { client: c.client.name } }}</p>
                    @if (c.requiredRegions.length) {
                      <p class="notice__list-title">{{ 'oauth.consent.requiredRegions' | t }}</p>
                      <ul class="notice__list" data-testid="oauth.consent.requiredRegions">
                        @for (region of c.requiredRegions; track region.id) {
                          <li data-testid="oauth.consent.requiredRegion" [attr.data-region]="region.id">{{ region.name }}</li>
                        }
                      </ul>
                    }
                  </div>
                </div>
                <div class="actions">
                  <p-button
                    [label]="'oauth.consent.back' | t: { client: c.client.name }"
                    icon="pi pi-arrow-left"
                    severity="secondary"
                    [outlined]="true"
                    [loading]="decline.busy()"
                    [disabled]="leaving()"
                    (onClick)="decline.run()"
                    data-testid="oauth.consent.back"
                  />
                </div>
              } @else {
                <section aria-labelledby="consent-scopes">
                  <h2 id="consent-scopes" class="section-title">
                    {{ 'oauth.consent.scopesTitle' | t: { client: c.client.name } }}
                  </h2>
                  <fs-oauth-scope-list [scopes]="c.scopes" testId="oauth.consent.scopes" />
                </section>

                <label class="remember" for="consent-remember">
                  <p-checkbox
                    inputId="consent-remember"
                    [binary]="true"
                    [ngModel]="remember() ?? c.alreadyRemembered"
                    (ngModelChange)="remember.set($event)"
                    data-testid="oauth.consent.remember"
                  />
                  <span>
                    {{ 'oauth.consent.remember' | t }}
                    <span class="muted remember__hint">{{ 'oauth.consent.rememberHint' | t }}</span>
                  </span>
                </label>

                @if (decisionError(); as error) {
                  <p class="field-error" role="alert" data-testid="oauth.consent.error">{{ error }}</p>
                }

                <div class="actions">
                  <p-button
                    [label]="'oauth.consent.decline' | t"
                    severity="secondary"
                    [outlined]="true"
                    [loading]="decline.busy()"
                    [disabled]="approve.busy() || leaving()"
                    (onClick)="decline.run()"
                    data-testid="oauth.consent.decline"
                  />
                  <p-button
                    [label]="'oauth.consent.approve' | t"
                    icon="pi pi-check"
                    [loading]="approve.busy() || leaving()"
                    [disabled]="decline.busy()"
                    (onClick)="approve.run(c)"
                    data-testid="oauth.consent.approve"
                  />
                </div>
                <p class="muted expires">
                  {{ 'oauth.consent.footer' | t: { time: (c.expiresAt | fsDate: 'time') } }}
                </p>
              }
            </article>
          </ng-template>
        </fs-data-state>
      } @else {
        <div class="fs-card sheet" role="alert" data-testid="oauth.consent.missing">
          <h1>{{ 'oauth.consent.missingTitle' | t }}</h1>
          <p>{{ 'errors.OAUTH_REQUEST_EXPIRED' | t }}</p>
        </div>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 34rem);
      margin: 0 auto;
    }
    .wrap {
      display: grid;
    }
    .sheet {
      display: grid;
      gap: 1.25rem;
      padding: 1.5rem;
    }
    .head {
      display: grid;
      justify-items: center;
      gap: 0.5rem;
      text-align: center;
    }
    .app-icon {
      display: inline-grid;
      place-items: center;
      width: 3.5rem;
      height: 3.5rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.5rem;
    }
    h1 {
      margin: 0;
      font-size: 1.35rem;
      line-height: 1.3;
    }
    .section-title {
      margin: 0 0 0.75rem;
      font-size: 1rem;
    }
    .muted {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.875rem;
    }
    .remember {
      display: flex;
      gap: 0.6rem;
      align-items: flex-start;
      padding: 0.75rem;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-surface-muted);
      cursor: pointer;
    }
    .remember__hint {
      display: block;
      font-size: 0.8125rem;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      justify-content: flex-end;
    }
    .expires {
      text-align: center;
      font-size: 0.8125rem;
    }
    .notice {
      display: flex;
      gap: 0.75rem;
      padding: 0.85rem 1rem;
      border-radius: var(--fs-radius-sm);
    }
    .notice p {
      margin: 0;
    }
    .notice--warn {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .notice__title {
      font-weight: 650;
    }
    .notice .notice__list-title {
      margin-top: 0.5rem;
    }
    .notice__list {
      margin: 0.25rem 0 0;
      padding-left: 1.25rem;
    }
    .field-error {
      margin: 0;
      color: var(--fs-danger);
      font-size: 0.875rem;
    }
  `,
})
export class ConsentPageComponent {
  private readonly api = inject(OauthService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);
  private readonly redirect = inject(OAUTH_REDIRECT);

  /** `?request=` (component input binding). */
  readonly request = input<string | undefined>(undefined);

  /** null = untouched (defaults to `alreadyRemembered`). */
  protected readonly remember = signal<boolean | null>(null);
  /** The decision went through and the browser is on its way to the client. */
  protected readonly leaving = signal(false);
  protected readonly userName = computed(() => this.session.user()?.displayName);

  protected readonly consent = loadResource<ConsentRequest, string>({
    params: () => this.request() || undefined,
    loader: (requestId) => this.api.getConsentRequest({ requestId }),
  });

  protected readonly approve = runMutation(
    (c: ConsentRequest) =>
      this.api.decideConsentRequest({
        requestId: this.request()!,
        consentDecision: { approve: true, remember: this.remember() ?? c.alreadyRemembered },
      }),
    { errorToast: false, onSuccess: ({ redirectUri }) => this.leave(redirectUri) },
  );

  protected readonly decline = runMutation(
    () => this.api.decideConsentRequest({ requestId: this.request()!, consentDecision: { approve: false, remember: false } }),
    { errorToast: false, onSuccess: ({ redirectUri }) => this.leave(redirectUri) },
  );

  protected readonly decisionError = computed(() => {
    const error = this.approve.error() ?? this.decline.error();
    return error ? this.i18n.error(error.code) : undefined;
  });

  private leave(redirectUri: string): void {
    this.leaving.set(true);
    this.redirect(redirectUri);
  }
}
