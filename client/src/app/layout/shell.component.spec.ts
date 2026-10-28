import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, DeferBlockState, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideOptimus } from '@openng/optimus-ui/config';

import { AppInfo, Language, Role, Theme } from '../api';
import { AppInfoService } from '../core/app-info.service';
import { SessionService } from '../core/auth/session.service';
import { HttpActivityService } from '../core/http/http-activity.service';
import { OnlineStatusService } from '../core/online/online-status.service';
import {
  TestingOnlineStatusService,
  provideDataStateTesting,
} from '../core/testing/data-state-testing';
import { NAV_ITEMS, NavContext, mobileTabs, visibleNavItems } from './nav.config';
import { ShellComponent, chromeOf } from './shell.component';

@Component({
  template: '<p class="page">page</p>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class PageComponent {}

const APP_INFO: AppInfo = {
  version: '1.2.3',
  devMode: true,
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
    const [overlays] = await fixture.getDeferBlocks();
    await overlays.render(DeferBlockState.Complete);
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

  it('filters by area and session', () => {
    expect(visibleNavItems(context()).map((item) => item.id)).toEqual(['home']);
    expect(visibleNavItems(context({ isAuthenticated: true })).map((item) => item.id)).toEqual([
      'dashboard',
      'messages',
      'activity',
      'regions',
      'quizzes',
    ]);
    expect(
      visibleNavItems(context({ isAuthenticated: true }), 'user').map((item) => item.id),
    ).toEqual(['account', 'notifications']);
    expect(mobileTabs(context({ isAuthenticated: true })).map((item) => item.id)).toEqual([
      'dashboard',
      'messages',
      'activity',
      'account',
    ]);
  });

  it('reads the chrome mode from the deepest route data', () => {
    const snapshot = {
      data: {},
      firstChild: { data: { chrome: 'minimal' }, firstChild: { data: {}, firstChild: null } },
    };
    expect(chromeOf(snapshot as never)).toBe('minimal');
    expect(chromeOf(null)).toBe('full');
  });
});
