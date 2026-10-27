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
    visible: notYet,
    mobileTab: true,
  }, // M3
  {
    id: 'messages',
    labelKey: 'nav.messages',
    icon: 'pi pi-comments',
    route: '/messages',
    visible: notYet,
    mobileTab: true,
  }, // M4
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
  },
  // --- dev (local/e2e profile) ---
  {
    id: 'gallery',
    labelKey: 'nav.gallery',
    icon: 'pi pi-palette',
    route: '/dev/gallery',
    visible: (ctx) => ctx.devMode,
  },
  // --- user menu ---
  {
    id: 'settings',
    labelKey: 'nav.settings',
    icon: 'pi pi-cog',
    route: '/account/settings',
    visible: loggedIn,
    area: 'user',
  },
  {
    id: 'sessions',
    labelKey: 'nav.sessions',
    icon: 'pi pi-desktop',
    route: '/account/sessions',
    visible: loggedIn,
    area: 'user',
  },
  {
    id: 'securityLog',
    labelKey: 'nav.securityLog',
    icon: 'pi pi-shield',
    route: '/account/security-log',
    visible: loggedIn,
    area: 'user',
  },
  // --- footer (CMS pages arrive in M6) ---
  {
    id: 'vision',
    labelKey: 'nav.vision',
    icon: 'pi pi-heart',
    route: '/about/vision',
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
    route: '/privacy',
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
