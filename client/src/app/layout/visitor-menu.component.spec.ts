import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideOptimus } from '@openng/optimus-ui/config';

import { Language, Role, Theme } from '../api';
import { SessionService } from '../core/auth/session.service';
import { provideDataStateTesting } from '../core/testing/data-state-testing';
import { ShellComponent } from './shell.component';
import { SidenavStateService } from './sidenav-state';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class PageComponent {}

describe('VisitorMenuComponent in the phone header', () => {
  async function render(options: { role?: Role; url?: string }): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: '', component: PageComponent },
            { path: 'dashboard', component: PageComponent },
            { path: 'login', component: PageComponent, data: { chrome: 'minimal' } },
          ],
        }),
        provideOptimus(),
      ],
    });
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
    const fixture = TestBed.createComponent(ShellComponent);
    await TestBed.inject(Router).navigateByUrl(options.url ?? '/');
    await fixture.whenStable();
    // phone: no side menu, the header is the frame
    TestBed.inject(SidenavStateService).desktop.set(false);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }
  const menuButton = (host: HTMLElement) => host.querySelector('[data-testid="shell.visitorMenu.open"]');

  it('shows the menu button next to log in for visitors', async () => {
    const host = await render({});
    expect(menuButton(host)).not.toBeNull();
    expect(menuButton(host)?.getAttribute('aria-expanded')).toBe('false');
    expect(host.querySelector('[data-testid="shell.login"]')).not.toBeNull();
  });

  it('leaves members to the bottom tab bar', async () => {
    const host = await render({ role: Role.Foodsaver, url: '/dashboard' });
    expect(menuButton(host)).toBeNull();
    expect(host.querySelector('[data-testid="shell.tabBar"]')).not.toBeNull();
  });

  it('has no menu in the minimal chrome (login)', async () => {
    const host = await render({ url: '/login' });
    expect(menuButton(host)).toBeNull();
  });
});
