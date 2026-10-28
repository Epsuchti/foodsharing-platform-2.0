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
}

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
  }, // M5
  {
    id: 'mailboxes',
    labelKey: 'nav.mailboxes',
    icon: 'pi pi-envelope',
    route: '/mailboxes',
    // A35 mayHaveMailbox; the page shows an empty state when the user reads no mailbox
    visible: (ctx) => ctx.isAuthenticated && ctx.hasRole(Role.StoreManager),
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
