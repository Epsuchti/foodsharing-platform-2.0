import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { Confirmation } from '@openng/optimus-ui/api';
import { afterEach, vi } from 'vitest';

import { SessionView } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { settle } from '../../auth/testing';
import { SessionsPageComponent } from './sessions-page.component';

const CHROME_MAC =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36';
const SAFARI_IPHONE =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1';

const SESSIONS: SessionView[] = [
  {
    id: 's-current',
    current: true,
    createdAt: '2026-11-01T08:00:00Z',
    lastSeenAt: '2026-11-02T08:59:00Z',
    userAgent: CHROME_MAC,
    ipHint: 'a1b2c3d4',
  },
  {
    id: 's-phone',
    current: false,
    createdAt: '2026-10-20T08:00:00Z',
    lastSeenAt: '2026-10-30T18:00:00Z',
    userAgent: SAFARI_IPHONE,
  },
];

describe('SessionsPageComponent', () => {
  let fixture: ComponentFixture<SessionsPageComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;
  let confirmed: Confirmation[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'account.sessions.browserOn': '{browser} auf {os}' },
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    confirmed = [];
    vi.spyOn(TestBed.inject(ConfirmationService), 'confirm').mockImplementation((confirmation) => {
      confirmed.push(confirmation);
      confirmation.accept?.();
      return TestBed.inject(ConfirmationService);
    });
    fixture = TestBed.createComponent(SessionsPageComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const state = () => host.querySelector('[data-testid="account.sessions.list"]')!;
  const byTestId = (id: string) => host.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  it('shows a skeleton, then the devices with the current one marked', async () => {
    expect(state().getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/auth/sessions').flush(SESSIONS);
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
    const current = byTestId('account.sessions.item.s-current')!;
    expect(current.textContent).toContain('Chrome auf macOS');
    expect(current.querySelector('[data-testid="account.sessions.current"]')).not.toBeNull();
    expect(byTestId('account.sessions.item.s-phone')!.textContent).toContain('Safari auf iOS');
    expect(byTestId('account.sessions.logoutEverywhere')).not.toBeNull();
  });

  it('shows the empty state', async () => {
    http.expectOne('/api/auth/sessions').flush([]);
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('empty');
    expect(byTestId('account.sessions.logoutEverywhere')).toBeNull();
  });

  it('shows the error with retry and recovers', async () => {
    http
      .expectOne('/api/auth/sessions')
      .flush(
        { code: 'UNEXPECTED_ERROR', message: 'x', traceId: 't', retryable: true },
        { status: 500, statusText: 'Error' },
      );
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('error');
    (byTestId('account.sessions.list.retry')!.querySelector('button') as HTMLElement).click();
    http.expectOne('/api/auth/sessions').flush(SESSIONS);
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
  });

  it('revokes another device after confirmation', async () => {
    http.expectOne('/api/auth/sessions').flush(SESSIONS);
    await fixture.whenStable();
    (
      byTestId('account.sessions.item.s-phone.revoke')!.querySelector('button') as HTMLElement
    ).click();
    expect(confirmed).toHaveLength(1);
    http.expectOne({ method: 'DELETE', url: '/api/auth/sessions/s-phone' }).flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    await settle(fixture);
    expect(byTestId('account.sessions.item.s-phone')).toBeNull();
    expect(byTestId('account.sessions.onlyThis')).not.toBeNull();
  });

  it('logs out everywhere else', async () => {
    http.expectOne('/api/auth/sessions').flush(SESSIONS);
    await fixture.whenStable();
    (byTestId('account.sessions.logoutEverywhere')!.querySelector('button') as HTMLElement).click();
    http.expectOne({ method: 'POST', url: '/api/auth/sessions/revoke-others' }).flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    await settle(fixture);
    expect(byTestId('account.sessions.item.s-phone')).toBeNull();
    expect(byTestId('account.sessions.item.s-current')).not.toBeNull();
  });
});
