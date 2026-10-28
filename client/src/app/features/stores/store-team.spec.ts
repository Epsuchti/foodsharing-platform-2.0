import { HttpTestingController } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { StoreMembershipStatus, StoreTeam, TeamMember, Role } from '../../api';
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
  members: [member('Nina', { isManager: true }), member('Sarah', { distanceKm: 2.25 })],
  jumpers: [member('Jonas', { status: StoreMembershipStatus.Jumper })],
  applications: [{ user: member('Julien').user, text: 'Hallo', appliedAt: '2026-10-01T10:00:00Z' }],
  invitations: [],
};

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

  const open = async (store: Record<string, unknown>) => {
    await harness.navigateByUrl('/stores/s1/team');
    http.expectOne('/api/stores/s1').flush(store);
    await harness.fixture.whenStable();
    http.expectOne('/api/stores/s1/team').flush(TEAM);
    await harness.fixture.whenStable();
  };

  it('managers get row menus, applications and the invite button', async () => {
    await open(detail({ mayEditTeam: true, maySeeMemberDistance: true, isManager: true }));
    expect(qa('[data-testid="stores.team.member"]')).toHaveLength(3);
    expect(qa('[data-testid="stores.team.manager"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.team.menu"]')).toHaveLength(3);
    expect(qa('[data-testid="stores.team.application"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.team.invite"]')).toHaveLength(1);
    expect(qa('[data-testid="stores.team.distance"]')).toHaveLength(1);
  });

  it('members see the team without actions', async () => {
    await open(detail({}));
    expect(qa('[data-testid="stores.team.member"]')).toHaveLength(3);
    expect(qa('[data-testid="stores.team.menu"]')).toHaveLength(0);
    expect(qa('[data-testid="stores.team.invite"]')).toHaveLength(0);
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
