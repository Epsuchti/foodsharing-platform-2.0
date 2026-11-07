/** A public CMS page of the about area with a fixed route (C1). */
export interface AboutPage {
  /** i18n suffix: `content.about.page.<id>` (route title). */
  id: string;
  path: string;
  icon: string;
  /** CMS slug rendered by the content page; absent for pages with their own component. */
  slug?: string;
}

/**
 * Canonical routes of the public pages (C1, plan-m7 Q10). CMS pages keep their slug in the database; the route names
 * are the upstream ones. Decision 172 removed Vision, About us, Team, Cities, Demands, Transparency, Communities,
 * International, Security and For companies; their paths redirect to `/` ({@link REMOVED_ABOUT_PATHS}).
 */
export const ABOUT_PAGES: readonly AboutPage[] = [
  { id: 'partners', path: '/about/partners', slug: 'partners', icon: 'pi pi-thumbs-up' },
  { id: 'press', path: '/about/press', slug: 'press', icon: 'pi pi-megaphone' },
  { id: 'education', path: '/about/education', slug: 'education', icon: 'pi pi-graduation-cap' },
  { id: 'imprint', path: '/imprint', slug: 'imprint', icon: 'pi pi-id-card' },
];

/** Paths of the pages removed by decision 172; bookmarks land on the start page. */
export const REMOVED_ABOUT_PATHS: readonly string[] = [
  'about',
  'team',
  'for-companies',
  ...['vision', 'team', 'cities', 'demands', 'transparency', 'communities', 'international', 'security'].map(
    (page) => `about/${page}`,
  ),
];

/** Route of a CMS page: its canonical about route, else `/pages/<slug>`. */
export function contentPath(slug: string): string {
  return ABOUT_PAGES.find((page) => page.slug === slug)?.path ?? `/pages/${slug}`;
}
