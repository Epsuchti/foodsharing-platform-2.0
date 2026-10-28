import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { HistoryActorType, HistoryEntry, HistoryEntryPage, HistorySubjectType } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { sessionInfo } from '../../auth/testing';
import { SecurityLogPageComponent } from './security-log-page.component';

const USER_ID = '00000001-0001-4000-8000-000000000006';

function entry(id: string, action: string, metadata: Record<string, unknown> = {}): HistoryEntry {
  return {
    id,
    occurredAt: '2026-11-02T08:00:00Z',
    actorType: HistoryActorType.User,
    actor: { id: USER_ID, displayName: 'Sarah Müller', type: HistoryActorType.User },
    subjectType: HistorySubjectType.User,
    subjectId: USER_ID,
    action,
    metadata,
  };
}

function page(items: HistoryEntry[], index: number, totalPages: number): HistoryEntryPage {
  return { items, page: { page: index, size: 20, totalItems: totalPages * 20, totalPages } };
}

const url = (index: number) => `/api/auth/security-log?page=${index}&size=20`;

describe('SecurityLogPageComponent', () => {
  let fixture: ComponentFixture<SecurityLogPageComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'history.action.USER.LOGIN':
              '{method, select, GOOGLE {Mit Google angemeldet} other {Angemeldet}}',
            'history.action.USER.PASSKEY_REGISTERED': 'Passkey hinzugefügt',
            'shared.historyTimeline.unknownAction': 'Aktion „{action}“',
            'shared.historyTimeline.meta.label': 'Name',
          },
        }),
        provideOptimus(),
      ],
    });
    TestBed.inject(SessionService).applySession(sessionInfo());
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SecurityLogPageComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const state = () => host.querySelector('[data-testid="account.securityLog.list"]')!;
  const labels = () =>
    [...host.querySelectorAll('[data-testid="account.securityLog.entry.label"]')].map((node) =>
      node.textContent?.trim(),
    );

  it('shows the shared timeline with labelled entries and no filters', async () => {
    expect(state().getAttribute('data-state')).toBe('loading');
    http
      .expectOne(url(0))
      .flush(
        page(
          [
            entry('1', 'LOGIN', { method: 'GOOGLE' }),
            entry('2', 'PASSKEY_REGISTERED', { label: 'Laptop' }),
            entry('3', 'SOMETHING_NEW'),
          ],
          0,
          1,
        ),
      );
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
    expect(labels()).toEqual([
      'Mit Google angemeldet',
      'Passkey hinzugefügt',
      'Aktion „Something new“',
    ]);
    expect(host.querySelector('[data-fact="label"]')?.textContent).toContain('Laptop');
    expect(host.querySelector('[data-testid="account.securityLog.filter.actions"]')).toBeNull();
  });

  it('shows the empty state', async () => {
    http.expectOne(url(0)).flush(page([], 0, 0));
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('empty');
  });

  it('shows the error with retry and recovers', async () => {
    http
      .expectOne(url(0))
      .flush(
        { code: 'UNEXPECTED_ERROR', message: 'x', traceId: 't', retryable: true },
        { status: 500, statusText: 'Error' },
      );
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('error');
    (
      host.querySelector('[data-testid="account.securityLog.list.retry"] button') as HTMLElement
    ).click();
    http.expectOne(url(0)).flush(page([entry('1', 'LOGOUT')], 0, 1));
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
  });

  it('pages through older entries', async () => {
    http.expectOne(url(0)).flush(page([entry('1', 'LOGOUT')], 0, 3));
    await fixture.whenStable();
    const next = host.querySelector(
      '[data-testid="account.securityLog.paginator"] .p-paginator-next',
    ) as HTMLElement;
    next.click();
    await fixture.whenStable();
    http.expectOne(url(1)).flush(page([entry('2', 'LOGIN')], 1, 3));
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
    expect(labels()).toHaveLength(1);
  });
});
