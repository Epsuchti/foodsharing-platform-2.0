import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService, Confirmation, MessageService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { Language, Role, Theme } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { StorePageComponent } from '../../stores/pages/store-page.component';
import { StorePickupsTab } from './store-pickups.tab';

const user = (id: string, displayName: string) => ({ id, displayName, role: 'FOODSAVER', verified: true });
const PRIYA = user('a', 'Priya Sharma');
const MARCO = user('b', 'Marco Bernasconi');
const LENA = user('c', 'Lena Huber');
const TOM = user('d', 'Tom Keller');

const pickup = (pickupAt: string, slotCount: number, slots: { user: unknown; confirmed: boolean }[]) => ({
  storeId: 's1',
  pickupAt,
  slotCount,
  occupiedSlots: slots,
  isAvailable: true,
  isRegular: true,
});

// Thursday: 1 confirmed + 2 unconfirmed; Friday: 1 unconfirmed; Saturday: nobody
const THU = '2030-11-07T18:00:00Z';
const FRI = '2030-11-08T18:00:00Z';
const SAT = '2030-11-09T09:00:00Z';
const LIST = [
  pickup(THU, 4, [
    { user: PRIYA, confirmed: true },
    { user: MARCO, confirmed: false },
    { user: LENA, confirmed: false },
  ]),
  pickup(FRI, 2, [{ user: TOM, confirmed: false }]),
  pickup(SAT, 2, []),
];

function detail(permissions: Record<string, boolean>): Record<string, unknown> {
  return {
    id: 's1',
    name: 'Coop',
    address: { street: 'Weg 1', postcode: '8000', city: 'Zürich' },
    cooperationStatus: 'COOPERATION_ESTABLISHED',
    teamStatus: 'OPEN',
    region: { id: 'r1', slug: 'zh', name: 'Zürich', type: 'CITY' },
    timeZone: 'Europe/Zurich',
    managers: [],
    teamCounts: { members: 2, jumpers: 1, applied: 0, invited: 0 },
    membership: 'MEMBER',
    prefetchDays: 14,
    permissions: { mayAccess: true, teamStatus: 'MEMBER', maySeePickups: true, ...permissions },
  };
}

const MANAGER = { mayConfirmPickups: true, mayEditPickups: true };

/** The tab asks the server clock once (today's day card). */
function answerClock(http: HttpTestingController): void {
  http
    .match((r) => r.url.endsWith('/api/public/app-info'))
    .forEach((r) => r.flush({ serverTime: '2030-11-06T08:00:00Z' }));
}

const signupDetail = (u: unknown, confirmed: boolean) => ({
  user: u,
  confirmed,
  signedUpAt: '2030-11-01T10:00:00Z',
  pickupCount: 3,
  upcomingSlots: [],
});

describe('store pickups tab: confirmed vs. unconfirmed (PU3)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const qa = (selector: string) => [...harness.routeNativeElement!.querySelectorAll<HTMLElement>(selector)];
  const settle = () => harness.fixture.whenStable();
  const pickupsUrl = '/api/stores/s1/pickups';
  const detailUrl = (at: string, userId: string) => `/api/stores/s1/pickups/${encodeURIComponent(at)}/signups/${userId}`;
  const confirmUrl = (at: string, userId: string) => `/api/stores/s1/pickups/${encodeURIComponent(at)}/signups/${userId}/confirm`;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'pickups.tab.confirmAllText': 'Für diese Abholung bestätigen: {names}.',
            'pickups.tab.confirmedAll': '{count} Einträge bestätigt.',
          },
          routes: [{ path: 'stores/:storeId', component: StorePageComponent, children: [{ path: 'pickups', component: StorePickupsTab }] }],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    answerClock(http);
    http.verify({ ignoreCancelled: true });
  });

  const open = async (permissions: Record<string, boolean>, list: unknown[] = LIST) => {
    await harness.navigateByUrl('/stores/s1/pickups');
    http.expectOne('/api/stores/s1').flush(detail(permissions));
    await settle();
    http.expectOne(pickupsUrl).flush(list);
    answerClock(http);
    await settle();
  };

  const pickupEl = (at: string) => qa(`[data-testid="stores.pickups.pickup"][data-pickup-at="${at}"]`)[0];
  const summaryOf = (at: string) => pickupEl(at)?.querySelector<HTMLElement>('[data-testid="stores.pickups.summary"]') ?? null;

  it('shows a summary per pickup with sign-ups, for everyone', async () => {
    await open({});
    expect(summaryOf(THU)?.getAttribute('data-confirmed')).toBe('1');
    expect(summaryOf(THU)?.getAttribute('data-pending')).toBe('2');
    expect(summaryOf(FRI)?.getAttribute('data-pending')).toBe('1');
    expect(summaryOf(SAT)).toBeNull();
    // foodsavers get neither the chip, the confirm-all button nor split pills
    expect(qa('[data-testid="stores.pickups.range.pending"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.pickups.confirmAll"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.pickups.slot.confirm"]')).toHaveLength(0);
  });

  it('managers: chip counts the unconfirmed sign-ups, filters and shows the empty text', async () => {
    await open(MANAGER);
    const chip = () => qa('[data-testid="stores.pickups.range.pending"]')[0];
    expect(chip().getAttribute('data-count')).toBe('3');
    expect(chip().getAttribute('aria-pressed')).toBe('false');
    expect(qa('[data-testid="stores.pickups.slot.confirm"]')).toHaveLength(3);
    // confirm-all only where 2 or more are unconfirmed
    expect(qa('[data-testid="stores.pickups.confirmAll"]')).toHaveLength(1);
    expect(pickupEl(THU).querySelector('[data-testid="stores.pickups.confirmAll"]')).not.toBeNull();

    chip().click();
    await settle();
    expect(chip().getAttribute('aria-pressed')).toBe('true');
    expect(qa('[data-testid="stores.pickups.pickup"]').map((el) => el.getAttribute('data-pickup-at'))).toEqual([THU, FRI]);
    expect(qa('[data-testid="stores.pickups.pendingEmpty"]')).toHaveLength(0);
  });

  it('managers: the chip is hidden when nothing is unconfirmed', async () => {
    await open(MANAGER, [pickup(THU, 2, [{ user: PRIYA, confirmed: true }])]);
    expect(qa('[data-testid="stores.pickups.range.pending"]')).toHaveLength(0);
  });

  it('managers: a pressed chip stays when nothing is left and the list says so', async () => {
    await open(MANAGER, [pickup(FRI, 2, [{ user: TOM, confirmed: false }])]);
    qa('[data-testid="stores.pickups.range.pending"]')[0].click();
    await settle();

    // confirm Tom via the ✓ → dialog → "Bestätigen"
    qa('[data-testid="stores.pickups.slot.confirm"]')[0].click();
    await settle();
    http.expectOne(detailUrl(FRI, 'd')).flush(signupDetail(TOM, false));
    await settle();
    expect(qa('[data-testid="stores.pickups.slotState"]')[0]?.getAttribute('data-confirmed')).toBe('false');
    qa('[data-testid="stores.pickups.confirmSlot"] button')[0].click();
    http.expectOne(confirmUrl(FRI, 'd')).flush(null);
    await settle();
    http.expectOne(pickupsUrl).flush([pickup(FRI, 2, [{ user: TOM, confirmed: true }])]);
    await settle();

    const chip = qa('[data-testid="stores.pickups.range.pending"]');
    expect(chip).toHaveLength(1);
    expect(chip[0].getAttribute('data-count')).toBe('0');
    expect(qa('[data-testid="stores.pickups.pickup"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.pickups.pendingEmpty"]')).toHaveLength(1);
  });

  it('managers: confirm-all asks once, naming the people, then confirms one after another', async () => {
    await open(MANAGER);
    const confirmations: Confirmation[] = [];
    vi.spyOn(TestBed.inject(ConfirmationService), 'confirm').mockImplementation((c) => {
      confirmations.push(c);
      return TestBed.inject(ConfirmationService);
    });
    const toasts = vi.spyOn(TestBed.inject(MessageService), 'add');

    qa('[data-testid="stores.pickups.confirmAll"] button')[0].click();
    await settle();
    expect(confirmations).toHaveLength(1);
    expect(confirmations[0].message).toContain('Marco Bernasconi, Lena Huber');
    expect(http.match((r) => r.url.endsWith('/confirm'))).toHaveLength(0);

    confirmations[0].accept!();
    // sequential: the second call starts only after the first one completed
    const first = http.expectOne(confirmUrl(THU, 'b'));
    expect(http.match(confirmUrl(THU, 'c'))).toHaveLength(0);
    first.flush(null);
    http.expectOne(confirmUrl(THU, 'c')).flush(null);
    await settle();

    expect(toasts).toHaveBeenCalledTimes(1);
    expect(toasts.mock.calls[0][0]).toMatchObject({ severity: 'success', summary: '2 Einträge bestätigt.' });
    http.expectOne(pickupsUrl).flush(LIST);
    await settle();
  });

  it('managers: a failure partway shows the error and reloads', async () => {
    await open(MANAGER);
    let accept: (() => void) | undefined;
    vi.spyOn(TestBed.inject(ConfirmationService), 'confirm').mockImplementation((c) => {
      accept = c.accept as () => void;
      return TestBed.inject(ConfirmationService);
    });
    const toasts = vi.spyOn(TestBed.inject(MessageService), 'add');

    qa('[data-testid="stores.pickups.confirmAll"] button')[0].click();
    await settle();
    accept!();
    http.expectOne(confirmUrl(THU, 'b')).flush(null);
    http
      .expectOne(confirmUrl(THU, 'c'))
      .flush({ code: 'UNEXPECTED_ERROR', message: 'x' }, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(toasts.mock.calls.map((call) => call[0].severity)).toEqual(['error']);
    http.expectOne(pickupsUrl).flush(LIST);
    await settle();
  });

  it('lists every extra slot of a pickup (AU6, decision 164)', async () => {
    const withExtras = [
      {
        ...pickup(THU, 4, [{ user: PRIYA, confirmed: true }]),
        extraSlots: [
          { byUser: PRIYA, addedAt: '2030-11-01T10:00:00Z' },
          { byUser: PRIYA, addedAt: '2030-11-02T10:00:00Z' },
        ],
      },
      pickup(FRI, 2, []),
    ];
    await open({}, withExtras);
    expect(pickupEl(THU).querySelectorAll('[data-testid="stores.pickups.extraSlot"]')).toHaveLength(2);
    expect(pickupEl(FRI).querySelectorAll('[data-testid="stores.pickups.extraSlot"]')).toHaveLength(0);
  });
});

describe('store pickups tab: slot dialog modes and day chat (PU3, decision 164)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const qa = (selector: string) => [...harness.routeNativeElement!.querySelectorAll<HTMLElement>(selector)];
  const settle = () => harness.fixture.whenStable();
  const detailUrl = (at: string, userId: string) => `/api/stores/s1/pickups/${encodeURIComponent(at)}/signups/${userId}`;
  const ME = user('me', 'Sarah Müller');

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'stores/:storeId', component: StorePageComponent, children: [{ path: 'pickups', component: StorePickupsTab }] },
            { path: 'messages/:id', children: [] },
          ],
        }),
        provideOptimus(),
      ],
    });
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: 'me',
        email: 'x@local',
        displayName: 'Sarah Müller',
        role: Role.Foodsaver,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    answerClock(http);
    http.verify({ ignoreCancelled: true });
  });

  const open = async (permissions: Record<string, boolean>, list: unknown[]) => {
    await harness.navigateByUrl('/stores/s1/pickups');
    http.expectOne('/api/stores/s1').flush(detail(permissions));
    await settle();
    http.expectOne('/api/stores/s1/pickups').flush(list);
    answerClock(http);
    await settle();
  };
  const dayEl = (day: string) => qa(`[data-testid="stores.pickups.day"][data-day="${day}"]`)[0];
  const takenOf = (at: string, name: string) =>
    qa(
      `[data-testid="stores.pickups.pickup"][data-pickup-at="${at}"] [data-testid="stores.pickups.slot.taken"][data-user="${name}"]`,
    )[0] as HTMLButtonElement;
  const dialog = () => qa('[data-testid="stores.pickups.slotDialog"]')[0];

  it('team members open the short dialog without a request', async () => {
    await open({}, [pickup(THU, 3, [{ user: PRIYA, confirmed: true }])]);
    const slot = takenOf(THU, 'Priya Sharma');
    expect(slot.disabled).toBe(false);
    slot.click();
    await settle();
    http.expectNone(detailUrl(THU, 'a'));
    expect(dialog()?.getAttribute('data-mode')).toBe('short');
    expect(qa('[data-testid="stores.pickups.slotDetail.profile"]')[0]?.getAttribute('href')).toBe('/profile/a');
    expect(qa('[data-testid="stores.pickups.slotDetail.message"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.pickups.confirmSlot"]')).toHaveLength(0);
  });

  it('co-pickups get the rich dialog with counts but no manager actions; no phone without S9', async () => {
    await open({}, [pickup(THU, 3, [{ user: ME, confirmed: true }, { user: MARCO, confirmed: false }])]);
    takenOf(THU, 'Marco Bernasconi').click();
    await settle();
    expect(dialog()?.getAttribute('data-mode')).toBe('rich');
    http.expectOne(detailUrl(THU, 'b')).flush({ ...signupDetail(MARCO, false), pickupCount: 7, upcomingSlots: [THU, FRI] });
    await settle();
    expect(qa('[data-testid="stores.pickups.slotDetail.pickupCount"] dd')[0]?.textContent?.trim()).toBe('7');
    expect(qa('[data-testid="stores.pickups.slotDetail.upcomingItem"]')).toHaveLength(2);
    expect(qa('[data-testid="stores.pickups.slotDetail.message"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.pickups.slotDetail.call"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.pickups.confirmSlot"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.pickups.removeSlot"]')).toHaveLength(0);
  });

  it('managers get Bestätigen and Austragen; the phone shows when the server sends it (S9)', async () => {
    await open(MANAGER, [pickup(THU, 3, [{ user: MARCO, confirmed: false }])]);
    takenOf(THU, 'Marco Bernasconi').click();
    await settle();
    http.expectOne(detailUrl(THU, 'b')).flush({ ...signupDetail(MARCO, false), phone: '+41 79 123 45 67' });
    await settle();
    expect(qa('[data-testid="stores.pickups.slotDetail.call"]')[0]?.getAttribute('href')).toBe('tel:+41791234567');
    expect(qa('[data-testid="stores.pickups.confirmSlot"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.pickups.removeSlot"]')).toHaveLength(1);
  });

  it('rich dialog: loading, then the error state when the request fails', async () => {
    await open({}, [pickup(THU, 3, [{ user: ME, confirmed: true }, { user: MARCO, confirmed: false }])]);
    takenOf(THU, 'Marco Bernasconi').click();
    await settle();
    const state = () => qa('fs-data-state[testid="stores.pickups.slotDetail.state"], [data-state]').find((el) =>
      el.closest('[data-testid="stores.pickups.slotDialog"]'),
    );
    expect(state()?.getAttribute('data-state')).toBe('loading');
    http
      .expectOne(detailUrl(THU, 'b'))
      .flush({ code: 'UNEXPECTED_ERROR', message: 'x' }, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(state()?.getAttribute('data-state')).toBe('error');
  });

  it('shows "Chat öffnen" only when someone else is signed up that day, and opens the named chat', async () => {
    await open({}, [pickup(THU, 3, [{ user: PRIYA, confirmed: true }]), pickup(FRI, 2, [{ user: ME, confirmed: true }])]);
    expect(dayEl('2030-11-08').querySelector('[data-testid="stores.pickups.dayChat"]')).toBeNull();
    const button = dayEl('2030-11-07').querySelector<HTMLButtonElement>('[data-testid="stores.pickups.dayChat"]')!;
    expect(button.getAttribute('aria-label')).toBe('pickups.dayChat.open');
    button.click();
    const req = http.expectOne('/api/stores/s1/pickup-days/2030-11-07/conversation');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.name).toBe('pickups.dayChat.name');
    req.flush({ conversationId: 'c1', name: 'Abholung Coop', created: true, addedMembers: 2 });
    await settle();
    expect(TestBed.inject(Router).url).toBe('/messages/c1');
  });
});
