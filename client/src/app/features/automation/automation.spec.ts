import { HttpTestingController } from '@angular/common/http/testing';
import { WritableSignal, computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, beforeAll, vi } from 'vitest';

import { AdvertChannel, ApiErrorCode, OpenSlotAdvert, StoreAutomation } from '../../api';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { AUTOMATION_ROUTES } from './automation.routes';
import { AdvertsSection } from './sections/adverts.section';
import { StoreAutomationContext } from './store-automation.context';
import {
  advertValues,
  appendPlaceholder,
  decisionTone,
  emptyAdvert,
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

  // Warm the lazy route chunks once, so the first test does not pay the cold import on a loaded machine.
  beforeAll(async () => {
    await Promise.all([import('./pages/region-automation-page.component'), import('./pages/region-sections')]);
  });

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

describe('adverts section: run now uses the saved adverts', () => {
  const S = '00000020-0001-4000-8000-000000000006';
  const saved: OpenSlotAdvert = {
    ...emptyAdvert(1),
    enabled: true,
    dryRun: false,
    channels: [AdvertChannel.Telegram],
    telegramMessages: ['Noch Platz bei {{storeName}}'],
  };
  const automation = signal<StoreAutomation | undefined>(undefined);
  const reload = vi.fn();
  let http: HttpTestingController;
  let fixture: ComponentFixture<AdvertsSection>;
  const q = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector);
  const runButton = () => q('[data-testid="automation.adverts.run"] button') as HTMLButtonElement;
  const runDryButton = () => q('[data-testid="automation.adverts.runDry"] button') as HTMLButtonElement;
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const section = () => fixture.componentInstance as unknown as { drafts: WritableSignal<OpenSlotAdvert[]> };
  const state = (advert: OpenSlotAdvert) => ({ adverts: [advert], permissions: { mayEdit: true } }) as unknown as StoreAutomation;

  beforeEach(async () => {
    automation.set(state(structuredClone(saved)));
    reload.mockReset();
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting(),
        provideOptimus(),
        {
          provide: StoreAutomationContext,
          useValue: {
            storeId: signal(S),
            data: automation,
            mayEdit: computed(() => !!automation()?.permissions.mayEdit),
            timeZone: signal('Europe/Zurich'),
            store: { data: signal({ id: S, name: "Babu's Bakery" }) },
            reload,
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdvertsSection);
    await settle();
    http.expectOne((r) => r.url === `/api/stores/${S}/automation/adverts/planned`).flush([]);
    http.match((r) => r.url.includes('telegram')).forEach((r) => r.flush({ chats: [] }));
    await settle();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('blocks run and dry run while a draft is unsaved or the save is in flight', async () => {
    expect(runButton().disabled).toBe(false);
    expect(runDryButton().disabled).toBe(false);
    expect(q('[data-testid="automation.adverts.saveFirst"]')).toBeNull();

    // the user changes the trigger: unsaved
    section().drafts.update((list) => list.map((a) => ({ ...a, triggerHoursBefore: 48 })));
    await settle();
    expect(runButton().disabled).toBe(true);
    expect(runDryButton().disabled).toBe(true);
    expect(q('[data-testid="automation.adverts.saveFirst"]')).not.toBeNull();

    // save in flight: still blocked
    (q('[data-testid="automation.adverts.save"] button') as HTMLButtonElement).click();
    await settle();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `/api/stores/${S}/automation/adverts/1`);
    expect(runButton().disabled).toBe(true);

    // saved, but the store automation is not reloaded yet: still blocked
    put.flush({ ...saved, triggerHoursBefore: 48 });
    await settle();
    expect(reload).toHaveBeenCalled();
    http.match((r) => r.url === `/api/stores/${S}/automation/adverts/planned`).forEach((r) => r.flush([]));
    await settle();
    expect(runButton().disabled).toBe(true);

    // the reloaded state matches the draft: run is possible again
    automation.set(state({ ...saved, triggerHoursBefore: 48 }));
    await settle();
    expect(runButton().disabled).toBe(false);
    expect(runDryButton().disabled).toBe(false);
    expect(q('[data-testid="automation.adverts.saveFirst"]')).toBeNull();
  });
});
