import { Role, SessionInfo, StoresImportPreview, UnitMembership, UnitType } from '../../api';
import { canConfirmStoresImport, mayImportStores, mightCoordinateStores } from './stores.util';

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

describe('mayImportStores (S2 import entry, RegionDetail.mayCoordinateStores)', () => {
  it('ORGA may always import, even before the region is loaded', () => {
    expect(mayImportStores(session(Role.Orga), undefined)).toBe(true);
    expect(mayImportStores(session(Role.Orga), { mayCoordinateStores: false })).toBe(true);
  });

  it('everybody else follows the server flag of the region', () => {
    const ambassador = session(Role.Ambassador, [unit({ id: 'zh', admin: true })]);
    expect(mayImportStores(ambassador, { mayCoordinateStores: true })).toBe(true);
    // the server decided against it (e.g. a store coordination group supersedes the ambassadors)
    expect(mayImportStores(ambassador, { mayCoordinateStores: false })).toBe(false);
    expect(mayImportStores(ambassador, {})).toBe(false);
    expect(mayImportStores(ambassador, undefined)).toBe(false);
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
