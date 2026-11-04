import { HttpTestingController } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { AdvertChannel, ApiErrorCode, LinkCandidate, LinkedStoreWindowMode, PickupPolicy, SignoutAlertChannel, StoreAutomation } from '../../api';
import { I18nService } from '../../core/i18n/i18n.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { RuleFormat } from '../pickups/rule-sentences';
import { emptyLinkRule, emptyPickupPolicy } from './automation.util';
import { lockedStores } from './components/link-rule.component';
import { StorePickerDialogComponent, groupByRegion, regionLabel } from './components/store-picker-dialog.component';
import { TelegramChatsComponent } from './components/telegram-chats.component';
import { activeRuleCount, gapSentence, linkSentence, ruleSentence } from './policy-sentences';
import { ChannelsSection } from './sections/channels.section';
import { PickupPolicySection } from './sections/pickup-policy.section';
import { StoreAutomationContext } from './store-automation.context';

/** Decision 165 S4: member sentences, picker, bidirectional lock, Kanäle. */

const S = '00000020-0001-4000-8000-000000000006';
const Y = '00000020-0001-4000-8000-000000000023';
const M = '00000020-0001-4000-8000-000000000001';

const fmt: RuleFormat = {
  day: (iso) => iso.slice(0, 10),
  date: (d) => d,
  list: (names, kind) => names.join(kind === 'and' ? ' und ' : ' oder '),
  t: (key, params) => `${key}${params && Object.keys(params).length ? JSON.stringify(params) : ''}`,
};

const candidate = (id: string, name: string, path: string[], mayEdit: boolean, automationEnabled = false): LinkCandidate => ({
  store: { id, name },
  city: 'Winterthur',
  regionPath: path,
  mayEdit,
  automationEnabled,
});

describe('policy sentences and picker helpers (decision 165 S4)', () => {
  it('builds the member sentence of one card from the summary lines', () => {
    expect(ruleSentence({ experience: true }, fmt)).toBe('pickups.rules.experience');
    expect(ruleSentence({ holidays: true }, fmt)).toBe('pickups.rules.holidays');
    expect(ruleSentence({ manualDates: ['2026-12-24'] }, fmt)).toBe('pickups.rules.manualDates{"dates":"2026-12-24"}');
    expect(ruleSentence({ cleaningMonths: 12 }, fmt)).toBe('pickups.rules.cleaning{"months":12}');
    expect(ruleSentence({}, fmt)).toBeUndefined();
    expect(gapSentence(7, { days: 3, stores: [{ id: S, name: 'A' }, { id: M, name: 'B' }] }, fmt)).toBe(
      'pickups.rules.gap{"days":7} pickups.rules.groupGap{"stores":"A und B","days":3}',
    );
    expect(gapSentence(undefined, undefined, fmt)).toBeUndefined();
  });

  it('describes a link rule with its window and its stores as "oder"', () => {
    const rule = { ...emptyLinkRule(), windowMode: LinkedStoreWindowMode.LastDays, windowDaysBefore: 0, windowDaysAfter: 3 };
    expect(linkSentence(rule, [{ id: Y, name: 'Y' }, { id: M, name: 'M' }], fmt)).toBe(
      'pickups.rules.linked{"stores":"Y oder M","window":"pickups.rules.window.after{\\"days\\":3}"}',
    );
    expect(linkSentence(rule, [], fmt)).toBeUndefined();
  });

  it('counts active rules for the status strip; 0 while the rules are off', () => {
    const policy: PickupPolicy = {
      ...emptyPickupPolicy(),
      enabled: true,
      gapRuleEnabled: true,
      minimumGapDays: 2,
      experienceRuleEnabled: true,
      linkedStores: [emptyLinkRule()],
      excludedDates: ['2026-12-24', '2026-12-31'],
    };
    expect(activeRuleCount(policy)).toBe(4);
    expect(activeRuleCount({ ...policy, gapRuleEnabled: false, gapStoreIds: [M] })).toBe(4);
    expect(activeRuleCount({ ...policy, enabled: false })).toBe(0);
    expect(activeRuleCount(undefined)).toBe(0);
  });

  it('groups results by the last two region names in server order', () => {
    expect(regionLabel(['Europa', 'Schweiz', 'Kanton Zürich', 'Winterthur'])).toBe('Kanton Zürich › Winterthur');
    const groups = groupByRegion([
      candidate('1', 'A', ['K', 'Zürich Stadt'], true),
      candidate('2', 'B', ['K', 'Winterthur'], false),
      candidate('3', 'C', ['K', 'Zürich Stadt'], false),
    ]);
    expect(groups.map((g) => [g.region, g.items.map((i) => i.store.name)])).toEqual([
      ['K › Zürich Stadt', ['A', 'C']],
      ['K › Winterthur', ['B']],
    ]);
  });

  it('locks bidirectional for every store the actor does not edit', () => {
    const rule = { storeIds: [Y, M], stores: [{ id: Y, name: 'Coop Winterthur' }, { id: M, name: 'Coop Bahnhofbrücke' }] };
    expect(lockedStores(rule, new Set([M])).map((s) => s.name)).toEqual(['Coop Winterthur']);
    expect(lockedStores(rule, new Set([Y, M]))).toEqual([]);
  });
});

describe('store picker dialog (decision 165 S4)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<StorePickerDialogComponent>;
  const body = () => document.body;
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(I18nService).useCatalog({});
    fixture = TestBed.createComponent(StorePickerDialogComponent);
    fixture.componentRef.setInput('storeId', S);
    fixture.componentRef.setInput('recent', [{ id: M, name: 'Coop Bahnhofbrücke' }]);
    fixture.componentRef.setInput('selected', [{ store: { id: M, name: 'Coop Bahnhofbrücke' } }]);
    fixture.componentRef.setInput('max', 2);
  });

  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    vi.useRealTimers();
    fixture.destroy();
  });

  const type = async (text: string) => {
    const input = body().querySelector('[data-testid="automation.picker.search"]') as HTMLInputElement;
    input.value = text;
    input.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(350);
    await settle();
  };
  const state = () => body().querySelector('[data-testid="automation.picker.state"]')?.getAttribute('data-state');

  it('shows the recent stores before typing, searches after 2 characters and groups by region', async () => {
    fixture.componentInstance.visible.set(true);
    await settle();
    expect(state()).toBe('idle');
    expect(body().querySelectorAll('[data-testid="automation.picker.result"]').length).toBe(1);
    expect(body().querySelectorAll('[data-testid="automation.picker.picked"]').length).toBe(1);

    await type('w');
    expect(state()).toBe('idle');
    http.expectNone((r) => r.url.includes('/link-candidates'));

    await type('wint');
    const req = http.expectOne((r) => r.url.endsWith(`/stores/${S}/automation/link-candidates`));
    expect(req.request.params.get('q')).toBe('wint');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({
      items: [candidate(Y, 'Coop Winterthur Bahnhof', ['Schweiz', 'Kanton Zürich', 'Winterthur'], false, true)],
      page: { page: 0, size: 20, totalItems: 1, totalPages: 1 },
    });
    await settle();
    expect(state()).toBe('success');
    expect(body().querySelector('[data-testid="automation.picker.count"]')?.textContent).toContain('automation.picker.count');
    expect(body().textContent).toContain('Kanton Zürich › Winterthur');
    const result = body().querySelector(`[data-testid="automation.picker.result"][data-store="${Y}"]`) as HTMLElement;
    expect(result.getAttribute('data-may-edit')).toBe('false');

    const done = vi.fn();
    fixture.componentInstance.done.subscribe(done);
    (result.querySelector('input') as HTMLInputElement).click();
    await settle();
    (body().querySelector('[data-testid="automation.picker.done"] button') as HTMLButtonElement).click();
    await settle();
    expect(done).toHaveBeenCalledWith([
      { store: { id: M, name: 'Coop Bahnhofbrücke' } },
      { store: { id: Y, name: 'Coop Winterthur Bahnhof' }, mayEdit: false, automationEnabled: true },
    ]);
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('shows empty and error states', async () => {
    fixture.componentInstance.visible.set(true);
    await settle();
    await type('Spar Oerlikon');
    http.expectOne((r) => r.url.includes('/link-candidates')).flush({ items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } });
    await settle();
    expect(state()).toBe('empty');

    await type('Spar Oerlikon 2');
    http
      .expectOne((r) => r.url.includes('/link-candidates'))
      .flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
    await settle();
    expect(state()).toBe('error');
  });
});

describe('pickup policy section: picker and bidirectional lock (decision 165 S4)', () => {
  const automation = signal<StoreAutomation | undefined>(undefined);
  let http: HttpTestingController;
  let fixture: ComponentFixture<PickupPolicySection>;
  const el = () => fixture.nativeElement as HTMLElement;
  const settle = async () => {
    await fixture.whenStable();
    fixture.detectChanges();
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
            store: { data: signal({ id: S, name: "Babu's Bakery" }) },
            reload: vi.fn(),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(I18nService).useCatalog({ 'automation.policy.bidiLocked': 'Keine Berechtigung: du verwaltest {store} nicht' });
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const open = async (policy: PickupPolicy) => {
    automation.set({ pickupPolicy: policy, adverts: [], permissions: { mayEdit: true }, cleaningMonths: 12 } as unknown as StoreAutomation);
    fixture = TestBed.createComponent(PickupPolicySection);
    await settle();
    http.match((r) => r.url.endsWith('/automation/gap-candidates')).forEach((r) => r.flush([]));
    await settle();
  };

  const saved: PickupPolicy = {
    ...emptyPickupPolicy(),
    enabled: true,
    experienceRuleEnabled: true,
    linkedStores: [
      {
        ...emptyLinkRule(),
        id: '00000000-0000-4000-8000-0000000000aa',
        storeIds: [Y],
        stores: [{ id: Y, name: 'Coop Winterthur Bahnhof' }],
        editableStoreIds: [],
      },
    ],
    version: 1,
  };

  it('locks bidirectional for a linked store the actor does not edit and shows the member sentences', async () => {
    await open(structuredClone(saved));
    const lock = el().querySelector('[data-testid="automation.policy.link.0.bidiLocked"]');
    expect(lock?.textContent).toContain('Coop Winterthur Bahnhof');
    const bidi = el().querySelector('[data-testid="automation.policy.link.0.bidirectional"] input') as HTMLInputElement;
    expect(bidi.disabled).toBe(true);
    expect(el().querySelector('[data-testid="automation.policy.link.member"]')?.textContent).toContain('pickups.rules.linked');
    expect(el().querySelector('[data-testid="automation.policy.member"][data-rule="EXPERIENCE"]')).not.toBeNull();
    // Frist nach Austragung lives in "Erweitert"
    expect(el().querySelector('[data-testid="automation.policy.link.0.advanced"] [data-testid="automation.policy.link.0.grace"]')).not.toBeNull();
  });

  it('the picker replaces the rule stores; a store the actor edits unlocks bidirectional', async () => {
    await open(structuredClone(saved));
    (el().querySelector('[data-testid="automation.policy.pickStore"] button') as HTMLButtonElement).click();
    await settle();
    const section = fixture.componentInstance as unknown as { applyPicked(p: unknown[]): void; draft: () => PickupPolicy };
    section.applyPicked([{ store: { id: M, name: 'Coop Bahnhofbrücke' }, mayEdit: true }]);
    await settle();
    expect(section.draft().linkedStores[0].storeIds).toEqual([M]);
    expect(el().querySelector('[data-testid="automation.policy.link.0.bidiLocked"]')).toBeNull();
    const bidi = el().querySelector('[data-testid="automation.policy.link.0.bidirectional"] input') as HTMLInputElement;
    expect(bidi.disabled).toBe(false);
    expect(el().querySelector(`[data-testid="automation.policy.link.store"][data-store="${M}"]`)).not.toBeNull();
  });
});

describe('Kanäle (decision 165 S4)', () => {
  const automation = signal<StoreAutomation | undefined>(undefined);
  const reload = vi.fn();
  let http: HttpTestingController;

  beforeEach(() => {
    reload.mockReset();
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting(),
        provideOptimus(),
        provideRouter([]),
        {
          provide: StoreAutomationContext,
          useValue: { storeId: signal(S), regionId: signal('00000010-0001-4000-8000-000000000040'), data: automation, reload },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(I18nService).useCatalog({});
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('lists the store chats, saves them to the store and lists who sends there', async () => {
    automation.set({
      adverts: [
        { advertNumber: 1, enabled: true, triggerHoursBefore: 24, channels: [AdvertChannel.Telegram] },
        { advertNumber: 2, enabled: true, triggerHoursBefore: 6, channels: [AdvertChannel.Push] },
      ],
      signoutAlert: { enabled: true, cutoffEnabled: false, maximumHoursBeforePickup: 72, channels: [SignoutAlertChannel.Telegram], telegramChatIds: [] },
    } as unknown as StoreAutomation);
    const fixture = TestBed.createComponent(ChannelsSection);
    const el = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    fixture.detectChanges();
    http.expectOne(`/api/stores/${S}/telegram-chats`).flush({ chats: [{ chatId: '-1002', title: 'Team', enabled: true }] });
    http.expectOne('/api/telegram/chats').flush([{ chatId: '-2001', title: 'Neue Gruppe', type: 'GROUP' }]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="automation.channels.state"]')?.getAttribute('data-state')).toBe('success');
    expect(el.querySelectorAll('[data-testid="automation.channels.chat"]').length).toBe(1);
    expect([...el.querySelectorAll('[data-testid="automation.channels.user"]')].map((u) => u.getAttribute('data-key'))).toEqual(['advert:1', 'signout']);

    (el.querySelector('[data-testid="automation.channels.remove"] button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="automation.channels.none"]')?.textContent).toContain('automation.channels.emptyHint');
    (el.querySelector('[data-testid="automation.channels.save"] button') as HTMLButtonElement).click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `/api/stores/${S}/telegram-chats`);
    expect(put.request.body).toEqual({ chats: [] });
    put.flush({ chats: [] });
    await fixture.whenStable();
    http.expectOne(`/api/stores/${S}/telegram-chats`).flush({ chats: [] });
    await fixture.whenStable();
    expect(reload).toHaveBeenCalled();
  });

  it('the shared component loads region chats for the region scope', async () => {
    const fixture = TestBed.createComponent(TelegramChatsComponent);
    fixture.componentRef.setInput('scope', { regionId: 'r1' });
    await fixture.whenStable();
    fixture.detectChanges();
    http.expectOne('/api/regions/r1/telegram-chats').flush({ chats: [] });
    http.expectOne('/api/telegram/chats').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="automation.telegram.state"]')?.getAttribute('data-state')).toBe('success');
  });
});
