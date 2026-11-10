import { HttpTestingController } from '@angular/common/http/testing';
import { WritableSignal, computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, beforeAll, vi } from 'vitest';

import {
  AdvertChannel,
  ApiErrorCode,
  GapCandidate,
  LinkedStoreWindowMode,
  OpenSlotAdvert,
  PickupPolicy,
  RequestPolicy,
  StoreAutomation,
} from '../../api';
import { I18nService } from '../../core/i18n/i18n.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { AUTOMATION_ROUTES } from './automation.routes';
import { AdvertsSection } from './sections/adverts.section';
import { PickupPolicySection } from './sections/pickup-policy.section';
import { RequestPolicySection } from './sections/request-policy.section';
import { StoreAutomationContext } from './store-automation.context';
import {
  advertValues,
  calendarGapDays,
  gapLinkFindings,
  gapLinkReach,
  gapPreview,
  joinAll,
  todayIn,
  appendPlaceholder,
  decisionTone,
  emptyAdvert,
  emptyPickupPolicy,
  emptyRequestPolicy,
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
    expect(decisionTone('LATE_CANCELLATION_REMINDER_SENT')).toBe('bad');
    expect(decisionTone('OCCUPIED_AT_TRIGGER')).toBe('neutral');
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

describe('gap list util (decision 151)', () => {
  it('previews 11 days around the pickup with the next possible date', () => {
    const preview = gapPreview('2026-11-08', 2);
    expect(preview.days).toHaveLength(11);
    // ±1 day: 0 free days, ±2: 1 free day (both < 2), ±3: 2 free days
    expect(preview.days.map((d) => d.result).join(' ')).toBe('OK OK OK BLOCKED BLOCKED PICKUP BLOCKED BLOCKED OK OK OK');
    expect(preview.days[0].date).toBe('2026-11-03');
    expect(preview.next).toBe('2026-11-11');
    expect(preview.actual).toBe(1);
    // Monday/Wednesday = 1 free day; the same and the next day = 0
    expect(calendarGapDays(2)).toBe(1);
    expect(calendarGapDays(1)).toBe(0);
    expect(gapPreview('2026-11-08', 0).days.filter((d) => d.result === 'BLOCKED')).toHaveLength(0);
  });

  it('finds blocking and partial link conflicts once per rule', () => {
    const rule = (owner: string, storeIds: string[], before = 0, after = 0, mode = LinkedStoreWindowMode.SameDay) => ({
      owner,
      storeIds,
      windowMode: mode,
      windowDaysBefore: before,
      windowDaysAfter: after,
    });
    expect(gapLinkReach(rule('a', ['b'], 3, 1, LinkedStoreWindowMode.LastDays))).toBe(2);
    const findings = gapLinkFindings('a', ['b'], 2, [rule('a', ['b']), rule('b', ['a']), rule('a', ['b', 'c'], 1, 1, LinkedStoreWindowMode.LastDays)]);
    expect(findings.map((f) => [f.store, f.blocking])).toEqual([
      ['b', true],
      ['b', false],
    ]);
    // a wide enough window and an empty list find nothing
    expect(gapLinkFindings('a', ['b'], 2, [rule('a', ['b'], 3, 3, LinkedStoreWindowMode.LastDays)])).toEqual([]);
    expect(gapLinkFindings('a', [], 2, [rule('a', ['b'])])).toEqual([]);
  });

  it('joins names and finds today in a time zone', () => {
    expect(joinAll(['A'], 'und')).toBe('A');
    expect(joinAll(['A', 'B', 'C'], 'und')).toBe('A, B und C');
    expect(todayIn('Europe/Zurich', new Date('2026-11-01T23:30:00Z'))).toBe('2026-11-02');
    expect(todayIn('America/New_York', new Date('2026-11-02T03:30:00Z'))).toBe('2026-11-01');
  });
});

describe('pickup policy section: gap list (decisions 151, 152)', () => {
  const S = '00000020-0001-4000-8000-000000000001';
  const B = '00000020-0001-4000-8000-000000000003';
  const C = '00000020-0001-4000-8000-000000000005';
  const D = '00000020-0001-4000-8000-000000000012';
  const automation = signal<StoreAutomation | undefined>(undefined);
  let http: HttpTestingController;
  let fixture: ComponentFixture<PickupPolicySection>;
  const el = () => fixture.nativeElement as HTMLElement;
  const all = (testId: string) => [...el().querySelectorAll(`[data-testid="${testId}"]`)] as HTMLElement[];
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const candidates: GapCandidate[] = [
    { store: { id: B, name: 'Migros Limmatplatz' }, automationActive: false, inOtherList: false, mayEdit: false, links: [] },
    {
      store: { id: C, name: 'Coop Stauffacher' },
      automationActive: true,
      inOtherList: false,
      mayEdit: true,
      links: [{ storeIds: [S], windowMode: LinkedStoreWindowMode.SameDay, windowDaysBefore: 0, windowDaysAfter: 0 }],
    },
    { store: { id: D, name: 'Coop Wiedikon' }, automationActive: true, inOtherList: false, mayEdit: true, links: [] },
  ];
  const open = async (policy: PickupPolicy) => {
    automation.set({ pickupPolicy: policy, adverts: [], permissions: { mayEdit: true } } as unknown as StoreAutomation);
    fixture = TestBed.createComponent(PickupPolicySection);
    await settle();
    http.expectOne((r) => r.url.endsWith(`/stores/${S}/automation/gap-candidates`)).flush(candidates);
    await settle();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting(),
        provideOptimus(),
        {
          provide: StoreAutomationContext,
          useValue: {
            storeId: signal(S),
            regionId: signal(undefined),
            data: automation,
            mayEdit: computed(() => true),
            timeZone: signal('Europe/Zurich'),
            store: { data: signal({ id: S, name: 'Coop Bahnhofbrücke' }) },
            reload: vi.fn(),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(I18nService).useCatalog({
      'automation.policy.gapAnd': 'und',
      'automation.policy.gapNote': 'Liste: {stores}',
      'automation.policy.gapOwnAlso': 'Eigener Abstand zusätzlich.',
      'automation.policy.gapForbidden': 'Keine Berechtigung für {store}',
      'automation.policy.gapPreview.message': '{date} {store} {stores} {gap} {actual}',
    });
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  interface Section { setGapMembers(ids: string[]): void; draft: WritableSignal<PickupPolicy | null> }
  const section = () => fixture.componentInstance as unknown as Section;
  const saveButton = () => el().querySelector('[data-testid="automation.policy.save"] button') as HTMLButtonElement;
  const input = (testId: string) => el().querySelector(`[data-testid="${testId}"] input`) as HTMLInputElement;
  const lockedGroup: PickupPolicy = {
    ...emptyPickupPolicy(),
    enabled: true,
    gapRuleEnabled: true,
    minimumGapDays: 3,
    gapStoreIds: [B],
    groupMinimumGapDays: 2,
    gapStores: [{ store: { id: B, name: 'Migros Limmatplatz' }, automationActive: false, mayEdit: false }],
    version: 1,
  };

  it('shows a locked member without remove button, the note, the no-automation warning and locks the group gap only', async () => {
    await open(lockedGroup);
    const [row] = all('automation.policy.gapMember');
    expect(row.dataset['locked']).toBe('true');
    expect(row.querySelector('[data-testid="automation.policy.gapMember.remove"]')).toBeNull();
    expect(all('automation.policy.gapLockedLeave')).toHaveLength(1);
    expect(all('automation.policy.gapNote')[0].textContent).toContain('Liste: Coop Bahnhofbrücke und Migros Limmatplatz');
    expect(all('automation.policy.gapNote')[0].textContent).toContain('Eigener Abstand zusätzlich.');
    expect(all('automation.policy.gapWarning').map((w) => w.dataset['kind'])).toEqual(['NO_AUTOMATION']);
    // the own gap is never locked by members; the group gap is
    expect(input('automation.policy.gapDays').disabled).toBe(false);
    expect(input('automation.policy.groupGapDays').disabled).toBe(true);
    // two previews: own (single-store text, own gap 3) and group (group text, group gap 2)
    expect(all('automation.policy.gapPreview.day')).toHaveLength(11);
    expect(all('automation.policy.gapPreview.day').filter((d) => d.dataset['result'] === 'BLOCKED')).toHaveLength(6);
    expect(all('automation.policy.groupGapPreview.day').filter((d) => d.dataset['result'] === 'BLOCKED')).toHaveLength(4);
    expect(all('automation.policy.gapPreview.message')[0].textContent).not.toContain('Migros Limmatplatz');
    expect(all('automation.policy.groupGapPreview.message')[0].textContent).toContain('Migros Limmatplatz');
    expect(all('automation.policy.gapStatus')[0].dataset['status']).toBe('on');
    // a locked member: the leave action is offered even with the own rule on
    expect(all('automation.policy.gapLeave')).toHaveLength(1);
  });

  it('a fully listed same-day link blocks save, also with the own rule off; the list is still sent', async () => {
    await open({ ...emptyPickupPolicy(), enabled: true, gapRuleEnabled: true, minimumGapDays: 2, version: 1 });
    section().setGapMembers([C]);
    await settle();
    const warnings = all('automation.policy.gapWarning');
    expect(warnings.map((w) => w.dataset['kind'])).toEqual(['CONFLICT']);
    expect(warnings[0].dataset['store']).toBe(C);
    expect(saveButton().disabled).toBe(true);

    // decision 152: switching the own rule off keeps the list, so the conflict stays
    section().draft.update((p) => ({ ...p!, gapRuleEnabled: false }));
    await settle();
    expect(all('automation.policy.gapWarning').map((w) => w.dataset['kind'])).toEqual(['CONFLICT']);
    expect(all('automation.policy.gapMember')).toHaveLength(1);
    expect(saveButton().disabled).toBe(true);

    // with D instead of C: the list and the group gap are sent with the own rule off
    section().setGapMembers([D]);
    await settle();
    saveButton().click();
    await settle();
    const put = http.expectOne((r) => r.method === 'PUT');
    expect(put.request.body.gapRuleEnabled).toBe(false);
    expect(put.request.body.gapStoreIds).toEqual([D]);
    expect(put.request.body.groupMinimumGapDays).toBe(2);
    put.flush({ ...emptyPickupPolicy(), version: 2 });
  });

  it('prefills the group gap with max(1, own gap) and shows the status badge', async () => {
    await open({ ...emptyPickupPolicy(), enabled: true, gapRuleEnabled: false, minimumGapDays: 0, version: 1 });
    expect(all('automation.policy.gapStatus')[0].dataset['status']).toBe('off');
    expect(all('automation.policy.gapPreview')).toHaveLength(0);
    section().setGapMembers([D]);
    await settle();
    expect(section().draft()!.groupMinimumGapDays).toBe(1);
    expect(all('automation.policy.gapStatus')[0].dataset['status']).toBe('on');
    expect(all('automation.policy.groupGapPreview')).toHaveLength(1);
    // the own gap does not follow later
    section().draft.update((p) => ({ ...p!, minimumGapDays: 4 }));
    section().setGapMembers([D, C]);
    expect(section().draft()!.groupMinimumGapDays).toBe(1);
    // a new group starts from the own gap
    section().setGapMembers([]);
    section().setGapMembers([D]);
    expect(section().draft()!.groupMinimumGapDays).toBe(4);
  });

  it('rule cards are off while the master switch is off, with a hint; on again with the master (review fix A7)', async () => {
    await open({
      ...emptyPickupPolicy(),
      enabled: false,
      gapRuleEnabled: true,
      minimumGapDays: 2,
      experienceRuleEnabled: true,
      publicHolidayRuleEnabled: false,
      version: 1,
    });
    expect(all('automation.policy.gapStatus')[0].dataset['status']).toBe('off');
    expect(all('automation.policy.gapStatus')[0].textContent).toContain('automation.policy.gapStatus.off');
    // gap and experience are switched on themselves: both say why they do not apply; holidays (off) says nothing
    expect(all('automation.policy.inactiveHint').map((h) => h.dataset['rule'])).toEqual(['GAP', 'EXPERIENCE']);
    expect(all('automation.policy.member')).toHaveLength(0);

    section().draft.update((p) => ({ ...p!, enabled: true }));
    await settle();
    expect(all('automation.policy.gapStatus')[0].dataset['status']).toBe('on');
    expect(all('automation.policy.inactiveHint')).toHaveLength(0);
  });

  it('a group gap below 1 blocks save, whatever the own switch says', async () => {
    await open({ ...emptyPickupPolicy(), gapRuleEnabled: false, minimumGapDays: 0, version: 1 });
    section().setGapMembers([D]);
    section().draft.update((p) => ({ ...p!, groupMinimumGapDays: 0 }));
    await settle();
    expect(saveButton().disabled).toBe(true);
    section().draft.update((p) => ({ ...p!, groupMinimumGapDays: 1 }));
    await settle();
    expect(saveButton().disabled).toBe(false);
  });

  it('offers the leave action in a saved group with the own rule off; it empties the list', async () => {
    const saved: PickupPolicy = {
      ...lockedGroup,
      gapStores: [{ store: { id: B, name: 'Migros Limmatplatz' }, automationActive: false, mayEdit: true }],
    };
    await open(saved);
    expect(all('automation.policy.gapLeave')).toHaveLength(0);
    section().draft.update((p) => ({ ...p!, gapRuleEnabled: false }));
    await settle();
    const leave = all('automation.policy.gapLeave');
    expect(leave).toHaveLength(1);
    (leave[0].querySelector('button') as HTMLButtonElement).click();
    await settle();
    expect(all('automation.policy.gapMember')).toHaveLength(0);
    expect(all('automation.policy.gapLeave')).toHaveLength(0);
    saveButton().click();
    await settle();
    const put = http.expectOne((r) => r.method === 'PUT');
    expect(put.request.body.gapStoreIds).toEqual([]);
    expect('groupMinimumGapDays' in put.request.body).toBe(false);
    put.flush({ ...emptyPickupPolicy(), version: 2 });
  });

  it('names the store of a 403', async () => {
    await open({ ...emptyPickupPolicy(), enabled: true, gapRuleEnabled: true, minimumGapDays: 2, version: 1 });
    (fixture.componentInstance as unknown as { setGapMembers(ids: string[]): void }).setGapMembers([D]);
    await settle();
    expect(all('automation.policy.gapWarning')).toHaveLength(0);
    (el().querySelector('[data-testid="automation.policy.save"] button') as HTMLButtonElement).click();
    await settle();
    const put = http.expectOne((r) => r.method === 'PUT');
    expect(put.request.body.gapStoreIds).toEqual([D]);
    put.flush(apiHttpError(403, ApiErrorCode.AccessDenied, { args: { storeId: D } }).error, { status: 403, statusText: 'Forbidden' });
    await settle();
    const error = all('automation.policy.error')[0];
    expect(error.dataset['code']).toBe('ACCESS_DENIED');
    expect(error.textContent).toContain('Keine Berechtigung für Coop Wiedikon');
    expect(all('automation.policy.gapMember')[0].dataset['error']).toBe('true');
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

  it('cleaning Fairteiler (FS2): toggles a candidate and saves the selection', async () => {
    const wiedikon = { id: 'f1', name: 'Fairteiler Wiedikon' };
    const oerlikon = { id: 'f3', name: 'Fairteiler Oerlikon' };
    await harness.navigateByUrl(`/regions/${R}/automation/cleaning-fsps`);
    region();
    await settle();
    http.expectOne(`/api/regions/${R}/cleaning-food-share-points`).flush({ selected: [wiedikon], candidates: [oerlikon, wiedikon] });
    await settle();
    const rows = harness.routeNativeElement!.querySelectorAll('[data-testid="automation.cleaningFsps.row"]');
    expect(rows.length).toBe(2);
    expect(q('[data-fsp="Fairteiler Wiedikon"]')?.getAttribute('data-selected')).toBe('true');
    expect((q('[data-testid="automation.cleaningFsps.save"] button') as HTMLButtonElement).disabled).toBe(true);
    (q('[data-fsp="Fairteiler Oerlikon"] [data-testid="automation.cleaningFsps.toggle"] input') as HTMLInputElement).click();
    await settle();
    (q('[data-testid="automation.cleaningFsps.save"] button') as HTMLButtonElement).click();
    const save = http.expectOne((r) => r.method === 'PUT' && r.url === `/api/regions/${R}/cleaning-food-share-points`);
    expect(save.request.body.fspIds).toEqual(['f1', 'f3']);
    save.flush({ selected: [oerlikon, wiedikon], candidates: [oerlikon, wiedikon] });
    await settle();
    http.expectOne(`/api/regions/${R}/cleaning-food-share-points`).flush({ selected: [oerlikon, wiedikon], candidates: [oerlikon, wiedikon] });
  });

  it('cleaning Fairteiler (FS2): empty state without candidates', async () => {
    await harness.navigateByUrl(`/regions/${R}/automation/cleaning-fsps`);
    region();
    await settle();
    http.expectOne(`/api/regions/${R}/cleaning-food-share-points`).flush({ selected: [], candidates: [] });
    await settle();
    expect(q('[data-testid="automation.cleaningFsps.state"]')?.getAttribute('data-state')).toBe('empty');
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
    channels: [AdvertChannel.Telegram],
    telegramMessages: ['Noch Platz bei {{storeName}}'],
  };
  const automation = signal<StoreAutomation | undefined>(undefined);
  const reload = vi.fn();
  let http: HttpTestingController;
  let fixture: ComponentFixture<AdvertsSection>;
  const q = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector);
  const runButton = () => q('[data-testid="automation.adverts.run"] button') as HTMLButtonElement;
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

  it('blocks run while a draft is unsaved or the save is in flight; there is no dry run (decision 167)', async () => {
    expect(runButton().disabled).toBe(false);
    expect(q('[data-testid="automation.adverts.runDry"]')).toBeNull();
    expect(q('[data-testid="automation.adverts.dryRun"]')).toBeNull();
    expect(q('[data-testid="automation.adverts.saveFirst"]')).toBeNull();

    // the user changes the trigger: unsaved
    section().drafts.update((list) => list.map((a) => ({ ...a, triggerHoursBefore: 48 })));
    await settle();
    expect(runButton().disabled).toBe(true);
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
    expect(q('[data-testid="automation.adverts.saveFirst"]')).toBeNull();
  });

  it('run now sends without a dryRun parameter', async () => {
    runButton().click();
    await settle();
    const run = http.expectOne((r) => r.method === 'POST' && r.url === `/api/stores/${S}/automation/adverts/run`);
    expect(run.request.params.keys()).toEqual([]);
    run.flush({ evaluated: 1, confirmed: 1, declined: 0, skipped: 0, deferred: 0, failed: 0, messages: [] });
    await settle();
    http.match((r) => r.url === `/api/stores/${S}/automation/adverts/planned`).forEach((r) => r.flush([]));
    await settle();
    expect(q('[data-testid="automation.adverts.runResult"]')).not.toBeNull();
  });
});

/**
 * Pickup policy and request policy (decision 167): one An/Aus switch, no dry run, no "test" / "run now"; the draft is
 * unsaved while it differs, while the save is in flight and until the saved state is reloaded.
 */
describe('policy sections: one switch, no dry run', () => {
  const S = '00000020-0001-4000-8000-000000000006';
  const savedPickup: PickupPolicy = { ...emptyPickupPolicy(), enabled: true, gapRuleEnabled: true, version: 1 };
  const savedRequest: RequestPolicy = { ...emptyRequestPolicy(), enabled: true };
  const automation = signal<StoreAutomation | undefined>(undefined);
  const reload = vi.fn();
  let http: HttpTestingController;
  let fixture: ComponentFixture<unknown>;
  const q = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector);
  const button = (testId: string) => q(`[data-testid="${testId}"] button`) as HTMLButtonElement;
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const state = (pickupPolicy: PickupPolicy, requestPolicy: RequestPolicy) =>
    ({ pickupPolicy, requestPolicy, adverts: [], permissions: { mayEdit: true } }) as unknown as StoreAutomation;

  beforeEach(() => {
    automation.set(state(structuredClone(savedPickup), structuredClone(savedRequest)));
    reload.mockReset();
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting(),
        provideOptimus(),
        {
          provide: StoreAutomationContext,
          useValue: {
            storeId: signal(S),
            regionId: signal(undefined),
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
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const cases = [
    {
      name: 'pickup policy',
      prefix: 'automation.policy',
      url: `/api/stores/${S}/automation/pickup-policy`,
      create: () => TestBed.createComponent(PickupPolicySection),
      edit: () => {
        const draft = (fixture.componentInstance as { draft: WritableSignal<PickupPolicy | null> }).draft;
        draft.update((p) => ({ ...p!, minimumGapDays: 5 }));
      },
      saved: (p: PickupPolicy, r: RequestPolicy) => state({ ...p, minimumGapDays: 5 }, r),
      body: () => ({ ...savedPickup, minimumGapDays: 5, version: 2 }),
    },
    {
      name: 'request policy',
      prefix: 'automation.request',
      url: `/api/stores/${S}/automation/request-policy`,
      create: () => TestBed.createComponent(RequestPolicySection),
      edit: () => {
        const draft = (fixture.componentInstance as { draft: WritableSignal<RequestPolicy | null> }).draft;
        draft.update((p) => ({ ...p!, maximumDistanceKm: 9 }));
      },
      saved: (p: PickupPolicy, r: RequestPolicy) => state(p, { ...r, maximumDistanceKm: 9 }),
      body: () => ({ ...savedRequest, maximumDistanceKm: 9 }),
    },
  ];

  for (const c of cases) {
    it(`${c.name}: has no dry run and no run buttons; saving sends no dryRun`, async () => {
      fixture = c.create();
      await settle();
      // the gap card loads its store candidates (decision 151)
      http.match((r) => r.url.endsWith('/automation/gap-candidates')).forEach((r) => r.flush([]));
      await settle();
      const root = () => q(`[data-testid="${c.prefix}.root"]`) as HTMLElement;
      for (const gone of ['dryRun', 'dryRunBanner', 'run', 'runDry', 'runResult', 'saveFirst', 'preview']) {
        expect(q(`[data-testid="${c.prefix}.${gone}"]`), gone).toBeNull();
      }
      expect(q(`[data-testid="${c.prefix}.enabled"]`)).not.toBeNull();
      expect(root().dataset['unsaved']).toBe('false');

      // the user changes a rule: unsaved
      c.edit();
      await settle();
      expect(root().dataset['unsaved']).toBe('true');

      // save in flight: still unsaved; the body has no dryRun
      button(`${c.prefix}.save`).click();
      await settle();
      const put = http.expectOne((r) => r.method === 'PUT' && r.url === c.url);
      expect(Object.keys(put.request.body as object)).not.toContain('dryRun');
      expect(root().dataset['unsaved']).toBe('true');

      // saved, but the store automation is not reloaded yet: still unsaved
      put.flush(c.body());
      await settle();
      expect(reload).toHaveBeenCalled();
      expect(root().dataset['unsaved']).toBe('true');

      // the reloaded state matches the draft
      automation.set(c.saved(structuredClone(savedPickup), structuredClone(savedRequest)));
      await settle();
      expect(root().dataset['unsaved']).toBe('false');
    });
  }

  it('pickup policy: the switch hint follows An/Aus', async () => {
    fixture = TestBed.createComponent(PickupPolicySection);
    await settle();
    http.match((r) => r.url.endsWith('/automation/gap-candidates')).forEach((r) => r.flush([]));
    await settle();
    const hint = () => q('[data-testid="automation.policy.enabledHint"]')?.textContent?.trim();
    expect(hint()).toBe('automation.policy.enabledHint');
    const draft = (fixture.componentInstance as { draft: WritableSignal<PickupPolicy | null> }).draft;
    draft.update((p) => ({ ...p!, enabled: false }));
    await settle();
    expect(hint()).toBe('automation.policy.disabledHint');
  });
});
