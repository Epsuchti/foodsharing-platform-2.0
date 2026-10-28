import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode, NewsletterStatus } from '../../api';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { NewsletterEditorPageComponent, progressPercent } from './pages/newsletter-editor-page.component';
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
