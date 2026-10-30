import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, Injectable, PLATFORM_ID, afterNextRender, computed, inject, signal } from '@angular/core';

import type { NavSection, NavSectionDef } from './nav.config';

/**
 * Rules of the desktop side menu (decision 133, docs/design/sidenav-proposal.md §9): pure functions (unit tested in
 * sidenav-state.spec.ts) and the service that applies them to the viewport. Nothing is persisted: a reload starts
 * expanded with the default sections.
 */

/** Below this width phones keep the header, tab bar and drawer (the existing 48rem boundary). */
export const SIDENAV_MIN_REM = 48;
/** From this width the side menu is always expanded and has no toggle. */
export const SIDENAV_WIDE_REM = 75;

/** `phone`: no side menu; `sidebar`: expanded; `rail`: collapsed to icons (48–75rem only). */
export type SidenavMode = 'phone' | 'sidebar' | 'rail';

/** Mode for a viewport width (px) and the user's collapse choice (only honoured between 48 and 75rem). */
export function resolveSidenavMode(widthPx: number, collapsed: boolean, remPx = 16): SidenavMode {
  if (widthPx < SIDENAV_MIN_REM * remPx) return 'phone';
  if (widthPx >= SIDENAV_WIDE_REM * remPx) return 'sidebar';
  return collapsed ? 'rail' : 'sidebar';
}

/** The collapse toggle (and its `[` shortcut) exists between 48 and 75rem only. */
export function canCollapse(widthPx: number, remPx = 16): boolean {
  return widthPx >= SIDENAV_MIN_REM * remPx && widthPx < SIDENAV_WIDE_REM * remPx;
}

/** Focus is in a field where `[` or Ctrl K are text (input, textarea, select, contenteditable). */
export function isTypingTarget(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  if (!element || typeof element.tagName !== 'string') return false;
  if (/^(INPUT|TEXTAREA|SELECT)$/.test(element.tagName)) return true;
  return (
    !!element.isContentEditable ||
    element.closest?.('[contenteditable=""], [contenteditable="true"]') != null
  );
}

type ShortcutEvent = Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'metaKey' | 'altKey' | 'target'>;

/**
 * `[` collapses / expands the side menu. AltGr (Ctrl+Alt on Windows) is allowed, because `[` needs AltGr on Swiss
 * and German keyboards; Ctrl, Alt or Cmd alone are browser shortcuts. Never while typing.
 */
export function isCollapseShortcut(event: ShortcutEvent): boolean {
  if (event.key !== '[' || event.metaKey || isTypingTarget(event.target)) return false;
  const altGr = event.ctrlKey && event.altKey;
  return altGr || (!event.ctrlKey && !event.altKey);
}

/** Ctrl K (Cmd K on macOS) focuses the search. Never while typing. */
export function isSearchShortcut(event: ShortcutEvent): boolean {
  if (event.key.toLowerCase() !== 'k' || event.altKey || isTypingTarget(event.target)) return false;
  return event.ctrlKey !== event.metaKey;
}

/** User's open/closed choices per section (reset on reload). */
export type SectionChoices = Partial<Record<NavSection, boolean>>;

/** A section is open when the user opened it, else by its default (Community open, Orga closed). */
export function isSectionOpen(
  def: Pick<NavSectionDef, 'id' | 'collapsible' | 'defaultOpen'>,
  choices: SectionChoices,
): boolean {
  if (!def.collapsible) return true;
  return choices[def.id] ?? def.defaultOpen;
}

/** On navigation the section of the current page opens (the user may close it again afterwards). */
export function openCurrentSection(
  choices: SectionChoices,
  current: NavSection | null,
): SectionChoices {
  if (!current || choices[current] === true) return choices;
  return { ...choices, [current]: true };
}

/** Sections folded into a rail flyout: the last `count` collapsible ones (Orga first, then Verwalten, …). */
export function foldedSections(
  collapsible: readonly NavSection[],
  count: number,
): ReadonlySet<NavSection> {
  const n = Math.max(0, Math.min(count, collapsible.length));
  return new Set(collapsible.slice(collapsible.length - n));
}

/** One more section folds while the rail overflows and something is left to fold. */
export function nextFoldCount(
  current: number,
  overflowing: boolean,
  collapsibleCount: number,
): number {
  return overflowing && current < collapsibleCount ? current + 1 : current;
}

/** Viewport state of the side menu, shared by the shell (layout) and the side menu (contents). */
@Injectable({ providedIn: 'root' })
export class SidenavStateService {
  /** Viewport ≥ 48rem (false on the server: the prerendered landing page renders the phone header). */
  readonly desktop = signal(false);
  /** Viewport ≥ 75rem: always expanded. */
  readonly wide = signal(false);
  /** The user collapsed the menu to the rail (48–75rem; not persisted). */
  readonly collapsed = signal(false);

  readonly mode = computed<SidenavMode>(() => {
    if (!this.desktop()) return 'phone';
    if (this.wide()) return 'sidebar';
    return this.collapsed() ? 'rail' : 'sidebar';
  });
  readonly canCollapse = computed(() => this.desktop() && !this.wide());

  constructor() {
    const view = inject(DOCUMENT).defaultView;
    if (!isPlatformBrowser(inject(PLATFORM_ID)) || !view?.matchMedia) return;
    const desktop = view.matchMedia(`(min-width: ${SIDENAV_MIN_REM}rem)`);
    const wide = view.matchMedia(`(min-width: ${SIDENAV_WIDE_REM}rem)`);
    const update = () => {
      this.desktop.set(desktop.matches);
      this.wide.set(wide.matches);
      // from 75rem the menu is always expanded; coming back below it starts expanded again
      if (wide.matches) this.collapsed.set(false);
    };
    // after the first render: the prerendered landing page was rendered with the phone header, and hydration needs
    // the client's first render to match it (the shell's media query already reserves the column and hides the
    // header, so the switch is not visible). An unmatched server view would stay in the DOM.
    afterNextRender(update);
    desktop.addEventListener?.('change', update);
    wide.addEventListener?.('change', update);
    inject(DestroyRef).onDestroy(() => {
      desktop.removeEventListener?.('change', update);
      wide.removeEventListener?.('change', update);
    });
  }

  /** Toggle (button or `[`): only between 48 and 75rem. */
  toggle(): void {
    if (this.canCollapse()) this.collapsed.update((value) => !value);
  }
}
