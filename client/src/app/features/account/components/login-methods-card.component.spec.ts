import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { AuthMethods, AuthProvider } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { settle } from '../../auth/testing';
import { LoginMethodsCardComponent } from './login-methods-card.component';

const METHODS: AuthMethods = {
  identities: [
    {
      id: 'id-google',
      provider: AuthProvider.Google,
      label: 'sarah@gmail.example',
      createdAt: '2026-01-01T10:00:00Z',
      lastUsedAt: '2026-11-01T10:00:00Z',
    },
    {
      id: 'id-email',
      provider: AuthProvider.Email,
      label: 'foodsaver@local',
      createdAt: '2025-12-19T18:18:00Z',
    },
  ],
  passkeys: [
    {
      id: 'pk-1',
      label: 'MacBook',
      createdAt: '2026-10-01T10:00:00Z',
      transports: ['internal'],
    },
  ],
};

describe('LoginMethodsCardComponent', () => {
  let fixture: ComponentFixture<LoginMethodsCardComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting(), provideOptimus()],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(ConfirmationService), 'confirm').mockImplementation((confirmation) => {
      confirmation.accept?.();
      return TestBed.inject(ConfirmationService);
    });
    fixture = TestBed.createComponent(LoginMethodsCardComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const state = () => host.querySelector('[data-testid="account.settings.methods"]')!;
  const byTestId = (id: string) => host.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const button = (id: string) => byTestId(id)!.querySelector('button') as HTMLButtonElement;

  it('shows a skeleton, then identities and passkeys', async () => {
    expect(state().getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/auth/methods').flush(METHODS);
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
    expect(byTestId('account.settings.identity.GOOGLE')!.textContent).toContain(
      'sarah@gmail.example',
    );
    expect(byTestId('account.settings.identity.EMAIL')).not.toBeNull();
    expect(byTestId('account.settings.passkey.pk-1')!.textContent).toContain('MacBook');
    // jsdom has no WebAuthn: no "add" button, but a hint
    expect(byTestId('account.settings.passkeys.add')).toBeNull();
  });

  it('shows the empty state', async () => {
    http.expectOne('/api/auth/methods').flush({ identities: [], passkeys: [] });
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('empty');
  });

  it('shows the error with retry and recovers', async () => {
    http
      .expectOne('/api/auth/methods')
      .flush(
        { code: 'UNEXPECTED_ERROR', message: 'x', traceId: 't', retryable: true },
        { status: 500, statusText: 'Error' },
      );
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('error');
    button('account.settings.methods.retry').click();
    http.expectOne('/api/auth/methods').flush(METHODS);
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
  });

  it('never allows removing the last method', async () => {
    http
      .expectOne('/api/auth/methods')
      .flush({ identities: [METHODS.identities[1]], passkeys: [] });
    await fixture.whenStable();
    expect(button('account.settings.identity.EMAIL.remove').disabled).toBe(true);
    expect(byTestId('account.settings.passkeys.empty')).not.toBeNull();
  });

  it('unlinks an identity and deletes a passkey after confirmation', async () => {
    http.expectOne('/api/auth/methods').flush(METHODS);
    await fixture.whenStable();

    button('account.settings.identity.GOOGLE.remove').click();
    http
      .expectOne({ method: 'DELETE', url: '/api/auth/methods/id-google' })
      .flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/methods').flush({ ...METHODS, identities: [METHODS.identities[1]] });
    await settle(fixture);
    expect(byTestId('account.settings.identity.GOOGLE')).toBeNull();

    button('account.settings.passkey.pk-1.delete').click();
    http
      .expectOne({ method: 'DELETE', url: '/api/auth/passkeys/pk-1' })
      .flush(null, { status: 204, statusText: 'No Content' });
    http
      .expectOne('/api/auth/methods')
      .flush({ identities: [METHODS.identities[1]], passkeys: [] });
    await settle(fixture);
    expect(byTestId('account.settings.passkey.pk-1')).toBeNull();
  });

  it('renames a passkey through the dialog', async () => {
    http.expectOne('/api/auth/methods').flush(METHODS);
    await fixture.whenStable();
    button('account.settings.passkey.pk-1.rename').click();
    await fixture.whenStable();
    const input = document.querySelector(
      '[data-testid="account.settings.passkeyDialog.label"]',
    ) as HTMLInputElement;
    expect(input.value).toBe('MacBook');
    input.value = 'Work laptop';
    input.dispatchEvent(new Event('input'));
    (input.form as HTMLFormElement).dispatchEvent(new Event('submit'));
    const rename = http.expectOne({ method: 'PATCH', url: '/api/auth/passkeys/pk-1' });
    expect(rename.request.body).toEqual({ label: 'Work laptop' });
    rename.flush({ ...METHODS.passkeys[0], label: 'Work laptop' });
    await settle(fixture);
    expect(byTestId('account.settings.passkey.pk-1')!.textContent).toContain('Work laptop');
  });
});
