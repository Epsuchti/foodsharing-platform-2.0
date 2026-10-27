import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ApiErrorCode } from '../../api';
import { ApiErrorView } from '../../core/data/api-error';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { ErrorStateComponent } from './error-state.component';

const TRANSLATIONS = {
  'errors.UNEXPECTED_ERROR': 'Unerwarteter Fehler',
  'errors.TOO_MANY_REQUESTS': 'Versuche es in {seconds} Sekunden erneut.',
  'errors.UNKNOWN': 'Unbekannt',
  'shared.errorState.title.error': 'Das hat nicht geklappt',
  'shared.errorState.title.forbidden': 'Kein Zugriff',
  'shared.errorState.title.notFound': 'Nicht gefunden',
  'shared.errorState.title.offline': 'Du bist offline',
  'shared.errorState.text.offline': 'Wir laden automatisch neu.',
  'shared.errorState.text.forbidden': 'Nur für bestimmte Rollen.',
  'shared.errorState.text.notFound': 'Gibt es nicht mehr.',
  'shared.errorState.retry': 'Nochmals versuchen',
  'shared.errorState.details': 'Details',
  'shared.errorState.traceId': 'Fehler-ID',
  'shared.errorState.copy': 'Kopieren',
  'shared.errorState.back': 'Zurück',
};

function view(
  status: number,
  code: ApiErrorView['code'],
  extra: Partial<ApiErrorView> = {},
): ApiErrorView {
  return {
    status,
    code,
    message: code,
    traceId: 'trace-42',
    retryable: status >= 500 || status === 0,
    fieldErrors: [],
    ...extra,
  };
}

describe('ErrorStateComponent', () => {
  let fixture: ComponentFixture<ErrorStateComponent>;
  let host: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: provideDataStateTesting({ translations: TRANSLATIONS }),
    });
    fixture = TestBed.createComponent(ErrorStateComponent);
    host = fixture.nativeElement as HTMLElement;
  });

  async function render(inputs: Record<string, unknown>): Promise<void> {
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
  }

  const button = (testId: string) =>
    host.querySelector<HTMLElement>(
      `[data-testid="shared.errorState.${testId}"] button, button[data-testid="shared.errorState.${testId}"]`,
    );

  it('shows the translated error code with retry for retryable errors', async () => {
    await render({ error: view(500, ApiErrorCode.UnexpectedError) });
    expect(host.getAttribute('data-kind')).toBe('error');
    expect(host.textContent).toContain('Das hat nicht geklappt');
    expect(host.textContent).toContain('Unerwarteter Fehler');

    const retry = vi.fn();
    fixture.componentInstance.retry.subscribe(retry);
    (
      host.querySelector('[data-testid="shared.errorState.retry"] button') as HTMLButtonElement
    ).click();
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('interpolates retry-after seconds', async () => {
    await render({
      error: view(429, ApiErrorCode.TooManyRequests, { retryAfterSeconds: 30, retryable: true }),
    });
    expect(host.textContent).toContain('Versuche es in 30 Sekunden erneut.');
  });

  it('hides retry for non-retryable errors unless forced', async () => {
    await render({ error: view(409, ApiErrorCode.ConcurrentModification, { retryable: false }) });
    expect(host.querySelector('[data-testid="shared.errorState.retry"]')).toBeNull();
    await render({ retryable: true });
    expect(host.querySelector('[data-testid="shared.errorState.retry"]')).not.toBeNull();
  });

  it('reveals the trace id in the details expander', async () => {
    await render({ error: view(500, ApiErrorCode.UnexpectedError) });
    expect(host.querySelector('[data-testid="shared.errorState.traceId"]')).toBeNull();
    (button('details') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(host.querySelector('[data-testid="shared.errorState.traceId"]')?.textContent).toBe(
      'trace-42',
    );
  });

  it('renders the forbidden variant with who to ask and without retry', async () => {
    await render({
      error: view(403, ApiErrorCode.AccessDenied),
      askWho: 'Frag die Botschafterin.',
    });
    expect(host.getAttribute('data-kind')).toBe('forbidden');
    expect(host.textContent).toContain('Kein Zugriff');
    expect(host.textContent).toContain('Frag die Botschafterin.');
    expect(host.querySelector('[data-testid="shared.errorState.retry"]')).toBeNull();
  });

  it('renders the not-found variant with a back link', async () => {
    await render({ error: view(404, ApiErrorCode.ResourceNotFound), backLink: '/dashboard' });
    expect(host.getAttribute('data-kind')).toBe('notFound');
    expect(host.querySelector('[data-testid="shared.errorState.back"]')?.getAttribute('href')).toBe(
      '/dashboard',
    );
  });

  it('renders the offline variant with retry', async () => {
    await render({ error: view(0, 'NETWORK') });
    expect(host.getAttribute('data-kind')).toBe('offline');
    expect(host.textContent).toContain('Du bist offline');
    expect(host.querySelector('[data-testid="shared.errorState.retry"]')).not.toBeNull();
  });

  it('uses the host test id as prefix', async () => {
    await render({
      error: view(500, ApiErrorCode.UnexpectedError),
      testId: 'account.sessions.list',
    });
    expect(host.querySelector('[data-testid="account.sessions.list.retry"]')).not.toBeNull();
  });
});
