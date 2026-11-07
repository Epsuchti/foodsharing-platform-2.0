import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import {
  ApiErrorCode,
  CleaningShift,
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
import { ServerClock } from '../../pickups/pickups.resource';
import { FspCleaningCardComponent, nextShifts } from './fsp-cleaning-card.component';

const ME = '00000001-0001-4000-8000-000000000006';
const GUIDE = '00000001-0001-4000-8000-000000000010';
const F1 = '00000049-0001-4000-8000-000000000001';
const BASE = `/api/food-share-points/${F1}/cleaning`;
const NOW = Date.parse('2026-11-06T12:00:00Z');

const FSP: Fsp = {
  id: F1,
  name: 'Fairteiler Wiedikon',
  cleaningAutoConfirm: false,
  timeZone: 'Europe/Zurich',
  region: { id: 'r1', slug: 'zuerich-stadt', name: 'Zürich Stadt', type: UnitType.BigCity },
  address: { street: 'Schlossgasse 10', postcode: '8003', city: 'Zürich' },
  status: FspStatus.Active,
  managers: [],
  createdAt: '2026-01-10T10:00:00Z',
  followerCount: 3,
  following: { following: false, bell: false, mail: false },
  permissions: { mayEdit: false, mayDelete: false, mayActivate: false, mayPost: true, mayFollow: true },
  cleaningEnabled: true,
};

function shift(shiftAt: string, over: Partial<CleaningShift> = {}): CleaningShift {
  return {
    fspId: F1,
    shiftAt,
    slotCount: 2,
    isRegular: true,
    isAvailable: true,
    occupiedSlots: [],
    eligibility: { eligible: true, reasons: [] },
    ...over,
  };
}

const MINE = { user: { id: ME, displayName: 'Sarah', role: Role.Foodsaver, verified: true }, confirmed: false };
const SHIFTS: CleaningShift[] = [
  shift('2026-11-05T09:00:00Z'), // past
  shift('2026-11-07T09:00:00Z', { occupiedSlots: [MINE] }),
  shift('2026-11-08T09:00:00Z', { slotCount: 0 }), // cancelled
  shift('2026-11-09T17:00:00Z', {
    occupiedSlots: [{ user: { id: GUIDE, displayName: 'Julien Favre', role: Role.Foodsaver, verified: true }, confirmed: true }],
  }),
  shift('2026-11-10T09:00:00Z', { eligibility: { eligible: false, reasons: [PickupEligibilityReason.Sleeping] } }),
  shift('2026-11-11T09:00:00Z'),
  shift('2026-11-12T09:00:00Z'),
  shift('2026-11-13T09:00:00Z'),
];

describe('nextShifts (decision 171)', () => {
  it('takes the next five future shifts that are not cancelled', () => {
    expect(nextShifts(SHIFTS, NOW).map((s) => s.shiftAt)).toEqual([
      '2026-11-07T09:00:00Z',
      '2026-11-09T17:00:00Z',
      '2026-11-10T09:00:00Z',
      '2026-11-11T09:00:00Z',
      '2026-11-12T09:00:00Z',
    ]);
  });
});

describe('FspCleaningCardComponent (FS2, decision 171)', () => {
  @Component({
    imports: [FspCleaningCardComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `<fs-fsp-cleaning-card [fsp]="fsp()" />`,
  })
  class Host {
    readonly fsp = signal(FSP);
  }

  let http: HttpTestingController;
  let fixture: ComponentFixture<Host>;
  const q = (selector: string) => (fixture.nativeElement as HTMLElement).querySelector(selector);
  const all = (selector: string) => (fixture.nativeElement as HTMLElement).querySelectorAll(selector);
  const doc = (selector: string) => document.querySelector(selector);
  const state = () => q('[data-testid="fsp.cleaningCard.state"]')?.getAttribute('data-state');
  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting(),
        provideOptimus(),
        { provide: ServerClock, useValue: { now: () => NOW } },
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
      },
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Host);
    await settle();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('shows the next five shifts and links to all shifts', async () => {
    expect(state()).toBe('loading');
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
    await settle();
    expect(state()).toBe('success');
    expect(all('[data-testid="fsp.cleaningCard.shift"]').length).toBe(5);
    expect(q('[data-testid="fsp.cleaningCard.unconfirmed"]')).not.toBeNull();
    expect(q('[data-testid="fsp.cleaning.open"]')?.getAttribute('href')).toBe(`/food-share-points/${F1}/cleaning`);
  });

  it('signs up for a free place and reloads', async () => {
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
    await settle();
    (q('[data-shift-at="2026-11-11T09:00:00Z"] [data-testid="fsp.cleaningCard.slot.free"]') as HTMLButtonElement).click();
    await settle();
    (doc('[data-testid="fsp.cleaningCard.signupConfirm"] button') as HTMLButtonElement).click();
    const signup = http.expectOne(`${BASE}/shifts/${encodeURIComponent('2026-11-11T09:00:00Z')}/signups`);
    expect(signup.request.method).toBe('POST');
    signup.flush({ confirmed: true });
    await settle();
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
  });

  it('shows why a sign-up is not possible', async () => {
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
    await settle();
    (q('[data-shift-at="2026-11-10T09:00:00Z"] [data-testid="fsp.cleaningCard.slot.free"]') as HTMLButtonElement).click();
    await settle();
    expect(doc('[data-testid="shared.eligibility.reason"]')?.getAttribute('data-reason')).toBe('SLEEPING');
    expect((doc('[data-testid="fsp.cleaningCard.signupConfirm"] button') as HTMLButtonElement).disabled).toBe(true);
  });

  it('signs out of the own shift after the confirmation', async () => {
    vi.spyOn(TestBed.inject(ConfirmationService), 'confirm').mockImplementation((confirmation) => {
      confirmation.accept?.();
      return TestBed.inject(ConfirmationService);
    });
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
    await settle();
    (q('[data-shift-at="2026-11-07T09:00:00Z"] [data-testid="fsp.cleaningCard.slot.taken"]') as HTMLButtonElement).click();
    const signout = http.expectOne(`${BASE}/shifts/${encodeURIComponent('2026-11-07T09:00:00Z')}/signups/${ME}`);
    expect(signout.request.method).toBe('DELETE');
    signout.flush(null);
    await settle();
    http.expectOne(`${BASE}/shifts`).flush(SHIFTS);
  });

  it('shows the empty state and the error state with retry', async () => {
    http
      .expectOne(`${BASE}/shifts`)
      .flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(state()).toBe('error');
    (q('[data-testid="fsp.cleaningCard.state"] [data-testid$=".retry"] button') as HTMLButtonElement).click();
    http.expectOne(`${BASE}/shifts`).flush([shift('2026-11-05T09:00:00Z')]);
    await settle();
    expect(state()).toBe('empty');
  });
});
