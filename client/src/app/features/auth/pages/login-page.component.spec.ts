import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { ApiErrorCode } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { LoginFlowService } from '../login-flow.service';
import { settle, useAppInfo } from '../testing';
import { LoginPageComponent } from './login-page.component';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class BlankComponent {}

describe('LoginPageComponent', () => {
  let fixture: ComponentFixture<LoginPageComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'errors.GOOGLE_EMAIL_NOT_VERIFIED': 'E-Mail bei Google nicht bestätigt',
            'errors.EMAIL_DOMAIN_BLOCKED': 'Domain gesperrt',
            'errors.TOO_MANY_LOGIN_REQUESTS': 'Zu viele Versuche ({seconds})',
          },
          routes: [{ path: 'login/sent', component: BlankComponent }],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function create(inputs: Record<string, string> = {}, devMode = false) {
    useAppInfo({ devMode });
    fixture = TestBed.createComponent(LoginPageComponent);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const byTestId = (id: string) => host.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  function typeEmail(value: string): void {
    const input = byTestId('auth.login.email') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(): void {
    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
  }

  it('offers Google, the email link and no dev switcher outside dev mode', async () => {
    await create();
    expect(byTestId('auth.login.google')).not.toBeNull();
    expect(byTestId('auth.login.email')).not.toBeNull();
    expect(byTestId('auth.login.error')).toBeNull();
    expect(host.querySelector('fs-dev-user-switcher')).toBeNull();
    // jsdom has no WebAuthn → no passkey button
    expect(byTestId('auth.login.passkey')).toBeNull();
  });

  it('shows the error handed over by the Google callback', async () => {
    await create({ error: ApiErrorCode.GoogleEmailNotVerified });
    expect(byTestId('auth.login.error')?.textContent).toContain(
      'E-Mail bei Google nicht bestätigt',
    );
  });

  it('goes to Google with the validated return URL', async () => {
    const redirect = vi
      .spyOn(TestBed.inject(LoginFlowService), 'redirect')
      .mockImplementation(() => undefined);
    await create({ returnUrl: '/account/sessions' });
    byTestId('auth.login.google')!.click();
    expect(redirect).toHaveBeenCalledWith(
      '/oauth2/authorization/google?returnUrl=%2Faccount%2Fsessions',
    );
  });

  it('starts Google right away for ?method=google but ignores foreign return URLs', async () => {
    const redirect = vi
      .spyOn(TestBed.inject(LoginFlowService), 'redirect')
      .mockImplementation(() => undefined);
    await create({ method: 'google', returnUrl: '//evil.example' });
    await settle(fixture);
    expect(redirect).toHaveBeenCalledWith('/oauth2/authorization/google');
  });

  it('validates the email before sending anything', async () => {
    await create();
    submit();
    await fixture.whenStable();
    expect(byTestId('auth.login.emailError')?.textContent).toContain('auth.login.emailRequired');
    typeEmail('not-an-email');
    submit();
    await fixture.whenStable();
    expect(byTestId('auth.login.emailError')?.textContent).toContain('auth.login.emailInvalid');
    http.expectNone('/api/auth/magic-link');
  });

  it('requests a magic link and continues to the "sent" page', async () => {
    await create({ returnUrl: '/account/settings' });
    typeEmail('  anna@example.ch ');
    submit();
    const request = http.expectOne('/api/auth/magic-link');
    expect(request.request.body).toEqual({
      email: 'anna@example.ch',
      returnUrl: '/account/settings',
      language: 'de',
    });
    request.flush({ message: 'sent' }, { status: 202, statusText: 'Accepted' });
    await settle(fixture);
    expect(TestBed.inject(Router).url).toBe(
      '/login/sent?email=anna@example.ch&returnUrl=%2Faccount%2Fsettings',
    );
  });

  it('shows a blocked domain next to the form', async () => {
    await create();
    typeEmail('x@mailinator.com');
    submit();
    http
      .expectOne('/api/auth/magic-link')
      .flush(
        { code: 'EMAIL_DOMAIN_BLOCKED', message: 'blocked', traceId: 't', retryable: false },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle(fixture);
    expect(byTestId('auth.login.requestError')?.textContent).toContain('Domain gesperrt');
  });

  it('shows rate limiting as a toast with the waiting time', async () => {
    await create();
    const add = vi.spyOn(TestBed.inject(MessageService), 'add');
    typeEmail('anna@example.ch');
    submit();
    http.expectOne('/api/auth/magic-link').flush(
      {
        code: 'TOO_MANY_LOGIN_REQUESTS',
        message: 'slow down',
        traceId: 't',
        retryable: true,
        retryAfterSeconds: 900,
      },
      { status: 429, statusText: 'Too Many Requests' },
    );
    await settle(fixture);
    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'warn', detail: 'Zu viele Versuche (900)' }),
    );
    expect(byTestId('auth.login.requestError')).toBeNull();
  });

  it('shows the dev user switcher in dev mode', async () => {
    await create({}, true);
    expect(host.querySelector('fs-dev-user-switcher')).not.toBeNull();
    http.expectOne('/api/dev/users').flush([]);
  });
});
