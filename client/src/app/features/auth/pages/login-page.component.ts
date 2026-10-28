import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Subscription } from 'rxjs';

import { ApiErrorCode, AuthService, Language, SessionInfo } from '../../../api';
import { AppInfoService } from '../../../core/app-info.service';
import { safeReturnUrl } from '../../../core/auth/return-url';
import { isPasskeyCancelled, WebAuthnClient } from '../../../core/auth/webauthn.client';
import { ApiErrorView, errorParams, toApiErrorView } from '../../../core/data/api-error';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ErrorStateComponent } from '../../../shared/data-state/error-state.component';
import { DevUserSwitcherComponent } from '../components/dev-user-switcher.component';
import { GoogleLogoComponent } from '../../../shared/google-logo/google-logo.component';
import { LoginFlowService } from '../login-flow.service';
import { passkeyLoginWithSession } from '../passkey-login.resource';

const KNOWN_LOGIN_ERRORS = new Set<string>(Object.values(ApiErrorCode));

/**
 * /login — sign up and log in in one place, without passwords (plan §8): Google (primary), magic link by email,
 * passkey (button + autofill via conditional mediation). `?error=<ApiErrorCode>` comes back from a failed
 * Google login, `?method=google|email` from the landing page CTAs, `?returnUrl=` from the auth guard.
 */
@Component({
  selector: 'fs-login-page',
  imports: [
    Button,
    DevUserSwitcherComponent,
    ErrorStateComponent,
    GoogleLogoComponent,
    InputText,
    ReactiveFormsModule,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.scss',
})
export class LoginPageComponent {
  private readonly authApi = inject(AuthService);
  private readonly flow = inject(LoginFlowService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly webauthn = inject(WebAuthnClient);
  protected readonly i18n = inject(I18nService);
  protected readonly appInfo = inject(AppInfoService);

  readonly returnUrl = input<string | null | undefined>(undefined);
  readonly error = input<string | null | undefined>(undefined);
  readonly method = input<string | null | undefined>(undefined);

  private readonly emailInput = viewChild<ElementRef<HTMLInputElement>>('emailInput');

  protected readonly safeReturn = computed(() => safeReturnUrl(this.returnUrl()));
  protected readonly loginMethods = computed(
    () => this.appInfo.info()?.loginMethods ?? { google: true, magicLink: true, passkeys: true },
  );
  protected readonly passkeysAvailable = computed(
    () => this.loginMethods().passkeys && this.webauthn.isSupported(),
  );
  protected readonly redirectingToGoogle = signal(false);

  /** Error handed over in the URL by the Google callback (unknown codes become GOOGLE_LOGIN_FAILED). */
  protected readonly urlError = computed<ApiErrorView | undefined>(() => {
    const code = this.error();
    if (!code) {
      return undefined;
    }
    const known = KNOWN_LOGIN_ERRORS.has(code) ? code : ApiErrorCode.GoogleLoginFailed;
    return {
      status: 400,
      code: known as ApiErrorCode,
      message: `Login failed: ${known}`,
      retryable: false,
      fieldErrors: [],
    };
  });
  protected readonly urlErrorDismissed = signal(false);

  protected readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(320)],
    }),
  });
  protected readonly submitted = signal(false);
  /** Errors of the magic-link request that are not field errors (blocked domain, IP blocked, …). */
  protected readonly emailError = signal<string | null>(null);

  protected readonly requestLink = runMutation(
    (email: string) =>
      this.authApi.requestMagicLink({
        magicLinkRequest: {
          email,
          returnUrl: this.safeReturn() ?? undefined,
          language: this.i18n.language() as Language,
        },
      }),
    {
      form: this.form,
      errorToast: false,
      onSuccess: () => {
        void this.router.navigate(['/login/sent'], {
          queryParams: { email: this.form.controls.email.value, returnUrl: this.safeReturn() },
        });
      },
      onError: (view) => this.showRequestError(view),
    },
  );

  protected readonly passkeyLogin = runMutation(
    () => passkeyLoginWithSession(this.webauthn, this.authApi),
    {
      errorToast: false,
      onSuccess: (info) => this.passkeyDone(info),
      onError: (view) => this.showPasskeyError(view),
    },
  );

  private conditional: { abort: AbortController; subscription: Subscription } | null = null;

  constructor() {
    afterNextRender(() => {
      if (this.method() === 'google' && !this.error() && this.loginMethods().google) {
        this.continueWithGoogle();
        return;
      }
      if (this.method() === 'email') {
        this.emailInput()?.nativeElement.focus();
      }
      void this.startConditionalPasskey();
    });
    inject(DestroyRef).onDestroy(() => this.stopConditionalPasskey());
  }

  protected continueWithGoogle(): void {
    this.redirectingToGoogle.set(true);
    this.stopConditionalPasskey();
    if (this.method()) {
      // "back" from Google must not land on ?method=google again (it would redirect right away)
      void this.router.navigate([], {
        queryParams: { method: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
    this.flow.startGoogle(this.safeReturn());
  }

  protected submitEmail(): void {
    this.submitted.set(true);
    this.emailError.set(null);
    const control = this.form.controls.email;
    control.setValue(control.value.trim());
    if (this.form.invalid) {
      control.markAsTouched();
      this.emailInput()?.nativeElement.focus();
      return;
    }
    void this.requestLink.run(control.value);
  }

  protected usePasskey(): void {
    this.stopConditionalPasskey();
    void this.passkeyLogin.run();
  }

  protected emailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.touched || this.submitted());
  }

  protected emailErrorKey(): string {
    const errors = this.form.controls.email.errors ?? {};
    if (errors['server']) {
      return 'auth.login.emailInvalid';
    }
    return errors['required'] ? 'auth.login.emailRequired' : 'auth.login.emailInvalid';
  }

  private showRequestError(view: ApiErrorView): void {
    if (view.fieldErrors.length) {
      return; // shown on the field
    }
    if (view.status === 429) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('auth.login.tooManyTitle'),
        detail: this.i18n.error(view.code, errorParams(view)),
        life: 10000,
      });
      return;
    }
    this.emailError.set(this.i18n.error(view.code, errorParams(view)));
  }

  private passkeyDone(info: SessionInfo): void {
    if (!info.authenticated) {
      this.showPasskeyError({
        status: 401,
        code: ApiErrorCode.AuthenticationRequired,
        message: 'No session after the passkey login',
        retryable: false,
        fieldErrors: [],
      });
      return;
    }
    this.flow.finish(info, this.safeReturn(), 'PASSKEY');
  }

  private showPasskeyError(view: ApiErrorView): void {
    if (isPasskeyCancelled(view)) {
      void this.startConditionalPasskey();
      return;
    }
    this.messages.add({
      severity: 'error',
      summary: this.i18n.t('auth.login.passkeyFailedTitle'),
      detail:
        view.status === 401
          ? this.i18n.t('auth.login.passkeyFailed')
          : this.i18n.error(view.code, errorParams(view)),
      life: 8000,
    });
    void this.startConditionalPasskey();
  }

  /** Offers saved passkeys in the email field's autofill (when the browser supports it). */
  private async startConditionalPasskey(): Promise<void> {
    if (this.conditional || !this.passkeysAvailable() || this.redirectingToGoogle()) {
      return;
    }
    if (!(await this.webauthn.isConditionalMediationAvailable())) {
      return;
    }
    const abort = new AbortController();
    const subscription = passkeyLoginWithSession(this.webauthn, this.authApi, {
      mediation: 'conditional',
      signal: abort.signal,
    }).subscribe({
      next: (info) => {
        this.conditional = null;
        this.passkeyDone(info);
      },
      error: (error: unknown) => {
        const aborted = abort.signal.aborted;
        this.conditional = null;
        if (!aborted) {
          const view = toApiErrorView(error);
          if (!isPasskeyCancelled(view) && view.status > 0) {
            this.showPasskeyError(view);
          }
        }
      },
    });
    this.conditional = { abort, subscription };
  }

  private stopConditionalPasskey(): void {
    if (this.conditional) {
      const { abort, subscription } = this.conditional;
      this.conditional = null;
      abort.abort();
      subscription.unsubscribe();
    }
  }
}
