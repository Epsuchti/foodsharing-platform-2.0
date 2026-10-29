import { Role, SessionInfo } from '../api';

/** What a nav item's `visible` sees. */
export interface NavContext {
  session: SessionInfo | null;
  isAuthenticated: boolean;
  /** Role hierarchy check (same as SessionService.hasRole). */
  hasRole(role: Role): boolean;
  hasPermission(permission: string): boolean;
  /** Backend runs the local or e2e profile. */
  devMode: boolean;
}

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
}

export type NavGroup = 'admin' | 'about';

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
  map: false,
  baskets: false,
  search: false,
  profile: true,
  adminSettings: false,
  adminOauth: false,
  adminUploads: false,
  adminAchievements: false,
  adminBounced: true,
  team: false,
  partners: false,
  press: false,
  blog: false,
  cities: false,
  demands: false,
  education: false,
  donate: false,
  statistics: false,
  support: false,
  releaseNotes: false,
  faq: false,
  /** Report button on profiles (Q8); no nav item of its own. */
  reports: false,
};
export type M6Page = keyof typeof M6_PAGES_READY;
const whenM6 =
  (page: M6Page, rule: (ctx: NavContext) => boolean) =>
  (ctx: NavContext): boolean =>
    M6_PAGES_READY[page] && rule(ctx);

/** Footer "about" pages for visitors (M6); hidden until their page flag flips. */
const ABOUT_PAGES: readonly { id: M6Page; icon: string; route: string }[] = [
  { id: 'team', icon: 'pi pi-users', route: '/team' },
  { id: 'partners', icon: 'pi pi-thumbs-up', route: '/pages/partners' },
  { id: 'press', icon: 'pi pi-megaphone', route: '/pages/press' },
  { id: 'blog', icon: 'pi pi-book', route: '/blog' },
  { id: 'cities', icon: 'pi pi-building', route: '/pages/cities' },
  { id: 'demands', icon: 'pi pi-flag', route: '/pages/demands' },
  { id: 'education', icon: 'pi pi-graduation-cap', route: '/pages/education' },
  { id: 'donate', icon: 'pi pi-heart-fill', route: '/donate' },
  { id: 'statistics', icon: 'pi pi-chart-bar', route: '/statistics' },
  { id: 'support', icon: 'pi pi-question', route: '/support' },
  { id: 'releaseNotes', icon: 'pi pi-list', route: '/release-notes' },
  { id: 'faq', icon: 'pi pi-comments', route: '/faq' },
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
    labelKey: 'nav.home',
    icon: 'pi pi-home',
    route: '/',
    visible: loggedOut,
    exact: true,
  },
  // --- logged in: main ---
  {
    id: 'dashboard',
    labelKey: 'nav.dashboard',
    icon: 'pi pi-home',
    route: '/dashboard',
    visible: loggedIn,
    mobileTab: true,
  },
  {
    id: 'pickups',
    labelKey: 'nav.pickups',
    icon: 'pi pi-calendar',
    route: '/pickups',
    visible: (ctx) => pickupsVisible(ctx),
    mobileTab: true,
  }, // M3
  {
    id: 'messages',
    labelKey: 'nav.messages',
    icon: 'pi pi-comments',
    route: '/messages',
    visible: loggedIn,
    mobileTab: true,
    badge: 'messages',
  }, // M5
  {
    id: 'mailboxes',
    labelKey: 'nav.mailboxes',
    icon: 'pi pi-envelope',
    route: '/mailboxes',
    // A35 mayHaveMailbox; the page shows an empty state when the user reads no mailbox
    visible: (ctx) => ctx.isAuthenticated && ctx.hasRole(Role.StoreManager),
    badge: 'mailboxes',
  }, // M5
  {
    id: 'bells',
    labelKey: 'nav.bells',
    icon: 'pi pi-bell',
    route: '/bells',
    visible: notYet,
    mobileTab: true,
  }, // M4
  {
    id: 'activity',
    labelKey: 'nav.activity',
    icon: 'pi pi-history',
    route: '/me/activity',
    visible: loggedIn,
    mobileTab: true,
  },
  // --- M2: regions & onboarding ---
  {
    id: 'onboarding',
    labelKey: 'nav.onboarding',
    icon: 'pi pi-compass',
    route: '/onboarding',
    visible: when('onboarding', onboardingOpen),
  },
  {
    id: 'regions',
    labelKey: 'nav.regions',
    icon: 'pi pi-map-marker',
    route: '/regions/mine',
    visible: when('regions', loggedIn),
  },
  {
    id: 'stores',
    labelKey: 'nav.stores',
    icon: 'pi pi-shopping-bag',
    route: '/stores/mine',
    visible: (ctx) => ctx.isAuthenticated && ctx.hasRole(Role.Foodsaver),
  }, // M3
  {
    id: 'quizzes',
    labelKey: 'nav.quizzes',
    icon: 'pi pi-question-circle',
    route: '/quizzes',
    visible: when('quizzes', loggedIn),
  },
  {
    id: 'introPickups',
    labelKey: 'nav.introPickups',
    icon: 'pi pi-users',
    route: '/me/intro-pickups',
    // newcomers only: verified foodsavers are done with intro pickups (guides have their own item)
    visible: when('introPickups', (ctx) => ctx.isAuthenticated && ctx.session?.user?.verified === false),
  },
  {
    id: 'guide',
    labelKey: 'nav.guide',
    icon: 'pi pi-flag',
    route: '/guide',
    visible: when('guide', canGuide),
  },
  // --- M2: admin section ---
  {
    id: 'newcomers',
    labelKey: 'nav.newcomers',
    icon: 'pi pi-chart-line',
    route: '/regions/home/newcomers',
    visible: when('newcomers', managesUsers),
  },
  {
    id: 'verification',
    labelKey: 'nav.verification',
    icon: 'pi pi-verified',
    route: '/regions/home/verification',
    visible: when('verification', managesUsers),
  },
  {
    id: 'users',
    labelKey: 'nav.users',
    icon: 'pi pi-id-card',
    route: '/regions/home/users',
    visible: when('users', managesUsers),
  },
  {
    id: 'adminRegions',
    labelKey: 'nav.adminRegions',
    icon: 'pi pi-sitemap',
    route: '/admin/regions',
    visible: when('adminRegions', isOrga),
  },
  {
    id: 'adminQuizzes',
    labelKey: 'nav.adminQuizzes',
    icon: 'pi pi-pencil',
    route: '/quizzes/edit',
    visible: when('adminQuizzes', editsQuizzes),
  },
  {
    id: 'adminContent',
    labelKey: 'nav.adminContent',
    icon: 'pi pi-book',
    route: '/admin/content',
    visible: when('adminContent', editsContent),
  },
  {
    id: 'storeChains',
    labelKey: 'nav.storeChains',
    icon: 'pi pi-link',
    route: '/store-chains',
    visible: (ctx) => ctx.isAuthenticated && ctx.hasRole(Role.Foodsaver),
  }, // M3
  {
    id: 'storeCategories',
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
    labelKey: 'nav.vision',
    icon: 'pi pi-heart',
    route: '/pages/vision',
    visible: () => true,
    area: 'footer',
  },
  {
    id: 'about',
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
    route: '/pages/imprint',
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
    labelKey: 'nav.adminIntegrations',
    icon: 'pi pi-sitemap',
    route: '/admin/integrations',
    visible: isOrga,
  },
  {
    id: 'adminJobs',
    labelKey: 'nav.adminJobs',
    icon: 'pi pi-clock',
    route: '/admin/jobs',
    visible: isOrga,
  },
  {
    id: 'adminMailOutbox',
    labelKey: 'nav.adminMailOutbox',
    icon: 'pi pi-envelope',
    route: '/admin/mail-outbox',
    visible: isOrga,
  },
  // --- M4 F5: newsletters (ORGA or any region admin; the API checks the scope) ---
  {
    id: 'newsletters',
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
    labelKey: 'nav.events',
    icon: 'pi pi-calendar',
    route: '/events/mine',
    visible: loggedIn,
  },
  {
    id: 'polls',
    labelKey: 'nav.polls',
    icon: 'pi pi-chart-bar',
    route: '/polls/mine',
    visible: loggedIn,
  },
  {
    id: 'surveys',
    labelKey: 'nav.surveys',
    icon: 'pi pi-list-check',
    route: '/surveys',
    visible: (ctx) => ctx.isAuthenticated && ctx.hasPermission('CAN_MANAGE_SURVEYS'),
  },
  // --- M6 (hidden until M6_PAGES_READY flips) ---
  {
    id: 'map',
    labelKey: 'nav.map',
    icon: 'pi pi-map',
    route: '/map',
    visible: whenM6('map', () => true),
  },
  {
    id: 'baskets',
    labelKey: 'nav.baskets',
    icon: 'pi pi-shopping-cart',
    route: '/baskets',
    visible: whenM6('baskets', loggedIn),
  },
  {
    id: 'search',
    labelKey: 'nav.search',
    icon: 'pi pi-search',
    route: '/search',
    visible: whenM6('search', loggedIn),
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
    id: 'adminSettings',
    labelKey: 'nav.adminSettings',
    icon: 'pi pi-sliders-h',
    route: '/admin/settings',
    visible: whenM6('adminSettings', isOrga),
    group: 'admin',
  },
  {
    id: 'adminOauth',
    labelKey: 'nav.adminOauth',
    icon: 'pi pi-key',
    route: '/admin/oauth-clients',
    visible: whenM6('adminOauth', isOrga),
    group: 'admin',
  },
  {
    id: 'adminUploads',
    labelKey: 'nav.adminUploads',
    icon: 'pi pi-images',
    route: '/admin/uploads',
    visible: whenM6('adminUploads', isOrga),
    group: 'admin',
  },
  {
    id: 'adminAchievements',
    labelKey: 'nav.adminAchievements',
    icon: 'pi pi-trophy',
    route: '/admin/achievements',
    visible: whenM6('adminAchievements', isOrga),
    group: 'admin',
  },
  {
    id: 'adminBounced',
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
      visible: whenM6(page.id, loggedOut),
      area: 'footer',
      group: 'about',
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

/** Tabs of the mobile bottom bar (the "Menu" tab is added by the component). */
export function mobileTabs(ctx: NavContext, items: readonly NavItem[] = NAV_ITEMS): NavItem[] {
  return items.filter((item) => item.mobileTab && item.visible(ctx)).slice(0, 4);
}
