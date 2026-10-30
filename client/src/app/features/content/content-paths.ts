/** A public page of the about area: either a CMS page with a fixed slug or a page of its own (team, statistics …). */
export interface AboutPage {
  /** i18n suffix: `content.about.page.<id>` (title) and `content.about.text.<id>` (teaser). */
  id: string;
  path: string;
  icon: string;
  /** CMS slug rendered by the content page; absent for pages with their own component. */
  slug?: string;
}

/**
 * Canonical routes of the public pages (C1, plan-m7 Q10). CMS pages keep their slug in the database; the route names
 * are the upstream ones (`/about/cities` shows the `foodsharing-cities` page).
 */
export const ABOUT_PAGES: readonly AboutPage[] = [
  { id: 'vision', path: '/about/vision', slug: 'vision', icon: 'pi pi-heart' },
  { id: 'team', path: '/about/team', slug: 'team', icon: 'pi pi-users' },
  { id: 'partners', path: '/about/partners', slug: 'partners', icon: 'pi pi-thumbs-up' },
  { id: 'press', path: '/about/press', slug: 'press', icon: 'pi pi-megaphone' },
  { id: 'cities', path: '/about/cities', slug: 'foodsharing-cities', icon: 'pi pi-building' },
  { id: 'demands', path: '/about/demands', slug: 'demands', icon: 'pi pi-flag' },
  { id: 'transparency', path: '/about/transparency', slug: 'transparency', icon: 'pi pi-eye' },
  { id: 'education', path: '/about/education', slug: 'education', icon: 'pi pi-graduation-cap' },
  { id: 'communities', path: '/about/communities', slug: 'communities', icon: 'pi pi-sitemap' },
  { id: 'international', path: '/about/international', slug: 'international', icon: 'pi pi-globe' },
  { id: 'security', path: '/about/security', slug: 'security', icon: 'pi pi-shield' },
  { id: 'imprint', path: '/imprint', slug: 'imprint', icon: 'pi pi-id-card' },
  { id: 'forCompanies', path: '/for-companies', slug: 'for-companies', icon: 'pi pi-briefcase' },
];

/** Pages of their own, listed on the about overview next to the CMS pages. */
export const ABOUT_FEATURE_PAGES: readonly AboutPage[] = [
  { id: 'groups', path: '/team', icon: 'pi pi-users' },
  { id: 'statistics', path: '/statistics', icon: 'pi pi-chart-bar' },
  { id: 'faq', path: '/faq', icon: 'pi pi-comments' },
  { id: 'releaseNotes', path: '/release-notes', icon: 'pi pi-gift' },
];

/** Route of a CMS page: its canonical about route, else `/pages/<slug>`. */
export function contentPath(slug: string): string {
  return ABOUT_PAGES.find((page) => page.slug === slug)?.path ?? `/pages/${slug}`;
}
