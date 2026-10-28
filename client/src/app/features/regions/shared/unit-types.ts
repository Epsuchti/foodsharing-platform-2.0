import { UnitType } from '../../../api';

/** Region types people join directly (conventions A10, server `UnitType.isAccessible`). */
export const ACCESSIBLE_UNIT_TYPES: readonly UnitType[] = [
  UnitType.PartOfTown,
  UnitType.City,
  UnitType.Region,
  UnitType.District,
];

export function isAccessibleType(type: UnitType | string | null | undefined): boolean {
  return ACCESSIBLE_UNIT_TYPES.includes(type as UnitType);
}
