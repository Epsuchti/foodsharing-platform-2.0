import { Role, SessionInfo, StoresImportPreview, UnitMembership, UnitType, WorkingGroupFunction } from '../../api';
import {
  RegionCoordinationInfo,
  canConfirmStoresImport,
  mayCoordinateRegionStores,
  mightCoordinateStores,
} from './stores.util';

const unit = (over: Partial<UnitMembership>): UnitMembership => ({
  id: 'u',
  name: 'Unit',
  slug: 'unit',
  type: UnitType.City,
  member: true,
  admin: false,
  homeRegion: false,
  ...over,
});

const session = (role: Role, units: UnitMembership[] = []): SessionInfo =>
  ({ authenticated: true, user: { role }, units, permissions: [] }) as unknown as SessionInfo;

const region = (over: Partial<RegionCoordinationInfo> = {}): RegionCoordinationInfo => ({
  id: 'zh',
  parentId: 'kanton',
  functions: [],
  membership: { member: true, admin: false, homeRegion: false },
  ...over,
});

const coordinated = region({
  functions: [{ function: WorkingGroupFunction.StoresCoordination, group: { id: 'coord', slug: 'coord', name: 'Coord' } }],
  membership: { member: true, admin: true, homeRegion: false },
});

describe('mayCoordinateRegionStores (S2 import entry)', () => {
  it('ORGA may always coordinate', () => {
    expect(mayCoordinateRegionStores(session(Role.Orga), coordinated)).toBe(true);
  });

  it('with a store coordination group only its admins may coordinate, not the ambassadors', () => {
    const groupAdmin = session(Role.StoreManager, [unit({ id: 'coord', type: UnitType.WorkingGroup, admin: true })]);
    const groupMember = session(Role.StoreManager, [unit({ id: 'coord', type: UnitType.WorkingGroup })]);
    const ambassador = session(Role.Ambassador, [unit({ id: 'zh', admin: true })]);
    expect(mayCoordinateRegionStores(groupAdmin, coordinated)).toBe(true);
    expect(mayCoordinateRegionStores(groupMember, coordinated)).toBe(false);
    expect(mayCoordinateRegionStores(ambassador, coordinated)).toBe(false);
  });

  it('without a coordination group ambassadors of the region or its parent may coordinate', () => {
    const plain = session(Role.Foodsaver, [unit({ id: 'zh' })]);
    expect(mayCoordinateRegionStores(plain, region())).toBe(false);
    expect(
      mayCoordinateRegionStores(plain, region({ membership: { member: true, admin: true, homeRegion: false } })),
    ).toBe(true);
    const parentAmbassador = session(Role.Ambassador, [unit({ id: 'kanton', admin: true })]);
    expect(mayCoordinateRegionStores(parentAmbassador, region())).toBe(true);
    // admins of a working group with the parent's id do not count as ambassadors
    const groupWithParentId = session(Role.Foodsaver, [unit({ id: 'kanton', type: UnitType.WorkingGroup, admin: true })]);
    expect(mayCoordinateRegionStores(groupWithParentId, region())).toBe(false);
    expect(mayCoordinateRegionStores(null, region())).toBe(false);
  });

  it('only ORGA and unit admins need the region to decide', () => {
    expect(mightCoordinateStores(session(Role.Orga))).toBe(true);
    expect(mightCoordinateStores(session(Role.Foodsaver, [unit({ admin: true })]))).toBe(true);
    expect(mightCoordinateStores(session(Role.Foodsaver, [unit({})]))).toBe(false);
    expect(mightCoordinateStores(null)).toBe(false);
  });
});

describe('canConfirmStoresImport', () => {
  const preview = (over: Partial<StoresImportPreview> = {}): StoresImportPreview => ({
    rows: [],
    created: 1,
    updated: 0,
    skipped: 0,
    errors: 1,
    dryRun: true,
    ...over,
  });

  it('allows confirming a dry run of the unchanged text that writes something, even with error rows', () => {
    expect(canConfirmStoresImport(preview(), 'a', 'a')).toBe(true);
    expect(canConfirmStoresImport(preview({ created: 0, updated: 2 }), 'a', 'a')).toBe(true);
  });

  it('blocks without preview, after edits, for results and when nothing would be written', () => {
    expect(canConfirmStoresImport(null, 'a', 'a')).toBe(false);
    expect(canConfirmStoresImport(preview(), 'a', 'b')).toBe(false);
    expect(canConfirmStoresImport(preview({ dryRun: false }), 'a', 'a')).toBe(false);
    expect(canConfirmStoresImport(preview({ created: 0, updated: 0, skipped: 3 }), 'a', 'a')).toBe(false);
  });
});
