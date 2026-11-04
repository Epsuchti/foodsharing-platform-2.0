import { HttpTestingController } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { Role, StoreMembershipStatus, StoreTeam, TeamAppointBlocker, TeamMember, TeamMemberPage } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { ApplyTarget, StoreApplyDialogComponent } from './components/apply-dialog.component';
import { StorePageComponent } from './pages/store-page.component';
import { StoreTeamTab } from './tabs/store-team.tab';

const member = (name: string, over: Partial<TeamMember> = {}): TeamMember => ({
  user: { id: name, displayName: name, role: Role.Foodsaver, verified: true },
  isManager: false,
  status: StoreMembershipStatus.Member,
  verified: true,
  pickupCount: 3,
  sleeping: false,
  ...over,
});

const TEAM: StoreTeam = {
  applications: [{ user: member('Julien').user, text: 'Hallo', appliedAt: '2026-10-01T10:00:00Z' }],
  invitations: [],
};

const PEOPLE: TeamMember[] = [
  member('Nina', { isManager: true }),
  member('Sarah', { distanceKm: 2.25 }),
  member('Jonas', { status: StoreMembershipStatus.Jumper }),
];

const page = (items: TeamMember[], totalItems = items.length, pageIndex = 0): TeamMemberPage => ({
  items,
  page: { page: pageIndex, size: 20, totalItems, totalPages: Math.ceil(totalItems / 20) },
});

@Component({
  imports: [StoreApplyDialogComponent],
  template: `<fs-store-apply-dialog [(visible)]="open" [store]="store()" />`,
})
class ApplyHost {
  readonly open = signal(true);
  readonly store = signal<ApplyTarget>({ id: 's1', name: 'Coop', requiresApplicationText: true, requiresHygieneCertificate: true });
}

describe('store apply dialog (S6)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const q = (selector: string) => document.body.querySelector(selector);

  it('requires the application text before calling the server, then shows refusals as a sentence', async () => {
    const fixture = TestBed.createComponent(ApplyHost);
    await fixture.whenStable();
    expect(q('[data-testid="stores.apply.requirements"]')?.textContent).toContain('stores.requirement.hygiene');

    (q('[data-testid="stores.apply.submit"] button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(q('[data-testid="stores.apply.textMissing"]')).not.toBeNull();
    http.expectNone('/api/stores/s1/applications');

    const text = q('[data-testid="stores.apply.text"]') as HTMLTextAreaElement;
    text.value = 'Ich helfe gern';
    text.dispatchEvent(new Event('input'));
    (q('[data-testid="stores.apply.submit"] button') as HTMLButtonElement).click();
    await fixture.whenStable();
    const req = http.expectOne('/api/stores/s1/applications');
    expect(req.request.body).toEqual({ text: 'Ich helfe gern' });
    req.flush(
      { status: 409, code: 'HYGIENE_CERTIFICATE_REQUIRED', message: 'x', retryable: false },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();
    expect(q('[data-testid="stores.apply.error"]')?.getAttribute('data-code')).toBe('HYGIENE_CERTIFICATE_REQUIRED');
    fixture.destroy();
  });
});

describe('store team tab (S5, S9)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const qa = (selector: string) => [...harness.routeNativeElement!.querySelectorAll(selector)];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            {
              path: 'stores/:storeId',
              component: StorePageComponent,
              children: [{ path: 'team', component: StoreTeamTab }],
            },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const MEMBERS_URL = '/api/stores/s1/team/members';
  const settle = () => harness.fixture.whenStable();
  const click = async (testId: string) => {
    (qa(`[data-testid="${testId}"]`)[0] as HTMLButtonElement).click();
    await settle();
  };

  const open = async (store: Record<string, unknown>, people = page(PEOPLE)) => {
    await harness.navigateByUrl('/stores/s1/team');
    http.expectOne('/api/stores/s1').flush(store);
    await settle();
    const req = http.expectOne((r) => r.url === MEMBERS_URL);
    http.expectOne('/api/stores/s1/team').flush(TEAM);
    req.flush(people);
    await settle();
    return req;
  };

  it('managers get row menus, applications and the invite button', async () => {
    await open(detail({ mayEditTeam: true, maySeeMemberDistance: true, isManager: true }));
    expect(qa('[data-testid="stores.team.member"]')).toHaveLength(3);
    expect(qa('[data-testid="stores.team.manager"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.team.jumperBadge"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.team.menu"]')).toHaveLength(3);
    expect(qa('[data-testid="stores.team.application"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.team.invite"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.team.distance"]')).toHaveLength(1);
  });

  it('offers "appoint" only as the server allows it: hidden, disabled with the reason, or enabled', async () => {
    await open(
      detail({ mayEditTeam: true, isManager: true }),
      page([
        member('Nina', { isManager: true, appointBlocker: TeamAppointBlocker.AlreadyManager }),
        member('Sarah', { appointBlocker: TeamAppointBlocker.RoleTooLow }),
        member('Tom', { appointBlocker: TeamAppointBlocker.TooManyManagers }),
        member('Uli', { mayBeAppointed: true }),
      ]),
    );
    /** Opens the row menu of the index-th member (a click on an open menu closes it first). */
    const menuFor = async (index: number) => {
      for (let i = 0; i < 2 && !document.body.querySelector('[data-testid="stores.team.remove"]'); i++) {
        (qa('[data-testid="stores.team.menu"] button')[index] as HTMLButtonElement).click();
        await settle();
      }
      expect(document.body.querySelector('[data-testid="stores.team.remove"]')).not.toBeNull();
      const item = document.body.querySelector('[data-testid="stores.team.appointManager"]') as HTMLButtonElement | null;
      const hint = document.body.querySelector('[data-testid="stores.team.appointBlocked"]')?.textContent ?? null;
      (qa('[data-testid="stores.team.menu"] button')[index] as HTMLButtonElement).click();
      await settle();
      return { item, hint };
    };
    expect((await menuFor(1)).item).toBeNull();
    const allowed = await menuFor(3);
    expect(allowed.item?.disabled).toBe(false);
    expect(allowed.hint).toBeNull();
    const blocked = await menuFor(2);
    expect(blocked.item?.disabled).toBe(true);
    expect(blocked.hint).toContain('stores.team.appointBlocked.TOO_MANY_MANAGERS');
  });

  it('members see the team without actions', async () => {
    await open(detail({}));
    expect(qa('[data-testid="stores.team.member"]')).toHaveLength(3);
    expect(qa('[data-testid="stores.team.menu"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.team.invite"]')).toHaveLength(0);
  });

  it('sends the default params and shows the chip counts from the store', async () => {
    const req = await open(detail({}));
    expect(req.request.params.get('status')).toBe('ALL');
    expect(req.request.params.get('sort')).toBe('NAME');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('search')).toBe(false);
    expect(qa('[data-testid="stores.team.filter.all"]')[0].textContent).toContain('3');
    expect(qa('[data-testid="stores.team.filter.member"]')[0].textContent).toContain('2');
    expect(qa('[data-testid="stores.team.filter.jumper"]')[0].textContent).toContain('1');
    expect(qa('[data-testid="stores.team.filter.manager"]')[0].textContent?.trim()).toBe('stores.team.filter.manager');
    expect(qa('[data-testid="stores.team.filter.all"]')[0].getAttribute('aria-pressed')).toBe('true');
    expect(qa('[data-testid="stores.team.paginator"]')).toHaveLength(0);
  });

  it('a chip filters on the server and resets to page 1', async () => {
    await open(detail({}), page(PEOPLE, 45));
    expect(qa('[data-testid="stores.team.paginator"]')).toHaveLength(1);
    await click('stores.team.filter.jumper');
    const jumpers = http.expectOne((r) => r.url === MEMBERS_URL);
    expect(jumpers.request.params.get('status')).toBe('JUMPER');
    expect(jumpers.request.params.get('page')).toBe('0');
    jumpers.flush(page([PEOPLE[2]]));
    await settle();
    expect(qa('[data-testid="stores.team.member"]').map((row) => row.getAttribute('data-status'))).toEqual(['JUMPER']);
    expect(qa('[data-testid="stores.team.filter.jumper"]')[0].getAttribute('aria-pressed')).toBe('true');
  });

  it('the search is debounced and sent trimmed', async () => {
    await open(detail({}));
    const input = qa('[data-testid="stores.team.search"]')[0] as HTMLInputElement;
    input.value = '  Jon ';
    input.dispatchEvent(new Event('input'));
    await settle();
    http.expectNone((r) => r.url === MEMBERS_URL);
    await new Promise((resolve) => setTimeout(resolve, 350));
    await settle();
    const req = http.expectOne((r) => r.url === MEMBERS_URL);
    expect(req.request.params.get('search')).toBe('Jon');
    req.flush(page([PEOPLE[2]]));
    await settle();
  });

  it('an empty filter result offers a reset back to all', async () => {
    await open(detail({}));
    await click('stores.team.filter.manager');
    http.expectOne((r) => r.url === MEMBERS_URL).flush(page([]));
    await settle();
    expect(qa('[data-testid="stores.team.emptyFilter"]')).toHaveLength(1);
    (qa('[data-testid="stores.team.resetFilter"] button')[0] as HTMLButtonElement).click();
    await settle();
    const reset = http.expectOne((r) => r.url === MEMBERS_URL);
    expect(reset.request.params.get('status')).toBe('ALL');
    reset.flush(page(PEOPLE));
    await settle();
    expect(qa('[data-testid="stores.team.member"]')).toHaveLength(3);
  });

  it('an empty team without a filter shows the plain hint', async () => {
    await open(detail({}), page([]));
    expect(qa('[data-testid="stores.team.emptyFilter"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.team.list"]')[0].textContent).toContain('stores.team.noMembers');
  });

  it('a page change asks for that page with the same filter', async () => {
    await open(detail({}), page(PEOPLE, 45));
    const next = qa('[data-testid="stores.team.paginator"] .p-paginator-next')[0] as HTMLButtonElement;
    next.click();
    await settle();
    const req = http.expectOne((r) => r.url === MEMBERS_URL);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('status')).toBe('ALL');
    req.flush(page(PEOPLE, 45, 1));
    await settle();
  });
});

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
    teamCounts: { members: 2, jumpers: 1, applied: 1, invited: 0 },
    membership: 'MEMBER',
    prefetchDays: 14,
    permissions: { mayAccess: true, teamStatus: 'MEMBER', ...permissions },
  };
}
