import { SearchHit, SearchIndexData, SearchIndexEntry, SearchKind, SearchResults } from '../../api';

/** Minimum query length of the server search (`search.q` minLength, A55). */
export const SEARCH_MIN_LENGTH = 2;
/** Debounce of the header search before the server is asked (plan-m7 Q12). */
export const SEARCH_DEBOUNCE_MS = 300;
/** Instant results per index group in the header (the full list is on /search). */
export const INDEX_HITS_PER_GROUP = 4;

export type SearchResultsKey = keyof SearchResults;
export type SearchIndexKey = keyof SearchIndexData;

/** One group of the server results, in display order. */
export interface SearchGroupDef {
  key: SearchResultsKey;
  kind: SearchKind;
  icon: string;
}

export const SEARCH_GROUPS: readonly SearchGroupDef[] = [
  { key: 'users', kind: SearchKind.User, icon: 'pi pi-user' },
  { key: 'regions', kind: SearchKind.Region, icon: 'pi pi-map-marker' },
  { key: 'groups', kind: SearchKind.Group, icon: 'pi pi-sitemap' },
  { key: 'stores', kind: SearchKind.Store, icon: 'pi pi-shopping-bag' },
  { key: 'threads', kind: SearchKind.Thread, icon: 'pi pi-comments' },
  { key: 'foodSharePoints', kind: SearchKind.Fsp, icon: 'pi pi-box' },
  { key: 'events', kind: SearchKind.Event, icon: 'pi pi-calendar' },
  { key: 'polls', kind: SearchKind.Poll, icon: 'pi pi-chart-bar' },
];

/** The user's own entities (`getSearchIndex`), searched in the browser while typing. */
export const INDEX_GROUPS: readonly { key: SearchIndexKey; icon: string }[] = [
  { key: 'conversations', icon: 'pi pi-envelope' },
  { key: 'regions', icon: 'pi pi-map-marker' },
  { key: 'groups', icon: 'pi pi-sitemap' },
  { key: 'stores', icon: 'pi pi-shopping-bag' },
  { key: 'foodSharePoints', icon: 'pi pi-box' },
];

/** One selectable result (header listbox option or /search link). */
export interface SearchOption {
  /** Unique within one result list (`<group>:<id>`). */
  key: string;
  title: string;
  subtitle?: string;
  link: string;
  icon: string;
  imageUrl?: string;
}

export interface SearchOptionGroup {
  /** `index.<key>` or `remote.<key>`: i18n suffix and test id. */
  id: string;
  labelKey: string;
  options: SearchOption[];
}

/** Lower case without accents, like `search_index.search_text` on the server (A55). */
export function normalizeSearch(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

/** Query terms (max. 5 like the server); empty when the query has no letters. */
export function searchTerms(query: string): string[] {
  return normalizeSearch(query).split(/\s+/).filter(Boolean).slice(0, 5);
}

/** Every term must occur in the name or subtitle. */
export function matchesIndexEntry(entry: SearchIndexEntry, terms: readonly string[]): boolean {
  if (!terms.length) return false;
  const text = normalizeSearch(`${entry.name} ${entry.subtitle ?? ''}`);
  return terms.every((term) => text.includes(term));
}

/** Instant results from the own index, grouped; at most `perGroup` per group (prefix matches first). */
export function filterIndex(index: SearchIndexData | undefined, query: string, perGroup = INDEX_HITS_PER_GROUP): SearchOptionGroup[] {
  const terms = searchTerms(query);
  if (!index || !terms.length) return [];
  const first = terms[0];
  return INDEX_GROUPS.map(({ key, icon }) => {
    const hits = index[key]
      .filter((entry) => matchesIndexEntry(entry, terms))
      .sort((a, b) => Number(!normalizeSearch(a.name).startsWith(first)) - Number(!normalizeSearch(b.name).startsWith(first)) || a.name.localeCompare(b.name))
      .slice(0, perGroup);
    return {
      id: `index.${key}`,
      labelKey: `search.index.${key}`,
      options: hits.map((entry) => ({ key: `index.${key}:${entry.id}`, title: entry.name, subtitle: entry.subtitle, link: entry.link, icon })),
    };
  }).filter((group) => group.options.length > 0);
}

/** Server results as groups; hits whose link already shows elsewhere (`seenLinks`, e.g. the index) are left out. */
export function resultGroups(results: SearchResults | undefined, seenLinks: ReadonlySet<string> = new Set(), kinds?: ReadonlySet<SearchKind>): SearchOptionGroup[] {
  if (!results) return [];
  return SEARCH_GROUPS.filter((group) => !kinds?.size || kinds.has(group.kind))
    .map(({ key, icon }) => ({
      id: `remote.${key}`,
      labelKey: `search.group.${key}`,
      options: (results[key] ?? [])
        .filter((hit) => !seenLinks.has(hit.link))
        .map((hit: SearchHit) => ({ key: `remote.${key}:${hit.id}`, title: hit.title, subtitle: hit.subtitle, link: hit.link, icon, imageUrl: hit.imageUrl })),
    }))
    .filter((group) => group.options.length > 0);
}

export function countHits(results: SearchResults): number {
  return SEARCH_GROUPS.reduce((sum, group) => sum + (results[group.key]?.length ?? 0), 0);
}

/** Next active option index for the arrow keys (wraps around; -1 = the input itself). */
export function moveActive(current: number, count: number, delta: 1 | -1): number {
  if (count === 0) return -1;
  if (current < 0) return delta === 1 ? 0 : count - 1;
  return (current + delta + count) % count;
}
