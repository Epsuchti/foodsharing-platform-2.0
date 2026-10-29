import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { ApiErrorCode, NewsletterStatus, Role } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { sessionInfo } from '../auth/testing';
import { NewsletterEditorPageComponent, progressPercent } from './pages/newsletter-editor-page.component';
import { NewsletterListPageComponent } from './pages/newsletter-list-page.component';
import { UnsubscribePageComponent } from './pages/unsubscribe-page.component';

const ID = '00000004-0006-4000-8000-000000000009';

describe('newsletters', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting(),
        provideRouter(
          [
            { path: 'newsletters', component: NewsletterListPageComponent },
            { path: 'newsletters/:newsletterId', component: NewsletterEditorPageComponent },
            { path: 'newsletter/unsubscribe/:token', component: UnsubscribePageComponent },
          ],
          withComponentInputBinding(),
        ),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('progress percent', () => {
    expect(progressPercent(undefined)).toBe(0);
    expect(progressPercent({ total: 0, sent: 0, failed: 0, status: NewsletterStatus.Sent })).toBe(0);
    expect(progressPercent({ total: 8, sent: 5, failed: 1, status: NewsletterStatus.Sending })).toBe(75);
  });

  it('editor: draft is editable with live preview and recipient count', async () => {
    await harness.navigateByUrl(`/newsletters/${ID}`);
    expect(q('[data-testid="newsletters.editor.state"]')?.getAttribute('data-state')).toBe('loading');
    http.expectOne(`/api/newsletters/${ID}`).flush({ id: ID, subject: 'Hallo', bodyMarkdown: '**fett**', status: 'DRAFT', createdAt: '2026-11-02T08:00:00Z' });
    http.expectOne(`/api/newsletters/${ID}/recipient-count`).flush({ count: 7 });
    await settle();
    expect(q('[data-testid="newsletters.editor.recipients"]')?.getAttribute('data-count')).toBe('7');
    expect(q('[data-testid="newsletters.editor.preview"] strong')?.textContent).toBe('fett');
  });

  it('editor: error state', async () => {
    await harness.navigateByUrl(`/newsletters/${ID}`);
    http.expectOne(`/api/newsletters/${ID}`).flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
    http.expectOne(`/api/newsletters/${ID}/recipient-count`).flush({ count: 0 });
    await settle();
    expect(q('[data-testid="newsletters.editor.state"]')?.getAttribute('data-state')).toBe('error');
  });

  it('editor: an info mail shows its kind, the hint and its own confirmation text (A20)', async () => {
    await harness.navigateByUrl(`/newsletters/${ID}`);
    http
      .expectOne(`/api/newsletters/${ID}`)
      .flush({ id: ID, kind: 'INFO_MAIL', subject: 'Info', bodyMarkdown: 'Text', status: 'DRAFT', createdAt: '2026-11-02T08:00:00Z' });
    http.expectOne(`/api/newsletters/${ID}/recipient-count`).flush({ count: 12 });
    await settle();
    expect(q('[data-testid="newsletters.editor.kind"]')?.getAttribute('data-kind')).toBe('INFO_MAIL');
    expect(q('[data-testid="newsletters.editor.infoMailHint"]')).not.toBeNull();
    const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
    (q('[data-testid="newsletters.editor.queue"] button') as HTMLButtonElement).click();
    expect(confirm.mock.calls[0][0].header).toBe('newsletters.editor.queueTitleInfoMail');
    expect(confirm.mock.calls[0][0].message).toBe('newsletters.editor.queueTextInfoMail');
  });

  it('editor: a newsletter keeps the newsletter texts', async () => {
    await harness.navigateByUrl(`/newsletters/${ID}`);
    http.expectOne(`/api/newsletters/${ID}`).flush({ id: ID, subject: 'N', bodyMarkdown: 'Text', status: 'DRAFT', createdAt: '2026-11-02T08:00:00Z' });
    http.expectOne(`/api/newsletters/${ID}/recipient-count`).flush({ count: 3 });
    await settle();
    expect(q('[data-testid="newsletters.editor.kind"]')?.getAttribute('data-kind')).toBe('NEWSLETTER');
    expect(q('[data-testid="newsletters.editor.infoMailHint"]')).toBeNull();
    const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
    (q('[data-testid="newsletters.editor.queue"] button') as HTMLButtonElement).click();
    expect(confirm.mock.calls[0][0].header).toBe('newsletters.editor.queueTitle');
  });

  it('list: ORGA picks the kind in the global scope and creates an info mail', async () => {
    TestBed.inject(SessionService).applySession(sessionInfo({ role: Role.Orga }));
    await harness.navigateByUrl('/newsletters');
    http.expectOne((req) => req.url === '/api/newsletters').flush({ items: [], page: { page: 0, size: 50, totalItems: 0, totalPages: 0 } });
    await settle();
    expect(q('[data-testid="newsletters.list.kind"]')).not.toBeNull();
    const list = harness.routeDebugElement!.componentInstance as { kind: { set(v: string): void }; create: { run(): Promise<boolean> } };
    list.kind.set('INFO_MAIL');
    void list.create.run();
    const create = http.expectOne((req) => req.url === '/api/newsletters' && req.method === 'POST');
    expect(create.request.body).toMatchObject({ kind: 'INFO_MAIL' });
    expect(create.request.body.regionId).toBeUndefined();
    create.flush({ id: ID, kind: 'INFO_MAIL', subject: 'x', bodyMarkdown: 'y', status: 'DRAFT', createdAt: '2026-11-02T08:00:00Z' });
    await settle();
    http.match(() => true).forEach((req) => req.flush({}));
  });

  it('list: without ORGA there is no kind select', async () => {
    TestBed.inject(SessionService).applySession(sessionInfo());
    await harness.navigateByUrl('/newsletters');
    http.match(() => true).forEach((req) => req.flush({ items: [], page: { page: 0, size: 50, totalItems: 0, totalPages: 0 } }));
    await settle();
    expect(q('[data-testid="newsletters.list.kind"]')).toBeNull();
  });

  it('unsubscribe: confirm shows the done state', async () => {
    await harness.navigateByUrl('/newsletter/unsubscribe/tok');
    http.expectOne('/api/public/newsletter/unsubscribe/tok').flush({ email: 'j***@local' });
    await settle();
    (q('[data-testid="newsletters.unsubscribe.confirm"] button') as HTMLButtonElement).click();
    http.expectOne((req) => req.url === '/api/public/newsletter/unsubscribe/tok' && req.method !== 'GET').flush(null);
    await settle();
    expect(q('[data-testid="newsletters.unsubscribe.done"]')).not.toBeNull();
  });
});
