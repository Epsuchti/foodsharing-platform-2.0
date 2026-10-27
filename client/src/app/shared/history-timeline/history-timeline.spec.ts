import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { Observable, Subject, of } from 'rxjs';

import { ApiErrorCode, HistoryActorType, HistoryEntry, HistoryEntryPage, HistorySubjectType } from '../../api';
import { toApiErrorView } from '../../core/data/api-error';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { DEMO_HISTORY, demoActionsSource, demoPage } from './history-demo';
import { HistoryLabeler, humanize, historyVisual } from './history-labels';
import { HistoryTimelineComponent, dayKey, rangeToFilter } from './history-timeline.component';
import { HistoryFilterValue, HistoryPageRequest, HistorySource } from './history-timeline.types';

const TRANSLATIONS: Record<string, string> = {
  'history.action.USER.LOGIN':
    '{method, select, EMAIL {Mit E-Mail-Link angemeldet} PASSKEY {Mit Passkey angemeldet} other {Angemeldet}}',
  'history.action.USER.SETTINGS_CHANGED':
    '{field, select, theme {Darstellung geändert} other {Einstellungen geändert}}',
  'history.action.USER.ROLE_CHANGED': 'Rolle geändert',
  'shared.historyTimeline.unknownAction': 'Aktion „{action}“',
  'shared.historyTimeline.field.theme': 'Darstellung',
  'shared.historyTimeline.field.role': 'Rolle',
  'shared.historyTimeline.noValue': '(leer)',
  'shared.historyTimeline.redacted': '(verborgen)',
  'shared.historyTimeline.meta.label': 'Name',
  'shared.historyTimeline.meta.reason': 'Grund',
  'shared.historyTimeline.count': '{count, plural, one {# Eintrag} other {# Einträge}}',
  'shared.historyTimeline.emptyTitle': 'Noch keine Einträge',
  'shared.historyTimeline.emptyFilteredTitle': 'Keine Einträge für diese Filter',
  'shared.historyTimeline.byYou': 'von dir',
  'theme.dark': 'Dunkel',
  'theme.system': 'Wie mein Gerät',
  'common.role.FOODSAVER': 'Foodsaver',
  'common.role.FOODSHARER': 'Foodsharer',
  'errors.LOGIN_TOKEN_EXPIRED_OR_USED': 'Link abgelaufen',
};

function entry(partial: Partial<HistoryEntry> & Pick<HistoryEntry, 'action'>): HistoryEntry {
  return {
    id: 'e-' + partial.action,
    occurredAt: '2026-11-02T08:00:00Z',
    actorType: HistoryActorType.User,
    subjectType: HistorySubjectType.User,
    subjectId: 'u1',
    ...partial,
  };
}

/** A source whose pages the test answers by hand; records every request. */
function manualSource() {
  const calls: { filter: HistoryFilterValue; page: HistoryPageRequest; subject: Subject<HistoryEntryPage> }[] = [];
  const source: HistorySource = (filter, page): Observable<HistoryEntryPage> => {
    const subject = new Subject<HistoryEntryPage>();
    calls.push({ filter, page, subject });
    return subject.asObservable();
  };
  return {
    source,
    calls,
    last: () => calls[calls.length - 1],
    resolve(page: HistoryEntryPage) {
      const call = calls[calls.length - 1];
      call.subject.next(page);
      call.subject.complete();
    },
    reject(error: unknown) {
      calls[calls.length - 1].subject.error(error);
    },
  };
}

describe('HistoryLabeler', () => {
  let labeler: HistoryLabeler;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting({ translations: TRANSLATIONS }), provideOptimus()],
    });
    labeler = TestBed.inject(HistoryLabeler);
  });

  it('interpolates metadata into the action label', () => {
    expect(labeler.label(entry({ action: 'LOGIN', metadata: { method: 'PASSKEY' } }))).toBe(
      'Mit Passkey angemeldet',
    );
    expect(labeler.label(entry({ action: 'LOGIN' }))).toBe('Angemeldet');
    expect(
      labeler.label(entry({ action: 'SETTINGS_CHANGED', field: 'theme', oldValue: 'SYSTEM', newValue: 'DARK' })),
    ).toBe('Darstellung geändert');
  });

  it('falls back to a humanized label for unknown actions', () => {
    expect(labeler.label(entry({ action: 'STORE_SLOT_SIGNED_UP' }))).toBe('Aktion „Store slot signed up“');
    expect(labeler.actionLabel('STORE', 'MOVED_TO_JUMPER')).toBe('Aktion „Moved to jumper“');
    expect(labeler.actionLabel('USER', 'LOGIN')).toBe('Angemeldet');
    expect(humanize('displayName')).toBe('Display name');
  });

  it('translates known field values and marks empty and redacted ones', () => {
    expect(labeler.change(entry({ action: 'SETTINGS_CHANGED', field: 'theme', oldValue: 'SYSTEM', newValue: 'DARK' }))).toEqual({
      field: 'Darstellung',
      oldValue: 'Wie mein Gerät',
      newValue: 'Dunkel',
    });
    expect(labeler.change(entry({ action: 'ROLE_CHANGED', field: 'role', oldValue: 'FOODSHARER', newValue: 'FOODSAVER' }))).toEqual({
      field: 'Rolle',
      oldValue: 'Foodsharer',
      newValue: 'Foodsaver',
    });
    expect(labeler.change(entry({ action: 'FIELD_CHANGED', field: 'email', oldValue: '***', newValue: '***' }))).toEqual({
      field: 'Email',
      oldValue: '(verborgen)',
      newValue: '(verborgen)',
    });
    expect(labeler.value('nickname', undefined)).toBe('(leer)');
    expect(labeler.change(entry({ action: 'LOGIN' }))).toBeNull();
  });

  it('shows short facts with translated reasons', () => {
    const facts = labeler.facts(
      entry({ action: 'LOGIN_FAILED', metadata: { method: 'EMAIL', reason: 'LOGIN_TOKEN_EXPIRED_OR_USED', sessionId: 'x' } }),
    );
    expect(facts).toEqual([{ key: 'reason', label: 'Grund', value: 'Link abgelaufen' }]);
  });

  it('chooses icons by action with suffix rules for new actions', () => {
    expect(historyVisual('LOGIN_FAILED').tone).toBe('danger');
    expect(historyVisual('STORE_DELETED').tone).toBe('warning');
    expect(historyVisual('MEMBER_ADDED').tone).toBe('primary');
    expect(historyVisual('WHATEVER').icon).toBe('pi pi-history');
  });
});

describe('history timeline helpers', () => {
  it('groups by the Zürich calendar day', () => {
    expect(dayKey('2026-11-01T23:30:00Z')).toBe('2026-11-02');
    expect(dayKey('2026-11-02T08:00:00Z')).toBe('2026-11-02');
  });

  it('turns a date range into whole days', () => {
    const { from, to } = rangeToFilter([new Date(2026, 9, 1, 15), new Date(2026, 9, 3, 8)]);
    expect(new Date(from!).getDate()).toBe(1);
    expect(new Date(from!).getHours()).toBe(0);
    expect(new Date(to!).getDate()).toBe(3);
    expect(new Date(to!).getHours()).toBe(23);
    expect(rangeToFilter(null)).toEqual({});
    const single = rangeToFilter([new Date(2026, 9, 5, 12), null]);
    expect(new Date(single.to!).getDate()).toBe(5);
  });
});

describe('HistoryTimelineComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting({ translations: TRANSLATIONS }), provideOptimus()],
    });
  });

  async function create(source: HistorySource, inputs: Record<string, unknown> = {}) {
    const fixture = TestBed.createComponent(HistoryTimelineComponent);
    fixture.componentRef.setInput('source', source);
    fixture.componentRef.setInput('actionsSource', demoActionsSource);
    fixture.componentRef.setInput('layout', 'cards');
    fixture.componentRef.setInput('testId', 'test.history');
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const list = () => el.querySelector('[data-testid="test.history.list"]') as HTMLElement;
    const entries = () => Array.from(el.querySelectorAll('fs-history-entry'));
    return { fixture, el, list, entries };
  }

  it('shows the skeleton, then the entries grouped by day with labels', async () => {
    const manual = manualSource();
    const { fixture, list, entries, el } = await create(manual.source);
    expect(list().getAttribute('data-state')).toBe('loading');
    expect(manual.last().page).toEqual({ page: 0, size: 20 });
    expect(manual.last().filter).toEqual({ actions: [] });

    manual.resolve(
      demoPage([
        entry({ action: 'LOGIN', metadata: { method: 'EMAIL' } }),
        entry({ action: 'ROLE_CHANGED', occurredAt: '2026-10-21T10:00:00Z', field: 'role', oldValue: 'FOODSHARER', newValue: 'FOODSAVER' }),
      ]),
    );
    await fixture.whenStable();
    expect(list().getAttribute('data-state')).toBe('success');
    expect(entries().length).toBe(2);
    expect(entries()[0].textContent).toContain('Mit E-Mail-Link angemeldet');
    expect(entries()[1].querySelector('.he__new')?.textContent).toContain('Foodsaver');
    expect(el.querySelectorAll('.ht__day').length).toBe(2);
    expect(el.querySelector('[data-testid="test.history.count"]')?.textContent).toContain('2 Einträge');
  });

  it('shows the empty state', async () => {
    const { fixture, list } = await create(() => of(demoPage([])));
    await fixture.whenStable();
    expect(list().getAttribute('data-state')).toBe('empty');
    expect(list().textContent).toContain('Noch keine Einträge');
  });

  it('shows an error with retry that reloads', async () => {
    const manual = manualSource();
    const { fixture, list, entries } = await create(manual.source);
    manual.reject(apiHttpError(500, ApiErrorCode.UnexpectedError));
    await fixture.whenStable();
    expect(list().getAttribute('data-state')).toBe('error');

    const retry = Array.from(list().querySelectorAll('button')).find((b) =>
      /retry|shared\.errorState\.retry/i.test(b.textContent ?? ''),
    ) as HTMLButtonElement;
    expect(retry).toBeTruthy();
    retry.click();
    await fixture.whenStable();
    expect(manual.calls.length).toBe(2);
    manual.resolve(demoPage(DEMO_HISTORY.slice(0, 2)));
    await fixture.whenStable();
    expect(list().getAttribute('data-state')).toBe('success');
    expect(entries().length).toBe(2);
  });

  it('shows the forbidden state for 403', async () => {
    const manual = manualSource();
    const { fixture, list } = await create(manual.source);
    manual.reject(toApiErrorView(apiHttpError(403, ApiErrorCode.HistorySubjectNotReadable)));
    await fixture.whenStable();
    expect(list().getAttribute('data-state')).toBe('forbidden');
  });

  it('emits and applies filter changes and resets to page 0', async () => {
    const manual = manualSource();
    const { fixture } = await create(manual.source, { pageSize: 2 });
    manual.resolve(demoPage(DEMO_HISTORY.slice(0, 2), 0, 2, 6));
    await fixture.whenStable();

    const emitted: HistoryFilterValue[] = [];
    const pages: number[] = [];
    fixture.componentInstance.filterChange.subscribe((value) => emitted.push(value));
    fixture.componentInstance.pageChange.subscribe((value) => pages.push(value));
    const component = fixture.componentInstance as unknown as {
      onPage(event: { page: number }): void;
      onActionsChange(actions: string[]): void;
      onRangeChange(range: Date[] | null): void;
      resetFilters(): void;
    };

    component.onPage({ page: 2 });
    await fixture.whenStable();
    expect(pages).toEqual([2]);
    expect(manual.last().page).toEqual({ page: 2, size: 2 });

    component.onActionsChange(['LOGIN']);
    await fixture.whenStable();
    expect(emitted).toEqual([{ actions: ['LOGIN'] }]);
    expect(manual.last().filter).toEqual({ actions: ['LOGIN'] });
    expect(manual.last().page.page).toBe(0);

    // the first click of a range does not filter yet
    component.onRangeChange([new Date(2026, 9, 1)]);
    expect(emitted.length).toBe(1);
    component.onRangeChange([new Date(2026, 9, 1), new Date(2026, 9, 2)]);
    expect(emitted.length).toBe(2);
    expect(emitted[1].actions).toEqual(['LOGIN']);
    expect(emitted[1].from).toBeTruthy();
    expect(emitted[1].to).toBeTruthy();

    component.resetFilters();
    expect(emitted[2]).toEqual({ actions: [] });
  });

  it('shows the export button only with an export function and hides filters in compact mode', async () => {
    const { el, fixture } = await create(() => of(demoPage(DEMO_HISTORY)));
    expect(el.querySelector('[data-testid="test.history.export"]')).toBeNull();
    expect(el.querySelector('[data-testid="test.history.filter.actions"]')).not.toBeNull();

    fixture.componentRef.setInput('csvExport', () => of('a,b'));
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="test.history.export"]')).not.toBeNull();

    fixture.componentRef.setInput('compact', true);
    fixture.componentRef.setInput('csvExport', undefined);
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="test.history.filter.actions"]')).toBeNull();
  });

  it('says "by you" for the viewer’s own actions', async () => {
    const own = entry({ action: 'LOGIN', actor: { id: 'me', displayName: 'Sarah', type: HistoryActorType.User } });
    const other = entry({
      id: 'x',
      action: 'ROLE_CHANGED',
      actor: { id: 'orga', displayName: 'Marco Rossi', type: HistoryActorType.User },
    });
    const { fixture, entries } = await create(() => of(demoPage([own, other])), { viewerId: 'me' });
    await fixture.whenStable();
    expect(entries()[0].textContent).toContain('von dir');
    expect(entries()[0].querySelector('fs-user-chip')).toBeNull();
    expect(entries()[1].querySelector('fs-user-chip')?.textContent).toContain('Marco Rossi');
  });
});
