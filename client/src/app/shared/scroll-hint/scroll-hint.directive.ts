import { DestroyRef, Directive, ElementRef, afterNextRender, inject } from '@angular/core';

/** The current item of a tab row: router links with `ariaCurrentWhenActive`, or tablist-style buttons. */
const ACTIVE_ITEM = '[aria-current="page"], [aria-current="true"], [aria-selected="true"]';
/** Width of the edge fade of `.fs-scroll-hint` (styles.scss): an item under the fade counts as hidden. */
const FADE_PX = 44;

/**
 * `<nav class="tabs" fsScrollHint>`: marks a horizontally scrolling row with `data-more-start` / `data-more-end`
 * while content is hidden on that side; the global `.fs-scroll-hint` styles fade that edge so a phone user sees there
 * is more to swipe to (design review M7 R1 #15: store tabs "Pinnwand", "Chat" were off-screen without any hint).
 * It also keeps the current item (`aria-current` / `aria-selected`) in view: after the first render and whenever the
 * current item changes (a navigation), an item that is off-screen or under the fade is scrolled to the middle of the
 * row (design review B4, C2, D9: the active store, region and settings tab was scrolled out of sight).
 * Only the row scrolls, never the page. Browser only (after the first render); listens to scroll and size changes of
 * the row and its content.
 */
@Directive({
  selector: '[fsScrollHint]',
  host: { class: 'fs-scroll-hint' },
})
export class ScrollHintDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const el = this.host.nativeElement;
      const update = () => updateScrollHint(el);
      el.addEventListener('scroll', update, { passive: true });
      const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(update);
      observer?.observe(el);
      for (const child of Array.from(el.children)) observer?.observe(child);
      const mutations =
        typeof MutationObserver === 'undefined'
          ? null
          : new MutationObserver((records) => {
              update();
              // a new current item (navigation) or new items in the row; not on changes inside an item (a badge count),
              // which would pull the row back while the user swipes through it
              if (records.some((r) => r.type === 'attributes' || (r.target === el && r.addedNodes.length > 0))) {
                revealActiveItem(el, true);
              }
            });
      mutations?.observe(el, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['aria-current', 'aria-selected'],
      });
      revealActiveItem(el, false);
      update();
      destroyRef.onDestroy(() => {
        el.removeEventListener('scroll', update);
        observer?.disconnect();
        mutations?.disconnect();
      });
    });
  }
}

/** Elements a keyboard user can tab to; a row with one of them can be scrolled by moving the focus through it. */
const FOCUSABLE = 'a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])';

/**
 * Sets `data-more-start` / `data-more-end` on a horizontally scrolling element (1px tolerance for sub-pixel widths).
 * A row that scrolls but holds nothing focusable (status pills, a preview strip) becomes a tab stop itself while it
 * scrolls, so it can be scrolled with the arrow keys (WCAG 2.1.1, axe "scrollable-region-focusable"); the row keeps
 * its own name (`aria-label`). A tabindex the template sets is left alone.
 */
export function updateScrollHint(el: HTMLElement): void {
  const max = el.scrollWidth - el.clientWidth;
  const left = Math.abs(el.scrollLeft);
  el.toggleAttribute('data-more-start', max > 1 && left > 1);
  el.toggleAttribute('data-more-end', max > 1 && left < max - 1);
  const added = el.hasAttribute('data-scroll-focus');
  if (max > 1 && !added && !el.hasAttribute('tabindex') && !el.querySelector(FOCUSABLE)) {
    el.setAttribute('tabindex', '0');
    el.setAttribute('data-scroll-focus', '');
  } else if (max <= 1 && added) {
    el.removeAttribute('tabindex');
    el.removeAttribute('data-scroll-focus');
  }
}

/**
 * Horizontal scroll (px) that brings an item into the middle of its row, or 0 when the item is already fully visible
 * outside the edge fades. The browser clamps the result at both ends of the row.
 */
export function scrollToReveal(
  row: Pick<DOMRect, 'left' | 'right'>,
  item: Pick<DOMRect, 'left' | 'right'>,
  fade = FADE_PX,
): number {
  const rowWidth = row.right - row.left;
  const inset = Math.min(fade, Math.max(0, (rowWidth - (item.right - item.left)) / 2));
  if (item.left >= row.left + inset - 1 && item.right <= row.right - inset + 1) {
    return 0;
  }
  return Math.round((item.left + item.right) / 2 - (row.left + row.right) / 2);
}

function revealActiveItem(el: HTMLElement, animate: boolean): void {
  if (el.scrollWidth - el.clientWidth <= 1) {
    return;
  }
  const item = el.querySelector<HTMLElement>(ACTIVE_ITEM);
  if (!item) {
    return;
  }
  const delta = scrollToReveal(el.getBoundingClientRect(), item.getBoundingClientRect());
  if (delta === 0) {
    return;
  }
  const reduced = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  el.scrollBy({ left: delta, behavior: animate && !reduced ? 'smooth' : 'auto' });
}
