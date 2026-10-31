import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { BookableIntroPickup, IntroPickupCategory, Role, SessionInfo } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { canSetCounted, isOnline, isoToZonedLocal, zonedLocalToIso } from './intro-category';
import { BookableListPageComponent } from './pages/bookable-list-page.component';
import { GuideDashboardPageComponent } from './pages/guide-dashboard-page.component';
import { NewcomerFunnelPageComponent } from './pages/newcomer-funnel-page.component';
import { MyBookingsPageComponent } from './pages/my-bookings-page.component';
import { UnsubscribePageComponent } from './pages/unsubscribe-page.component';

const REGION = 'r-zh';
const SESSION: SessionInfo = {
  authenticated: true,
  permissions: [],
  user: {
    id: 'u1',
    email: 'einab1@local',
    displayName: 'Luca',
    role: Role.Foodsharer,
    language: 'de',
    theme: 'system',
    verified: false,
    homeRegionId: REGION,
    foodsaverNumber: 100004,
  } as unknown as SessionInfo['user'],
  units: [{ id: REGION, name: 'Zürich Stadt', slug: 'zuerich-stadt', type: 'REGION', member: true, admin: false, homeRegion: true }] as unknown as SessionInfo['units'],
};

const problem = (code: string, status: number) => ({ status, code, message: code, retryable: false, traceId: 't' });

function einab(id: string, bookable: boolean, reason?: string): BookableIntroPickup {
  return {
    id,
    category: IntroPickupCategory.Supermarket,
    startAt: '2026-11-04T17:00:00Z',
    guide: { id: 'g', displayName: 'Julien', role: Role.Foodsaver, verified: true },
    visitFairteiler: false,
    freeSlots: 2,
    slotId: 's-' + id,
    bookable,
    notBookableReason: reason as BookableIntroPickup['notBookableReason'],
  };
}

describe('intro pickup helpers', () => {
  it('converts wall time in the region zone to an instant and back (summer and winter time)', () => {
    expect(zonedLocalToIso('2026-11-04T18:00', 'Europe/Zurich')).toBe('2026-11-04T17:00:00.000Z');
    expect(zonedLocalToIso('2026-07-01T18:00', 'Europe/Zurich')).toBe('2026-07-01T16:00:00.000Z');
    expect(isoToZonedLocal('2026-11-04T17:00:00Z', 'Europe/Zurich')).toBe('2026-11-04T18:00');
    expect(zonedLocalToIso('nope', 'Europe/Zurich')).toBe('');
    expect(isoToZonedLocal(undefined, 'Europe/Zurich')).toBe('');
  });

  it('knows the online category', () => {
    expect(isOnline(IntroPickupCategory.Online)).toBe(true);
    expect(isOnline(IntroPickupCategory.Bakery)).toBe(false);
  });
});

describe('counted switch rule (EA18)', () => {
  it('shows the switch for DONE and CANCELLED bookings', () => {
    expect(canSetCounted({ status: 'DONE', counted: true })).toBe(true);
    expect(canSetCounted({ status: 'CANCELLED', counted: false })).toBe(true);
  });

  it('keeps the switch for a counted past BOOKED booking (a CANCELLED one switched on)', () => {
    expect(canSetCounted({ status: 'BOOKED', counted: true })).toBe(true);
  });

  it('has no switch for a future BOOKED booking, which is not counted', () => {
    expect(canSetCounted({ status: 'BOOKED', counted: false })).toBe(false);
    expect(canSetCounted({ status: 'BOOKED' })).toBe(false);
  });
});

describe('intro pickup pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const qa = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'intro-pickups', component: BookableListPageComponent },
            { path: 'intro-pickups/unsubscribe', component: UnsubscribePageComponent },
            { path: 'me/intro-pickups', component: MyBookingsPageComponent },
            { path: 'guide', component: GuideDashboardPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('bookable: without a region the region picker is shown and nothing is loaded', async () => {
    await harness.navigateByUrl('/intro-pickups');
    expect(q('[data-testid="introPickups.bookable.noRegion"]')).not.toBeNull();
    http.expectNone((req) => req.url.includes('/intro-pickups/bookable'));
  });

  it('bookable: loading, then bookable cards and translated reasons', async () => {
    TestBed.inject(SessionService).applySession(SESSION);
    await harness.navigateByUrl('/intro-pickups');
    expect(q('[data-testid="introPickups.bookable.state"]')?.getAttribute('data-state')).toBe('loading');
    http
      .expectOne((req) => req.url === `/api/regions/${REGION}/intro-pickups/bookable`)
      .flush({ items: [einab('a', true), einab('b', false, 'USER_ALREADY_BOOKED_WITH_GUIDE')], page: { page: 0, size: 100, totalItems: 2, totalPages: 1 } });
    await settle();
    expect(qa('[data-testid="introPickups.bookable.card"]').length).toBe(2);
    expect(qa('[data-testid="introPickups.bookable.book"]').length).toBe(1);
    expect(q('[data-testid="introPickups.bookable.reason"]')?.getAttribute('data-reason')).toBe('USER_ALREADY_BOOKED_WITH_GUIDE');
  });

  it('bookable: empty list offers notifications, errors offer a retry', async () => {
    TestBed.inject(SessionService).applySession(SESSION);
    await harness.navigateByUrl('/intro-pickups');
    http
      .expectOne((req) => req.url.endsWith('/intro-pickups/bookable'))
      .flush(problem('INTERNAL_ERROR', 500), { status: 500, statusText: 'Server Error' });
    await settle();
    expect(q('[data-testid="introPickups.bookable.state"]')?.getAttribute('data-state')).toBe('error');
    (q('[data-testid="introPickups.bookable.state.retry"] button') as HTMLButtonElement).click();
    await settle();
    http.expectOne((req) => req.url.endsWith('/intro-pickups/bookable')).flush({ items: [], page: { page: 0, size: 100, totalItems: 0, totalPages: 0 } });
    await settle();
    expect(q('[data-testid="introPickups.bookable.state"]')?.getAttribute('data-state')).toBe('empty');
  });

  it('my bookings: empty state and the subscription card for newcomers', async () => {
    TestBed.inject(SessionService).applySession(SESSION);
    await harness.navigateByUrl('/me/intro-pickups');
    http.expectOne((req) => req.url === '/api/users/me/intro-pickups' && req.params.get('past') === 'false').flush([]);
    http.expectOne((req) => req.url === '/api/users/me/intro-pickups' && req.params.get('past') === 'true').flush([]);
    http.expectOne('/api/onboarding/journey').flush({ complete: false, steps: [], extraSteps: [] });
    http.expectOne('/api/users/me/intro-pickup-subscription').flush(problem('RESOURCE_NOT_FOUND', 404), { status: 404, statusText: 'Not Found' });
    await settle();
    expect(q('[data-testid="introPickups.my.state"]')?.getAttribute('data-state')).toBe('empty');
    expect(q('[data-testid="introPickups.subscription.subscribe"]')).not.toBeNull();
    expect(q('[data-testid="introPickups.my.foodsaverNumber"]')?.textContent).toContain('100004');
  });

  it('unsubscribe: without a token explains the problem', async () => {
    await harness.navigateByUrl('/intro-pickups/unsubscribe');
    expect(q('[data-testid="introPickups.unsubscribe.missing"]')).not.toBeNull();
  });

  it('unsubscribe: with a token unsubscribes', async () => {
    TestBed.inject(SessionService).applySession({ authenticated: false, permissions: [] });
    const fixture = TestBed.createComponent(UnsubscribePageComponent);
    fixture.componentRef.setInput('token', 'abc');
    fixture.detectChanges();
    await fixture.whenStable();
    const req = http.expectOne('/api/public/intro-pickups/unsubscribe');
    expect(req.request.body).toEqual({ token: 'abc' });
    req.flush({ message: 'ok' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="introPickups.unsubscribe.done"]')).not.toBeNull();
  });

  it('guide: no guide regions → empty state', async () => {
    await harness.navigateByUrl('/guide');
    http.expectOne('/api/guide/regions').flush([]);
    http.expectOne('/api/guide/ical-candidates').flush([]);
    http.expectOne('/api/guide/private-info-templates').flush([]);
    await settle();
    expect(q('[data-testid="introPickups.guide.regionsState"]')?.getAttribute('data-state')).toBe('empty');
  });

  it('guide: EinAbs of the guide region with slots', async () => {
    await harness.navigateByUrl('/guide');
    http.expectOne('/api/guide/regions').flush([{ id: REGION, slug: 'zuerich-stadt', name: 'Zürich Stadt', type: 'REGION' }]);
    await settle();
    http.expectOne('/api/guide/ical-candidates').flush([]);
    http.expectOne('/api/guide/private-info-templates').flush([]);
    http
      .expectOne((req) => req.url === `/api/regions/${REGION}/guide/intro-pickups`)
      .flush({
        items: [
          {
            id: 'e1',
            regionId: REGION,
            category: 'BAKERY',
            startAt: '2026-11-08T17:00:00Z',
            guide: { id: 'g', displayName: 'Julien', role: Role.Foodsaver, verified: true },
            slotCount: 2,
            visitFairteiler: false,
            booked: true,
            slots: [
              { id: 's1', status: 'BOOKED', bookingUser: { id: 'u', displayName: 'Luca', role: Role.Foodsharer, verified: false }, commentCount: 2 },
              { id: 's2', status: 'AVAILABLE' },
            ],
          },
        ],
        page: { page: 0, size: 100, totalItems: 1, totalPages: 1 },
      });
    await settle();
    expect(qa('[data-testid="introPickups.guide.einab"]').length).toBe(1);
    expect(qa('[data-testid="introPickups.slot"]').length).toBe(2);
    expect(q('[data-testid="introPickups.slot.cancel"]')).not.toBeNull();
    expect(q('[data-testid="introPickups.slot.bookFor"]')).not.toBeNull();
    expect(q('[data-testid="introPickups.slot.commentCount"]')?.textContent?.trim()).toBe('2');
    expect(q('[data-testid="introPickups.guide.ical"]')).toBeNull();
  });

  it('funnel: steps, names and gap severities; 403 → forbidden', async () => {
    const fixture = TestBed.createComponent(NewcomerFunnelPageComponent);
    fixture.componentRef.setInput('regionId', REGION);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    http
      .expectOne((req) => req.url === `/api/regions/${REGION}/newcomer-funnel`)
      .flush({
        targetIntroPickups: 3,
        fairteilerRequired: true,
        days: 180,
        totalUsers: 2,
        inTrainingUsers: 1,
        stalledUsers: 1,
        introPickupsStillNeeded: 4,
        openSlots: 2,
        fairteilerVisitsStillNeeded: 1,
        openFairteilerSlots: 0,
        gapHorizonDays: 28,
        steps: [
          { stage: 'REGISTERED', userCount: 1, reachedCount: 2, stalledCount: 1, users: [{ stage: 'REGISTERED', counted: 0, upcoming: 0, target: 3, stalled: true, nextStep: 'BOOK_EINAB', user: { id: 'u', displayName: 'Luca', role: Role.Foodsharer, verified: false } }] },
          { stage: 'IN_PROGRESS', userCount: 1, reachedCount: 1, stalledCount: 0, users: [] },
        ],
        gaps: [
          { category: 'BAKERY', withFairteiler: false, students: 2, openSlots: 0, severity: 'MISSING' },
          { category: 'SUPERMARKET', withFairteiler: false, students: 1, openSlots: 3, severity: 'OK' },
        ],
      });
    await fixture.whenStable();
    expect(el.querySelectorAll('[data-testid="introPickups.funnel.step"]').length).toBe(2);
    expect(el.querySelectorAll('[data-testid="introPickups.funnel.names"]').length).toBe(1);
    expect(el.querySelector('[data-severity="MISSING"]')).not.toBeNull();
  });

  it('funnel: plain members see the forbidden state', async () => {
    const fixture = TestBed.createComponent(NewcomerFunnelPageComponent);
    fixture.componentRef.setInput('regionId', REGION);
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne((req) => req.url.endsWith('/newcomer-funnel')).flush(problem('FORBIDDEN', 403), { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    const state = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="introPickups.funnel.state"]');
    expect(state?.getAttribute('data-state')).toBe('forbidden');
  });
});
