import { Resource, ResourceCategory, ResourceOpenness, UnitType } from '../../api';

/**
 * Unit types with a resource mosaic (upstream `REGION_TYPES_WITH_RESOURCES`, A55); other regions answer 403
 * RESOURCES_NOT_AVAILABLE, so the region page hides the tab there.
 */
export const RESOURCE_UNIT_TYPES: readonly UnitType[] = [UnitType.BigCity, UnitType.City, UnitType.PartOfTown, UnitType.WorkingGroup];

/**
 * Own resources per user (`app.resources.max-per-user`, default 10). Only for the "x of 10" hint on /resources/mine:
 * the region tab uses `ResourcePermissions.mayAdd` and the server answers 409 RESOURCE_LIMIT_REACHED.
 */
export const RESOURCE_LIMIT = 10;
export const RESOURCE_NAME_MAX = 200;
export const RESOURCE_DESCRIPTION_MAX = 4000;
export const RESOURCE_CATEGORIES_MAX = 5;
export const RESOURCE_IMAGES_MAX = 5;

export const OPENNESS_VALUES: readonly ResourceOpenness[] = [ResourceOpenness.AskFirst, ResourceOpenness.FreeToBorrow, ResourceOpenness.GiveAway];

export function hasResources(type: UnitType | string | undefined | null): boolean {
  return !!type && (RESOURCE_UNIT_TYPES as readonly string[]).includes(type);
}

/** Commons belong to the region (no owner, A55). */
export function isCommons(resource: Resource): boolean {
  return !resource.owner;
}

/** Region list → commons first section, members' resources second (both keep the server order). */
export function splitCommons(resources: readonly Resource[]): { commons: Resource[]; members: Resource[] } {
  return {
    commons: resources.filter(isCommons),
    members: resources.filter((resource) => !isCommons(resource)),
  };
}

/** Categories used by the listed resources, by name (the category chips). */
export function usedCategories(resources: readonly Resource[]): ResourceCategory[] {
  const byId = new Map<string, ResourceCategory>();
  for (const resource of resources) {
    for (const category of resource.categories) byId.set(category.id, category);
  }
  return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name));
}

/** Resources in the category (null = all). */
export function inCategory(resources: readonly Resource[], categoryId: string | null): Resource[] {
  return categoryId ? resources.filter((resource) => resource.categories.some((category) => category.id === categoryId)) : [...resources];
}

/** Within the per-user limit (the server rule `ResourceRules.mayAdd`). */
export function mayAddMore(ownCount: number, limit = RESOURCE_LIMIT): boolean {
  return ownCount < limit;
}

/** Form check mirrored from `ResourceCreate`: name 1–200, 1–5 categories, description ≤ 4000. */
export function resourceFormErrors(value: { name: string; description: string; categoryIds: readonly string[] }): string[] {
  const errors: string[] = [];
  const name = value.name.trim();
  if (!name) errors.push('nameRequired');
  if (name.length > RESOURCE_NAME_MAX) errors.push('nameTooLong');
  if (value.description.length > RESOURCE_DESCRIPTION_MAX) errors.push('descriptionTooLong');
  if (value.categoryIds.length < 1) errors.push('categoryRequired');
  if (value.categoryIds.length > RESOURCE_CATEGORIES_MAX) errors.push('tooManyCategories');
  return errors;
}

/** Sub-categories follow their parent; top-level categories by name. */
export function sortCategories(categories: readonly ResourceCategory[]): ResourceCategory[] {
  const roots = categories.filter((category) => !category.parentId || !categories.some((c) => c.id === category.parentId));
  const byName = (a: ResourceCategory, b: ResourceCategory) => a.name.localeCompare(b.name);
  return roots.sort(byName).flatMap((root) => [root, ...categories.filter((c) => c.parentId === root.id).sort(byName)]);
}
