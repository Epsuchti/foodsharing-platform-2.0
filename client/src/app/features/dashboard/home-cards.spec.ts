import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import {
  ApiErrorCode,
  CooperationStatus,
  EventSummary,
  EventType,
  FreeSlotStatus,
  InvitationStatus,
  Language,
  PickupOption,
  Role,
  StoreListItem,
  TeamSearchStatus,
  Theme,
  UnitType,
  UserPickup,
  UserTeamStatus,
} from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { UpcomingEventsCardComponent } from '../events/components/upcoming-events-card.component';
import { HelpNeededCardComponent } from '../pickups/components/help-needed-card.component';
import { WaitingCardComponent } from './components/waiting-card.component';
import { YourStoresCardComponent } from './components/your-stores-card.component';
import { storesNeedingPeople, upcomingEvents, waitingRows } from './dashboard.util';
import { nextStepFor } from './pages/dashboard-page.component';

const tick = () => new Promise((resolve) => setTimeout(resolve));
const serverError = { status: 500, statusText: 'Server Error' };

const store = (over: Partial<StoreListItem>): StoreListItem => ({
  id: over.name ?? 's',
  name: 'Store',
  address: { street: 'Weg 1', postcode: '8000', city: 'Zürich' },
  cooperationStatus: CooperationStatus.CooperationEstablished,
  teamStatus: TeamSearchStatus.Open,
  region: { id: 'r1', slug: 'zh', name: 'Zürich', type: UnitType.City },
  membership: UserTeamStatus.Member,
  isManager: false,
  freeSlotStatus: FreeSlotStatus.Green,
  freeSlotCount: 0,
  ...over,
});

const pickup = (storeName: string, confirmed: boolean): UserPickup => ({
  store: { id: storeName, name: storeName },
  pickupAt: '2026-11-05T17:00:00Z',
  confirmed,
  slotCount: 2,
});

const option = (storeName: string, pickupAt: string, taken: number, total: number): PickupOption => ({
  store: { id: storeName, name: storeName },
  pickupAt,
  slotCount: total,
  occupiedSlots: Array.from({ length: taken }, (_, i) => ({ id: `u${i}`, displayName: `U${i}`, role: Role.Foodsaver, verified: true })),
  confirmedCount: 0,
});

const event = (name: string, startsAt: string, myStatus?: InvitationStatus): EventSummary => ({
  id: name,
  regionId: 'r1',
  name,
  type: EventType.Offline,
  startsAt,
  endsAt: startsAt,
  public: false,
  location: { name: 'Kanzlei' },
  myStatus,
  acceptedCount: 1,
  maybeCount: 0,
});

describe('home rules (owner feedback 2026-11-04)', () => {
  it('shows a next step only when something is missing', () => {
    // foodsharer: passkey, as before
    expect(nextStepFor({ role: Role.Foodsharer, avatarUrl: '/a.svg', phone: '+41' })?.id).toBe('passkey');
    expect(nextStepFor(null)?.id).toBe('passkey');
    // foodsaver and up: profile only without avatar or without phone
    expect(nextStepFor({ role: Role.Foodsaver, avatarUrl: '/a.svg', phone: '+41' })).toBeNull();
    expect(nextStepFor({ role: Role.StoreManager, avatarUrl: undefined, phone: '+41' })?.id).toBe('profile');
    expect(nextStepFor({ role: Role.Foodsaver, avatarUrl: '/a.svg', phone: undefined })?.link).toBe('/account/profile');
    // no "activity" step for ambassadors and orga any more
    expect(nextStepFor({ role: Role.Ambassador, avatarUrl: '/a.svg', phone: '+41' })).toBeNull();
    expect(nextStepFor({ role: Role.Orga, avatarUrl: '', phone: '+41' })?.id).toBe('profile');
  });

  it('lists the waiting rows in their order and only with a count', () => {
    const rows = waitingRows({
      stores: [
        store({ name: 'A', membership: UserTeamStatus.Applied }),
        store({ name: 'B', membership: UserTeamStatus.Invited }),
        store({ name: 'C', membership: UserTeamStatus.Invited }),
        store({ name: 'D' }),
      ],
      unreadConversations: 0,
      pickups: [pickup('X', false), pickup('Y', true)],
    });
    expect(rows.map((row) => [row.id, row.count, row.warn])).toEqual([
      ['invitations', 2, false],
      ['unconfirmed', 1, true],
      ['applications', 1, true],
    ]);
    expect(rows.find((row) => row.id === 'unconfirmed')).toMatchObject({ link: '/pickups', fragment: 'mine' });
    expect(waitingRows({ stores: null, unreadConversations: 3, pickups: null }).map((row) => row.id)).toEqual(['unread']);
    expect(waitingRows({ stores: [], unreadConversations: 0, pickups: [] })).toEqual([]);
  });

  it('keeps the next three events from now on, without "won\'t join"', () => {
    const now = new Date('2026-11-04T08:00:00Z');
    const list = [
      event('past', '2026-11-03T18:00:00Z', InvitationStatus.Accepted),
      event('later', '2026-12-01T17:30:00Z', InvitationStatus.Maybe),
      event('no', '2026-11-05T18:00:00Z', InvitationStatus.WontJoin),
      event('soon', '2026-11-05T18:00:00Z', InvitationStatus.Invited),
      event('next', '2026-11-14T09:00:00Z', InvitationStatus.Accepted),
      event('last', '2026-12-10T09:00:00Z', InvitationStatus.Accepted),
    ];
    expect(upcomingEvents(list, now).map((entry) => entry.name)).toEqual(['soon', 'next', 'later']);
  });

  it('lists my teams with free slots, most urgent first, at most five', () => {
    const list = [
      store({ name: 'green', freeSlotStatus: FreeSlotStatus.Green, freeSlotCount: 1 }),
      store({ name: 'red', freeSlotStatus: FreeSlotStatus.Red, freeSlotCount: 8 }),
      store({ name: 'full', freeSlotStatus: FreeSlotStatus.Red, freeSlotCount: 0 }),
      store({ name: 'jumper', membership: UserTeamStatus.WaitingList, freeSlotStatus: FreeSlotStatus.Red, freeSlotCount: 3 }),
      store({ name: 'applied', membership: UserTeamStatus.Applied, freeSlotCount: 3 }),
      store({ name: 'coord', membership: UserTeamStatus.Coordinator, freeSlotStatus: FreeSlotStatus.Orange, freeSlotCount: 2 }),
      store({ name: 'yellow', freeSlotStatus: FreeSlotStatus.Yellow, freeSlotCount: 2 }),
    ];
    expect(storesNeedingPeople(list).map((entry) => entry.name)).toEqual(['red', 'coord', 'yellow', 'green']);
    expect(storesNeedingPeople(list, 2)).toHaveLength(2);
  });
});

describe('home cards (DataState)', () => {
  let http: HttpTestingController;
  const settle = async (fixture: ComponentFixture<unknown>) => {
    await tick();
    await fixture.whenStable();
  };
  const q = (fixture: ComponentFixture<unknown>, selector: string) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(selector);
  const all = (fixture: ComponentFixture<unknown>, selector: string) =>
    (fixture.nativeElement as HTMLElement).querySelectorAll(selector);

  function signIn(role: Role, verified: boolean): void {
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: 'me',
        email: 'me@local',
        displayName: 'Sarah',
        role,
        language: Language.De,
        theme: Theme.System,
        verified,
      },
    });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('help needed soon: skeleton, the next three free pickups, hidden when nothing is free', async () => {
    signIn(Role.Foodsaver, true);
    const fixture = TestBed.createComponent(HelpNeededCardComponent);
    await settle(fixture);
    expect(q(fixture, '[data-testid="dashboard.help.state"]')?.getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/users/me/pickups/options').flush([
      option('D', '2026-11-06T17:00:00Z', 0, 2),
      option('full', '2026-11-04T17:00:00Z', 2, 2),
      option('A', '2026-11-04T17:30:00Z', 1, 2),
      option('B', '2026-11-05T17:30:00Z', 0, 3),
      option('C', '2026-11-05T18:30:00Z', 0, 1),
    ]);
    await settle(fixture);
    expect([...all(fixture, '[data-testid="dashboard.help.option"]')].map((li) => li.getAttribute('data-store'))).toEqual(['A', 'B', 'C']);
    expect(q(fixture, '[data-testid="dashboard.help.all"]')?.getAttribute('href')).toBe('/pickups#options');

    const empty = TestBed.createComponent(HelpNeededCardComponent);
    await settle(empty);
    http.expectOne('/api/users/me/pickups/options').flush([option('full', '2026-11-04T17:00:00Z', 2, 2)]);
    await settle(empty);
    expect(q(empty, '[data-testid="dashboard.help"]')).toBeNull();
  });

  it('help needed soon: compact error with retry stays visible; nothing for unverified users', async () => {
    signIn(Role.Foodsaver, true);
    const fixture = TestBed.createComponent(HelpNeededCardComponent);
    await settle(fixture);
    http.expectOne('/api/users/me/pickups/options').flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
    await settle(fixture);
    expect(q(fixture, '[data-testid="dashboard.help.state"]')?.getAttribute('data-state')).toBe('error');
    q(fixture, '[data-testid="dashboard.help.state.retry"] button')!.click();
    await settle(fixture);
    http.expectOne('/api/users/me/pickups/options').flush([option('A', '2026-11-04T17:30:00Z', 0, 2)]);
    await settle(fixture);
    expect(all(fixture, '[data-testid="dashboard.help.option"]')).toHaveLength(1);
    fixture.destroy();

    signIn(Role.Foodsharer, false);
    const newcomer = TestBed.createComponent(HelpNeededCardComponent);
    await settle(newcomer);
    http.expectNone('/api/users/me/pickups/options');
    expect((newcomer.nativeElement as HTMLElement).children).toHaveLength(0);
  });

  it('waiting for you: rows with counts and links; only unread conversations for unverified users', async () => {
    signIn(Role.Foodsaver, true);
    const fixture = TestBed.createComponent(WaitingCardComponent);
    await settle(fixture);
    http.expectOne((req) => req.url === '/api/users/me/stores').flush([store({ name: 'I', membership: UserTeamStatus.Invited })]);
    http.expectOne('/api/conversations/unread-count').flush({ conversations: 2, messages: 5 });
    http.expectOne('/api/users/me/pickups').flush([pickup('X', false)]);
    await settle(fixture);
    expect([...all(fixture, '.wait__row')].map((row) => row.getAttribute('data-testid'))).toEqual([
      'dashboard.waiting.invitations',
      'dashboard.waiting.unread',
      'dashboard.waiting.unconfirmed',
    ]);
    expect(q(fixture, '[data-testid="dashboard.waiting.unread"]')?.getAttribute('href')).toBe('/messages');
    expect(q(fixture, '[data-testid="dashboard.waiting.unconfirmed"]')?.getAttribute('href')).toBe('/pickups#mine');
    expect(q(fixture, '[data-testid="dashboard.waiting.unconfirmed"] .wait__count')?.textContent?.trim()).toBe('1');
    fixture.destroy();

    signIn(Role.Foodsharer, false);
    const newcomer = TestBed.createComponent(WaitingCardComponent);
    await settle(newcomer);
    http.expectNone('/api/users/me/stores');
    http.expectOne('/api/conversations/unread-count').flush({ conversations: 0, messages: 0 });
    await settle(newcomer);
    expect(q(newcomer, '[data-testid="dashboard.waiting"]')).toBeNull();
  });

  it('upcoming events: tile rows with status word, hidden without events', async () => {
    signIn(Role.Foodsaver, true);
    const fixture = TestBed.createComponent(UpcomingEventsCardComponent);
    await settle(fixture);
    http.expectOne('/api/events/mine').flush([event('Treffen', '2099-11-05T18:00:00Z', InvitationStatus.Invited)]);
    await settle(fixture);
    const row = q(fixture, '[data-testid="dashboard.events.event"]')!;
    expect(row.getAttribute('data-status')).toBe('INVITED');
    expect(row.querySelector('fs-date-tile')?.getAttribute('aria-hidden')).toBe('true');
    expect(row.querySelector('time')?.getAttribute('datetime')).toBe('2099-11-05T18:00:00Z');
    expect(row.querySelector('.state--wait .pi-hourglass')).not.toBeNull();

    const none = TestBed.createComponent(UpcomingEventsCardComponent);
    await settle(none);
    http.expectOne('/api/events/mine').flush([]);
    await settle(none);
    expect(q(none, '[data-testid="dashboard.events"]')).toBeNull();
  });

  it('your stores: teams with free slots, urgent first, error with retry', async () => {
    signIn(Role.Foodsaver, true);
    const fixture = TestBed.createComponent(YourStoresCardComponent);
    await settle(fixture);
    http.expectOne((req) => req.url === '/api/users/me/stores').flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
    await settle(fixture);
    expect(q(fixture, '[data-testid="dashboard.stores.state"]')?.getAttribute('data-state')).toBe('error');
    q(fixture, '[data-testid="dashboard.stores.state.retry"] button')!.click();
    await settle(fixture);
    http.expectOne((req) => req.url === '/api/users/me/stores').flush([
      store({ name: 'Hiltl', freeSlotStatus: FreeSlotStatus.Yellow, freeSlotCount: 2, nextPickupAt: '2026-11-04T19:30:00Z' }),
      store({ name: 'Migros', freeSlotStatus: FreeSlotStatus.Red, freeSlotCount: 8 }),
    ]);
    await settle(fixture);
    expect([...all(fixture, '[data-testid="dashboard.stores.store"]')].map((li) => li.getAttribute('data-store'))).toEqual(['Migros', 'Hiltl']);
    expect(q(fixture, '[data-testid="dashboard.stores.store"] a')?.getAttribute('href')).toBe('/stores/Migros/pickups');
  });
});
