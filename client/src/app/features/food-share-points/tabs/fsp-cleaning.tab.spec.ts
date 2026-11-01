import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import {
  ApiErrorCode,
  CleaningShift,
  CleaningTeam,
  Fsp,
  FspStatus,
  Language,
  PickupEligibilityReason,
  Role,
  Theme,
  UnitType,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { FspCleaningTab } from './fsp-cleaning.tab';

const ME = '00000001-0001-4000-8000-000000000006';
const BIEB = '00000001-0001-4000-8000-000000000009';
const GUIDE = '00000001-0001-4000-8000-000000000010';
const ZH = '00000010-0001-4000-8000-000000000040';
const F1 = '00000049-0001-4000-8000-000000000001';
const BASE = `/api/food-share-points/${F1}/cleaning`;

function fsp(overrides: Partial<Fsp> = {}): Fsp {
  return {
    id: F1,
    name: 'Fairteiler Wiedikon',
    region: { id: ZH, slug: 'zuerich-stadt', name: 'Zürich Stadt', type: UnitType.BigCity },
    address: { street: 'Schlossgasse 10', postcode: '8003', city: 'Zürich' },
    status: FspStatus.Active,
    managers: [
      { id: BIEB, displayName: 'Nina Schneider', role: Role.StoreManager, verified: true },
    ],
    createdAt: '2026-01-10T10:00:00Z',
    followerCount: 3,
    following: { following: false, bell: false, mail: false },
    permissions: {
      mayEdit: false,
      mayDelete: false,
      mayActivate: false,
      mayPost: true,
      mayFollow: true,
    },
    cleaningEnabled: true,
    ...overrides,
  };
}

const SHIFTS: CleaningShift[] = [
  {
    fspId: F1,
    shiftAt: '2026-11-07T09:00:00Z',
    slotCount: 2,
    description: 'Kühlschrank auswischen',
    isRegular: true,
    isAvailable: true,
    occupiedSlots: [
      {
        user: { id: GUIDE, displayName: 'Julien Favre', role: Role.Foodsaver, verified: true },
        confirmed: false,
      },
    ],
    eligibility: { eligible: true, reasons: [] },
  },
  {
    fspId: F1,
    shiftAt: '2026-11-11T17:00:00Z',
    slotCount: 1,
    isRegular: false,
    isAvailable: true,
    occupiedSlots: [],
    eligibility: { eligible: false, reasons: [PickupEligibilityReason.Sleeping] },
  },
];

const TEAM: CleaningTeam = {
  totalShifts: 2,
  members: [
    {
      user: { id: BIEB, displayName: 'Nina Schneider', role: Role.StoreManager, verified: true },
      manager: true,
      shiftCount: 1,
      upcomingCount: 0,
      lastShiftAt: '2026-10-17T08:00:00Z',
    },
    {
      user: { id: GUIDE, displayName: 'Julien Favre', role: Role.Foodsaver, verified: true },
      manager: false,
      shiftCount: 0,
      upcomingCount: 1,
    },
  ],
};

describe('Fairteiler cleaning tab (FS2)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const all = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const doc = (selector: string) => document.querySelector(selector);
  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await harness.fixture.whenStable();
  };
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [{ path: 'food-share-points/:fspId/cleaning', component: FspCleaningTab }],
        }),
        provideOptimus(),
      ],
    });
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: ME,
        email: 'foodsaver@local',
        displayName: 'Sarah',
        role: Role.Foodsaver,
        language: Language.De,
        theme: Theme.System,
        verified: true,
        homeRegionId: ZH,
      },
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  async function open(f: Fsp): Promise<void> {
    await harness.navigateByUrl(`/food-share-points/${F1}/cleaning`);
    http.expectOne(`/api/food-share-points/${F1}`).flush(f);
    await settle();
  }

  it('lists the shifts by day with the team and signs a foodsaver up', async () => {
    await open(fsp());
    expect(state('fsp.cleaning.state')).toBe('loading');
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
    http.expectOne(`${BASE}/team`).flush(TEAM);
    await settle();
    expect(state('fsp.cleaning.state')).toBe('success');
    expect(all('[data-testid="fsp.cleaning.day"]').length).toBe(2);
    expect(q('[data-testid="fsp.cleaning.toolbar"]')).toBeNull();
    expect(all('[data-testid="fsp.cleaning.team.member"]').length).toBe(2);
    expect(q('[data-testid="fsp.cleaning.team.member"]')?.getAttribute('data-manager')).toBe(
      'true',
    );

    (
      q(
        '[data-shift-at="2026-11-07T09:00:00Z"] [data-testid="fsp.cleaning.slot.free"]',
      ) as HTMLButtonElement
    ).click();
    await settle();
    (doc('[data-testid="fsp.cleaning.signupConfirm"] button') as HTMLButtonElement).click();
    const signup = http.expectOne(
      `${BASE}/shifts/${encodeURIComponent('2026-11-07T09:00:00Z')}/signups`,
    );
    expect(signup.request.method).toBe('POST');
    signup.flush({ confirmed: false });
    await settle();
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
    http.expectOne(`${BASE}/team`).flush(TEAM);
  });

  it('shows why a sign-up is not possible and keeps the button disabled', async () => {
    await open(fsp());
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
    http.expectOne(`${BASE}/team`).flush(TEAM);
    await settle();
    (
      q(
        '[data-shift-at="2026-11-11T17:00:00Z"] [data-testid="fsp.cleaning.slot.free"]',
      ) as HTMLButtonElement
    ).click();
    await settle();
    expect(doc('[data-testid="shared.eligibility.reason"]')?.getAttribute('data-reason')).toBe(
      'SLEEPING',
    );
    expect(
      (doc('[data-testid="fsp.cleaning.signupConfirm"] button') as HTMLButtonElement).disabled,
    ).toBe(true);
  });

  it('managers get the editors and confirm an unconfirmed sign-up', async () => {
    await open(
      fsp({
        permissions: {
          mayEdit: true,
          mayDelete: false,
          mayActivate: false,
          mayPost: true,
          mayFollow: true,
        },
      }),
    );
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
    http.expectOne(`${BASE}/team`).flush(TEAM);
    await settle();
    expect(q('[data-testid="fsp.cleaning.toolbar"]')).not.toBeNull();
    expect(all('[data-testid="fsp.cleaning.edit"]').length).toBe(2);
    (q('[data-testid="fsp.cleaning.slot.confirm"]') as HTMLButtonElement).click();
    await settle();
    (doc('[data-testid="fsp.cleaning.confirmSlot"] button') as HTMLButtonElement).click();
    const confirm = http.expectOne(
      `${BASE}/shifts/${encodeURIComponent('2026-11-07T09:00:00Z')}/signups/${GUIDE}/confirm`,
    );
    expect(confirm.request.method).toBe('POST');
    confirm.flush(null);
    await settle();
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
    http.expectOne(`${BASE}/team`).flush(TEAM);
  });

  it('a Fairteiler without cleaning shifts loads no shifts', async () => {
    await open(fsp({ cleaningEnabled: false }));
    expect(q('[data-testid="fsp.cleaning.disabled"]')).not.toBeNull();
    http.expectNone(`${BASE}/shifts`);
  });

  it('shows the empty state and the error state with retry', async () => {
    await open(fsp());
    http
      .expectOne(`${BASE}/shifts`)
      .flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, {
        status: 500,
        statusText: 'Server Error',
      });
    http.expectOne(`${BASE}/team`).flush({ totalShifts: 0, members: [] });
    await settle();
    expect(state('fsp.cleaning.state')).toBe('error');
    expect(state('fsp.cleaning.team.state')).toBe('empty');
    (
      q('[data-testid="fsp.cleaning.state"] [data-testid$=".retry"] button') as HTMLButtonElement
    ).click();
    http.expectOne(`${BASE}/shifts`).flush([]);
    await settle();
    expect(state('fsp.cleaning.state')).toBe('empty');
  });
});
