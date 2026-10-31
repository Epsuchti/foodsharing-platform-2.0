import { placeCard } from './sidenav-popover';
import {
  canCollapse,
  foldedSections,
  isCollapseShortcut,
  isSearchShortcut,
  isSectionOpen,
  isTypingTarget,
  nextFoldCount,
  openCurrentSection,
  resolveSidenavMode,
} from './sidenav-state';

/** Rules of the desktop side menu (decision 133, docs/design/sidenav-proposal.md §9). */
describe('side menu state rules', () => {
  const key = (key: string, extra: Partial<KeyboardEvent> = {}) => ({
    key,
    ctrlKey: false,
    metaKey: false,
    altKey: false,
    target: document.body,
    ...extra,
  });

  it('shows no side menu on phones, always expands it from 75rem and lets it collapse between 48 and 75rem', () => {
    expect(resolveSidenavMode(390, false)).toBe('phone');
    expect(resolveSidenavMode(767, true)).toBe('phone');
    // 48rem = 768px: the side menu starts expanded
    expect(resolveSidenavMode(768, false)).toBe('sidebar');
    expect(resolveSidenavMode(1000, false)).toBe('sidebar');
    expect(resolveSidenavMode(1000, true)).toBe('rail');
    expect(resolveSidenavMode(1199, true)).toBe('rail');
    // 75rem = 1200px: always expanded, a collapse choice is ignored
    expect(resolveSidenavMode(1200, true)).toBe('sidebar');
    expect(resolveSidenavMode(1280, true)).toBe('sidebar');
  });

  it('offers the collapse toggle only between 48 and 75rem', () => {
    expect(canCollapse(767)).toBe(false);
    expect(canCollapse(768)).toBe(true);
    expect(canCollapse(1000)).toBe(true);
    expect(canCollapse(1199)).toBe(true);
    expect(canCollapse(1200)).toBe(false);
    expect(canCollapse(1920)).toBe(false);
  });

  it('toggles with "[" but not while typing, and not with Ctrl, Alt or Cmd alone', () => {
    expect(isCollapseShortcut(key('['))).toBe(true);
    // AltGr ([ on Swiss and German keyboards) reports Ctrl+Alt on Windows
    expect(isCollapseShortcut(key('[', { ctrlKey: true, altKey: true }))).toBe(true);
    expect(isCollapseShortcut(key('[', { ctrlKey: true }))).toBe(false);
    expect(isCollapseShortcut(key('[', { altKey: true }))).toBe(false);
    expect(isCollapseShortcut(key('[', { metaKey: true }))).toBe(false);
    expect(isCollapseShortcut(key(']'))).toBe(false);
    for (const tag of ['input', 'textarea', 'select']) {
      expect(isCollapseShortcut(key('[', { target: document.createElement(tag) })), tag).toBe(
        false,
      );
    }
    const editable = document.createElement('div');
    editable.setAttribute('contenteditable', 'true');
    const inside = document.createElement('span');
    editable.appendChild(inside);
    document.body.appendChild(editable);
    expect(isTypingTarget(inside)).toBe(true);
    expect(isCollapseShortcut(key('[', { target: inside }))).toBe(false);
    editable.remove();
  });

  it('focuses the search with Ctrl K or Cmd K, never while typing', () => {
    expect(isSearchShortcut(key('k', { ctrlKey: true }))).toBe(true);
    expect(isSearchShortcut(key('K', { metaKey: true }))).toBe(true);
    expect(isSearchShortcut(key('k'))).toBe(false);
    expect(isSearchShortcut(key('k', { ctrlKey: true, altKey: true }))).toBe(false);
    expect(
      isSearchShortcut(key('k', { ctrlKey: true, target: document.createElement('input') })),
    ).toBe(false);
  });

  it('opens Community by default, keeps Orga closed unless chosen and never closes the daily group', () => {
    const community = { id: 'community', collapsible: true, defaultOpen: true } as const;
    const orga = { id: 'orga', collapsible: true, defaultOpen: false } as const;
    const daily = { id: 'daily', collapsible: false, defaultOpen: true } as const;
    expect(isSectionOpen(community, {})).toBe(true);
    expect(isSectionOpen(orga, {})).toBe(false);
    expect(isSectionOpen(orga, { orga: true })).toBe(true);
    expect(isSectionOpen(community, { community: false })).toBe(false);
    expect(isSectionOpen(daily, { daily: false })).toBe(true);
  });

  it('opens the section of the current page on navigation and keeps the other choices', () => {
    expect(openCurrentSection({}, 'orga')).toEqual({ orga: true });
    expect(openCurrentSection({ community: false }, 'orga')).toEqual({
      community: false,
      orga: true,
    });
    const unchanged = { orga: true };
    expect(openCurrentSection(unchanged, 'orga')).toBe(unchanged);
    expect(openCurrentSection(unchanged, null)).toBe(unchanged);
  });

  it('folds rail sections from the bottom up until the rail fits', () => {
    const sections = ['community', 'manage', 'orga'] as const;
    expect([...foldedSections(sections, 0)]).toEqual([]);
    expect([...foldedSections(sections, 1)]).toEqual(['orga']);
    expect([...foldedSections(sections, 2)]).toEqual(['manage', 'orga']);
    expect([...foldedSections(sections, 5)]).toEqual(['community', 'manage', 'orga']);
    expect(nextFoldCount(0, true, 3)).toBe(1);
    expect(nextFoldCount(1, false, 3)).toBe(1);
    // everything folded: no further step even if it still overflows
    expect(nextFoldCount(3, true, 3)).toBe(3);
  });

  it('places a card outside the side menu, aligned with its trigger and inside the viewport', () => {
    const sidenav = { top: 0, bottom: 800, right: 264, height: 800 };
    // top aligned to the trigger, 12px right of the menu, notch at the trigger's middle
    expect(
      placeCard(sidenav, { top: 100, bottom: 136, right: 252, height: 36 }, 300, 800, 'top'),
    ).toEqual({
      top: 100,
      left: 276,
      notch: 18,
    });
    // bottom aligned (user block), clamped 8px from the bottom
    expect(
      placeCard(sidenav, { top: 740, bottom: 790, right: 252, height: 50 }, 400, 800, 'bottom'),
    ).toEqual({
      top: 390,
      left: 276,
      notch: 375,
    });
    // a tall card near the bottom moves up; the notch stays within the card
    const low = placeCard(
      sidenav,
      { top: 760, bottom: 796, right: 252, height: 36 },
      500,
      800,
      'top',
    );
    expect(low.top).toBe(292);
    expect(low.notch).toBe(484);
  });
});
