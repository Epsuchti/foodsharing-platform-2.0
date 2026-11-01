import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { NavigateButtonComponent } from './navigate-button.component';
import { NavigationLinks, navigationLinks } from './navigate.util';

describe('navigationLinks', () => {
  it('links to the coordinates when both are set', () => {
    const links = navigationLinks({ lat: 47.3779, lon: 8.5427, street: 'Bahnhofbrücke 1', city: 'Zürich', label: 'Migros City' });
    expect(links?.google).toBe('https://www.google.com/maps/dir/?api=1&destination=47.3779,8.5427');
    expect(links?.apple).toBe('https://maps.apple.com/?daddr=47.3779,8.5427&q=Migros%20City');
  });

  it('treats 0 coordinates as set', () => {
    const links = navigationLinks({ lat: 0, lon: 0, city: 'Zürich' });
    expect(links?.google).toContain('destination=0,0');
    expect(links?.apple).toContain('daddr=0,0');
  });

  it('falls back to the address and skips empty parts', () => {
    const links = navigationLinks({ lat: 47.3, lon: null, street: 'Bahnhofbrücke 1', postcode: '', city: 'Zürich' });
    expect(links?.google).toBe('https://www.google.com/maps/dir/?api=1&destination=Bahnhofbr%C3%BCcke%201%20Z%C3%BCrich');
    expect(links?.apple).toBe('https://maps.apple.com/?daddr=Bahnhofbr%C3%BCcke%201%20Z%C3%BCrich');
  });

  it('is null without coordinates and address', () => {
    expect(navigationLinks({})).toBeNull();
    expect(navigationLinks({ lat: 47.3, street: '', postcode: '', city: '', label: 'X' })).toBeNull();
  });

  it('encodes the label and leaves out q without one', () => {
    expect(navigationLinks({ lat: 1, lon: 2, label: 'Café & Co' })?.apple).toBe('https://maps.apple.com/?daddr=1,2&q=Caf%C3%A9%20%26%20Co');
    expect(navigationLinks({ lat: 1, lon: 2 })?.apple).not.toContain('q=');
  });
});

@Component({
  imports: [NavigateButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<fs-navigate-button [links]="links" testId="test.page.navigate" [note]="note()" />`,
})
class HostComponent {
  readonly links: NavigationLinks = { google: 'https://google.test/dir', apple: 'https://apple.test/dir' };
  readonly note = signal<string | undefined>(undefined);
}

describe('NavigateButtonComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: { 'shared.navigate.button': 'Navigieren', 'shared.navigate.google': 'Google Maps', 'shared.navigate.apple': 'Apple Karten' },
        }),
        provideOptimus(),
      ],
    });
  });

  const byId = (id: string) => document.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;

  async function open(note?: string) {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.note.set(note);
    fixture.detectChanges();
    await fixture.whenStable();
    const trigger = byId('test.page.navigate') as HTMLButtonElement;
    expect(trigger.getAttribute('aria-haspopup')).toBe('menu');
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    trigger.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, trigger };
  }

  afterEach(() => document.querySelectorAll('.p-popover').forEach((node) => node.remove()));

  it('opens a menu with both directions links in a new tab', async () => {
    const { fixture, trigger } = await open();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    const google = byId('test.page.navigate.google') as HTMLAnchorElement;
    const apple = byId('test.page.navigate.apple') as HTMLAnchorElement;
    expect(google.getAttribute('href')).toBe('https://google.test/dir');
    expect(apple.getAttribute('href')).toBe('https://apple.test/dir');
    expect(google.target).toBe('_blank');
    expect(apple.target).toBe('_blank');
    expect(apple.rel).toContain('noopener');
    expect(byId('test.page.navigate.note')).toBeNull();
    fixture.destroy();
  });

  it('shows the note only when given', async () => {
    const { fixture } = await open('Ungefähr');
    expect(byId('test.page.navigate.note')?.textContent).toContain('Ungefähr');
    fixture.destroy();
  });
});
