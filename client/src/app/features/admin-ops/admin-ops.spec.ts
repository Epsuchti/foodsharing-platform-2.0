import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode } from '../../api';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { IntegrationsPageComponent } from './pages/integrations-page.component';
import { JobsPageComponent } from './pages/jobs-page.component';
import { MailOutboxPageComponent } from './pages/mail-outbox-page.component';

describe('admin operation pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const qa = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'integrations', component: IntegrationsPageComponent },
            { path: 'jobs', component: JobsPageComponent },
            { path: 'outbox', component: MailOutboxPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('integrations: loading, cards, probe', async () => {
    await harness.navigateByUrl('/integrations');
    expect(q('[data-testid="adminOps.integrations.state"]')?.getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/admin/integrations').flush([
      { integration: 'SMTP', mode: 'LOG', state: 'CLOSED', failureCount: 0 },
      { integration: 'GEOCODING', mode: 'LIVE', state: 'OPEN', failureCount: 3, lastError: 'timeout', downSince: '2026-11-02T08:00:00Z' },
    ]);
    await settle();
    expect(qa('[data-testid="adminOps.integrations.card"]').length).toBe(2);
    expect(q('[data-integration="GEOCODING"]')?.getAttribute('data-state')).toBe('OPEN');
    (q('[data-integration="SMTP"] [data-testid="adminOps.integrations.probe"] button') as HTMLButtonElement).click();
    http.expectOne('/api/admin/integrations/SMTP/probe').flush({ integration: 'SMTP', mode: 'LOG', state: 'CLOSED' });
    await settle();
    http.expectOne('/api/admin/integrations').flush([]);
  });

  it('jobs: error state, run now reloads', async () => {
    await harness.navigateByUrl('/jobs');
    http.expectOne('/api/admin/jobs').flush(apiHttpError(503, ApiErrorCode.UnexpectedError).error, { status: 503, statusText: 'Error' });
    await settle();
    expect(q('[data-testid="adminOps.jobs.state"]')?.getAttribute('data-state')).toBe('error');
  });

  it('outbox: empty, status filter resets the page', async () => {
    await harness.navigateByUrl('/outbox');
    const first = http.expectOne((req) => req.url === '/api/admin/mail-outbox');
    expect(first.request.params.get('status')).toBeNull();
    first.flush({ items: [], page: { page: 0, size: 25, totalItems: 0, totalPages: 0 } });
    await settle();
    expect(q('[data-testid="adminOps.outbox.state"]')?.getAttribute('data-state')).toBe('empty');
    (harness.routeDebugElement!.componentInstance as { setStatus(v: string): void }).setStatus('FAILED');
    await settle();
    const filtered = http.expectOne((req) => req.url === '/api/admin/mail-outbox');
    expect(filtered.request.params.get('status')).toBe('FAILED');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush({ items: [], page: { page: 0, size: 25, totalItems: 0, totalPages: 0 } });
  });
});
