import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { SessionService } from '../../../core/auth/session.service';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { sessionInfo, settle } from '../testing';
import { MagicLinkVerifyPageComponent } from './magic-link-verify-page.component';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class BlankComponent {}

describe('MagicLinkVerifyPageComponent', () => {
  let fixture: ComponentFixture<MagicLinkVerifyPageComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'errors.LOGIN_TOKEN_EXPIRED_OR_USED': 'Link abgelaufen' },
          routes: [
            { path: 'dashboard', component: BlankComponent },
            { path: 'account/settings', component: BlankComponent },
            { path: 'login', component: BlankComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function create(token: string | undefined, returnUrl?: string) {
    fixture = TestBed.createComponent(MagicLinkVerifyPageComponent);
    if (token !== undefined) fixture.componentRef.setInput('token', token);
    if (returnUrl) fixture.componentRef.setInput('returnUrl', returnUrl);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  }

  const state = () => host.querySelector('[data-testid="auth.verify.state"]')!;
  const fail = (status: number, code: string, retryable = false) =>
    http
      .expectOne('/api/auth/magic-link/verify')
      .flush({ code, message: code, traceId: 'abc', retryable }, { status, statusText: 'Error' });

  it('shows progress while verifying, then logs in and continues to the return URL', async () => {
    await create('tok-1', '/account/settings');
    expect(state().getAttribute('data-state')).toBe('loading');
    expect(state().textContent).toContain('auth.verify.loadingTitle');
    const request = http.expectOne('/api/auth/magic-link/verify');
    expect(request.request.body).toEqual({ token: 'tok-1' });
    request.flush(sessionInfo());
    await settle(fixture);
    expect(TestBed.inject(SessionService).isAuthenticated()).toBe(true);
    expect(TestBed.inject(Router).url).toBe('/account/settings');
  });

  it('offers a new link for expired or used tokens', async () => {
    await create('tok-2', '/account/settings');
    fail(400, 'LOGIN_TOKEN_EXPIRED_OR_USED');
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('error');
    expect(host.querySelector('[data-testid="auth.verify.tokenError"]')?.textContent).toContain(
      'Link abgelaufen',
    );
    const link = host.querySelector('[data-testid="auth.verify.newLink"]') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/login?method=email&returnUrl=%2Faccount%2Fsettings');
  });

  it('treats a missing token as an invalid link without calling the server', async () => {
    await create(undefined);
    await fixture.whenStable();
    expect(host.querySelector('[data-testid="auth.verify.tokenError"]')).not.toBeNull();
    http.expectNone('/api/auth/magic-link/verify');
  });

  it('shows other errors with retry and recovers', async () => {
    await create('tok-3');
    fail(500, 'UNEXPECTED_ERROR', true);
    await fixture.whenStable();
    expect(host.querySelector('[data-testid="auth.verify.tokenError"]')).toBeNull();
    (host.querySelector('[data-testid="auth.verify.state.retry"] button') as HTMLElement).click();
    http.expectOne('/api/auth/magic-link/verify').flush(sessionInfo());
    await settle(fixture);
    expect(TestBed.inject(Router).url).toBe('/dashboard');
  });
});
