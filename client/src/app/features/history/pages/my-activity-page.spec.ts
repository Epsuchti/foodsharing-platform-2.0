import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { ApiErrorCode, Language, Role, Theme } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { demoPage, DEMO_HISTORY } from '../../../shared/history-timeline/history-demo';
import { MyActivityPageComponent } from './my-activity-page.component';

describe('MyActivityPageComponent', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'history.myActivity.title': 'Meine Aktivität',
            'history.myActivity.emptyTitle': 'Noch keine Aktivität',
            'history.action.USER.LOGIN': 'Angemeldet',
          },
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function logIn(role: Role) {
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: DEMO_HISTORY[0].subjectId,
        email: 'foodsaver@local',
        displayName: 'Sarah Müller',
        role,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });
  }

  async function create() {
    const fixture = TestBed.createComponent(MyActivityPageComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const list = () => el.querySelector('[data-testid="history.myActivity.list"]') as HTMLElement;
    return { fixture, el, list };
  }

  function answerActions() {
    http.expectOne((r) => r.url.endsWith('/api/history/actions')).flush([]);
  }

  it('loads the own activity and shows it (loading → success)', async () => {
    logIn(Role.Foodsaver);
    const { fixture, list, el } = await create();
    expect(list().getAttribute('data-state')).toBe('loading');
    answerActions();
    const request = http.expectOne((r) => r.url.endsWith('/api/history/me'));
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    request.flush(demoPage(DEMO_HISTORY.slice(0, 1)));
    await fixture.whenStable();
    expect(list().getAttribute('data-state')).toBe('success');
    expect(el.textContent).toContain('Meine Aktivität');
    expect(el.textContent).toContain('Angemeldet');
    // not ORGA: no export
    expect(el.querySelector('[data-testid="history.myActivity.export"]')).toBeNull();
  });

  it('shows the empty state', async () => {
    logIn(Role.Foodsaver);
    const { fixture, list } = await create();
    answerActions();
    http.expectOne((r) => r.url.endsWith('/api/history/me')).flush(demoPage([]));
    await fixture.whenStable();
    expect(list().getAttribute('data-state')).toBe('empty');
    expect(list().textContent).toContain('Noch keine Aktivität');
  });

  it('shows an error and recovers on retry', async () => {
    logIn(Role.Foodsaver);
    const { fixture, list } = await create();
    answerActions();
    http
      .expectOne((r) => r.url.endsWith('/api/history/me'))
      .flush(
        { code: ApiErrorCode.UnexpectedError, message: 'x', traceId: 'abc', retryable: true, fieldErrors: [] },
        { status: 500, statusText: 'Error' },
      );
    // the retry interceptor is not installed in tests: one request, one failure
    await fixture.whenStable();
    expect(list().getAttribute('data-state')).toBe('error');

    const retry = Array.from(list().querySelectorAll('button')).find((b) =>
      (b.textContent ?? '').includes('retry'),
    ) as HTMLButtonElement;
    retry.click();
    await fixture.whenStable();
    http.expectOne((r) => r.url.endsWith('/api/history/me')).flush(demoPage(DEMO_HISTORY.slice(0, 1)));
    await fixture.whenStable();
    expect(list().getAttribute('data-state')).toBe('success');
  });

  it('offers the CSV export to ORGA', async () => {
    logIn(Role.Orga);
    const { fixture, el } = await create();
    answerActions();
    http.expectOne((r) => r.url.endsWith('/api/history/me')).flush(demoPage([]));
    await fixture.whenStable();
    const button = el.querySelector('[data-testid="history.myActivity.export"] button') as HTMLButtonElement;
    const saved = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    button.click();
    await fixture.whenStable();
    const exportRequest = http.expectOne((r) => r.url.endsWith('/api/history/export'));
    expect(exportRequest.request.headers.get('Accept')).toBe('text/csv');
    expect(exportRequest.request.responseType).toBe('text');
    expect(exportRequest.request.params.get('subjectType')).toBe('USER');
    expect(exportRequest.request.params.get('subjectId')).toBe(DEMO_HISTORY[0].subjectId);
    exportRequest.flush('occurredAt,action\r\n');
    await fixture.whenStable();
    // the CSV is handed to the browser as a file download
    expect(saved).toHaveBeenCalled();
    saved.mockRestore();
  });
});
