import { Permission, Role, SessionInfo, UnitMembership } from '../../api';
import { hasScopedPermission } from './auth.guard';

/**
 * Permission checks on a SessionInfo (conventions A10): `permissions` holds `<PERMISSION>@<regionId>` (ORGA:
 * `@*`). Without `regionId` a grant for any region counts. The server re-checks every call; these only decide what
 * the UI offers.
 */
export function hasPermission(
  session: SessionInfo | null | undefined,
  permission: Permission | string,
  regionId?: string | null,
): boolean {
  return !!session?.authenticated && hasScopedPermission(session.permissions ?? [], permission, regionId);
}

export function isOrga(session: SessionInfo | null | undefined): boolean {
  return session?.user?.role === Role.Orga;
}

/** Ambassador of the region (admin of a region unit) or ORGA; without `regionId`: of any region. */
export function isAmbassador(session: SessionInfo | null | undefined, regionId?: string | null): boolean {
  if (isOrga(session)) {
    return true;
  }
  return (session?.units ?? []).some(
    (unit) => unit.admin && unit.type !== 'WORKING_GROUP' && (!regionId || unit.id === regionId),
  );
}

export function isMemberOf(session: SessionInfo | null | undefined, unitId: string): boolean {
  return (session?.units ?? []).some((unit) => unit.id === unitId && unit.member);
}

/** Regions (not working groups) the user belongs to, home region first. */
export function memberRegions(session: SessionInfo | null | undefined): UnitMembership[] {
  return (session?.units ?? [])
    .filter((unit) => unit.member && unit.type !== 'WORKING_GROUP')
    .sort((a, b) => Number(b.homeRegion) - Number(a.homeRegion) || a.name.localeCompare(b.name));
}

export function homeRegion(session: SessionInfo | null | undefined): UnitMembership | null {
  return (session?.units ?? []).find((unit) => unit.homeRegion) ?? null;
}
