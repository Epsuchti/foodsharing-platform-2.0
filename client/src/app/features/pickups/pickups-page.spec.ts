import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { ApiErrorCode, PickupOption, UserPickup } from '../../api';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { PickupsPageComponent } from './pages/pickups-page.component';
import { PICKUPS_ROUTES } from './pickups.routes';

const at = (day: number) => `2026-11-${String(day).padStart(2, '0')}T17:00:00Z`;
const mine = (count: number): UserPickup[] =>
  Array.from({ length: count }, (_, i) => ({ store: { id: `s${i}`, name: `Store ${i}` }, pickupAt: at(5 + i), confirmed: i % 2 === 0, slotCount: 2 }));
const options = (count: number): PickupOption[] =>
  Array.from({ length: count }, (_, i) => ({
    store: { id: `o${i}`, name: `Option ${i}` },
    pickupAt: at(5 + i),
    slotCount: 2,
    occupiedSlots: [],
    confirmedCount: 0,
  }));

describe('/pickups (owner feedback 2026-11-04: one page, two sections)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector<HTMLElement>(selector);
  const qa = (selector: string) => [...harness.routeNativeElement!.querySelectorAll<HTMLElement>(selector)];
  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await harness.fixture.whenStable();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'pickups.page.showMore': 'Show {count} more' },
          routes: [
            { path: 'pickups', component: PickupsPageComponent },
            // the real redirect of pickups.routes.ts
            PICKUPS_ROUTES.find((route) => route.path === 'pickups/mine')!,
            { path: '**', children: [] },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  function flush(pickups: UserPickup[] | 'error', open: PickupOption[]): void {
    // cleaning shifts first: an error of the pickups request cancels the rest of the forkJoin
    http.expectOne('/api/users/me/cleaning-shifts').flush([]);
    const mineRequest = http.expectOne('/api/users/me/pickups');
    if (pickups === 'error') {
      mineRequest.flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, { status: 500, statusText: 'Error' });
    } else {
      mineRequest.flush(pickups);
    }
    http.expectOne('/api/users/me/pickups/options').flush(open);
  }

  it('shows the first 5 of my pickups and the first 10 options, each with "Show N more"', async () => {
    await harness.navigateByUrl('/pickups');
    flush(mine(7), options(13));
    await settle();
    expect(qa('[data-testid="pickups.mine.pickup"]')).toHaveLength(5);
    expect(q('[data-testid="pickups.mine.count"]')?.textContent?.trim()).toBe('7');
    expect(q('[data-testid="pickups.options.count"]')?.textContent?.trim()).toBe('13');
    expect(qa('[data-testid="pickups.options.option"]')).toHaveLength(10);
    const more = q('[data-testid="pickups.mine.showMore"]')!;
    expect(more.getAttribute('aria-controls')).toBe('mine-list');
    expect(more.textContent).toContain('Show 2 more');

    more.click();
    await settle();
    expect(qa('[data-testid="pickups.mine.pickup"]')).toHaveLength(7);
    expect(q('[data-testid="pickups.mine.showMore"]')).toBeNull();
    // the focus moves to the first revealed store link
    expect(document.activeElement?.textContent).toBe('Store 5');

    q('[data-testid="pickups.options.showMore"]')!.click();
    await settle();
    expect(qa('[data-testid="pickups.options.option"]')).toHaveLength(13);
    // History and the day overview are header actions; the old cross links are gone
    expect(q('[data-testid="pickups.mine.history"]')).not.toBeNull();
    expect(q('[data-testid="pickups.mine.agenda"]')).not.toBeNull();
    expect(q('[data-testid="pickups.options.mine"]')).toBeNull();
    expect(q('[data-testid="pickups.mine.options"]')).toBeNull();
    expect(q('section#mine')).not.toBeNull();
    expect(q('section#options')).not.toBeNull();
  });

  it('keeps the sections independent: an error in mine, options still listed; empty mine offers the options', async () => {
    await harness.navigateByUrl('/pickups');
    flush('error', options(1));
    await settle();
    expect(q('[data-testid="pickups.mine.state"]')?.getAttribute('data-state')).toBe('error');
    expect(qa('[data-testid="pickups.options.option"]')).toHaveLength(1);

    q('[data-testid="pickups.mine.state.retry"] button')!.click();
    await settle();
    http.expectOne('/api/users/me/pickups').flush([]);
    http.expectOne('/api/users/me/cleaning-shifts').flush([]);
    await settle();
    expect(q('[data-testid="pickups.mine.state"]')?.getAttribute('data-state')).toBe('empty');
    expect(q('[data-testid="pickups.mine.state.action"]')).not.toBeNull();
    expect(q('[data-testid="pickups.mine.agenda"]')).toBeNull();
  });

  it('redirects /pickups/mine to /pickups#mine', async () => {
    await harness.navigateByUrl('/pickups/mine');
    flush([], []);
    await settle();
    expect(TestBed.inject(Router).url).toBe('/pickups#mine');
  });
});
