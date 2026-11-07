import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { CooperationStatus, StoreDetail, StorePermissions, TeamSearchStatus, UnitType, UserTeamStatus } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { StorePageContext } from '../store-page.context';
import { StoreOverviewTab } from './store-overview.tab';

const permissions = (over: Partial<StorePermissions> = {}): StorePermissions => ({
  mayAccess: true,
  mayChangePublicNaming: false,
  mayReadWall: false,
  mayPostWall: false,
  mayDeleteAnyWallPost: false,
  mayEditStore: false,
  mayEditTeam: false,
  maySeePhoneNumbers: false,
  maySeeMemberDistance: false,
  maySeePickups: false,
  mayEditPickups: false,
  mayConfirmPickups: false,
  maySeePickupHistory: false,
  maySeeStoreLog: false,
  mayDelete: false,
  mayApply: false,
  mayLeave: true,
  mayChatTeam: false,
  mayChatJumper: false,
  isKam: false,
  isManager: false,
  teamStatus: UserTeamStatus.WaitingList,
  ...over,
});

const detail = (over: Partial<StoreDetail> = {}): StoreDetail => ({
  id: 's1',
  name: 'Coop Bahnhofbrücke',
  address: { street: 'Bahnhofbrücke 1', postcode: '8001', city: 'Zürich' },
  cooperationStatus: CooperationStatus.CooperationEstablished,
  prefetchDays: 14,
  teamStatus: TeamSearchStatus.Open,
  region: { id: 'r1', slug: 'zh', name: 'Zürich', type: UnitType.City },
  timeZone: 'Europe/Zurich',
  managers: [],
  teamCounts: { members: 3, jumpers: 1, applied: 0, invited: 0 },
  membership: UserTeamStatus.WaitingList,
  permissions: permissions(),
  ...over,
});

/** Provides the page context like StorePageComponent does. */
@Component({
  imports: [StoreOverviewTab],
  providers: [StorePageContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<fs-store-overview-tab />`,
})
class StorePageHost {}

/** Decision 171 (D1, D4): description for the team (the server leaves it out otherwise), public info for everyone. */
describe('store overview tab', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (testId: string) => harness.routeNativeElement!.querySelector(`[data-testid="${testId}"]`);

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [{ path: 'stores/:storeId/overview', component: StorePageHost }],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  async function open(store: StoreDetail): Promise<void> {
    await harness.navigateByUrl('/stores/s1/overview');
    await new Promise((resolve) => setTimeout(resolve));
    await harness.fixture.whenStable();
    http.expectOne('/api/stores/s1').flush(store);
    await harness.fixture.whenStable();
  }

  it('renders the description and the public information as Markdown for the team', async () => {
    await open(
      detail({
        membership: UserTeamStatus.Member,
        permissions: permissions({ mayReadWall: true, teamStatus: UserTeamStatus.Member }),
        description: 'Hintereingang **Rampe 2**',
        publicInfo: 'Abholung _nur_ nach Absprache',
      }),
    );
    expect(q('stores.overview.description')?.querySelector('strong')?.textContent).toBe('Rampe 2');
    expect(q('stores.overview.publicInfoCard')).not.toBeNull();
    expect(q('stores.overview.publicInfo')?.querySelector('em')?.textContent).toBe('nur');
    expect(q('stores.overview.publicInfoEmpty')).toBeNull();
  });

  it('shows the public information card without a description to the waiting list', async () => {
    await open(detail({ publicInfo: 'Abholung nach Absprache' }));
    expect(q('stores.overview.description')).toBeNull();
    expect(q('stores.overview.publicInfo')?.textContent).toContain('Abholung nach Absprache');
  });

  it('says when there is no public information', async () => {
    await open(detail());
    expect(q('stores.overview.publicInfoCard')).not.toBeNull();
    expect(q('stores.overview.publicInfoEmpty')).not.toBeNull();
  });
});
