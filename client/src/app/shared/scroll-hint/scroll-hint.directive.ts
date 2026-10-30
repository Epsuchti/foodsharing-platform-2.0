import { DestroyRef, Directive, ElementRef, afterNextRender, inject } from '@angular/core';

/**
 * `<nav class="tabs" fsScrollHint>`: marks a horizontally scrolling row with `data-more-start` / `data-more-end`
 * while content is hidden on that side; the global `.fs-scroll-hint` styles fade that edge so a phone user sees there
 * is more to swipe to (design review M7 R1 #15: store tabs "Pinnwand", "Chat" were off-screen without any hint).
 * Browser only (after the first render); listens to scroll and size changes of the row and its content.
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
      const mutations = typeof MutationObserver === 'undefined' ? null : new MutationObserver(update);
      mutations?.observe(el, { childList: true });
      update();
      destroyRef.onDestroy(() => {
        el.removeEventListener('scroll', update);
        observer?.disconnect();
        mutations?.disconnect();
      });
    });
  }
}

/** Sets `data-more-start` / `data-more-end` on a horizontally scrolling element (1px tolerance for sub-pixel widths). */
export function updateScrollHint(el: HTMLElement): void {
  const max = el.scrollWidth - el.clientWidth;
  const left = Math.abs(el.scrollLeft);
  el.toggleAttribute('data-more-start', max > 1 && left > 1);
  el.toggleAttribute('data-more-end', max > 1 && left < max - 1);
}
