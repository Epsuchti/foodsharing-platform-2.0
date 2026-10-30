import { SearchIndexData, SearchKind, SearchResults } from '../../api';
import { countHits, filterIndex, matchesIndexEntry, moveActive, normalizeSearch, resultGroups, searchTerms } from './search.util';

const INDEX: SearchIndexData = {
  regions: [{ id: 'r1', name: 'Zürich Stadt', link: '/regions/r1' }],
  groups: [{ id: 'g1', name: 'Kochgruppe', subtitle: 'Zürich Stadt', link: '/groups/g1' }],
  stores: [
    { id: 's2', name: 'Zum Löwen', link: '/stores/s2' },
    { id: 's1', name: 'Löwen-Bäckerei', subtitle: 'Wiedikon', link: '/stores/s1' },
  ],
  foodSharePoints: [{ id: 'f1', name: 'Fairteiler Wiedikon', link: '/food-share-points/f1' }],
  conversations: [],
};

const EMPTY: SearchResults = { users: [], regions: [], groups: [], stores: [], threads: [], foodSharePoints: [], events: [], polls: [] };

describe('search util', () => {
  it('normalizes like the server index: lower case, no accents, at most five terms', () => {
    expect(normalizeSearch('  Zürich Bäckerei ')).toBe('zurich backerei');
    expect(searchTerms('a b c d e f')).toEqual(['a', 'b', 'c', 'd', 'e']);
    expect(searchTerms('   ')).toEqual([]);
  });

  it('matches index entries when every term occurs in name or subtitle', () => {
    expect(matchesIndexEntry(INDEX.groups[0], ['koch', 'zurich'])).toBe(true);
    expect(matchesIndexEntry(INDEX.groups[0], ['koch', 'bern'])).toBe(false);
    expect(matchesIndexEntry(INDEX.groups[0], [])).toBe(false);
  });

  it('groups instant index results, prefix matches first, limited per group', () => {
    const groups = filterIndex(INDEX, 'lowen');
    expect(groups.map((group) => group.id)).toEqual(['index.stores']);
    expect(groups[0].options.map((option) => option.title)).toEqual(['Löwen-Bäckerei', 'Zum Löwen']);
    expect(groups[0].labelKey).toBe('search.index.stores');
    expect(filterIndex(INDEX, 'wiedikon').map((group) => group.id)).toEqual(['index.stores', 'index.foodSharePoints']);
    expect(filterIndex(INDEX, 'lowen', 1)[0].options).toHaveLength(1);
    expect(filterIndex(undefined, 'lowen')).toEqual([]);
    expect(filterIndex(INDEX, ' ')).toEqual([]);
  });

  it('turns server results into groups in a fixed order, leaving out links shown already and filtered kinds', () => {
    const results: SearchResults = {
      ...EMPTY,
      stores: [{ id: 's1', kind: SearchKind.Store, title: 'Löwen-Bäckerei', link: '/stores/s1' }],
      users: [{ id: 'u1', kind: SearchKind.User, title: 'Nina', link: '/profile/u1', imageUrl: '/a.png' }],
    };
    expect(resultGroups(results).map((group) => group.id)).toEqual(['remote.users', 'remote.stores']);
    expect(resultGroups(results, new Set(['/stores/s1'])).map((group) => group.id)).toEqual(['remote.users']);
    expect(resultGroups(results, new Set(), new Set([SearchKind.Store])).map((group) => group.id)).toEqual(['remote.stores']);
    expect(resultGroups(results)[0].options[0]).toMatchObject({ title: 'Nina', imageUrl: '/a.png', link: '/profile/u1' });
    expect(countHits(results)).toBe(2);
    expect(countHits(EMPTY)).toBe(0);
  });

  it('moves the active option with wrap-around', () => {
    expect(moveActive(-1, 3, 1)).toBe(0);
    expect(moveActive(-1, 3, -1)).toBe(2);
    expect(moveActive(2, 3, 1)).toBe(0);
    expect(moveActive(0, 3, -1)).toBe(2);
    expect(moveActive(0, 0, 1)).toBe(-1);
  });
});
