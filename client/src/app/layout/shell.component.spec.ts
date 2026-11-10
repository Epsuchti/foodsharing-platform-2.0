import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, DeferBlockState, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideOptimus } from '@openng/optimus-ui/config';

import { AppInfo, Language, NavCapability, Role, Theme } from '../api';
import { AppInfoService } from '../core/app-info.service';
import { SessionService } from '../core/auth/session.service';
import { HttpActivityService } from '../core/http/http-activity.service';
import { OnlineStatusService } from '../core/online/online-status.service';
import {
  TestingOnlineStatusService,
  provideDataStateTesting,
} from '../core/testing/data-state-testing';
import {
  orderHeaderNav,
  M6_PAGES_READY,
  NAV_ITEMS,
  NavContext,
  drawerItems,
  mobileTabs,
  sidenavSections,
  splitHeaderNav,
  visibleNavItems,
} from './nav.config';
import { LEGAL_ROUTES } from '../features/legal/legal.routes';
import { ShellComponent, TOAST_BREAKPOINTS, chromeOf } from './shell.component';

@Component({
  template: '<p class="page">page</p>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class PageComponent {}

const APP_INFO: AppInfo = {
  version: '1.2.3',
  devMode: true,
  personaPanel: false,
  demoBanner: false,
  languages: [],
  loginMethods: { google: true, magicLink: true, passkeys: true },
  serverTime: '2026-11-02T09:00:00Z',
};

describe('ShellComponent', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let host: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'layout.footer.version': 'Version {version}' },
          routes: [
            { path: '', component: PageComponent },
            { path: 'dashboard', component: PageComponent },
            { path: 'login', component: PageComponent, data: { chrome: 'minimal' } },
            { path: 'survey', component: PageComponent, data: { chrome: 'none' } },
          ],
        }),
        provideOptimus(),
      ],
    });
    fixture = TestBed.createComponent(ShellComponent);
    host = fixture.nativeElement as HTMLElement;
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();
  });

  const byTestId = (id: string) => host.querySelector(`[data-testid="${id}"]`);

  function logIn(role = Role.Foodsaver): void {
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: 'u1',
        email: 'anna@local',
        displayName: 'Anna Muster',
        role,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });
  }

  it('loads toast and confirm dialog only after the first page (deferred, D11.5.3)', async () => {
    expect(host.querySelector('p-toast')).toBeNull();
    // the header menus defer their popovers too: render every deferred block
    for (const block of await fixture.getDeferBlocks()) {
      await block.render(DeferBlockState.Complete);
    }
    expect(host.querySelector('p-toast')).not.toBeNull();
    expect(host.querySelector('p-confirmdialog')).not.toBeNull();
  });

  it('renders header, page, footer and the login button when logged out', () => {
    expect(byTestId('shell.brand')).not.toBeNull();
    expect(byTestId('shell.languageMenu')).not.toBeNull();
    expect(byTestId('shell.themeMenu')).not.toBeNull();
    expect(byTestId('shell.login')).not.toBeNull();
    expect(byTestId('nav.home')).not.toBeNull();
    expect(byTestId('shell.userMenu')).toBeNull();
    expect(byTestId('shell.tabBar')).toBeNull();
    expect(host.querySelector('.page')).not.toBeNull();
    expect(host.querySelector('fs-footer')).not.toBeNull();
  });

  it('shows the user menu, main navigation and mobile tab bar when logged in', async () => {
    logIn();
    await TestBed.inject(Router).navigateByUrl('/dashboard');
    await fixture.whenStable();
    expect(byTestId('shell.userMenu')?.textContent).toContain('Anna Muster');
    expect(byTestId('shell.login')).toBeNull();
    expect(byTestId('nav.dashboard')).not.toBeNull();
    expect(byTestId('nav.home')).toBeNull();
    expect(byTestId('shell.tabBar')).not.toBeNull();
    expect(byTestId('nav.tab.dashboard')).not.toBeNull();
    expect(byTestId('nav.tab.menu')).not.toBeNull();
    // M3: pickups tab for verified foodsavers
    expect(byTestId('nav.tab.pickups')).not.toBeNull();
    // M5: messages
    expect(byTestId('nav.tab.messages')).not.toBeNull();
    // decision 149: Betriebe is a tab
    expect(byTestId('nav.tab.stores')).not.toBeNull();
  });

  it('reskins the page in the old colours only while logged out (fs-public on main, decision 175)', async () => {
    const main = () => host.querySelector('main#main-content') as HTMLElement;
    expect(main().classList).toContain('fs-public');
    logIn();
    await fixture.whenStable();
    expect(main().classList).not.toContain('fs-public');
    TestBed.inject(SessionService).clear();
    await fixture.whenStable();
    expect(main().classList).toContain('fs-public');
  });

  it('hides navigation, footer and tab bar on minimal-chrome routes', async () => {
    logIn();
    await TestBed.inject(Router).navigateByUrl('/login');
    await fixture.whenStable();
    expect(host.getAttribute('data-chrome')).toBe('minimal');
    expect(byTestId('shell.brand')).not.toBeNull();
    expect(byTestId('shell.languageMenu')).not.toBeNull();
    expect(byTestId('nav.dashboard')).toBeNull();
    expect(byTestId('shell.userMenu')).toBeNull();
    expect(host.querySelector('fs-footer')).toBeNull();
    expect(byTestId('shell.tabBar')).toBeNull();

    await TestBed.inject(Router).navigateByUrl('/survey');
    await fixture.whenStable();
    expect(host.querySelector('fs-header')).toBeNull();
  });

  it('shows the offline banner while offline', async () => {
    const online = TestBed.inject(OnlineStatusService) as unknown as TestingOnlineStatusService;
    expect(byTestId('shell.offlineBanner')).toBeNull();
    online.setOnline(false);
    await fixture.whenStable();
    expect(byTestId('shell.offlineBanner')).not.toBeNull();
    online.setOnline(true);
    await fixture.whenStable();
    expect(byTestId('shell.offlineBanner')).toBeNull();
  });

  it('shows the request bar while requests run', async () => {
    const activity = TestBed.inject(HttpActivityService);
    activity.start();
    await fixture.whenStable();
    expect(byTestId('shell.requestBar')).not.toBeNull();
    activity.end();
    await fixture.whenStable();
    expect(byTestId('shell.requestBar')).toBeNull();
  });

  it('shows dev navigation and the version from app info', async () => {
    TestBed.inject(AppInfoService).info.set(APP_INFO);
    await fixture.whenStable();
    expect(byTestId('nav.gallery')).toBeNull();
    expect(byTestId('nav.footer.gallery')).not.toBeNull();
    expect(byTestId('shell.version')?.textContent).toContain('1.2.3');
  });
});

describe('nav.config', () => {
  const context = (overrides: Partial<NavContext> = {}): NavContext => ({
    session: null,
    isAuthenticated: false,
    hasRole: () => false,
    hasPermission: () => false,
    devMode: false,
    ...overrides,
  });

  it('has unique ids and global nav.* label keys', () => {
    const ids = NAV_ITEMS.map((item) => item.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(NAV_ITEMS.every((item) => item.labelKey.startsWith('nav.'))).toBe(true);
  });

  it('puts a newcomer journey right after the dashboard (labelled in the compact header)', () => {
    const newcomer = context({
      isAuthenticated: true,
      session: { onboarding: { complete: false } } as unknown as NavContext['session'],
    });
    expect(visibleNavItems(newcomer).map((item) => item.id).slice(0, 2)).toEqual(['dashboard', 'onboarding']);
  });

  it('splits long desktop navs into header links and a "More" menu, secondary items last (design review M7 R3)', () => {
    const foodsaver = context({
      isAuthenticated: true,
      hasRole: (role) => role === Role.Foodsaver,
      session: { user: { verified: true }, permissions: [], units: [{ id: 'g1', member: true, type: 'WORKING_GROUP' }] } as unknown as NavContext['session'],
    });
    const ordered = orderHeaderNav(
      visibleNavItems(foodsaver).filter((item) => item.id !== 'search'),
      foodsaver,
    );
    // primary items in their declared order, the occasional ones (events, polls) last; store chains are for store
    // managers only (owner feedback 2026-11-04)
    expect(ordered.map((item) => item.id)).toEqual([
      'dashboard',
      'pickups',
      'messages',
      'regions',
      'stores',
      'map',
      'baskets',
      'learn',
      'events',
      'polls',
    ]);
    const split = splitHeaderNav(ordered, 4);
    expect(split.inline.map((item) => item.id)).toEqual(['dashboard', 'pickups', 'messages', 'regions']);
    expect(split.more.map((item) => item.id)).toEqual(ordered.slice(4).map((item) => item.id));
    // everything fits: no menu; nothing measured yet: everything in the header; at least one link stays
    expect(splitHeaderNav(ordered, ordered.length)).toEqual({ inline: ordered, more: [] });
    expect(splitHeaderNav(ordered, Number.MAX_SAFE_INTEGER).more).toEqual([]);
    expect(splitHeaderNav(ordered, 0).inline.map((item) => item.id)).toEqual(['dashboard']);

    // a newcomer keeps the journey first; the quizzes are in the user menu (decision 149)
    const newcomer = context({
      isAuthenticated: true,
      session: { user: { verified: false }, onboarding: { complete: false } } as unknown as NavContext['session'],
    });
    const newcomerIds = orderHeaderNav(visibleNavItems(newcomer), newcomer).map((item) => item.id);
    expect(newcomerIds).not.toContain('quizzes');
    expect(newcomerIds.slice(0, 2)).toEqual(['dashboard', 'onboarding']);
  });

  it('filters by area and session', () => {
    // the map (M1) is public
    expect(visibleNavItems(context()).map((item) => item.id)).toEqual(['home', 'map']);
    // F3: without any region or group no Termine and Abstimmungen (their pages list only what comes from units)
    expect(visibleNavItems(context({ isAuthenticated: true })).map((item) => item.id)).toEqual([
      'dashboard',
      'messages',
      'regions',
      'learn',
      'map',
      'baskets',
      'search',
    ]);
    // the baskets and search entries follow the BASKETS / GLOBAL_SEARCH feature toggles (A43)
    const togglesOff = context({ isAuthenticated: true, featureEnabled: () => false });
    expect(visibleNavItems(togglesOff).map((item) => item.id)).not.toContain('baskets');
    expect(visibleNavItems(togglesOff).map((item) => item.id)).not.toContain('search');
    expect(
      visibleNavItems(context({ isAuthenticated: true }), 'user').map((item) => item.id),
    ).toEqual(['activity', 'quizzes', 'account', 'notifications', 'help', 'profile']);
    expect(mobileTabs(context({ isAuthenticated: true })).map((item) => item.id)).toEqual(['dashboard', 'messages']);
  });

  describe('phone tabs, user menu and drawer (decision 149)', () => {
    const unit = { id: 'r1', name: 'Zürich', homeRegion: true, admin: false, member: true, type: 'CITY' };
    const newcomer = context({
      isAuthenticated: true,
      session: { user: { verified: false }, onboarding: { complete: false }, units: [] } as unknown as NavContext['session'],
    });
    const unverified = context({
      isAuthenticated: true,
      hasRole: (role) => role === Role.Foodsaver,
      session: { user: { verified: false }, onboarding: { complete: true }, units: [unit] } as unknown as NavContext['session'],
    });
    const foodsaver = context({
      isAuthenticated: true,
      hasRole: (role) => role === Role.Foodsaver,
      session: { user: { verified: true }, onboarding: { complete: true }, units: [unit] } as unknown as NavContext['session'],
    });
    const ids = (items: { id: string }[]) => items.map((item) => item.id);

    it('gives each persona its tabs, without filler', () => {
      expect(ids(mobileTabs(newcomer))).toEqual(['onboarding', 'messages']);
      expect(ids(mobileTabs(foodsaver))).toEqual(['dashboard', 'pickups', 'stores', 'messages']);
      // decision 156 (S17): no store link before verification
      expect(ids(mobileTabs(unverified))).toEqual(['dashboard', 'messages']);
      expect(mobileTabs(context())).toEqual([]);
    });

    it('shows Mein Einstieg only while the journey is open, in the menu and as a tab', () => {
      const withJourney = (onboarding: unknown) =>
        context({ isAuthenticated: true, session: { user: { verified: false }, onboarding } as unknown as NavContext['session'] });
      for (const [onboarding, open] of [
        [{ complete: false }, true],
        [{ complete: true }, false],
        [undefined, false],
      ] as const) {
        expect(ids(visibleNavItems(withJourney(onboarding))).includes('onboarding')).toBe(open);
        expect(ids(mobileTabs(withJourney(onboarding))).includes('onboarding')).toBe(open);
        expect(ids(mobileTabs(withJourney(onboarding))).includes('dashboard')).toBe(!open);
      }
    });

    it('declares the user menu in its order: Meine Aktivität and Quiz first', () => {
      expect(ids(visibleNavItems(foodsaver, 'user'))).toEqual([
        'activity',
        'quizzes',
        'account',
        'notifications',
        'passport',
        'help',
        'profile',
      ]);
    });

    it('lists the drawer main items in side menu order, without the bell and the user items', () => {
      expect(ids(drawerItems(foodsaver))).toEqual([
        'dashboard',
        'pickups',
        'stores',
        'messages',
        'baskets',
        'map',
        'events',
        'regions',
        'polls',
        'forum',
        'learn',
        'search',
      ]);
      // F3: a newcomer without a region has no Termine, Abstimmungen or Einführungsabholungen
      expect(ids(drawerItems(newcomer))).toEqual(['dashboard', 'onboarding', 'messages', 'baskets', 'map', 'regions', 'learn', 'search']);
      // logged out: no drawer (footer pages stay out)
      expect(drawerItems(context())).toEqual([]);
    });
  });

  describe('newcomer menu (review 2026-11-10 F3, decision 176)', () => {
    const region = { id: 'r1', name: 'Zürich', homeRegion: true, admin: false, member: true, type: 'CITY' };
    const newcomer = (units: unknown[], currentStep: string) =>
      context({
        isAuthenticated: true,
        // journey role FOODSHARER: hasRole(FOODSAVER) is false
        hasRole: (role) => role === Role.Foodsharer,
        session: { user: { verified: false, role: Role.Foodsharer }, onboarding: { complete: false, currentStep }, units, permissions: [] } as unknown as NavContext['session'],
      });
    const main = (ctx: NavContext) => [...visibleNavItems(ctx).map((item) => item.id), ...sidenavSections(ctx).flatMap((s) => s.items.map((item) => item.id))];

    it('hides Termine, Abstimmungen and Einführungsabholungen from a newcomer without a region', () => {
      const ids = new Set(main(newcomer([], 'PROFILE_REGION')));
      for (const id of ['dashboard', 'onboarding', 'messages', 'bells', 'baskets', 'map', 'regions', 'learn']) {
        expect(ids.has(id), id).toBe(true);
      }
      for (const id of ['events', 'polls', 'forum', 'introPickups', 'pickups', 'stores', 'mailboxes', 'guide']) {
        expect(ids.has(id), id).toBe(false);
      }
    });

    it('gives a newcomer with a region Forum and Termine, Einführungsabholungen once the journey reached the step', () => {
      const atIntro = new Set(main(newcomer([region], 'INTRO_PICKUPS')));
      expect(['forum', 'events', 'introPickups'].every((id) => atIntro.has(id))).toBe(true);
      // polls: every poll scope needs FOODSAVER, so /polls/mine is always empty for a FOODSHARER
      expect(atIntro.has('polls')).toBe(false);
      expect(main(newcomer([region], 'ID_CHECK'))).toContain('introPickups');
      for (const step of ['PROFILE_REGION', 'LEARN', 'QUIZ', 'AGREEMENT']) {
        expect(main(newcomer([region], step)), step).not.toContain('introPickups');
      }
      // a working group is no region to book intro pickups in, but brings events
      const group = { ...region, homeRegion: false, type: 'WORKING_GROUP' };
      expect(main(newcomer([group], 'INTRO_PICKUPS'))).not.toContain('introPickups');
      expect(main(newcomer([group], 'INTRO_PICKUPS'))).toContain('events');
      // a quiz-passed FOODSAVER member may vote: Abstimmungen comes back
      const foodsaver = context({ ...newcomer([region], 'AGREEMENT'), hasRole: (role) => role === Role.Foodsaver || role === Role.Foodsharer });
      expect(main(foodsaver)).toContain('polls');
    });
  });

  it('keeps the M6 items hidden until their page flag flips, then applies their own rule', () => {
    const orga = context({
      isAuthenticated: true,
      hasRole: (role) => role === Role.Orga,
      session: { capabilities: [NavCapability.BlogManage] } as never,
    });
    const m6Ids = Object.keys(M6_PAGES_READY);
    const notReady = m6Ids.filter((id) => !M6_PAGES_READY[id as keyof typeof M6_PAGES_READY]);
    expect(visibleNavItems(orga, 'user').map((item) => item.id)).toContain('profile');
    const all = [
      ...visibleNavItems(orga),
      ...visibleNavItems(orga, 'user'),
      ...visibleNavItems(orga, 'footer'),
      ...visibleNavItems(context(), 'footer'),
    ].map((item) => item.id);
    expect(all.filter((id) => notReady.includes(id))).toEqual([]);

    const saved = { ...M6_PAGES_READY };
    try {
      for (const key of Object.keys(M6_PAGES_READY) as (keyof typeof M6_PAGES_READY)[]) {
        M6_PAGES_READY[key] = true;
      }
      const ids = (ctx: NavContext, area?: 'main' | 'user' | 'footer') => visibleNavItems(ctx, area).map((item) => item.id);
      // public map for everyone, baskets/search for logged-in users, admin tools for ORGA only
      expect(ids(context())).toContain('map');
      expect(ids(context())).not.toContain('baskets');
      const foodsaver = context({ isAuthenticated: true, hasRole: (role) => role === Role.Foodsaver });
      expect(ids(foodsaver)).toEqual(expect.arrayContaining(['map', 'baskets', 'search']));
      expect(ids(foodsaver)).not.toContain('adminSettings');
      expect(ids(orga)).toEqual(expect.arrayContaining(['adminSettings', 'adminOauth', 'adminUploads', 'adminAchievements', 'adminBounced', 'adminAdmins']));
      expect(ids(foodsaver, 'user')).toContain('profile');
      // the "about" pages are in the logged-out footer only
      const about = NAV_ITEMS.filter((item) => item.group === 'about').map((item) => item.id);
      // decision 172: team, cities and demands are gone; C1 adds the knowledge area (learnAbout)
      expect(about).toHaveLength(10);
      expect(ids(context(), 'footer')).toEqual(expect.arrayContaining(about));
      expect(ids(foodsaver, 'footer').filter((id) => about.includes(id))).toEqual([]);
      // Q11: blog management with the server capability BLOG_MANAGE (decision 162: editors and region ambassadors,
      // not every unit admin), support in the user menu; both and the footer entries of blog and support follow their
      // feature toggles (BLOG, SUPPORT_FORM)
      expect(ids(orga)).toContain('blogManage');
      expect(ids(foodsaver)).not.toContain('blogManage');
      const ambassador = context({
        isAuthenticated: true,
        hasRole: (role) => role === Role.Foodsaver,
        session: { units: [{ admin: true }], capabilities: [NavCapability.BlogManage] } as never,
      });
      expect(ids(ambassador)).toContain('blogManage');
      const groupAdmin = context({
        isAuthenticated: true,
        hasRole: (role) => role === Role.Foodsaver,
        session: { units: [{ admin: true, type: 'WORKING_GROUP' }], capabilities: [] } as never,
      });
      expect(ids(groupAdmin)).not.toContain('blogManage');
      expect(ids(foodsaver, 'user')).toContain('help');
      const off = (ctx: NavContext) => ({ ...ctx, featureEnabled: () => false });
      expect(ids(off(orga))).not.toContain('blogManage');
      expect(ids(off(foodsaver), 'user')).not.toContain('help');
      expect(ids(off(context()), 'footer')).toEqual(expect.arrayContaining(['donate']));
      expect(ids(off(context()), 'footer')).not.toContain('blog');
      expect(ids(off(context()), 'footer')).not.toContain('support');
    } finally {
      Object.assign(M6_PAGES_READY, saved);
    }
  });

  it('reads the chrome mode from the deepest route data', () => {
    const snapshot = {
      data: {},
      firstChild: { data: { chrome: 'minimal' }, firstChild: { data: {}, firstChild: null } },
    };
    expect(chromeOf(snapshot as never)).toBe('minimal');
    expect(chromeOf(null)).toBe('full');
  });

  it('shows the consent wall with minimal chrome (D3)', () => {
    const consent = LEGAL_ROUTES.find((route) => route.path === 'consent')!;
    expect(chromeOf({ data: {}, firstChild: { data: consent.data, firstChild: null } } as never)).toBe('minimal');
    const document = LEGAL_ROUTES.find((route) => route.path === 'legal/:kind')!;
    expect(chromeOf({ data: {}, firstChild: { data: document.data ?? {}, firstChild: null } } as never)).toBe('full');
  });
});

describe('ShellComponent first navigation', () => {
  it('applies the minimal chrome before the first navigation ends (no flash of the side menu on /login)', async () => {
    let release: () => void = () => undefined;
    const gate = new Promise<boolean>((resolve) => {
      release = () => resolve(true);
    });
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            {
              path: 'login',
              component: PageComponent,
              data: { chrome: 'minimal' },
              canActivate: [() => gate],
            },
          ],
        }),
        provideOptimus(),
      ],
    });
    const fixture = TestBed.createComponent(ShellComponent);
    const host = fixture.nativeElement as HTMLElement;
    const navigation = TestBed.inject(Router).navigateByUrl('/login');
    // routes are recognised, the guard still holds the navigation: NavigationEnd has not fired
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
    expect(host.getAttribute('data-chrome')).toBe('minimal');
    release();
    await navigation;
    await fixture.whenStable();
    expect(host.getAttribute('data-chrome')).toBe('minimal');
  });
});

describe('toast breakpoints', () => {
  it('keeps a 16px gutter on phones: centred at 50% (the container translates by -50%), never anchored at left: 1rem', () => {
    const phone = TOAST_BREAKPOINTS['640px'];
    expect(phone.width).toBe('calc(100vw - 2rem)');
    expect(phone.left).toBe('50%');
    expect(phone.right).toBe('auto');
  });
});
