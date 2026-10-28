import { Language, Permission, Role, SessionInfo, Theme, UnitType } from '../../api';
import { hasPermission, homeRegion, isAmbassador, isMemberOf, memberRegions } from './permission.util';

const ZH = '00000010-0001-4000-8000-000000000040';
const WT = '00000010-0001-4000-8000-000000000041';
const GROUP = '00000011-0001-4000-8000-000000000001';

function session(role: Role, permissions: string[] = []): SessionInfo {
  return {
    authenticated: true,
    permissions,
    user: {
      id: 'u',
      email: 'u@local',
      displayName: 'U',
      role,
      language: Language.De,
      theme: Theme.System,
      verified: true,
    },
    units: [
      { id: WT, name: 'Winterthur', slug: 'winterthur', type: UnitType.City, member: true, admin: false, homeRegion: false },
      { id: ZH, name: 'Zürich Stadt', slug: 'zuerich-stadt', type: UnitType.BigCity, member: true, admin: true, homeRegion: true },
      { id: GROUP, name: 'Welcome', slug: 'welcome', type: UnitType.WorkingGroup, member: true, admin: true, homeRegion: false },
    ],
  };
}

describe('permission.util (A10)', () => {
  it('checks scoped permissions', () => {
    const s = session(Role.Foodsaver, [`CAN_MANAGE_USERS@${ZH}`]);
    expect(hasPermission(s, Permission.CanManageUsers, ZH)).toBe(true);
    expect(hasPermission(s, Permission.CanManageUsers, WT)).toBe(false);
    expect(hasPermission(s, Permission.CanManageUsers)).toBe(true);
    expect(hasPermission(s, Permission.CanGiveIntroPickups)).toBe(false);
    expect(hasPermission(null, Permission.CanManageUsers)).toBe(false);
  });

  it('knows ambassadors (region admins, not group admins) and ORGA', () => {
    const s = session(Role.Ambassador);
    expect(isAmbassador(s, ZH)).toBe(true);
    expect(isAmbassador(s, WT)).toBe(false);
    expect(isAmbassador(s, GROUP)).toBe(false);
    expect(isAmbassador(session(Role.Orga), WT)).toBe(true);
  });

  it('lists member regions with the home region first', () => {
    const s = session(Role.Foodsaver);
    expect(memberRegions(s).map((unit) => unit.slug)).toEqual(['zuerich-stadt', 'winterthur']);
    expect(homeRegion(s)?.id).toBe(ZH);
    expect(isMemberOf(s, GROUP)).toBe(true);
    expect(isMemberOf(s, 'other')).toBe(false);
  });
});
