import { Role, SessionInfo, StoreChain, StoreChainStatus, UnitType, WorkingGroupFunction } from '../../api';
import { isKamOf, mayAdministrateChains } from './store-chains.util';

const session = (role: Role, units: SessionInfo['units'] = []): SessionInfo => ({
  authenticated: true,
  permissions: [],
  user: { id: 'u1', role } as SessionInfo['user'],
  units,
});

const group = (admin: boolean, fn?: WorkingGroupFunction) => ({
  id: 'g1',
  name: 'Betriebsketten CH',
  slug: 'chains',
  type: UnitType.WorkingGroup,
  member: true,
  admin,
  homeRegion: false,
  ...(fn ? { function: fn } : {}),
});

describe('store chain rights (decision 55)', () => {
  it('ORGA and admins of a STORE_CHAINS group administrate chains', () => {
    expect(mayAdministrateChains(session(Role.Orga))).toBe(true);
    expect(mayAdministrateChains(session(Role.StoreManager, [group(true, WorkingGroupFunction.StoreChains)]))).toBe(true);
    expect(mayAdministrateChains(session(Role.StoreManager, [group(false, WorkingGroupFunction.StoreChains)]))).toBe(false);
    expect(mayAdministrateChains(session(Role.Ambassador, [group(true, WorkingGroupFunction.Welcome)]))).toBe(false);
    expect(mayAdministrateChains(null)).toBe(false);
  });

  it('KAMs are recognised by id', () => {
    const chain = {
      id: 'c1',
      name: 'Coop',
      status: StoreChainStatus.Cooperating,
      pressAllowed: true,
      estimatedStoreCount: 10,
      storeCount: 4,
      kams: [{ id: 'u1', displayName: 'Nina', role: Role.StoreManager, verified: true }],
    } as StoreChain;
    expect(isKamOf(chain, 'u1')).toBe(true);
    expect(isKamOf(chain, 'u2')).toBe(false);
    expect(isKamOf(undefined, 'u1')).toBe(false);
  });
});
