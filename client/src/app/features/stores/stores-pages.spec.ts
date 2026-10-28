import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import {
  CooperationStatus,
  FreeSlotStatus,
  StoreDetail,
  StoreListItem,
  StorePermissions,
  TeamSearchStatus,
  UnitType,
  UserTeamStatus,
} from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { MyStoresPageComponent } from './pages/my-stores-page.component';
import { RegionStoresPageComponent } from './pages/region-stores-page.component';
import { StorePageComponent } from './pages/store-page.component';
import { mapLinks } from './stores.util';

const REGION = { id: 'r1', slug: 'zh', name: 'Zürich', type: UnitType.City };

const item = (over: Partial<StoreListItem>): StoreListItem => ({
  id: over.name ?? 's',
  name: 'Store',
  address: { street: 'Weg 1', postcode: '8000', city: 'Zürich' },
  cooperationStatus: CooperationStatus.CooperationEstablished,
  teamStatus: TeamSearchStatus.Open,
  region: REGION,
  membership: UserTeamStatus.Member,
  isManager: false,
  freeSlotStatus: FreeSlotStatus.Green,
  freeSlotCount: 0,
  ...over,
});

const permissions = (over: Partial<StorePermissions> = {}): StorePermissions => ({
  mayAccess: true,
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
  latitude: 47.3779,
  longitude: 8.5427,
  cooperationStatus: CooperationStatus.CooperationEstablished,
  prefetchDays: 14,
  teamStatus: TeamSearchStatus.Open,
  region: REGION,
  timeZone: 'Europe/Zurich',
  managers: [],
  teamCounts: { members: 3, jumpers: 1, applied: 0, invited: 0 },
  membership: UserTeamStatus.WaitingList,
  permissions: permissions(),
  contact: { person: 'Filialleitung', phone: '+41440000001' },
  ...over,
});

describe('stores pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const qa = (selector: string) => [...harness.routeNativeElement!.querySelectorAll(selector)];
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'stores/mine', component: MyStoresPageComponent },
            { path: 'regions/:regionId/stores', component: RegionStoresPageComponent },
            { path: 'stores/:storeId', component: StorePageComponent, children: [{ path: '**', children: [] }] },
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

  it('my stores: groups by membership, hides closed stores until toggled', async () => {
    await harness.navigateByUrl('/stores/mine');
    http.expectOne((req) => req.url === '/api/users/me/stores').flush([
      item({ name: 'A', isManager: true, membership: UserTeamStatus.Coordinator }),
      item({ name: 'B' }),
      item({ name: 'C', membership: UserTeamStatus.WaitingList }),
      item({ name: 'D', membership: UserTeamStatus.Invited }),
      item({ name: 'E', cooperationStatus: CooperationStatus.PermanentlyClosed }),
    ]);
    await settle();
    expect(qa('[data-testid^="stores.mine.group."]').map((el) => el.getAttribute('data-testid'))).toEqual([
      'stores.mine.group.manager',
      'stores.mine.group.member',
      'stores.mine.group.jumper',
      'stores.mine.group.invited',
    ]);
    expect(qa('[data-testid="stores.mine.store"]')).toHaveLength(4);
  });

  it('my stores: empty and error with retry', async () => {
    await harness.navigateByUrl('/stores/mine');
    http
      .expectOne((req) => req.url === '/api/users/me/stores')
      .flush({ status: 500, code: 'INTERNAL_ERROR', message: 'x', retryable: true }, { status: 500, statusText: 'Error' });
    await settle();
    expect(q('[data-testid="stores.mine.state"]')?.getAttribute('data-state')).toBe('error');
    (q('[data-testid="stores.mine.state.retry"] button') as HTMLButtonElement).click();
    await settle();
    http.expectOne((req) => req.url === '/api/users/me/stores').flush([]);
    await settle();
    expect(q('[data-testid="stores.mine.state"]')?.getAttribute('data-state')).toBe('empty');
  });

  it('region stores: requests the four default cooperation states', async () => {
    await harness.navigateByUrl('/regions/r1/stores');
    const req = http.expectOne((r) => r.url === '/api/regions/r1/stores');
    expect(req.request.params.getAll('cooperationStatus')).toEqual([
      'UNCLEAR',
      'NO_CONTACT',
      'IN_NEGOTIATION',
      'COOPERATION_ESTABLISHED',
    ]);
    req.flush({ items: [item({ name: 'X' })], page: { page: 0, size: 200, totalItems: 1, totalPages: 1 } });
    await settle();
    expect(qa('[data-testid="stores.region.row"]')).toHaveLength(1);
  });

  it('store page: jumpers see only the overview and team tabs, no edit, no contact call', async () => {
    await harness.navigateByUrl('/stores/s1');
    http.expectOne('/api/stores/s1').flush(detail());
    await settle();
    expect(q('[data-testid="stores.page.name"]')?.textContent?.trim()).toBe('Coop Bahnhofbrücke');
    expect(qa('[data-testid^="stores.page.tab."]').map((el) => el.getAttribute('data-testid'))).toEqual([
      'stores.page.tab.overview',
      'stores.page.tab.team',
    ]);
    expect(q('[data-testid="stores.page.edit"]')).toBeNull();
    expect(q('[data-testid="stores.page.call"]')).toBeNull();
  });

  it('store page: managers get the edit button and the contact call', async () => {
    await harness.navigateByUrl('/stores/s1');
    http
      .expectOne('/api/stores/s1')
      .flush(detail({ permissions: permissions({ mayEditStore: true, mayReadWall: true, teamStatus: UserTeamStatus.Coordinator }) }));
    await settle();
    expect(q('[data-testid="stores.page.edit"]')).not.toBeNull();
    expect(q('[data-testid="stores.page.call"]')).not.toBeNull();
  });

  it('map links use coordinates, else the address', () => {
    expect(mapLinks(detail())?.google).toContain('query=47.3779,8.5427');
    expect(mapLinks(detail({ latitude: undefined, longitude: undefined }))?.google).toContain('Bahnhofbr%C3%BCcke%201');
  });
});
