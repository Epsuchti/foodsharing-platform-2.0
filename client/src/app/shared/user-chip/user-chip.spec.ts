import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { Language, Role, Theme } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { UserAvatarComponent } from './user-avatar.component';
import { UserChipComponent } from './user-chip.component';
import { UserMentionComponent } from './user-mention.component';

@Component({
  imports: [UserAvatarComponent, UserChipComponent, UserMentionComponent],
  template: `
    <fs-user-chip id="default" name="Sarah" userId="u2" />
    <fs-user-chip id="off" name="Sarah" userId="u2" [link]="null" />
    <fs-user-chip id="custom" name="Sarah" userId="u2" [link]="['/users', 'u2']" />
    <fs-user-chip id="anonymous" name="Sarah" />
    <fs-user-avatar id="avatar" name="Sarah" userId="u2" link="profile" />
    <fs-user-avatar id="plain" name="Sarah" userId="u2" />
    <fs-user-mention id="other" name="Sarah" userId="u2" />
    <fs-user-mention id="me" name="Anna" [userId]="me()" />
  `,
})
class Host {
  readonly me = signal('u1');
}

describe('profile links of people (user chip, avatar, mention)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
  });

  const login = () =>
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: 'u1',
        email: 'a@b.ch',
        displayName: 'Anna',
        role: Role.Foodsaver,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });

  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return (selector: string) => el.querySelector(selector) as HTMLElement | null;
  }

  it('links chips to the profile by default when logged in; null or another link override it', async () => {
    login();
    const q = await render();
    expect(q('#default a')?.getAttribute('href')).toBe('/profile/u2');
    expect(q('#off a')).toBeNull();
    expect(q('#custom a')?.getAttribute('href')).toBe('/users/u2');
    expect(q('#anonymous a')).toBeNull();
    expect(q('#avatar a')?.getAttribute('href')).toBe('/profile/u2');
    expect(q('#avatar')?.getAttribute('role')).toBeNull();
    expect(q('#plain a')).toBeNull();
    expect(q('#plain')?.getAttribute('role')).toBe('img');
  });

  it('shows no profile links to visitors', async () => {
    const q = await render();
    expect(q('#default a')).toBeNull();
    expect(q('#avatar a')).toBeNull();
  });

  it('renders mentions as "@Name" links with a ring for the viewer', async () => {
    login();
    const q = await render();
    expect(q('#other a')?.textContent?.trim()).toBe('@Sarah');
    expect(q('#other a')?.getAttribute('href')).toBe('/profile/u2');
    expect(q('#other a')?.classList.contains('fs-mention--me')).toBe(false);
    expect(q('#me a')?.classList.contains('fs-mention--me')).toBe(true);
  });
});
