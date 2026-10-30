import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideOptimus } from '@openng/optimus-ui/config';

import { Language, Role, Theme, UnitMembership } from '../api';
import { SessionService } from '../core/auth/session.service';
import { provideDataStateTesting } from '../core/testing/data-state-testing';
import { journeyProgress } from '../features/onboarding/components/journey-meter.component';
import { NAV_ITEMS, NavContext, activeNavId, matchesPrefix, sidenavSections } from './nav.config';
import { filterUnits } from './region-switcher.component';
import { ShellComponent } from './shell.component';
import { SidenavStateService } from './sidenav-state';

describe('side menu nav.config (decision 133)', () => {
  const context = (overrides: Partial<NavContext> = {}): NavContext => ({
    session: null,
    isAuthenticated: false,
    hasRole: () => false,
    hasPermission: () => false,
    devMode: false,
    ...overrides,
  });
  const unit = (overrides: Partial<UnitMembership>): UnitMembership =>
    ({
      id: 'r1',
      name: 'Zürich',
      slug: 'zuerich',
      type: 'CITY',
      member: true,
      admin: false,
      homeRegion: false,
      ...overrides,
    }) as UnitMembership;
  const shape = (ctx: NavContext) =>
    Object.fromEntries(
      sidenavSections(ctx).map((section) => [section.def.id, section.items.map((item) => item.id)]),
    );

  const foodsaver = context({
    isAuthenticated: true,
    hasRole: (role) => role === Role.Foodsaver,
    session: {
      user: { verified: true },
      permissions: [],
      units: [unit({ homeRegion: true })],
    } as unknown as NavContext['session'],
  });

  it('gives every main item a section (nothing falls into a "More" menu)', () => {
    const main = NAV_ITEMS.filter(
      (item) => (item.area ?? 'main') === 'main' && item.id !== 'search',
    );
    expect(main.filter((item) => !item.section).map((item) => item.id)).toEqual([]);
  });

  it('lists a foodsaver in fixed places: daily group with the bell, Community, store chains in Verwalten', () => {
    expect(shape(foodsaver)).toEqual({
      daily: ['dashboard', 'pickups', 'stores', 'messages', 'bells', 'baskets'],
      community: ['map', 'regions', 'events', 'polls', 'quizzes', 'activity'],
      manage: ['storeChains'],
    });
  });

  it('shows the journey for newcomers and the admin sections for ambassadors and orga', () => {
    const newcomer = context({
      isAuthenticated: true,
      session: {
        onboarding: { complete: false },
        user: { verified: false },
        units: [],
      } as unknown as NavContext['session'],
    });
    expect(shape(newcomer)).toEqual({
      daily: ['dashboard', 'onboarding', 'messages', 'bells', 'baskets'],
      community: ['map', 'regions', 'introPickups', 'events', 'polls', 'quizzes', 'activity'],
    });

    const ambassador = context({
      isAuthenticated: true,
      hasRole: (role) => role === Role.Foodsaver || role === Role.StoreManager,
      session: {
        user: { verified: true },
        permissions: [],
        units: [unit({ admin: true, homeRegion: true })],
      } as unknown as NavContext['session'],
    });
    expect(shape(ambassador)['manage']).toEqual([
      'newcomers',
      'verification',
      'users',
      'newsletters',
      'blogManage',
      'storeChains',
    ]);
    expect(shape(ambassador)['daily']).toContain('mailboxes');
    expect(shape(ambassador)['orga']).toBeUndefined();

    const orga = context({
      isAuthenticated: true,
      hasRole: () => true,
      hasPermission: () => true,
      session: {
        user: { verified: true },
        permissions: [],
        units: [],
      } as unknown as NavContext['session'],
    });
    expect(shape(orga)['orga']).toEqual([
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
    ]);
  });

  it('gives visitors the public pages: start, map, statistics, blog, then the about pages', () => {
    expect(shape(context())).toEqual({
      daily: ['home', 'map', 'statistics', 'blog'],
      about: [
        'vision',
        'about',
        'team',
        'partners',
        'press',
        'cities',
        'demands',
        'education',
        'donate',
        'faq',
      ],
    });
  });

  it('marks the item of the current page by the longest route prefix', () => {
    const items = sidenavSections(foodsaver).flatMap((section) => section.items);
    expect(activeNavId('/stores/mine', items)).toBe('stores');
    expect(activeNavId('/stores/12/team?tab=1', items)).toBe('stores');
    expect(activeNavId('/store-chains', items)).toBe('storeChains');
    expect(activeNavId('/regions/5/forum', items)).toBe('regions');
    expect(activeNavId('/events/7', items)).toBe('events');
    expect(activeNavId('/dashboard#top', items)).toBe('dashboard');
    expect(activeNavId('/account/settings', items)).toBeNull();

    const ambassador = context({
      isAuthenticated: true,
      hasRole: () => true,
      hasPermission: () => true,
      session: {
        user: { verified: true },
        permissions: [],
        units: [unit({ admin: true })],
      } as unknown as NavContext['session'],
    });
    const all = sidenavSections(ambassador).flatMap((section) => section.items);
    // a deeper route of another item wins over the region prefix
    expect(activeNavId('/regions/home/verification', all)).toBe('verification');
    expect(activeNavId('/quizzes/edit/3', all)).toBe('adminQuizzes');
    expect(activeNavId('/quizzes/3', all)).toBe('quizzes');

    const visitor = sidenavSections(context()).flatMap((section) => section.items);
    // the start page matches exactly, "Über uns" too (its sub-pages are their own items)
    expect(activeNavId('/', visitor)).toBe('home');
    expect(activeNavId('/about', visitor)).toBe('about');
    expect(activeNavId('/about/press', visitor)).toBe('press');
    expect(activeNavId('/login', visitor)).toBeNull();
  });

  it('matches whole path segments only', () => {
    expect(matchesPrefix('/stores', '/stores')).toBe(true);
    expect(matchesPrefix('/stores/1', '/stores')).toBe(true);
    expect(matchesPrefix('/store-chains', '/stores')).toBe(false);
    expect(matchesPrefix('/storesX', '/stores')).toBe(false);
    expect(matchesPrefix('/dashboard', '/')).toBe(false);
  });

  it('filters the switcher units by name, ignoring case and accents', () => {
    const units = [unit({ name: 'Zürich Stadt' }), unit({ id: 'r2', name: 'Winterthur' })];
    expect(filterUnits(units, 'zur').map((u) => u.name)).toEqual(['Zürich Stadt']);
    expect(filterUnits(units, '  ').length).toBe(2);
  });

  it('counts the journey progress from the main steps', () => {
    const steps = ['ACCOUNT', 'PROFILE_REGION', 'LEARN', 'QUIZ', 'INTRO', 'VERIFY'].map(
      (key, index) => ({
        key,
        status: index < 2 ? 'DONE' : 'AVAILABLE',
      }),
    );
    expect(journeyProgress({ steps, currentStep: 'LEARN' } as never)).toEqual({
      current: 3,
      total: 6,
      done: 2 / 6,
    });
    expect(journeyProgress({ steps, currentStep: undefined } as never).current).toBe(3);
  });
});

@Component({
  template: '<p class="page">page</p>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class PageComponent {}

describe('SidenavComponent in the shell', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let host: HTMLElement;
  let state: SidenavStateService;
  const byTestId = (id: string) => host.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  async function setUp(options: { wide: boolean; role?: Role }): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: '', component: PageComponent },
            { path: 'dashboard', component: PageComponent },
            { path: 'stores/:id', component: PageComponent },
            { path: 'admin/jobs', component: PageComponent },
            { path: 'login', component: PageComponent, data: { chrome: 'minimal' } },
          ],
        }),
        provideOptimus(),
      ],
    });
    state = TestBed.inject(SidenavStateService);
    state.desktop.set(true);
    state.wide.set(options.wide);
    if (options.role) {
      TestBed.inject(SessionService).applySession({
        authenticated: true,
        permissions: [],
        units: [],
        user: {
          id: 'u1',
          email: 'anna@local',
          displayName: 'Anna Muster',
          role: options.role,
          language: Language.De,
          theme: Theme.System,
          verified: true,
        },
      });
    }
    fixture = TestBed.createComponent(ShellComponent);
    host = fixture.nativeElement as HTMLElement;
    await TestBed.inject(Router).navigateByUrl(options.role ? '/dashboard' : '/');
    await fixture.whenStable();
  }

  async function press(key: string, target: EventTarget = document.body): Promise<void> {
    target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    await fixture.whenStable();
  }

  it('replaces the header on desktop and has no toggle from 75rem', async () => {
    await setUp({ wide: true, role: Role.Foodsaver });
    expect(byTestId('nav.sidenav')?.getAttribute('data-mode')).toBe('sidebar');
    expect(host.querySelector('fs-header')).toBeNull();
    expect(byTestId('nav.sidenav.toggle')).toBeNull();
    expect(byTestId('nav.dashboard')?.getAttribute('aria-current')).toBe('page');
    expect(byTestId('shell.userMenu')?.textContent).toContain('Anna Muster');
    expect(byTestId('bells.button')).not.toBeNull();
    // "[" does nothing at this width
    await press('[');
    expect(byTestId('nav.sidenav')?.getAttribute('data-mode')).toBe('sidebar');
  });

  it('collapses to the rail with the toggle and "[" between 48 and 75rem, not while typing', async () => {
    await setUp({ wide: false, role: Role.Foodsaver });
    const toggle = byTestId('nav.sidenav.toggle')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    toggle.click();
    await fixture.whenStable();
    expect(byTestId('nav.sidenav')?.getAttribute('data-mode')).toBe('rail');
    expect(host.getAttribute('data-nav')).toBe('rail');
    expect(byTestId('nav.sidenav.toggle')?.getAttribute('aria-expanded')).toBe('false');

    await press('[');
    expect(byTestId('nav.sidenav')?.getAttribute('data-mode')).toBe('sidebar');

    const input = document.createElement('input');
    document.body.appendChild(input);
    await press('[', input);
    expect(byTestId('nav.sidenav')?.getAttribute('data-mode')).toBe('sidebar');
    input.remove();
  });

  it('marks the parent item current by route prefix and opens Orga only when its page is current', async () => {
    await setUp({ wide: true, role: Role.Orga });
    const orga = byTestId('nav.section.orga')!;
    expect(orga.getAttribute('aria-expanded')).toBe('false');
    expect(byTestId('nav.section.orga.list')?.hidden).toBe(true);
    expect(byTestId('nav.section.community')?.getAttribute('aria-expanded')).toBe('true');
    expect(byTestId('nav.adminJobs')?.getAttribute('data-nav-section')).toBe('orga');

    await TestBed.inject(Router).navigateByUrl('/stores/12');
    await fixture.whenStable();
    expect(byTestId('nav.stores')?.getAttribute('aria-current')).toBe('page');
    expect(byTestId('nav.dashboard')?.getAttribute('aria-current')).toBeNull();

    await TestBed.inject(Router).navigateByUrl('/admin/jobs');
    await fixture.whenStable();
    expect(byTestId('nav.section.orga')?.getAttribute('aria-expanded')).toBe('true');
    expect(byTestId('nav.adminJobs')?.getAttribute('aria-current')).toBe('page');
    // the user may close it again
    byTestId('nav.section.orga')!.click();
    await fixture.whenStable();
    expect(byTestId('nav.section.orga')?.getAttribute('aria-expanded')).toBe('false');
  });

  it('opens the user menu as a card, closes it with Escape and returns the focus', async () => {
    await setUp({ wide: true, role: Role.Foodsaver });
    const trigger = byTestId('shell.userMenu')!;
    const card = byTestId('shell.userMenu.card')!;
    expect(card.hidden).toBe(true);
    trigger.click();
    await fixture.whenStable();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(card.hidden).toBe(false);
    expect(byTestId('shell.userMenu.language.fr')).not.toBeNull();
    // one card at a time: the bell replaces the user menu
    byTestId('bells.button')!.click();
    await fixture.whenStable();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(byTestId('bells.button')?.getAttribute('aria-expanded')).toBe('true');

    await press('Escape');
    expect(byTestId('bells.button')?.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(byTestId('bells.button'));
  });

  it('gives visitors the side menu with language and theme and the login buttons', async () => {
    await setUp({ wide: true });
    expect(byTestId('nav.home')?.getAttribute('aria-current')).toBe('page');
    expect(byTestId('shell.userMenu')).toBeNull();
    expect(byTestId('shell.login')?.getAttribute('href')).toBe('/login');
    expect(byTestId('shell.register')).not.toBeNull();
    byTestId('shell.prefs')!.click();
    await fixture.whenStable();
    expect(byTestId('shell.prefs')?.getAttribute('aria-expanded')).toBe('true');
    expect(byTestId('shell.prefs.theme.dark')).not.toBeNull();
    // a click outside closes it
    host.querySelector<HTMLElement>('.page')!.click();
    await fixture.whenStable();
    expect(byTestId('shell.prefs')?.getAttribute('aria-expanded')).toBe('false');
  });

  it('keeps the header on minimal-chrome pages', async () => {
    await setUp({ wide: true });
    await TestBed.inject(Router).navigateByUrl('/login');
    await fixture.whenStable();
    expect(byTestId('nav.sidenav')).toBeNull();
    expect(host.querySelector('fs-header')).not.toBeNull();
  });
});
