import { FeatureToggleKey, Role, SessionInfo } from '../api';

/** What a nav item's `visible` sees. */
export interface NavContext {
  session: SessionInfo | null;
  isAuthenticated: boolean;
  /** Role hierarchy check (same as SessionService.hasRole). */
  hasRole(role: Role): boolean;
  hasPermission(permission: string): boolean;
  /** Backend runs the local or e2e profile. */
  devMode: boolean;
  /**
   * Feature toggle state (A43, FeatureToggleService: off until loaded). Optional so contexts without toggle
   * information (unit tests) show toggled items.
   */
  featureEnabled?: (key: FeatureToggleKey) => boolean;
}

const featureOn = (ctx: NavContext, key: FeatureToggleKey) => ctx.featureEnabled?.(key) ?? true;

export type NavArea = 'main' | 'user' | 'footer';

export interface NavItem {
  id: string;
  /** i18n key (global `nav.*`). */
  labelKey: string;
  /** `pi pi-*` icon class. */
  icon: string;
  route: string;
  visible: (ctx: NavContext) => boolean;
  /** Where the item appears: main navigation (default), user menu or footer. */
  area?: NavArea;
  /** Shown as a tab in the mobile bottom bar (max 4 + "Menu"). */
  mobileTab?: boolean;
  /** routerLinkActive exact matching. */
  exact?: boolean;
  /** Unread count bubble (`NavBadgesService`, loaded after the first page). */
  badge?: NavBadge;
  /** Section inside its area (M6): `admin` tools, `about` pages of the logged-out footer. */
  group?: NavGroup;
  /**
   * Less frequent main items (design review M7 R3): in the desktop header they go behind "More" before any other
   * item. Phones list every item in the menu drawer anyway.
   */
  secondary?: boolean | ((ctx: NavContext) => boolean);
  /**
   * Section of the desktop side menu (decision 133): main items and, for visitors, the public footer pages that carry
   * a section. Items without one are not in the side menu.
   */
  section?: NavSection;
  /**
   * Extra path prefix that makes the item current in the side menu when the item route is not the prefix of its
   * pages (`/stores/mine` → `/stores/:id`). The longest matching prefix wins (`/regions/home/verification` beats
   * `/regions`).
   */
  activeMatch?: string;
  /** Visibility in the side menu when it differs from `visible` (the bell is a panel there, not a page). */
  sidenavVisible?: (ctx: NavContext) => boolean;
}

export type NavGroup = 'admin' | 'about';

/** Sections of the desktop side menu, top to bottom (docs/design/sidenav-proposal.md §1.3, decision 133). */
export type NavSection = 'daily' | 'community' | 'manage' | 'orga' | 'about';

/** Sources of nav badges: conversations with unread messages, unread inbox mails. */
export type NavBadge = 'messages' | 'mailboxes';

const loggedIn = (ctx: NavContext) => ctx.isAuthenticated;
const loggedOut = (ctx: NavContext) => !ctx.isAuthenticated;
/** Items whose feature arrives in a later milestone stay declared but hidden. */
const notYet = () => false;

/**
 * M2 pages that exist. Each page task (T18–T23) flips its flag when its page lands; until then the item is
 * declared with its real visibility rule but hidden.
 */
export const M2_PAGES_READY = {
  regions: true,
  onboarding: true,
  quizzes: true,
  introPickups: true,
  passport: true,
  guide: true,
  newcomers: true,
  verification: true,
  users: true,
  adminRegions: true,
  adminQuizzes: true,
  adminContent: true,
};
type M2Page = keyof typeof M2_PAGES_READY;
const when =
  (page: M2Page, rule: (ctx: NavContext) => boolean) =>
  (ctx: NavContext): boolean =>
    M2_PAGES_READY[page] && rule(ctx);

/**
 * M6 pages (plan-m7 Q1, A9). Same pattern as M2_PAGES_READY: each page task flips its flag when the page lands;
 * until then the item is declared with its real visibility rule but hidden.
 */
export const M6_PAGES_READY = {
  map: true,
  baskets: true,
  search: true,
  profile: true,
  adminSettings: true,
  /** OAuth client administration (Q14); follows the OAUTH_PROVIDER toggle. */
  adminOauth: true,
  adminUploads: true,
  adminAchievements: true,
  adminBounced: true,
  /** AD1 global admins list (Q13). */
  adminAdmins: true,
  team: true,
  partners: true,
  press: true,
  blog: true,
  cities: true,
  demands: true,
  education: true,
  donate: true,
  statistics: true,
  support: true,
  releaseNotes: true,
  faq: true,
  /** ORGA editor of the release notes (Q10). */
  adminReleaseNotes: true,
  /** Report button on profiles, region tabs reports/report-log/mediation (Q8); no nav item of its own. */
  reports: true,
};
export type M6Page = keyof typeof M6_PAGES_READY;
const whenM6 =
  (page: M6Page, rule: (ctx: NavContext) => boolean) =>
  (ctx: NavContext): boolean =>
    M6_PAGES_READY[page] && rule(ctx);

/** Footer "about" pages for visitors (M6); hidden until their page flag flips. */
const ABOUT_PAGES: readonly {
  id: M6Page;
  icon: string;
  route: string;
  toggle?: FeatureToggleKey;
  /** Side menu section for visitors (decision 133); support and release notes stay in the footer only. */
  section?: NavSection;
}[] = [
  { id: 'team', icon: 'pi pi-users', route: '/team', section: 'about' },
  { id: 'partners', icon: 'pi pi-thumbs-up', route: '/about/partners', section: 'about' },
  { id: 'press', icon: 'pi pi-megaphone', route: '/about/press', section: 'about' },
  { id: 'blog', icon: 'pi pi-book', route: '/blog', toggle: FeatureToggleKey.Blog, section: 'daily' },
  { id: 'cities', icon: 'pi pi-building', route: '/about/cities', section: 'about' },
  { id: 'demands', icon: 'pi pi-flag', route: '/about/demands', section: 'about' },
  { id: 'education', icon: 'pi pi-graduation-cap', route: '/about/education', section: 'about' },
  { id: 'donate', icon: 'pi pi-heart-fill', route: '/donate', section: 'about' },
  { id: 'statistics', icon: 'pi pi-chart-bar', route: '/statistics', section: 'daily' },
  { id: 'support', icon: 'pi pi-question', route: '/support', toggle: FeatureToggleKey.SupportForm },
  { id: 'releaseNotes', icon: 'pi pi-list', route: '/release-notes' },
  { id: 'faq', icon: 'pi pi-comments', route: '/faq', section: 'about' },
];

/** Grant of a scoped permission (`<P>@<regionId>` / `<P>@*`) for any region. */
export function hasPermissionAnywhere(ctx: NavContext, permission: string): boolean {
  return (ctx.session?.permissions ?? []).some(
    (entry) => entry === permission || entry.startsWith(permission + '@'),
  );
}
/** Ambassador of a region / admin of a group, or ORGA. */
export function isAnyAdmin(ctx: NavContext): boolean {
  return ctx.hasRole(Role.Orga) || (ctx.session?.units ?? []).some((unit) => unit.admin);
}
const isOrga = (ctx: NavContext) => ctx.isAuthenticated && ctx.hasRole(Role.Orga);
const onboardingOpen = (ctx: NavContext) =>
  ctx.isAuthenticated && ctx.session?.onboarding?.complete === false;
const canGuide = (ctx: NavContext) =>
  ctx.isAuthenticated && hasPermissionAnywhere(ctx, 'CAN_GIVE_INTRO_PICKUPS');
const managesUsers = (ctx: NavContext) =>
  ctx.isAuthenticated && (isAnyAdmin(ctx) || hasPermissionAnywhere(ctx, 'CAN_MANAGE_USERS'));
/** Quiz editors: ORGA or admins of a working group (the server decides per quiz). */
const editsQuizzes = (ctx: NavContext) =>
  ctx.isAuthenticated &&
  (ctx.hasRole(Role.Orga) ||
    (ctx.session?.units ?? []).some((unit) => unit.admin && unit.type === 'WORKING_GROUP'));
/** Content admins: ORGA or admins of the PR function group. */
const editsContent = (ctx: NavContext) =>
  ctx.isAuthenticated &&
  (ctx.hasRole(Role.Orga) ||
    (ctx.session?.units ?? []).some((unit) => unit.admin && unit.function === 'PR'));
/** M3: pickups for verified foodsavers and above (unverified users cannot sign up, A19). */
const pickupsVisible = (ctx: NavContext) =>
  ctx.isAuthenticated && ctx.hasRole(Role.Foodsaver) && !!ctx.session?.user?.verified;

/**
 * The declarative navigation (docs/conventions.md A7). Extended once per milestone in one commit.
 * Test ids: `nav.<id>` (desktop/drawer), `nav.tab.<id>` (mobile tab bar), `nav.user.<id>` (user menu).
 */
export const NAV_ITEMS: readonly NavItem[] = [
  // --- logged out ---
  {
    id: 'home',
    section: 'daily',
    labelKey: 'nav.home',
    icon: 'pi pi-home',
    route: '/',
    visible: loggedOut,
    exact: true,
  },
  // --- logged in: main ---
  {
    id: 'dashboard',
    section: 'daily',
    labelKey: 'nav.dashboard',
    icon: 'pi pi-home',
    route: '/dashboard',
    visible: loggedIn,
    mobileTab: true,
  },
  // M2: a newcomer's main item comes right after the dashboard (labelled in the compact header, design review M7 R2);
  // the flag matches the journey page's header icon
  {
    id: 'onboarding',
    section: 'daily',
    labelKey: 'nav.onboarding',
    icon: 'pi pi-flag',
    route: '/onboarding',
    visible: when('onboarding', onboardingOpen),
  },
  {
    id: 'pickups',
    section: 'daily',
    labelKey: 'nav.pickups',
    icon: 'pi pi-calendar',
    route: '/pickups',
    visible: (ctx) => pickupsVisible(ctx),
    mobileTab: true,
  }, // M3
  {
    id: 'messages',
    section: 'daily',
    labelKey: 'nav.messages',
    icon: 'pi pi-comments',
    route: '/messages',
    visible: loggedIn,
    mobileTab: true,
    badge: 'messages',
  }, // M5
  {
    id: 'mailboxes',
    section: 'daily',
    labelKey: 'nav.mailboxes',
    icon: 'pi pi-envelope',
    route: '/mailboxes',
    // A35 mayHaveMailbox; the page shows an empty state when the user reads no mailbox
    visible: (ctx) => ctx.isAuthenticated && ctx.hasRole(Role.StoreManager),
    badge: 'mailboxes',
  }, // M5
  {
    id: 'bells',
    section: 'daily',
    labelKey: 'nav.bells',
    icon: 'pi pi-bell',
    route: '/bells',
    visible: notYet,
    // side menu: the bell item opens the bell panel next to it (decision 133), no page
    sidenavVisible: loggedIn,
    mobileTab: true,
  }, // M4
  {
    id: 'activity',
    section: 'community',
    labelKey: 'nav.activity',
    icon: 'pi pi-history',
    route: '/me/activity',
    visible: loggedIn,
    mobileTab: true,
  },
  // --- M2: regions ---
  {
    id: 'regions',
    section: 'community',
    activeMatch: '/regions',
    labelKey: 'nav.regions',
    icon: 'pi pi-map-marker',
    route: '/regions/mine',
    visible: when('regions', loggedIn),
  },
  {
    id: 'stores',
    section: 'daily',
    activeMatch: '/stores',
    labelKey: 'nav.stores',
    icon: 'pi pi-shopping-bag',
    route: '/stores/mine',
    visible: (ctx) => ctx.isAuthenticated && ctx.hasRole(Role.Foodsaver),
  }, // M3
  {
    id: 'quizzes',
    section: 'community',
    // newcomers need the quizzes on their way; verified members only now and then
    secondary: (ctx) => !!ctx.session?.user?.verified,
    labelKey: 'nav.quizzes',
    icon: 'pi pi-question-circle',
    route: '/quizzes',
    visible: when('quizzes', loggedIn),
  },
  {
    id: 'introPickups',
    section: 'community',
    activeMatch: '/intro-pickups',
    labelKey: 'nav.introPickups',
    icon: 'pi pi-users',
    route: '/me/intro-pickups',
    // newcomers only: verified foodsavers are done with intro pickups (guides have their own item)
    visible: when('introPickups', (ctx) => ctx.isAuthenticated && ctx.session?.user?.verified === false),
  },
  {
    id: 'guide',
    section: 'community',
    labelKey: 'nav.guide',
    icon: 'pi pi-flag',
    route: '/guide',
    visible: when('guide', canGuide),
  },
  // --- M2: admin section ---
  {
    id: 'newcomers',
    section: 'manage',
    labelKey: 'nav.newcomers',
    icon: 'pi pi-chart-line',
    route: '/regions/home/newcomers',
    visible: when('newcomers', managesUsers),
  },
  {
    id: 'verification',
    section: 'manage',
    labelKey: 'nav.verification',
    icon: 'pi pi-verified',
    route: '/regions/home/verification',
    visible: when('verification', managesUsers),
  },
  {
    id: 'users',
    section: 'manage',
    secondary: true,
    labelKey: 'nav.users',
    icon: 'pi pi-id-card',
    route: '/regions/home/users',
    visible: when('users', managesUsers),
  },
  {
    id: 'adminRegions',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminRegions',
    icon: 'pi pi-sitemap',
    route: '/admin/regions',
    visible: when('adminRegions', isOrga),
  },
  {
    id: 'adminQuizzes',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminQuizzes',
    icon: 'pi pi-pencil',
    route: '/quizzes/edit',
    visible: when('adminQuizzes', editsQuizzes),
  },
  {
    id: 'adminContent',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminContent',
    icon: 'pi pi-book',
    route: '/admin/content',
    visible: when('adminContent', editsContent),
  },
  {
    id: 'storeChains',
    section: 'manage',
    secondary: true,
    labelKey: 'nav.storeChains',
    icon: 'pi pi-link',
    route: '/store-chains',
    visible: (ctx) => ctx.isAuthenticated && ctx.hasRole(Role.Foodsaver),
  }, // M3
  {
    id: 'storeCategories',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.storeCategories',
    icon: 'pi pi-tags',
    route: '/admin/store-categories',
    visible: isOrga,
  }, // M3
  // --- user menu ---
  {
    // One entry for all account pages (settings, devices, security log, profile have their own tabs).
    id: 'account',
    labelKey: 'nav.account',
    icon: 'pi pi-user',
    route: '/account',
    visible: loggedIn,
    area: 'user',
    mobileTab: true,
  },
  {
    id: 'passport',
    labelKey: 'nav.passport',
    icon: 'pi pi-id-card',
    route: '/me/passport',
    visible: when('passport', (ctx) => ctx.isAuthenticated && !!ctx.session?.user?.verified),
    area: 'user',
  },
  // --- footer (CMS pages arrive in M6) ---
  {
    id: 'vision',
    section: 'about',
    labelKey: 'nav.vision',
    icon: 'pi pi-heart',
    route: '/about/vision',
    visible: () => true,
    area: 'footer',
  },
  {
    id: 'about',
    section: 'about',
    labelKey: 'nav.about',
    icon: 'pi pi-info-circle',
    route: '/about',
    visible: () => true,
    area: 'footer',
    exact: true,
  },
  {
    id: 'privacy',
    labelKey: 'nav.privacy',
    icon: 'pi pi-lock',
    route: '/legal/privacy-policy',
    visible: () => true,
    area: 'footer',
  },
  {
    id: 'imprint',
    labelKey: 'nav.imprint',
    icon: 'pi pi-file',
    route: '/imprint',
    visible: () => true,
    area: 'footer',
  },
  // --- dev (local/e2e profile): component gallery, out of the main navigation ---
  {
    id: 'gallery',
    labelKey: 'nav.gallery',
    icon: 'pi pi-palette',
    route: '/dev/gallery',
    visible: (ctx) => ctx.devMode,
    area: 'footer',
  },
  // --- M4: automations & notifications ---
  {
    id: 'notifications',
    labelKey: 'nav.notifications',
    icon: 'pi pi-bell',
    route: '/account/notifications',
    visible: loggedIn,
    area: 'user',
  },
  // --- M4 F6: operations (ORGA) ---
  {
    id: 'adminIntegrations',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminIntegrations',
    icon: 'pi pi-sitemap',
    route: '/admin/integrations',
    visible: isOrga,
  },
  {
    id: 'adminJobs',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminJobs',
    icon: 'pi pi-clock',
    route: '/admin/jobs',
    visible: isOrga,
  },
  {
    id: 'adminMailOutbox',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminMailOutbox',
    icon: 'pi pi-envelope',
    route: '/admin/mail-outbox',
    visible: isOrga,
  },
  // --- M4 F5: newsletters (ORGA or any region admin; the API checks the scope) ---
  {
    id: 'newsletters',
    section: 'manage',
    labelKey: 'nav.newsletters',
    icon: 'pi pi-megaphone',
    route: '/newsletters',
    visible: (ctx) =>
      ctx.isAuthenticated &&
      (ctx.hasRole(Role.Orga) || (ctx.session?.units ?? []).some((unit) => unit.admin)),
  },
  // --- M5 F5/F6: events, polls, surveys ---
  {
    id: 'events',
    section: 'community',
    activeMatch: '/events',
    secondary: true,
    labelKey: 'nav.events',
    icon: 'pi pi-calendar',
    route: '/events/mine',
    visible: loggedIn,
  },
  {
    id: 'polls',
    section: 'community',
    activeMatch: '/polls',
    secondary: true,
    labelKey: 'nav.polls',
    icon: 'pi pi-chart-bar',
    route: '/polls/mine',
    visible: loggedIn,
  },
  {
    id: 'surveys',
    section: 'manage',
    secondary: true,
    labelKey: 'nav.surveys',
    icon: 'pi pi-list-check',
    route: '/surveys',
    visible: (ctx) => ctx.isAuthenticated && ctx.hasPermission('CAN_MANAGE_SURVEYS'),
  },
  // --- M6 (hidden until M6_PAGES_READY flips) ---
  {
    id: 'map',
    section: 'community',
    labelKey: 'nav.map',
    icon: 'pi pi-map',
    route: '/map',
    visible: whenM6('map', () => true),
  },
  {
    id: 'baskets',
    section: 'daily',
    labelKey: 'nav.baskets',
    icon: 'pi pi-shopping-cart',
    route: '/baskets',
    visible: whenM6('baskets', (ctx) => loggedIn(ctx) && featureOn(ctx, FeatureToggleKey.Baskets)),
  },
  {
    id: 'search',
    labelKey: 'nav.search',
    icon: 'pi pi-search',
    route: '/search',
    visible: whenM6('search', (ctx) => loggedIn(ctx) && featureOn(ctx, FeatureToggleKey.GlobalSearch)),
  },
  {
    id: 'profile',
    labelKey: 'nav.profile',
    icon: 'pi pi-user',
    route: '/profile/me',
    visible: whenM6('profile', loggedIn),
    area: 'user',
  },
  {
    // C2: ambassadors write posts of their regions, ORGA and PR group admins check and publish (A54)
    id: 'blogManage',
    section: 'manage',
    secondary: true,
    labelKey: 'nav.blogManage',
    icon: 'pi pi-book',
    route: '/blog/manage',
    visible: whenM6(
      'blog',
      (ctx) => loggedIn(ctx) && isAnyAdmin(ctx) && featureOn(ctx, FeatureToggleKey.Blog),
    ),
  },
  {
    // C9: the support form for signed-in users (visitors find it in the footer)
    id: 'help',
    labelKey: 'nav.support',
    icon: 'pi pi-question-circle',
    route: '/support',
    visible: whenM6('support', (ctx) => loggedIn(ctx) && featureOn(ctx, FeatureToggleKey.SupportForm)),
    area: 'user',
  },
  {
    id: 'adminSettings',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminSettings',
    icon: 'pi pi-sliders-h',
    route: '/admin/feature-toggles',
    visible: whenM6('adminSettings', isOrga),
    group: 'admin',
  },
  {
    id: 'adminOauth',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminOauth',
    icon: 'pi pi-key',
    route: '/admin/oauth-clients',
    visible: whenM6('adminOauth', (ctx) => isOrga(ctx) && featureOn(ctx, FeatureToggleKey.OauthProvider)),
    group: 'admin',
  },
  {
    id: 'adminUploads',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminUploads',
    icon: 'pi pi-images',
    route: '/admin/uploads',
    visible: whenM6('adminUploads', isOrga),
    group: 'admin',
  },
  {
    id: 'adminAchievements',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminAchievements',
    icon: 'pi pi-trophy',
    route: '/admin/achievements',
    visible: whenM6('adminAchievements', isOrga),
    group: 'admin',
  },
  {
    id: 'adminAdmins',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminAdmins',
    icon: 'pi pi-star',
    route: '/admin/admins',
    visible: whenM6('adminAdmins', isOrga),
    group: 'admin',
  },
  {
    id: 'adminReleaseNotes',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminReleaseNotes',
    icon: 'pi pi-gift',
    route: '/admin/release-notes',
    visible: whenM6('adminReleaseNotes', isOrga),
    group: 'admin',
  },
  {
    id: 'adminBounced',
    section: 'orga',
    secondary: true,
    labelKey: 'nav.adminBounced',
    icon: 'pi pi-exclamation-circle',
    route: '/admin/bounced-emails',
    visible: whenM6('adminBounced', isOrga),
    group: 'admin',
  },
  ...ABOUT_PAGES.map(
    (page): NavItem => ({
      id: page.id,
      labelKey: `nav.${page.id}`,
      icon: page.icon,
      route: page.route,
      visible: whenM6(page.id, (ctx) => loggedOut(ctx) && (!page.toggle || featureOn(ctx, page.toggle))),
      area: 'footer',
      group: 'about',
      section: page.section,
    }),
  ),
];

export function visibleNavItems(
  ctx: NavContext,
  area: NavArea = 'main',
  items: readonly NavItem[] = NAV_ITEMS,
): NavItem[] {
  return items.filter((item) => (item.area ?? 'main') === area && item.visible(ctx));
}

/**
 * Order of the main items in the desktop header (design review M7 R3): primary items in their declared order, then
 * the secondary ones, so the occasional items are the first to move into the "More" menu.
 */
export function orderHeaderNav(items: readonly NavItem[], ctx: NavContext): NavItem[] {
  const isSecondary = (item: NavItem) =>
    typeof item.secondary === 'function' ? item.secondary(ctx) : !!item.secondary;
  return [...items.filter((item) => !isSecondary(item)), ...items.filter(isSecondary)];
}

/**
 * Splits the ordered header items at the number of links that fit the header row (`fit`, measured by the header):
 * the first ones stay in the header, the rest go to the "More" menu. At least one link stays in the header.
 */
export function splitHeaderNav(ordered: readonly NavItem[], fit: number): { inline: NavItem[]; more: NavItem[] } {
  const count = Math.max(1, Math.min(fit, ordered.length));
  return { inline: ordered.slice(0, count), more: ordered.slice(count) };
}

/** Static description of a side menu section. */
export interface NavSectionDef {
  id: NavSection;
  /** i18n key of the heading (visually hidden for the daily group). */
  labelKey: string;
  /** Heading of the daily group for visitors ("Entdecken" instead of "Täglich"). */
  visitorLabelKey?: string;
  /** Rail tile of a folded section. */
  icon: string;
  /** False for the daily group: always open, never folds into a flyout. */
  collapsible: boolean;
  /** Open on load (decision 133: Community open, Orga closed unless a child is current). */
  defaultOpen: boolean;
  /**
   * Fixed order of the items (owner decision 6: everything has a fixed, consistent place). Items not listed keep
   * their declared order after the listed ones.
   */
  order: readonly string[];
}

export const NAV_SECTIONS: readonly NavSectionDef[] = [
  {
    id: 'daily',
    labelKey: 'nav.section.daily',
    visitorLabelKey: 'nav.section.discover',
    icon: 'pi pi-home',
    collapsible: false,
    defaultOpen: true,
    order: ['home', 'dashboard', 'onboarding', 'pickups', 'stores', 'messages', 'mailboxes', 'bells', 'baskets', 'map', 'statistics', 'blog'],
  },
  {
    id: 'community',
    labelKey: 'nav.section.community',
    icon: 'pi pi-users',
    collapsible: true,
    defaultOpen: true,
    order: ['map', 'regions', 'introPickups', 'guide', 'events', 'polls', 'quizzes', 'activity'],
  },
  {
    id: 'manage',
    labelKey: 'nav.section.manage',
    icon: 'pi pi-briefcase',
    collapsible: true,
    defaultOpen: true,
    order: ['newcomers', 'verification', 'users', 'newsletters', 'blogManage', 'surveys', 'storeChains'],
  },
  {
    id: 'orga',
    labelKey: 'nav.section.orga',
    icon: 'pi pi-cog',
    collapsible: true,
    defaultOpen: false,
    order: [
      'adminRegions',
      'adminQuizzes',
      'adminContent',
      'storeCategories',
      'adminIntegrations',
      'adminJobs',
      'adminMailOutbox',
      'adminSettings',
      'adminOauth',
      'adminUploads',
      'adminAchievements',
      'adminAdmins',
      'adminReleaseNotes',
      'adminBounced',
    ],
  },
  {
    id: 'about',
    labelKey: 'nav.section.about',
    icon: 'pi pi-info-circle',
    collapsible: true,
    defaultOpen: true,
    order: ['vision', 'about', 'team', 'partners', 'press', 'cities', 'demands', 'education', 'donate', 'faq'],
  },
];

export interface SidenavSection {
  def: NavSectionDef;
  items: NavItem[];
}

/**
 * The side menu (decision 133): every visible main item in its section, plus for visitors the public footer pages
 * that carry a section. The map is a daily item for visitors (their main use) and a Community item for members.
 * Sections without a visible item are left out, so the groups per role follow from the `visible` rules.
 */
export function sidenavSections(ctx: NavContext, items: readonly NavItem[] = NAV_ITEMS): SidenavSection[] {
  const shown = items.filter((item) => {
    if (!item.section) return false;
    const area = item.area ?? 'main';
    if (area === 'user') return false;
    if (area === 'footer' && ctx.isAuthenticated) return false;
    return (item.sidenavVisible ?? item.visible)(ctx);
  });
  const sectionOf = (item: NavItem): NavSection =>
    item.id === 'map' && !ctx.isAuthenticated ? 'daily' : (item.section as NavSection);
  return NAV_SECTIONS.map((def) => {
    const rank = (item: NavItem) => {
      const index = def.order.indexOf(item.id);
      return index < 0 ? def.order.length : index;
    };
    const inSection = shown.filter((item) => sectionOf(item) === def.id);
    // stable sort: unlisted items keep their declared order
    const sorted = inSection
      .map((item, index) => ({ item, index }))
      .sort((a, b) => rank(a.item) - rank(b.item) || a.index - b.index)
      .map((entry) => entry.item);
    return { def, items: sorted };
  }).filter((section) => section.items.length > 0);
}

/** Path of a URL without query and fragment. */
function pathOf(url: string): string {
  const end = url.search(/[?#]/);
  return end < 0 ? url : url.slice(0, end);
}

/** `prefix` is `path` or a parent path of it (`/stores` matches `/stores/12`, not `/store-chains`). */
export function matchesPrefix(path: string, prefix: string): boolean {
  if (prefix === '/') return path === '/';
  return path === prefix || path.startsWith(prefix.endsWith('/') ? prefix : prefix + '/');
}

/**
 * Id of the side menu item that is current for `url`: the item whose route or `activeMatch` is the longest prefix of
 * the path (`exact` items match their route only). Null when no item matches.
 */
export function activeNavId(url: string, items: readonly NavItem[]): string | null {
  const path = pathOf(url) || '/';
  let best: { id: string; length: number } | null = null;
  for (const item of items) {
    const prefixes = item.exact ? [] : [item.route, ...(item.activeMatch ? [item.activeMatch] : [])];
    const hits = item.exact ? (path === item.route ? [item.route] : []) : prefixes.filter((prefix) => matchesPrefix(path, prefix));
    for (const hit of hits) {
      if (!best || hit.length > best.length) best = { id: item.id, length: hit.length };
    }
  }
  return best?.id ?? null;
}

/** Tabs of the mobile bottom bar (the "Menu" tab is added by the component). */
export function mobileTabs(ctx: NavContext, items: readonly NavItem[] = NAV_ITEMS): NavItem[] {
  return items.filter((item) => item.mobileTab && item.visible(ctx)).slice(0, 4);
}
