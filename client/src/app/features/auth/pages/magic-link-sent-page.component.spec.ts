import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { settle, useAppInfo } from '../testing';
import { MagicLinkSentPageComponent } from './magic-link-sent-page.component';

describe('MagicLinkSentPageComponent', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting(), provideOptimus()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function create(devMode: boolean) {
    useAppInfo({ devMode });
    const fixture = TestBed.createComponent(MagicLinkSentPageComponent);
    fixture.componentRef.setInput('email', 'anna@example.ch');
    fixture.componentRef.setInput('returnUrl', '/account/settings');
    await fixture.whenStable();
    return { fixture, host: fixture.nativeElement as HTMLElement };
  }

  it('shows the address and the Mailpit link only in dev mode', async () => {
    const { host } = await create(false);
    expect(host.querySelector('[data-testid="auth.sent.email"]')?.textContent).toBe(
      'anna@example.ch',
    );
    expect(host.querySelector('[data-testid="auth.sent.mailpit"]')).toBeNull();

    const dev = await create(true);
    expect(dev.host.querySelector('[data-testid="auth.sent.mailpit"]')).not.toBeNull();
  });

  it('sends the link again', async () => {
    const { fixture, host } = await create(false);
    (host.querySelector('[data-testid="auth.sent.resend"] button') as HTMLElement).click();
    const request = http.expectOne('/api/auth/magic-link');
    expect(request.request.body).toEqual({
      email: 'anna@example.ch',
      returnUrl: '/account/settings',
      language: 'de',
    });
    request.flush({ message: 'sent' }, { status: 202, statusText: 'Accepted' });
    await settle(fixture);
  });
});
