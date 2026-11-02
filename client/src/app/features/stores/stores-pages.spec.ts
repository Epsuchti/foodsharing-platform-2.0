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
  PublicityStatus,
} from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { MyStoresPageComponent } from './pages/my-stores-page.component';
import { RegionStoresPageComponent } from './pages/region-stores-page.component';
import { StoreFormPageComponent } from './pages/store-form-page.component';
import { StorePageComponent } from './pages/store-page.component';
import { navigationLinks } from '../../shared/navigate-button/navigate.util';
import { isInTeam } from './tabs/store-overview.tab';

describe('store overview requirements wording', () => {
  it('treats members and coordinators as in the team, everyone else as a possible applicant', () => {
    expect(isInTeam(UserTeamStatus.Member)).toBe(true);
    expect(isInTeam(UserTeamStatus.Coordinator)).toBe(true);
    for (const status of [UserTeamStatus.NoMember, UserTeamStatus.Applied, UserTeamStatus.Invited, UserTeamStatus.WaitingList, undefined]) {
      expect(isInTeam(status), String(status)).toBe(false);
    }
  });
});

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
            { path: 'stores/:storeId/edit', component: StoreFormPageComponent },
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

  const openForm = async (over: Partial<StoreDetail>) => {
    await harness.navigateByUrl('/stores/s1/edit');
    http.expectOne('/api/store-metadata').flush({ categories: [], groceries: [], weightCategories: [], chains: [] });
    http.expectOne('/api/stores/s1').flush(detail(over));
    await new Promise((resolve) => setTimeout(resolve));
    await settle();
    return q('#sf-public-name') as HTMLInputElement;
  };

  it('store form: the public name switch is for managers and ORGA, with a warning, and only with publicity ALLOWED', async () => {
    const manager = await openForm({
      publicity: PublicityStatus.Allowed,
      publiclyNamed: true,
      permissions: permissions({ mayEditStore: true, mayChangePublicNaming: true }),
    });
    expect(manager.disabled).toBe(false);
    expect(manager.checked).toBe(true);
    expect(q('[data-testid="stores.form.publicNameWarning"]')).not.toBeNull();
    expect(q('[data-testid="stores.form.requiresVerified"]')).toBeNull();
  });

  it('store form: a coordinator who does not manage the store sees the switch disabled', async () => {
    const input = await openForm({ publicity: PublicityStatus.Allowed, permissions: permissions({ mayEditStore: true }) });
    expect(input.disabled).toBe(true);
  });

  it('store form: the switch stays off while publicity is not allowed', async () => {
    const input = await openForm({
      publicity: PublicityStatus.NotChosen,
      permissions: permissions({ mayEditStore: true, mayChangePublicNaming: true }),
    });
    expect(input.disabled).toBe(true);
  });

  it('store page: jumpers see only the overview, team and statistics tabs, no edit, no contact call', async () => {
    await harness.navigateByUrl('/stores/s1');
    http.expectOne('/api/stores/s1').flush(detail());
    await settle();
    expect(q('[data-testid="stores.page.name"]')?.textContent?.trim()).toBe('Coop Bahnhofbrücke');
    expect(qa('[data-testid^="stores.page.tab."]').map((el) => el.getAttribute('data-testid'))).toEqual([
      'stores.page.tab.overview',
      'stores.page.tab.team',
      'stores.page.tab.statistics',
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

  it('store page: one "Navigate" button opens a menu with both directions links (resume item 15)', async () => {
    await harness.navigateByUrl('/stores/s1');
    http.expectOne('/api/stores/s1').flush(detail());
    await settle();
    const trigger = q('[data-testid="stores.page.navigate"]') as HTMLButtonElement;
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    trigger.click();
    await settle();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    // the popover renders to the body
    const item = (id: string) => document.querySelector(`[data-testid="${id}"]`);
    expect(item('stores.page.navigate.google')?.getAttribute('href')).toContain('destination=47.3779,8.5427');
    expect(item('stores.page.navigate.apple')?.getAttribute('href')).toContain('daddr=47.3779,8.5427');
  });

  it('navigation links use coordinates, else the address', () => {
    const links = (store: ReturnType<typeof detail>) =>
      navigationLinks({ lat: store.latitude, lon: store.longitude, ...store.address, label: store.name });
    expect(links(detail())?.google).toContain('destination=47.3779,8.5427');
    expect(links(detail({ latitude: undefined, longitude: undefined }))?.google).toContain('Bahnhofbr%C3%BCcke%201');
  });
});
