import { Resource, ResourceOpenness, Role } from '../../api';
import { hasResources, inCategory, mayAddMore, resourceFormErrors, sortCategories, splitCommons, usedCategories } from './resources.util';

const TOOLS = { id: 'c1', name: 'Werkzeug' };
const KITCHEN = { id: 'c2', name: 'Küche' };

function resource(overrides: Partial<Resource>): Resource {
  return {
    id: 'r',
    name: 'Ding',
    categories: [],
    private: false,
    openness: ResourceOpenness.AskFirst,
    images: [],
    favourite: false,
    mayEdit: false,
    mayDelete: false,
    ...overrides,
  };
}

describe('resources util', () => {
  it('shows resources where the server says RegionDetail.resourcesAvailable', () => {
    expect(hasResources({ resourcesAvailable: true })).toBe(true);
    expect(hasResources({ resourcesAvailable: false })).toBe(false);
    expect(hasResources({})).toBe(false);
    expect(hasResources(undefined)).toBe(false);
  });

  it('splits commons (no owner) from members’ resources and filters by category', () => {
    const owner = { id: 'u', displayName: 'Sarah', role: Role.Foodsaver, verified: true };
    const list = [
      resource({ id: 'a', owner, categories: [TOOLS] }),
      resource({ id: 'b', categories: [KITCHEN, TOOLS] }),
      resource({ id: 'c', owner, categories: [KITCHEN] }),
    ];
    const split = splitCommons(list);
    expect(split.commons.map((r) => r.id)).toEqual(['b']);
    expect(split.members.map((r) => r.id)).toEqual(['a', 'c']);
    expect(inCategory(list, 'c2').map((r) => r.id)).toEqual(['b', 'c']);
    expect(inCategory(list, null)).toHaveLength(3);
    expect(usedCategories(list).map((c) => c.name)).toEqual(['Küche', 'Werkzeug']);
  });

  it('applies the per-user limit of 10 like the server', () => {
    expect(mayAddMore(9, 10)).toBe(true);
    expect(mayAddMore(10, 10)).toBe(false);
    expect(mayAddMore(2, 2)).toBe(false);
  });

  it('validates the form like ResourceCreate', () => {
    expect(resourceFormErrors({ name: ' ', description: '', categoryIds: [] })).toEqual(['nameRequired', 'categoryRequired']);
    expect(resourceFormErrors({ name: 'x'.repeat(201), description: 'y'.repeat(4001), categoryIds: ['1', '2', '3', '4', '5', '6'] })).toEqual([
      'nameTooLong',
      'descriptionTooLong',
      'tooManyCategories',
    ]);
    expect(resourceFormErrors({ name: 'Velo', description: '', categoryIds: ['1'] })).toEqual([]);
  });

  it('sorts categories by name with sub-categories after their parent', () => {
    const sorted = sortCategories([
      { id: 's', name: 'Akku', parentId: 't' },
      { id: 't', name: 'Werkzeug' },
      { id: 'k', name: 'Küche' },
    ]);
    expect(sorted.map((c) => c.id)).toEqual(['k', 't', 's']);
  });
});
