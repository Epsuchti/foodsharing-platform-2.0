import { DestroyRef, Directive, ElementRef, afterNextRender, inject } from '@angular/core';

/** CSS variable with the bottom edge of the sticky bars above the content (shell header + this bar). */
export const STICKY_OFFSET_VAR = '--fs-sticky-offset';

/**
 * `<nav class="tabs" fsStickyOffset>` on a bar that sticks right below the shell header: writes
 * `--fs-sticky-offset: calc(var(--fs-header-h) + <bar height>px)` on the bar's parent, so content after it (e.g. a
 * sub-navigation that sticks on phones) can stick directly below the bar instead of sliding underneath it. Browser
 * only; follows size changes of the bar (wrapping, font loading).
 */
@Directive({ selector: '[fsStickyOffset]' })
export class StickyOffsetDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const el = this.host.nativeElement;
      const scope = el.parentElement;
      if (!scope) return;
      const update = () =>
        scope.style.setProperty(STICKY_OFFSET_VAR, `calc(var(--fs-header-h) + ${Math.ceil(el.getBoundingClientRect().height)}px)`);
      const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(update);
      observer?.observe(el);
      update();
      destroyRef.onDestroy(() => {
        observer?.disconnect();
        scope.style.removeProperty(STICKY_OFFSET_VAR);
      });
    });
  }
}
