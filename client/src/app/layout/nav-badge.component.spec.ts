import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { provideDataStateTesting } from '../core/testing/data-state-testing';
import { NavBadgeComponent } from './nav-badge.component';
import { NavBadgesService } from './nav-badges.service';
import { NAV_ITEMS, NavBadge } from './nav.config';

const counts = signal<Record<NavBadge, number>>({ messages: 0, mailboxes: 0 });

@Component({
  imports: [NavBadgeComponent],
  template: `@for (item of items; track item.id) {<fs-nav-badge [item]="item" />}`,
})
class HostComponent {
  readonly items = NAV_ITEMS.filter((i) => i.id === 'messages' || i.id === 'mailboxes' || i.id === 'dashboard');
}

describe('NavBadgeComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideDataStateTesting({ translations: { 'layout.nav.unread': '({count} unread)' } }),
        { provide: NavBadgesService, useValue: { count: (badge: NavBadge) => counts()[badge] } },
      ],
    });
  });

  it('shows unread counts of badge items only, capped at 99+, hidden at 0', async () => {
    expect(NAV_ITEMS.find((i) => i.id === 'messages')?.badge).toBe('messages');
    expect(NAV_ITEMS.find((i) => i.id === 'mailboxes')?.badge).toBe('mailboxes');
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('.badge').length).toBe(0);

    counts.set({ messages: 3, mailboxes: 120 });
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="nav.badge.messages"]')?.textContent?.trim()).toBe('3');
    expect(el.querySelector('[data-testid="nav.badge.mailboxes"]')?.textContent?.trim()).toBe('99+');
    expect(el.querySelector('[data-testid="nav.badge.dashboard"]')).toBeNull();
    expect(el.textContent).toContain('(3 unread)');
  });
});
