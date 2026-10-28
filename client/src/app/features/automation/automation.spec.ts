import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode } from '../../api';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { AUTOMATION_ROUTES } from './automation.routes';
import {
  advertValues,
  appendPlaceholder,
  decisionTone,
  fillDays,
  renderTemplate,
  toggle,
  unknownPlaceholders,
  ADVERT_PLACEHOLDERS,
} from './automation.util';

const R = '00000002-0001-4000-8000-000000000003';

describe('automation util', () => {
  it('renders placeholders and keeps unknown ones visible', () => {
    expect(renderTemplate('{{storeName}}: {{ freeSlots }} frei {{nope}}', { storeName: 'Coop', freeSlots: 2 })).toBe('Coop: 2 frei {{nope}}');
    expect(unknownPlaceholders('{{date}} {{foo}} {{foo}}', ADVERT_PLACEHOLDERS)).toEqual(['foo']);
    expect(appendPlaceholder('Hallo', 'link')).toBe('Hallo {{link}}');
    expect(appendPlaceholder('', 'link')).toBe('{{link}}');
  });

  it('maps decisions to badge tones', () => {
    expect(decisionTone('CONFIRMED')).toBe('ok');
    expect(decisionTone('WOULD_DECLINE')).toBe('dry');
    expect(decisionTone('DECLINED')).toBe('bad');
    expect(decisionTone('FAILED')).toBe('error');
    expect(decisionTone('SKIPPED')).toBe('neutral');
    expect(decisionTone('ADDED')).toBe('ok');
  });

  it('fills missing days with zero and toggles list values', () => {
    expect(fillDays([{ day: '2026-10-02', value: 3 }], '2026-10-01', '2026-10-03').map((p) => p.value)).toEqual([0, 3, 0]);
    expect(toggle(['A'], 'B', true)).toEqual(['A', 'B']);
    expect(toggle(['A', 'B'], 'A', false)).toEqual(['B']);
  });

  it('formats advert values in the store time zone', () => {
    const values = advertValues('2026-11-05T18:00:00Z', 2, 'Coop', 'https://x/s', 'de-CH', 'Europe/Zurich');
    expect(values['time']).toBe('19:00');
    expect(values['dateDe']).toBe('05.11.2026');
    expect(values['freeSlots']).toBe(2);
  });
});

describe('region automation page', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting(), provideRouter(AUTOMATION_ROUTES.map((route) => ({ ...route, canActivate: [] }))), provideOptimus()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const region = () => http.expectOne(`/api/regions/${R}`).flush({ id: R, name: 'Zürich' });

  it('holidays: loading then rows', async () => {
    await harness.navigateByUrl(`/regions/${R}/automation/holidays`);
    region();
    await settle();
    const req = http.expectOne((r) => r.url === `/api/regions/${R}/holidays`);
    expect(q('[data-testid="automation.holidays.state"]')?.getAttribute('data-state')).toBe('loading');
    req.flush([{ date: '2026-12-25', name: 'Weihnachten', scope: 'NATIONAL' }]);
    await settle();
    expect(harness.routeNativeElement!.querySelectorAll('[data-testid="automation.holidays.row"]').length).toBe(1);
  });

  it('audits: empty and error with retry', async () => {
    await harness.navigateByUrl(`/regions/${R}/automation/audits`);
    region();
    await settle();
    http
      .expectOne((r) => r.url === `/api/regions/${R}/automation/audits`)
      .flush({ items: [], page: { page: 0, size: 25, totalItems: 0, totalPages: 0 } });
    await settle();
    expect(q('[data-testid="automation.region.audits.state"]')?.getAttribute('data-state')).toBe('empty');

    (q('[data-testid="automation.region.audits.kind.PICKUP"]') as HTMLButtonElement).click();
    await settle();
    const failed = http.expectOne((r) => r.url === `/api/regions/${R}/automation/audits`);
    expect(failed.request.params.get('kind')).toBe('PICKUP');
    failed.flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
    await settle();
    expect(q('[data-testid="automation.region.audits.state"]')?.getAttribute('data-state')).toBe('error');
  });

  it('grouping: forbidden state', async () => {
    await harness.navigateByUrl(`/regions/${R}/automation/grouping`);
    region();
    await settle();
    http
      .expectOne((r) => r.url === `/api/regions/${R}/automation/pickup-count-grouping`)
      .flush(apiHttpError(403, ApiErrorCode.AccessDenied).error, { status: 403, statusText: 'Forbidden' });
    await settle();
    expect(q('[data-testid="automation.grouping.state"]')?.getAttribute('data-state')).toBe('forbidden');
  });
});
