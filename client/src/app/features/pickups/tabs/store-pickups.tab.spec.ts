import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService, Confirmation, MessageService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

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

describe('store pickups tab: confirmed vs. unconfirmed (PU3)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const qa = (selector: string) => [...harness.routeNativeElement!.querySelectorAll<HTMLElement>(selector)];
  const settle = () => harness.fixture.whenStable();
  const pickupsUrl = '/api/stores/s1/pickups';
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

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const open = async (permissions: Record<string, boolean>, list: unknown[] = LIST) => {
    await harness.navigateByUrl('/stores/s1/pickups');
    http.expectOne('/api/stores/s1').flush(detail(permissions));
    await settle();
    http.expectOne(pickupsUrl).flush(list);
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
